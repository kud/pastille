package app.pastille.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TagEntity): Long

    @Query(
        "SELECT snippet_tags.snippetId AS snippetId, tags.name AS name FROM snippet_tags " +
            "JOIN tags ON tags.id = snippet_tags.tagId ORDER BY tags.name COLLATE NOCASE ASC",
    )
    fun observeSnippetTags(): Flow<List<SnippetTagName>>

    @Query(
        "SELECT snippet_tags.snippetId AS snippetId, tags.name AS name FROM snippet_tags " +
            "JOIN tags ON tags.id = snippet_tags.tagId WHERE snippet_tags.snippetId IN (:snippetIds) " +
            "ORDER BY tags.name COLLATE NOCASE ASC",
    )
    suspend fun tagsFor(snippetIds: List<Long>): List<SnippetTagName>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(entity: SnippetTagEntity)

    @Query("DELETE FROM snippet_tags WHERE snippetId = :snippetId")
    suspend fun unlinkSnippet(snippetId: Long)

    @Query("DELETE FROM snippet_tags WHERE snippetId IN (:snippetIds)")
    suspend fun unlinkSnippets(snippetIds: List<Long>)

    // A tag nobody carries any more, binned snippets included, leaves the suggestions.
    @Query("DELETE FROM tags WHERE id NOT IN (SELECT tagId FROM snippet_tags)")
    suspend fun deleteUnused()
}
