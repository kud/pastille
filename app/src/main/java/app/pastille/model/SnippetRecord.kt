package app.pastille.model

data class SnippetRecord(
    val id: Long = 0,
    val title: String = "",
    val text: String,
    val pinned: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val lastUsedAt: Long = 0,
    val categoryId: Long? = null,
    val imageFile: String? = null,
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
    val position: Int = 0,
    val deletedAt: Long? = null,
    // Attached in memory from snippet_tags; not a column.
    val tags: List<String> = emptyList(),
) {
    val isImage: Boolean get() = imageFile != null
}
