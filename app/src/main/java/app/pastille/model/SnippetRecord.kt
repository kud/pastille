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
    val sticker: Boolean = false,
    // Attached in memory from snippet_tags; not a column.
    val tags: List<String> = emptyList(),
    // Stable across devices (v7); empty only on a record not yet saved.
    val uid: String = "",
) {
    val isImage: Boolean get() = imageFile != null
}
