package app.pastille.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SnippetDao {
    // Every snippet reader carries `sticker = 0`: stickers live only in their own tab.
    @Query("SELECT * FROM snippets WHERE deletedAt IS NULL AND sticker = 0 ORDER BY position ASC, lastUsedAt DESC")
    fun observeAll(): Flow<List<SnippetEntity>>

    // The export: stickers stay in, so its summary counts them with the image snippets it leaves out.
    @Query("SELECT * FROM snippets WHERE deletedAt IS NULL ORDER BY position ASC, lastUsedAt DESC")
    suspend fun getAll(): List<SnippetEntity>

    @Query("SELECT * FROM snippets WHERE id = :id")
    suspend fun getById(id: Long): SnippetEntity?

    // Any row, the bin included: uids are unique across the table, and upsert replaces on conflict.
    @Query("SELECT * FROM snippets WHERE uid = :uid LIMIT 1")
    suspend fun findByUid(uid: String): SnippetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SnippetEntity): Long

    @Query("DELETE FROM snippets WHERE id = :id")
    suspend fun deleteById(id: Long)

    // A use, not an edit (v7): updatedAt stays, so sync doesn't treat inserting a snippet as editing it.
    @Query("UPDATE snippets SET lastUsedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("UPDATE snippets SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun clearCategory(categoryId: Long)

    @Query("SELECT * FROM snippets WHERE imageFile IS NULL AND text = :text AND deletedAt IS NULL AND sticker = 0 LIMIT 1")
    suspend fun findTextDuplicate(text: String): SnippetEntity?

    @Query("SELECT * FROM snippets WHERE imageFile = :name AND deletedAt IS NULL LIMIT 1")
    suspend fun findByImageFile(name: String): SnippetEntity?

    // Binned rows count: their files stay on disk until the row is purged.
    @Query("SELECT imageFile FROM snippets WHERE imageFile IS NOT NULL")
    suspend fun allImageFiles(): List<String>

    @Query("UPDATE snippets SET categoryId = :categoryId, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setCategory(ids: List<Long>, categoryId: Long?, now: Long)

    @Query("SELECT title FROM snippets WHERE categoryId IS :categoryId AND id != :excludeId AND deletedAt IS NULL")
    suspend fun titlesInCategory(categoryId: Long?, excludeId: Long): List<String>

    @Query("SELECT * FROM snippets WHERE TRIM(title) = ''")
    suspend fun getBlankTitled(): List<SnippetEntity>

    @Query("UPDATE snippets SET title = :title WHERE id = :id")
    suspend fun setTitle(id: Long, title: String)

    @Query("UPDATE snippets SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun editTitle(id: Long, title: String, now: Long)

    @Query("DELETE FROM snippets WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE snippets SET position = :position, updatedAt = :now WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int, now: Long)

    @Query("SELECT * FROM snippets WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<SnippetEntity>

    @Query("SELECT MIN(position) FROM snippets")
    suspend fun minPosition(): Int?

    @Query("UPDATE snippets SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun moveToBin(id: Long, now: Long)

    @Query("UPDATE snippets SET deletedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun restoreFromBin(id: Long, now: Long)

    @Query("SELECT * FROM snippets WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeBin(): Flow<List<SnippetEntity>>

    @Query("DELETE FROM snippets WHERE deletedAt IS NOT NULL AND id = :id")
    suspend fun deleteBinned(id: Long)

    @Query("SELECT id FROM snippets WHERE deletedAt IS NOT NULL")
    suspend fun binnedIds(): List<Long>

    @Query("SELECT id FROM snippets WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun expiredBinnedIds(cutoff: Long): List<Long>

    @Query("SELECT * FROM snippets WHERE sticker = 1 AND imageFile IS NOT NULL AND deletedAt IS NULL ORDER BY position ASC")
    fun observeStickers(): Flow<List<SnippetEntity>>

    @Query("SELECT MAX(position) FROM snippets WHERE sticker = 1")
    suspend fun maxStickerPosition(): Int?

    @Query("UPDATE snippets SET sticker = :sticker, categoryId = :categoryId, position = :position, updatedAt = :now WHERE id = :id")
    suspend fun setSticker(id: Long, sticker: Boolean, categoryId: Long?, position: Int, now: Long)

    // Sync (v7): every row, the bin included, and the few writes a merge needs.
    @Query("SELECT * FROM snippets")
    suspend fun getEverything(): List<SnippetEntity>

    @Query("SELECT uid FROM snippets WHERE id IN (:ids)")
    suspend fun uidsFor(ids: List<Long>): List<String>

    @Query("UPDATE snippets SET uid = :uid WHERE id = :id")
    suspend fun setUid(id: Long, uid: String)
}
