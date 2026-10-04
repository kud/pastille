package app.pastille.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SnippetEntity::class], version = 1, exportSchema = true)
abstract class PastilleDatabase : RoomDatabase() {
    abstract fun snippets(): SnippetDao
}
