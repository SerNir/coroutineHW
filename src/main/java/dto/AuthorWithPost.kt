package dto

data class AuthorWithPost (
    val author: Author,
    val posts: List<Post>
        )