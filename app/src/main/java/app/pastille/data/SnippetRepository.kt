package app.pastille.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import app.pastille.backup.BackupArchive
import app.pastille.backup.SnippetBackup
import app.pastille.sync.MergeResult
import app.pastille.sync.SyncCategory
import app.pastille.sync.SyncSnippet
import app.pastille.sync.SyncState
import app.pastille.sync.SyncTag
import app.pastille.sync.SyncTombstone
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
import java.io.File
import java.io.OutputStream
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

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
    private val tombstoneDao: TombstoneDao,
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
            val before = tagDao.tagsFor(listOf(snippetId)).map { it.name }.toSet()
            val after = names.map(::normaliseTag).filter { it.isNotEmpty() }.toSet()
            if (before != after) dao.getById(snippetId)?.let { dao.upsert(it.copy(updatedAt = now)) }
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
        val uid = record.uid.ifEmpty { if (record.id == 0L) newUid() else dao.getById(record.id)?.uid ?: newUid() }
        val entity = SnippetEntity(
            id = record.id,
            uid = uid,
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

    suspend fun setSnippetOrder(shown: List<SnippetRecord>, newOrder: List<Long>, now: Long = System.currentTimeMillis()) {
        db.withTransaction {
            val stored = dao.getByIds(shown.map { it.id }).associate { it.id to it.position }
            positionWrites(shown, stored, newOrder).forEach { (id, position) ->
                dao.updatePosition(id, position, now)
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
    // It counts as an edit, so a sync that already carried the delete takes the undo too.
    suspend fun restore(record: SnippetRecord, now: Long = System.currentTimeMillis()): Long =
        dao.upsert(record.copy(deletedAt = null, updatedAt = now).toEntity())

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
    suspend fun setSticker(id: Long, on: Boolean, now: Long = System.currentTimeMillis()): Boolean = db.withTransaction {
        val row = dao.getById(id) ?: return@withTransaction false
        if (row.imageFile == null) return@withTransaction false
        if (row.sticker == on) return@withTransaction true
        val position = if (on) nextStickerPosition() else newTopPosition()
        dao.setSticker(id, on, categoryId = null, position = position, now = now)
        true
    }

    private suspend fun nextStickerPosition(): Int = (dao.maxStickerPosition() ?: -1) + 1

    suspend fun setTitle(id: Long, title: String, now: Long = System.currentTimeMillis()) {
        val trimmed = title.trim()
        if (trimmed.isNotEmpty()) dao.editTitle(id, trimmed, now)
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
                if (sticker) dao.setSticker(id, true, categoryId = null, position = nextStickerPosition(), now = now)
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

    suspend fun setCategory(ids: List<Long>, categoryId: Long?, now: Long = System.currentTimeMillis()) {
        if (ids.isEmpty()) return
        dao.setCategory(ids, categoryId, now)
    }

    // Deleting moves a snippet to the bin; only the bin itself deletes for good.
    suspend fun delete(id: Long, now: Long = System.currentTimeMillis()) {
        dao.moveToBin(id, now)
    }

    suspend fun restoreFromBin(id: Long, now: Long = System.currentTimeMillis()) {
        dao.restoreFromBin(id, now)
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
    // Each deleted row leaves a tombstone, so the deletion reaches the other phone through sync.
    private suspend fun hardDelete(ids: List<Long>, now: Long = System.currentTimeMillis()) {
        if (ids.isEmpty()) return
        val uids = dao.uidsFor(ids).filter { it.isNotEmpty() }
        tombstoneDao.insertAll(uids.map { TombstoneEntity(uid = it, kind = KIND_SNIPPET, deletedAt = now) })
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

    suspend fun renameCategory(id: Long, name: String, now: Long = System.currentTimeMillis()) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Category name must not be blank" }
        val existing = categoryDao.findByName(trimmed)
        if (existing != null && existing.id != id) {
            throw IllegalArgumentException("A category named $trimmed already exists")
        }
        categoryDao.rename(id, trimmed, now)
    }

    suspend fun moveCategory(id: Long, delta: Int, now: Long = System.currentTimeMillis()) {
        db.withTransaction {
            val current = categoryDao.getAll().map { it.toRecord() }
            val before = current.associate { it.id to it.position }
            val reordered = reorderCategories(current, id, delta)
            reordered.forEach {
                if (before[it.id] != it.position) {
                    categoryDao.updatePosition(it.id, it.position, now)
                }
            }
        }
    }

    suspend fun setCategoryOrder(newOrder: List<Long>, now: Long = System.currentTimeMillis()) {
        db.withTransaction {
            val current = categoryDao.getAll().map { it.toRecord() }
            val before = current.associate { it.id to it.position }
            orderCategories(current, newOrder).forEach {
                if (before[it.id] != it.position) categoryDao.updatePosition(it.id, it.position, now)
            }
        }
    }

    suspend fun deleteCategory(id: Long, now: Long = System.currentTimeMillis()) {
        db.withTransaction {
            categoryDao.getById(id)?.let {
                tombstoneDao.insertAll(listOf(TombstoneEntity(uid = it.uid, kind = KIND_CATEGORY, deletedAt = now)))
            }
            dao.clearCategory(id)
            categoryDao.deleteById(id)
        }
    }

    // What a backup holds: every live snippet, image and sticker (never the bin), with its tags.
    private suspend fun backupRecords(): List<SnippetRecord> {
        val entities = dao.getAll()
        val tagsBySnippet = tagDao.tagsFor(entities.map { it.id }).groupBy({ it.snippetId }, { it.name })
        return entities.map { it.toRecord().copy(tags = tagsBySnippet[it.id].orEmpty()) }
    }

    /**
     * Writes the whole collection as a backup archive ([BackupArchive]) to [out]. [withSync] adds what
     * only the synced file needs (the bin, tag ids, tombstones); Export and Share leave them out.
     */
    suspend fun exportBackup(out: OutputStream, imagesDir: File, withSync: Boolean = false): BackupArchive.Written =
        BackupArchive.writeState(out, syncState(), withSync) { File(imagesDir, it) }

    /** Everything sync compares, read in one transaction: every row, the bin included. */
    suspend fun syncState(): SyncState = db.withTransaction {
        val categories = categoryDao.getAll()
        val categoryUids = categories.associate { it.id to it.uid }
        val rows = dao.getEverything()
        val tagsBySnippet = tagDao.allSnippetTags().groupBy({ it.snippetId }, { it.name })
        SyncState(
            snippets = rows.map { row ->
                SyncSnippet(
                    uid = row.uid,
                    title = row.title,
                    text = row.text,
                    pinned = row.pinned,
                    createdAt = row.createdAt,
                    updatedAt = row.updatedAt,
                    lastUsedAt = row.lastUsedAt,
                    categoryUid = row.categoryId?.let(categoryUids::get),
                    imageFile = row.imageFile,
                    imageWidth = row.imageWidth,
                    imageHeight = row.imageHeight,
                    position = row.position,
                    deletedAt = row.deletedAt,
                    sticker = row.sticker,
                    tags = tagsBySnippet[row.id].orEmpty().map { it.lowercase() }.distinct().sorted(),
                )
            },
            categories = categories.map { SyncCategory(it.uid, it.name, it.position, it.createdAt, it.updatedAt) },
            tags = tagDao.getAll().map { SyncTag(it.uid, it.name, it.createdAt, it.updatedAt) },
            tombstones = tombstoneDao.getAll().map { SyncTombstone(it.uid, it.kind, it.deletedAt) },
        )
    }

    /**
     * Makes this database hold [result]'s state, in one transaction, touching only rows that differ
     * (so an idempotent merge writes nothing). A snippet whose image file isn't here ([hasImage]) is
     * left as it was: it can't be shown, and the next sync brings it once the file is readable.
     * Titles and positions are taken as merged, never re-resolved, so both phones agree.
     */
    suspend fun applySync(result: MergeResult, hasImage: (String) -> Boolean) = db.withTransaction {
        val merged = result.state

        // Same item on both phones: keep the row, take the shared uid.
        val categoriesByUid = categoryDao.getAll().associateBy { it.uid }
        result.renamedCategories.forEach { (old, new) ->
            categoriesByUid[old]?.let { categoryDao.replace(it.copy(uid = new)) }
        }
        val rowsByUid = dao.getEverything().associateBy { it.uid }
        result.renamedSnippets.forEach { (old, new) -> rowsByUid[old]?.let { dao.setUid(it.id, new) } }

        // Folders: drop the gone, park changed names (the unique index), then write.
        val localCategories = categoryDao.getAll().associateBy { it.uid }
        val mergedCategories = merged.categories.associateBy { it.uid }
        localCategories.values.filter { it.uid !in mergedCategories }.forEach {
            dao.clearCategory(it.id)
            categoryDao.deleteById(it.id)
        }
        val renamed = merged.categories.filter { c -> localCategories[c.uid]?.let { it.name != c.name } == true }
        renamed.forEach { c -> categoryDao.setName(localCategories.getValue(c.uid).id, "\u0001${c.uid}") }
        merged.categories.forEach { c ->
            val existing = localCategories[c.uid]
            val entity = CategoryEntity(
                id = existing?.id ?: 0,
                name = c.name,
                position = c.position,
                createdAt = c.createdAt,
                uid = c.uid,
                updatedAt = c.updatedAt,
            )
            if (existing == null || existing != entity || c in renamed) categoryDao.replace(entity)
        }
        val categoryIds = categoryDao.getAll().associate { it.uid to it.id }

        // Snippets.
        val local = dao.getEverything().associateBy { it.uid }
        val localTags = tagDao.allSnippetTags().groupBy({ it.snippetId }, { it.name.lowercase() })
        val mergedUids = merged.snippets.map { it.uid }.toSet()
        val gone = local.values.filter { it.uid !in mergedUids }.map { it.id }
        if (gone.isNotEmpty()) {
            dao.deleteByIds(gone)
            tagDao.unlinkSnippets(gone)
        }
        val now = System.currentTimeMillis()
        merged.snippets.forEach { s ->
            val existing = local[s.uid]
            if (s.imageFile != null && !hasImage(s.imageFile)) return@forEach
            val entity = SnippetEntity(
                id = existing?.id ?: 0,
                uid = s.uid,
                title = s.title,
                text = s.text,
                pinned = s.pinned,
                createdAt = s.createdAt,
                updatedAt = s.updatedAt,
                lastUsedAt = s.lastUsedAt,
                categoryId = s.categoryUid?.let(categoryIds::get),
                imageFile = s.imageFile,
                imageWidth = s.imageWidth,
                imageHeight = s.imageHeight,
                position = s.position,
                deletedAt = s.deletedAt,
                sticker = s.sticker,
            )
            val id = existing?.takeIf { it == entity }?.id ?: dao.upsert(entity)
            val before = existing?.let { localTags[it.id].orEmpty().distinct().sorted() }.orEmpty()
            if (before != s.tags) {
                tagDao.unlinkSnippet(id)
                linkTags(id, s.tags, now)
            }
        }
        tagDao.deleteUnused()

        // Tags: one uid per name on both phones.
        val tagsByName = tagDao.getAll().associateBy { it.name.lowercase() }
        merged.tags.forEach { t ->
            val row = tagsByName[t.name.lowercase()] ?: return@forEach
            if (row.uid != t.uid || row.createdAt != t.createdAt || row.updatedAt != t.updatedAt) {
                tagDao.setIdentity(row.id, t.uid, t.createdAt, t.updatedAt)
            }
        }

        val tombstones = tombstoneDao.getAll().toSet()
        val wanted = merged.tombstones.map { TombstoneEntity(it.uid, it.kind, it.deletedAt) }.toSet()
        if (tombstones != wanted) {
            tombstoneDao.clear()
            tombstoneDao.insertAll(wanted.toList())
        }
    }

    /** The backup's JSON on its own, as the archive's pastille.json holds it. */
    internal suspend fun backupJson(): String = SnippetBackup.encode(backupRecords(), getCategories())

    /** Changes when the backup's contents do, but not when a snippet is only used. */
    suspend fun backupFingerprint(): String = SnippetBackup.fingerprint(syncState())

    /**
     * Imports a backup file: the zip archive, or an old JSON export (versions 1–3). Whatever is
     * already here is skipped, so importing the same file twice adds nothing: text by exact text,
     * images and stickers by their content-addressed file. Image files are copied in before the
     * rows; if the rows then fail, the start-up orphan sweep removes the files.
     */
    suspend fun importBackup(
        file: File,
        imagesDir: File,
        now: Long = System.currentTimeMillis(),
    ): BackupArchive.Counts {
        val contents = SnippetBackup.decode(BackupArchive.readJson(file))
        val zip = BackupArchive.isZip(file)
        val available = contents.snippets.mapNotNull { it.record.imageFile }.distinct()
            .filter { zip && BackupArchive.extractImage(file, it, imagesDir) }
            .toSet()
        return db.withTransaction {
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
            var snippets = 0
            var images = 0
            var stickers = 0
            val (stickerEntries, listEntries) = contents.snippets.partition { it.record.sticker }
            val ordered = listEntries.sortedBy { it.record.position }
            val base = (dao.minPosition() ?: 0) - ordered.size
            ordered.forEachIndexed { index, entry ->
                val record = entry.record
                val image = record.imageFile
                if (image != null) {
                    if (image !in available || dao.findByImageFile(image) != null) return@forEachIndexed
                } else if (dao.findTextDuplicate(record.text) != null) {
                    return@forEachIndexed
                }
                val categoryId = entry.category?.let { nameToId[it.lowercase()] }
                val newId = dao.upsert(
                    SnippetEntity(
                        title = resolveTitle(record.copy(id = 0, categoryId = categoryId)),
                        text = if (image != null) "" else record.text,
                        pinned = record.pinned,
                        createdAt = now,
                        updatedAt = now,
                        lastUsedAt = record.lastUsedAt,
                        categoryId = categoryId,
                        imageFile = image,
                        imageWidth = record.imageWidth,
                        imageHeight = record.imageHeight,
                        position = base + index,
                    ),
                )
                linkTags(newId, record.tags, now)
                if (image != null) images++ else snippets++
            }
            stickerEntries.sortedBy { it.record.position }.forEach { entry ->
                val record = entry.record
                val image = record.imageFile ?: return@forEach
                if (image !in available || dao.findByImageFile(image) != null) return@forEach
                val newId = dao.upsert(
                    SnippetEntity(
                        title = resolveTitle(record.copy(id = 0, categoryId = null)),
                        text = "",
                        pinned = record.pinned,
                        createdAt = now,
                        updatedAt = now,
                        lastUsedAt = record.lastUsedAt,
                        categoryId = null,
                        imageFile = image,
                        imageWidth = record.imageWidth,
                        imageHeight = record.imageHeight,
                        position = nextStickerPosition(),
                        sticker = true,
                    ),
                )
                linkTags(newId, record.tags, now)
                stickers++
            }
            BackupArchive.Counts(snippets = snippets, images = images, stickers = stickers)
        }
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
        uid = uid,
    )

    private fun SnippetRecord.toEntity() = SnippetEntity(
        uid = uid.ifEmpty { newUid() },
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
        uid = uid,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        const val BIN_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
        const val MAX_IMPORT = 20
        const val KIND_SNIPPET = "snippet"
        const val KIND_CATEGORY = "category"

        fun forContext(context: Context): SnippetRepository {
            return forDatabase(DatabaseHolder.get(context))
        }

        internal fun forDatabase(db: PastilleDatabase): SnippetRepository =
            SnippetRepository(db, db.snippets(), db.categories(), db.tags(), db.tombstones())
    }
}
