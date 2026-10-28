package app.pastille.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TombstoneDao {
    @Query("SELECT * FROM tombstones")
    suspend fun getAll(): List<TombstoneEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<TombstoneEntity>)

    @Query("DELETE FROM tombstones")
    suspend fun clear()
}
