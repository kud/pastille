package app.pastille.backup

import app.pastille.model.CategoryRecord
import app.pastille.model.SnippetRecord
import app.pastille.sync.SyncCategory
import app.pastille.sync.SyncMerge
import app.pastille.sync.SyncSnippet
import app.pastille.sync.SyncState
import app.pastille.sync.SyncTag
import app.pastille.sync.SyncTombstone
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupCategory(
    val name: String,
    val position: Int = 0,
    // Sync fields (2026-10-28, still version 4: additive, so older builds keep reading the file).
    val uid: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class BackupTag(
    val uid: String,
    val name: String,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class BackupTombstone(
    val uid: String,
    val kind: String,
    val deletedAt: Long,
)

@Serializable
data class BackupSnippet(
    val id: Long = 0,
    val title: String = "",
    val text: String,
    val pinned: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val lastUsedAt: Long = 0,
    val category: String? = null,
    val position: Int = 0,
    // v3: tag names. Older files have none.
    val tags: List<String> = emptyList(),
    // v4: an image snippet names its file inside the archive's images/ folder. Older files have
    // text snippets only.
    val image: String? = null,
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
    val sticker: Boolean = false,
    // Sync fields: the cross-device id, the folder by uid (`category` keeps the name for older
    // readers), and, for entries under `bin`, when the snippet was binned.
    val uid: String = "",
    val categoryUid: String? = null,
    val deletedAt: Long? = null,
)

/**
 * Version 4 throughout: sync only adds keys, and every reader ignores keys it doesn't know, so a
 * build from before sync still imports a synced file. Binned snippets go under `bin`, never
 * `snippets`, so those older builds don't import the bin as live snippets.
 */
@Serializable
data class BackupPayload(
    val version: Int,
    val categories: List<BackupCategory> = emptyList(),
    val snippets: List<BackupSnippet>,
    val tags: List<BackupTag> = emptyList(),
    val bin: List<BackupSnippet> = emptyList(),
    val tombstones: List<BackupTombstone> = emptyList(),
)

data class BackupEntry(
    val record: SnippetRecord,
    val category: String?,
)

data class BackupContents(
    val snippets: List<BackupEntry>,
    val categories: List<BackupCategory>,
)

object SnippetBackup {
    const val CURRENT_VERSION = 4

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /**
     * Every record goes in, image snippets and stickers included: their bytes travel beside the
     * JSON in the archive ([BackupArchive]), named by [SnippetRecord.imageFile].
     */
    fun encode(records: List<SnippetRecord>, categories: List<CategoryRecord> = emptyList()): String {
        val namesById = categories.associate { it.id to it.name }
        return json.encodeToString(
            BackupPayload.serializer(),
            BackupPayload(
                version = CURRENT_VERSION,
                categories = categories.sortedBy { it.position }.map {
                    BackupCategory(name = it.name, position = it.position)
                },
                snippets = records.map {
                    BackupSnippet(
                        id = it.id,
                        title = it.title,
                        text = it.text,
                        pinned = it.pinned,
                        createdAt = it.createdAt,
                        updatedAt = it.updatedAt,
                        lastUsedAt = it.lastUsedAt,
                        category = it.categoryId?.let(namesById::get),
                        position = it.position,
                        tags = it.tags,
                        image = it.imageFile,
                        imageWidth = it.imageWidth,
                        imageHeight = it.imageHeight,
                        sticker = it.sticker,
                    )
                },
            ),
        )
    }

    /**
     * What decides whether the backup file needs rewriting: the contents without `lastUsedAt`, so
     * inserting a snippet from the keyboard (which moves `updatedAt` too) doesn't rewrite a file in Drive every time.
     */
    fun fingerprint(records: List<SnippetRecord>, categories: List<CategoryRecord>): String {
        val stable = encode(records.map { it.copy(lastUsedAt = 0, updatedAt = 0) }, categories)
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(stable.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** The sync state as the shared file holds it: snippets, bin, folders, tags and tombstones. */
    fun encodeState(state: SyncState, withSync: Boolean = true): String {
        val categoryNames = state.categories.associate { it.uid to it.name }
        fun entry(s: SyncSnippet) = BackupSnippet(
            title = s.title,
            text = s.text,
            pinned = s.pinned,
            createdAt = s.createdAt,
            updatedAt = s.updatedAt,
            lastUsedAt = s.lastUsedAt,
            category = s.categoryUid?.let(categoryNames::get),
            position = s.position,
            tags = s.tags,
            image = s.imageFile,
            imageWidth = s.imageWidth,
            imageHeight = s.imageHeight,
            sticker = s.sticker,
            uid = s.uid,
            categoryUid = s.categoryUid,
            deletedAt = s.deletedAt,
        )
        val (binned, live) = state.snippets.sortedWith(compareBy({ it.position }, { it.uid })).partition { it.deletedAt != null }
        return json.encodeToString(
            BackupPayload.serializer(),
            BackupPayload(
                version = CURRENT_VERSION,
                categories = state.categories.sortedWith(compareBy({ it.position }, { it.uid })).map {
                    BackupCategory(name = it.name, position = it.position, uid = it.uid, createdAt = it.createdAt, updatedAt = it.updatedAt)
                },
                snippets = live.map(::entry),
                tags = if (withSync) state.tags.sortedBy { it.name }.map { BackupTag(it.uid, it.name, it.createdAt, it.updatedAt) } else emptyList(),
                bin = if (withSync) binned.map(::entry) else emptyList(),
                tombstones = if (withSync) state.tombstones.sortedBy { it.uid }.map { BackupTombstone(it.uid, it.kind, it.deletedAt) } else emptyList(),
            ),
        )
    }

    /**
     * Any backup, v1 to v4, as sync state. Entries from before sync carry no uid: they get one
     * derived from their content (folders from their name), so the same old file reads the same on
     * both phones and the merge's duplicate rule pairs them with what's already here.
     */
    fun decodeState(raw: String): SyncState {
        val payload = json.decodeFromString(BackupPayload.serializer(), raw)
        require(payload.version in 1..CURRENT_VERSION) { "Unsupported backup version: ${payload.version}" }
        val categories = payload.categories.map {
            SyncCategory(
                uid = it.uid.ifEmpty { legacyCategoryUid(it.name) },
                name = it.name,
                position = it.position,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt,
            )
        }.toMutableList()
        val byName = categories.associateBy { it.name.lowercase() }.toMutableMap()
        fun snippet(it: BackupSnippet, binned: Boolean): SyncSnippet {
            val folder = it.categoryUid ?: it.category?.let { name ->
                byName.getOrPut(name.lowercase()) {
                    SyncCategory(uid = legacyCategoryUid(name), name = name, position = categories.size).also(categories::add)
                }.uid
            }
            return SyncSnippet(
                uid = it.uid.ifEmpty { legacySnippetUid(it.image, it.text) },
                title = it.title,
                text = if (it.image != null) "" else it.text,
                pinned = it.pinned,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt,
                lastUsedAt = it.lastUsedAt,
                categoryUid = folder,
                imageFile = it.image,
                imageWidth = it.imageWidth,
                imageHeight = it.imageHeight,
                position = it.position,
                deletedAt = if (binned) it.deletedAt ?: it.updatedAt else null,
                sticker = it.sticker && it.image != null,
                tags = it.tags.map { tag -> tag.lowercase() }.distinct().sorted(),
            )
        }
        val snippets = payload.snippets.map { snippet(it, binned = false) } + payload.bin.map { snippet(it, binned = true) }
        return SyncState(
            snippets = snippets,
            categories = categories,
            tags = payload.tags.map { SyncTag(it.uid, it.name, it.createdAt, it.updatedAt) },
            tombstones = payload.tombstones.map { SyncTombstone(it.uid, it.kind, it.deletedAt) },
        )
    }

    /** What decides whether the shared file needs rewriting: the state without use times. */
    fun fingerprint(state: SyncState): String {
        val stable = encodeState(state.copy(snippets = state.snippets.map { it.copy(lastUsedAt = 0) }))
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(stable.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun legacySnippetUid(image: String?, text: String): String =
        SyncMerge.derivedUid("legacy-snippet", image?.let { "image:$it" } ?: "text:$text")

    fun legacyCategoryUid(name: String): String = SyncMerge.derivedUid("legacy-category", name.lowercase())

    fun decode(raw: String): BackupContents {
        val payload = json.decodeFromString(BackupPayload.serializer(), raw)
        require(payload.version in 1..CURRENT_VERSION) {
            "Unsupported backup version: ${payload.version}"
        }
        return BackupContents(
            snippets = payload.snippets.map {
                BackupEntry(
                    record = SnippetRecord(
                        id = it.id,
                        title = it.title,
                        text = it.text,
                        pinned = it.pinned,
                        createdAt = it.createdAt,
                        updatedAt = it.updatedAt,
                        lastUsedAt = it.lastUsedAt,
                        position = it.position,
                        tags = it.tags,
                        imageFile = it.image,
                        imageWidth = it.imageWidth,
                        imageHeight = it.imageHeight,
                        sticker = it.sticker && it.image != null,
                        uid = it.uid,
                    ),
                    category = it.category,
                )
            },
            categories = payload.categories,
        )
    }
}
