package app.pastille.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "snippets",
    indices = [Index(value = ["categoryId"]), Index(value = ["deletedAt"])],
)
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
    @ColumnInfo(defaultValue = "0") val position: Int = 0,
    // Set while the snippet is in the bin; null for live snippets.
    val deletedAt: Long? = null,
    // A sticker is an image snippet shown in the Stickers tab instead of under Snippets.
    @ColumnInfo(defaultValue = "0") val sticker: Boolean = false,
)
