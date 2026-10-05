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
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build().also { instance = it }
        }
}
