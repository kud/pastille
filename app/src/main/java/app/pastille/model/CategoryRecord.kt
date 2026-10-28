package app.pastille.model

data class CategoryRecord(
    val id: Long = 0,
    val name: String,
    val position: Int = 0,
    val uid: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)
