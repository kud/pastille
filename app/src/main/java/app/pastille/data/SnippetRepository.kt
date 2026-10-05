package app.pastille.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import app.pastille.backup.SnippetBackup
import app.pastille.images.ImageImportException
import app.pastille.images.ImageImporter
import app.pastille.model.CategoryRecord
import app.pastille.model.SnippetRecord
import app.pastille.model.moveCategory as reorderCategories
import app.pastille.model.sortSnippets
import app.pastille.model.orderCategories
import app.pastille.model.positionWrites
import app.pastille.model.uniqueTitle
import app.pastille.share.autoTitle
import app.pastille.model.normaliseTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class ExportResult(
    val json: String,
    val exported: Int,
    val skippedImages: Int,
)

// One image brought in by [SnippetRepository.importImages], new or already there.
data class ImportedImage(
    val id: Long,
    val fileName: String,
    val title: String,
    val width: Int,
    val height: Int,
)

// `converted` holds the image snippets an import into Stickers turned into stickers, as they were
// before, so Undo can put them back in their folder.
data class ImportResult(
    val requested: Int,
    val created: List<ImportedImage>,
    val converted: List<SnippetRecord>,
    val alreadyThere: List<SnippetRecord>,
    val failed: List<String>,
    val images: List<ImportedImage>,
) {
    val savedCount: Int get() = created.size + converted.size
}

class SnippetRepository private constructor(
    private val db: PastilleDatabase,
    private val dao: SnippetDao,
    private val categoryDao: CategoryDao,
    private val tagDao: TagDao,
) {

    // Tags are attached in memory from one snippet_tags flow: never a query per row.
    fun observeSnippets(): Flow<List<SnippetRecord>> =
        combine(dao.observeAll(), tagDao.observeSnippetTags()) { entities, links ->
            val tagsBySnippet = links.groupBy({ it.snippetId }, { it.name })
            sortSnippets(entities.map { it.toRecord().copy(tags = tagsBySnippet[it.id].orEmpty()) })
        }

    fun observeStickers(): Flow<List<SnippetRecord>> =
        dao.observeStickers().map { entities -> entities.map { it.toRecord() } }

    suspend fun get(id: Long): SnippetRecord? =
        dao.getById(id)?.toRecord()?.let { record -> record.copy(tags = tagDao.tagsFor(listOf(id)).map { it.name }) }

    /** Every tag name, for suggestions. */
    fun observeTagNames(): Flow<List<String>> = tagDao.observeAll().map { tags -> tags.map { it.name } }

    /** Replaces a snippet's tags; names are normalised, and tags nobody carries any more go. */
    suspend fun setTags(snippetId: Long, names: List<String>, now: Long = System.currentTimeMillis()) {
        db.withTransaction {
            tagDao.unlinkSnippet(snippetId)
            linkTags(snippetId, names, now)
            tagDao.deleteUnused()
        }
    }

    private suspend fun linkTags(snippetId: Long, names: List<String>, now: Long) {
        names.map(::normaliseTag).filter { it.isNotEmpty() }.distinct().forEach { name ->
            val tagId = tagDao.findByName(name)?.id ?: tagDao.insert(TagEntity(name = name, createdAt = now))
            tagDao.link(SnippetTagEntity(snippetId = snippetId, tagId = tagId))
        }
    }

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
            sticker = record.sticker,
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

    // Undo after a delete: the row is put back as it was, out of the bin.
    suspend fun restore(record: SnippetRecord): Long =
        dao.upsert(record.copy(deletedAt = null).toEntity())

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

    // A sticker must be an image, so a text row is refused (false). Turning it on clears the folder and
    // puts it at the end of the sticker order; turning it off brings it back at the top level, first.
    suspend fun setSticker(id: Long, on: Boolean): Boolean = db.withTransaction {
        val row = dao.getById(id) ?: return@withTransaction false
        if (row.imageFile == null) return@withTransaction false
        if (row.sticker == on) return@withTransaction true
        val position = if (on) nextStickerPosition() else newTopPosition()
        dao.setSticker(id, on, categoryId = null, position = position)
        true
    }

    private suspend fun nextStickerPosition(): Int = (dao.maxStickerPosition() ?: -1) + 1

    suspend fun setTitle(id: Long, title: String) {
        val trimmed = title.trim()
        if (trimmed.isNotEmpty()) dao.setTitle(id, trimmed)
    }

    /**
     * The one way images come in, from the share sheet and the app's photo picker. At most
     * [MAX_IMPORT] are taken: the picker below Android 13 doesn't enforce its own limit. Files are
     * content-addressed, so an image already saved is never stored twice. Into Stickers, an image
     * that is already an image snippet becomes a sticker (`converted`); one already a sticker, or
     * any duplicate saved as an image, is left as it is (`alreadyThere`).
     */
    suspend fun importImages(
        uris: List<Uri>,
        categoryId: Long?,
        sticker: Boolean,
        importer: ImageImporter,
        titleFor: (index: Int) -> String,
        now: Long = System.currentTimeMillis(),
    ): ImportResult {
        val created = mutableListOf<ImportedImage>()
        val converted = mutableListOf<SnippetRecord>()
        val alreadyThere = mutableListOf<SnippetRecord>()
        val failed = mutableListOf<String>()
        val images = mutableListOf<ImportedImage>()
        uris.take(MAX_IMPORT).forEachIndexed { index, uri ->
            val stored = try {
                importer.import(uri)
            } catch (error: ImageImportException) {
                failed.add(error.reason)
                return@forEachIndexed
            } catch (_: Exception) {
                failed.add("Unreadable image")
                return@forEachIndexed
            }
            val existing = findByImageFile(stored.fileName)
            val image = if (existing != null) {
                if (sticker && !existing.sticker && setSticker(existing.id, on = true)) {
                    converted.add(existing)
                } else {
                    alreadyThere.add(existing)
                }
                ImportedImage(existing.id, stored.fileName, existing.title.ifEmpty { titleFor(index) }, stored.width, stored.height)
            } else {
                val title = titleFor(index)
                val id = insertImage(
                    title = title,
                    imageFile = stored.fileName,
                    width = stored.width,
                    height = stored.height,
                    categoryId = if (sticker) null else categoryId,
                    now = now,
                )
                if (sticker) dao.setSticker(id, true, categoryId = null, position = nextStickerPosition())
                ImportedImage(id, stored.fileName, title, stored.width, stored.height).also { created.add(it) }
            }
            images.add(image)
        }
        return ImportResult(uris.size, created, converted, alreadyThere, failed, images)
    }

    // Undoing an import: new rows and their files go for good; converted rows are put back as they were.
    suspend fun undoImport(result: ImportResult, deleteFile: (String) -> Unit) {
        deleteMany(result.created.map { it.id })
        val stillNamed = referencedImageFiles()
        result.created.map { it.fileName }.distinct().filter { it !in stillNamed }.forEach(deleteFile)
        result.converted.forEach { restore(it) }
    }

    suspend fun setCategory(ids: List<Long>, categoryId: Long?) {
        if (ids.isEmpty()) return
        dao.setCategory(ids, categoryId)
    }

    // Deleting moves a snippet to the bin; only the bin itself deletes for good.
    suspend fun delete(id: Long, now: Long = System.currentTimeMillis()) {
        dao.moveToBin(id, now)
    }

    suspend fun restoreFromBin(id: Long) {
        dao.restoreFromBin(id)
    }

    fun observeBin(): Flow<List<SnippetRecord>> =
        dao.observeBin().map { entities -> entities.map { it.toRecord() } }

    suspend fun deleteForever(id: Long) {
        db.withTransaction { hardDelete(listOf(id).filter { it in dao.binnedIds() }) }
    }

    suspend fun emptyBin() {
        db.withTransaction { hardDelete(dao.binnedIds()) }
    }

    /** Deletes for good what has been in the bin for longer than [BIN_RETENTION_MS]. */
    suspend fun purgeExpiredBin(now: Long = System.currentTimeMillis()) {
        db.withTransaction { hardDelete(dao.expiredBinnedIds(now - BIN_RETENTION_MS)) }
    }

    // Live and binned rows alike: a file goes only once no row at all names it.
    suspend fun referencedImageFiles(): Set<String> = dao.allImageFiles().toSet()

    // Snippet rows and their snippet_tags rows go together, in the caller's transaction.
    private suspend fun hardDelete(ids: List<Long>) {
        if (ids.isEmpty()) return
        dao.deleteByIds(ids)
        tagDao.unlinkSnippets(ids)
        tagDao.deleteUnused()
    }

    suspend fun deleteMany(ids: List<Long>) {
        if (ids.isEmpty()) return
        db.withTransaction { hardDelete(ids) }
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
        val entities = dao.getAll()
        val tagsBySnippet = tagDao.tagsFor(entities.map { it.id }).groupBy({ it.snippetId }, { it.name })
        val records = entities.map { it.toRecord().copy(tags = tagsBySnippet[it.id].orEmpty()) }
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
                val newId = dao.upsert(
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
                linkTags(newId, entry.record.tags, now)
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
        deletedAt = deletedAt,
        sticker = sticker,
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
        deletedAt = deletedAt,
        sticker = sticker,
    )

    private fun CategoryEntity.toRecord() = CategoryRecord(
        id = id,
        name = name,
        position = position,
    )

    companion object {
        const val BIN_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
        const val MAX_IMPORT = 20

        fun forContext(context: Context): SnippetRepository {
            return forDatabase(DatabaseHolder.get(context))
        }

        internal fun forDatabase(db: PastilleDatabase): SnippetRepository =
            SnippetRepository(db, db.snippets(), db.categories(), db.tags())
    }
}
