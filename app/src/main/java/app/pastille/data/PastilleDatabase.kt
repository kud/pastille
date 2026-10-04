package app.pastille.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SnippetEntity::class, CategoryEntity::class], version = 3, exportSchema = true)
abstract class PastilleDatabase : RoomDatabase() {
    abstract fun snippets(): SnippetDao

    abstract fun categories(): CategoryDao
}
