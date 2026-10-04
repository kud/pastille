package app.pastille.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SnippetDao {
    @Query("SELECT * FROM snippets ORDER BY position ASC, lastUsedAt DESC")
    fun observeAll(): Flow<List<SnippetEntity>>

    @Query("SELECT * FROM snippets ORDER BY position ASC, lastUsedAt DESC")
    suspend fun getAll(): List<SnippetEntity>

    @Query("SELECT * FROM snippets WHERE id = :id")
    suspend fun getById(id: Long): SnippetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SnippetEntity): Long

    @Query("DELETE FROM snippets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE snippets SET lastUsedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("UPDATE snippets SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun clearCategory(categoryId: Long)

    @Query("SELECT * FROM snippets WHERE imageFile IS NULL AND text = :text LIMIT 1")
    suspend fun findTextDuplicate(text: String): SnippetEntity?

    @Query("SELECT * FROM snippets WHERE imageFile = :name LIMIT 1")
    suspend fun findByImageFile(name: String): SnippetEntity?

    @Query("SELECT imageFile FROM snippets WHERE imageFile IS NOT NULL")
    suspend fun allImageFiles(): List<String>

    @Query("UPDATE snippets SET categoryId = :categoryId WHERE id IN (:ids)")
    suspend fun setCategory(ids: List<Long>, categoryId: Long?)

    @Query("SELECT title FROM snippets WHERE categoryId IS :categoryId AND id != :excludeId")
    suspend fun titlesInCategory(categoryId: Long?, excludeId: Long): List<String>

    @Query("SELECT * FROM snippets WHERE TRIM(title) = ''")
    suspend fun getBlankTitled(): List<SnippetEntity>

    @Query("UPDATE snippets SET title = :title WHERE id = :id")
    suspend fun setTitle(id: Long, title: String)

    @Query("DELETE FROM snippets WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE snippets SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)

    @Query("SELECT * FROM snippets WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<SnippetEntity>

    @Query("SELECT MIN(position) FROM snippets")
    suspend fun minPosition(): Int?
}
