package app.pastille.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val createdAt: Long,
)

// No SQLite foreign keys (house rule): the repository removes these rows with their snippet or tag.
@Entity(
    tableName = "snippet_tags",
    primaryKeys = ["snippetId", "tagId"],
    indices = [Index(value = ["tagId"])],
)
data class SnippetTagEntity(
    val snippetId: Long,
    val tagId: Long,
)

data class SnippetTagName(
    val snippetId: Long,
    val name: String,
)
