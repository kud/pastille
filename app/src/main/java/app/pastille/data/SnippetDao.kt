package app.pastille.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SnippetDao {
    @Query("SELECT * FROM snippets ORDER BY pinned DESC, lastUsedAt DESC")
    fun observeAll(): Flow<List<SnippetEntity>>

    @Query("SELECT * FROM snippets ORDER BY pinned DESC, lastUsedAt DESC")
    suspend fun getAll(): List<SnippetEntity>

    @Query("SELECT * FROM snippets WHERE id = :id")
    suspend fun getById(id: Long): SnippetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SnippetEntity): Long

    @Query("DELETE FROM snippets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE snippets SET lastUsedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)
}
