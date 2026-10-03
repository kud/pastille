package app.pastille.data

import android.content.Context
import app.pastille.backup.SnippetBackup
import app.pastille.model.SnippetRecord
import app.pastille.model.sortSnippets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SnippetRepository private constructor(private val dao: SnippetDao) {

    fun observeSnippets(): Flow<List<SnippetRecord>> =
        dao.observeAll().map { entities -> sortSnippets(entities.map { it.toRecord() }) }

    suspend fun get(id: Long): SnippetRecord? = dao.getById(id)?.toRecord()

    suspend fun upsert(record: SnippetRecord, now: Long = System.currentTimeMillis()): Long {
        val entity = SnippetEntity(
            id = record.id,
            title = record.title,
            text = record.text,
            pinned = record.pinned,
            createdAt = if (record.id == 0L) now else record.createdAt,
            updatedAt = now,
            lastUsedAt = if (record.id == 0L) now else record.lastUsedAt,
        )
        return dao.upsert(entity)
    }

    suspend fun recordUse(id: Long, now: Long = System.currentTimeMillis()) {
        dao.touch(id, now)
    }

    suspend fun saveClipboardText(text: String, now: Long = System.currentTimeMillis()): Long =
        dao.upsert(
            SnippetEntity(
                title = "",
                text = text,
                pinned = false,
                createdAt = now,
                updatedAt = now,
                lastUsedAt = now,
            ),
        )

    suspend fun delete(id: Long) {
        dao.deleteById(id)
    }

    suspend fun exportJson(): String =
        SnippetBackup.encode(dao.getAll().map { it.toRecord() })

    suspend fun importJson(raw: String, now: Long = System.currentTimeMillis()): Int {
        val records = SnippetBackup.decode(raw)
        records.forEach { record ->
            dao.upsert(
                SnippetEntity(
                    title = record.title,
                    text = record.text,
                    pinned = record.pinned,
                    createdAt = now,
                    updatedAt = now,
                    lastUsedAt = record.lastUsedAt,
                ),
            )
        }
        return records.size
    }

    private fun SnippetEntity.toRecord() = SnippetRecord(
        id = id,
        title = title,
        text = text,
        pinned = pinned,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastUsedAt = lastUsedAt,
    )

    companion object {
        fun forContext(context: Context): SnippetRepository =
            SnippetRepository(DatabaseHolder.get(context).snippets())
    }
}
