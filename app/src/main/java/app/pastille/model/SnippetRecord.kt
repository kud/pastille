package app.pastille.model

data class SnippetRecord(
    val id: Long = 0,
    val title: String = "",
    val text: String,
    val pinned: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val lastUsedAt: Long = 0,
)
