package app.pastille.backup

import app.pastille.model.CategoryRecord
import app.pastille.model.SnippetRecord
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupCategory(
    val name: String,
    val position: Int = 0,
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
)

@Serializable
data class BackupPayload(
    val version: Int,
    val categories: List<BackupCategory> = emptyList(),
    val snippets: List<BackupSnippet>,
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
                    ),
                    category = it.category,
                )
            },
            categories = payload.categories,
        )
    }
}
