package app.pastille.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SnippetEntity::class, CategoryEntity::class, TagEntity::class, SnippetTagEntity::class],
    version = 5,
    exportSchema = true,
)
abstract class PastilleDatabase : RoomDatabase() {
    abstract fun snippets(): SnippetDao

    abstract fun categories(): CategoryDao

    abstract fun tags(): TagDao
}
