import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dto.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.lang.RuntimeException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private val gson = Gson()
private const val BASE_URL = "http://127.0.0.1:9999/api/slow/"
private val client = OkHttpClient.Builder()
    .addInterceptor(HttpLoggingInterceptor(::println).apply {
        level = HttpLoggingInterceptor.Level.BODY
    })
    .connectTimeout(30, TimeUnit.SECONDS)
    .build()

fun main() {
    val scope = CoroutineScope(EmptyCoroutineContext)

    scope.launch {

        try {
            val posts = getPosts(client)
                .map { post ->
                    async {
                        val author = getAuthor(client, post.authorId)
                        val comments = getComments(client, post.id)
                            .map { comment ->
                                async {
                                    CommentWithAuthor(comment, getAuthor(client, comment.authorId))
                                }
                            }.awaitAll()
                        PostWithAuthorsAndComments(post, author, comments)
                    }
                }.awaitAll()
            println(posts)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Thread.sleep(35_000)
}

suspend fun <T> makeRequest(endpoint: String, client: OkHttpClient, typeToken: TypeToken<T>): T =
    withContext(Dispatchers.IO) {
        client.apiCall("$BASE_URL$endpoint")
            .let { response ->
                if (!response.isSuccessful) {
                    response.close()
                    throw RuntimeException(response.message)
                }
                val body = response.body ?: throw RuntimeException("Response body is null")
                gson.fromJson(body.string(), typeToken.type)
            }

    }

suspend fun getPosts(client: OkHttpClient): List<Post> = makeRequest("posts", client, object : TypeToken<List<Post>>() {})

suspend fun getComments(client: OkHttpClient,id: Long): List<Comment> =
    makeRequest("posts/$id/comments", client, object : TypeToken<List<Comment>>() {})

suspend fun getAuthor(client: OkHttpClient, id: Long): Author =
    makeRequest("authors/$id", client, object : TypeToken<Author>() {})

suspend fun OkHttpClient.apiCall(url: String): Response {
    return suspendCoroutine { continuation ->
        Request.Builder()
            .url(url)
            .build()
            .let(client::newCall)
            .enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }

            })
    }
}

