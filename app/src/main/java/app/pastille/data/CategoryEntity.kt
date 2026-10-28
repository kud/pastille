package app.pastille.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true), Index(value = ["uid"], unique = true)])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val position: Int,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "''") val uid: String = newUid(),
    // Rename or reorder (v7); a new folder starts at its createdAt.
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = createdAt,
)

/** A fresh cross-device id: a random UUID, lowercase. */
fun newUid(): String = java.util.UUID.randomUUID().toString()
