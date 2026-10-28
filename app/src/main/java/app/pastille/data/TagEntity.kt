package app.pastille.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true), Index(value = ["uid"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "''") val uid: String = newUid(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = createdAt,
)

/**
 * A deletion that has to reach the other devices (v7): a snippet deleted for good (Empty bin, the
 * 30-day purge, Delete forever) or a folder deleted. Kept after the row is gone, so a sync can tell
 * "deleted here" from "never seen here". `kind` is "snippet" or "category".
 */
@Entity(tableName = "tombstones")
data class TombstoneEntity(
    @PrimaryKey val uid: String,
    val kind: String,
    val deletedAt: Long,
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
