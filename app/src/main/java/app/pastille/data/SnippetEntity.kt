package app.pastille.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "snippets", indices = [Index(value = ["categoryId"])])
data class SnippetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val text: String,
    val pinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long,
    val categoryId: Long? = null,
    val imageFile: String? = null,
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
)
