package app.pastille.data

import android.content.Context
import androidx.room.withTransaction
import app.pastille.backup.SnippetBackup
import app.pastille.model.CategoryRecord
import app.pastille.model.SnippetRecord
import app.pastille.model.moveCategory as reorderCategories
import app.pastille.model.sortSnippets
import app.pastille.model.orderCategories
import app.pastille.model.positionWrites
import app.pastille.model.uniqueTitle
import app.pastille.share.autoTitle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ExportResult(
    val json: String,
    val exported: Int,
    val skippedImages: Int,
)

class SnippetRepository private constructor(
    private val db: PastilleDatabase,
    private val dao: SnippetDao,
    private val categoryDao: CategoryDao,
) {

    fun observeSnippets(): Flow<List<SnippetRecord>> =
        dao.observeAll().map { entities -> sortSnippets(entities.map { it.toRecord() }) }

    suspend fun get(id: Long): SnippetRecord? = dao.getById(id)?.toRecord()

    suspend fun upsert(record: SnippetRecord, now: Long = System.currentTimeMillis()): Long {
        val entity = SnippetEntity(
            id = record.id,
            title = resolveTitle(record),
            text = record.text,
            pinned = record.pinned,
            createdAt = if (record.id == 0L) now else record.createdAt,
            updatedAt = now,
            lastUsedAt = if (record.id == 0L) now else record.lastUsedAt,
            categoryId = record.categoryId,
            imageFile = record.imageFile,
            imageWidth = record.imageWidth,
            imageHeight = record.imageHeight,
            position = if (record.id == 0L) newTopPosition() else record.position,
        )
        return dao.upsert(entity)
    }

    private suspend fun newTopPosition(): Int = (dao.minPosition() ?: 1) - 1

    suspend fun setSnippetOrder(shown: List<SnippetRecord>, newOrder: List<Long>) {
        db.withTransaction {
            val stored = dao.getByIds(shown.map { it.id }).associate { it.id to it.position }
            positionWrites(shown, stored, newOrder).forEach { (id, position) ->
                dao.updatePosition(id, position)
            }
        }
    }

    // Every snippet carries a title: blank ones take the auto title, and a clash
    // within the same folder gets a " 2", " 3" suffix.
    private suspend fun resolveTitle(record: SnippetRecord): String {
        val typed = record.title.trim()
        val base = when {
            typed.isNotEmpty() -> typed
            record.isImage -> "Image"
            else -> autoTitle(record.text)
        }
        if (base.isEmpty()) return base
        return uniqueTitle(base, dao.titlesInCategory(record.categoryId, record.id))
    }

    suspend fun backfillTitles() {
        db.withTransaction {
            dao.getBlankTitled().forEach { entity ->
                val title = resolveTitle(entity.toRecord().copy(title = ""))
                if (title.isNotEmpty()) dao.setTitle(entity.id, title)
            }
        }
    }

    suspend fun restore(record: SnippetRecord): Long =
        dao.upsert(record.toEntity())

    suspend fun findTextDuplicate(text: String): SnippetRecord? =
        dao.findTextDuplicate(text)?.toRecord()

    suspend fun findByImageFile(name: String): SnippetRecord? =
        dao.findByImageFile(name)?.toRecord()

    suspend fun recordUse(id: Long, now: Long = System.currentTimeMillis()) {
        dao.touch(id, now)
    }

    suspend fun saveClipboardText(text: String, now: Long = System.currentTimeMillis()): Long =
        upsert(SnippetRecord(text = text), now)

    suspend fun insertImage(
        title: String,
        imageFile: String,
        width: Int,
        height: Int,
        categoryId: Long? = null,
        now: Long = System.currentTimeMillis(),
    ): Long =
        upsert(
            SnippetRecord(
                title = title,
                text = "",
                categoryId = categoryId,
                imageFile = imageFile,
                imageWidth = width,
                imageHeight = height,
            ),
            now,
        )

    suspend fun setCategory(ids: List<Long>, categoryId: Long?) {
        if (ids.isEmpty()) return
        dao.setCategory(ids, categoryId)
    }

    suspend fun delete(id: Long) {
        dao.deleteById(id)
    }

    suspend fun deleteMany(ids: List<Long>) {
        if (ids.isEmpty()) return
        dao.deleteByIds(ids)
    }

    fun observeCategories(): Flow<List<CategoryRecord>> =
        categoryDao.observeAll().map { entities -> entities.map { it.toRecord() } }

    suspend fun getCategories(): List<CategoryRecord> =
        categoryDao.getAll().map { it.toRecord() }

    suspend fun createCategory(name: String, now: Long = System.currentTimeMillis()): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Category name must not be blank" }
        categoryDao.findByName(trimmed)?.let { return it.id }
        val position = (categoryDao.maxPosition() ?: -1) + 1
        return categoryDao.insert(CategoryEntity(name = trimmed, position = position, createdAt = now))
    }

    suspend fun renameCategory(id: Long, name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Category name must not be blank" }
        val existing = categoryDao.findByName(trimmed)
        if (existing != null && existing.id != id) {
            throw IllegalArgumentException("A category named $trimmed already exists")
        }
        categoryDao.rename(id, trimmed)
    }

    suspend fun moveCategory(id: Long, delta: Int) {
        db.withTransaction {
            val current = categoryDao.getAll().map { it.toRecord() }
            val before = current.associate { it.id to it.position }
            val reordered = reorderCategories(current, id, delta)
            reordered.forEach {
                if (before[it.id] != it.position) {
                    categoryDao.updatePosition(it.id, it.position)
                }
            }
        }
    }

    suspend fun setCategoryOrder(newOrder: List<Long>) {
        db.withTransaction {
            val current = categoryDao.getAll().map { it.toRecord() }
            val before = current.associate { it.id to it.position }
            orderCategories(current, newOrder).forEach {
                if (before[it.id] != it.position) categoryDao.updatePosition(it.id, it.position)
            }
        }
    }

    suspend fun deleteCategory(id: Long) {
        db.withTransaction {
            dao.clearCategory(id)
            categoryDao.deleteById(id)
        }
    }

    suspend fun exportJson(): ExportResult {
        val records = dao.getAll().map { it.toRecord() }
        val skipped = records.count { it.isImage }
        return ExportResult(
            json = SnippetBackup.encode(records, getCategories()),
            exported = records.size - skipped,
            skippedImages = skipped,
        )
    }

    suspend fun importJson(raw: String, now: Long = System.currentTimeMillis()): Int =
        db.withTransaction {
            val contents = SnippetBackup.decode(raw)
            val nameToId = categoryDao.getAll()
                .associate { it.name.lowercase() to it.id }
                .toMutableMap()
            var position = (categoryDao.maxPosition() ?: -1) + 1
            contents.categories.sortedBy { it.position }.forEach { backupCategory ->
                val key = backupCategory.name.lowercase()
                if (!nameToId.containsKey(key)) {
                    val id = categoryDao.insert(
                        CategoryEntity(name = backupCategory.name, position = position++, createdAt = now),
                    )
                    nameToId[key] = id
                }
            }
            var imported = 0
            val ordered = contents.snippets.sortedBy { it.record.position }
            val base = (dao.minPosition() ?: 0) - ordered.size
            ordered.forEachIndexed { index, entry ->
                if (dao.findTextDuplicate(entry.record.text) != null) return@forEachIndexed
                val categoryId = entry.category?.let { nameToId[it.lowercase()] }
                dao.upsert(
                    SnippetEntity(
                        title = resolveTitle(entry.record.copy(id = 0, categoryId = categoryId)),
                        text = entry.record.text,
                        pinned = entry.record.pinned,
                        createdAt = now,
                        updatedAt = now,
                        lastUsedAt = entry.record.lastUsedAt,
                        categoryId = categoryId,
                        position = base + index,
                    ),
                )
                imported++
            }
            imported
        }

    private fun SnippetEntity.toRecord() = SnippetRecord(
        id = id,
        title = title,
        text = text,
        pinned = pinned,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastUsedAt = lastUsedAt,
        categoryId = categoryId,
        imageFile = imageFile,
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        position = position,
    )

    private fun SnippetRecord.toEntity() = SnippetEntity(
        id = id,
        title = title,
        text = text,
        pinned = pinned,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastUsedAt = lastUsedAt,
        categoryId = categoryId,
        imageFile = imageFile,
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        position = position,
    )

    private fun CategoryEntity.toRecord() = CategoryRecord(
        id = id,
        name = name,
        position = position,
    )

    companion object {
        fun forContext(context: Context): SnippetRepository {
            return forDatabase(DatabaseHolder.get(context))
        }

        internal fun forDatabase(db: PastilleDatabase): SnippetRepository =
            SnippetRepository(db, db.snippets(), db.categories())
    }
}
