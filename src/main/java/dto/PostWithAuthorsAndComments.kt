package dto

data class PostWithAuthorsAndComments(
    val post: Post,
    val author: Author,
    val comments: List<CommentWithAuthor>)