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
    const val CURRENT_VERSION = 2

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun encode(records: List<SnippetRecord>, categories: List<CategoryRecord> = emptyList()): String {
        val namesById = categories.associate { it.id to it.name }
        return json.encodeToString(
            BackupPayload.serializer(),
            BackupPayload(
                version = CURRENT_VERSION,
                categories = categories.sortedBy { it.position }.map {
                    BackupCategory(name = it.name, position = it.position)
                },
                snippets = records.filter { !it.isImage }.map {
                    BackupSnippet(
                        id = it.id,
                        title = it.title,
                        text = it.text,
                        pinned = it.pinned,
                        createdAt = it.createdAt,
                        updatedAt = it.updatedAt,
                        lastUsedAt = it.lastUsedAt,
                        category = it.categoryId?.let(namesById::get),
                    )
                },
            ),
        )
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
                    ),
                    category = it.category,
                )
            },
            categories = payload.categories,
        )
    }
}
