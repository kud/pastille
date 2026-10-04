package app.pastille.data

import android.content.Context
import androidx.room.Room

object DatabaseHolder {
    @Volatile
    private var instance: PastilleDatabase? = null

    fun get(context: Context): PastilleDatabase =
        instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                PastilleDatabase::class.java,
                "pastille.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
}
