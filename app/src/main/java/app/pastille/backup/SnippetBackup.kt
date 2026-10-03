package app.pastille.backup

import app.pastille.model.SnippetRecord
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupSnippet(
    val id: Long = 0,
    val title: String = "",
    val text: String,
    val pinned: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val lastUsedAt: Long = 0,
)

@Serializable
data class BackupPayload(
    val version: Int,
    val snippets: List<BackupSnippet>,
)

object SnippetBackup {
    const val CURRENT_VERSION = 1

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun encode(records: List<SnippetRecord>): String =
        json.encodeToString(
            BackupPayload.serializer(),
            BackupPayload(
                version = CURRENT_VERSION,
                snippets = records.map {
                    BackupSnippet(
                        id = it.id,
                        title = it.title,
                        text = it.text,
                        pinned = it.pinned,
                        createdAt = it.createdAt,
                        updatedAt = it.updatedAt,
                        lastUsedAt = it.lastUsedAt,
                    )
                },
            ),
        )

    fun decode(raw: String): List<SnippetRecord> {
        val payload = json.decodeFromString(BackupPayload.serializer(), raw)
        require(payload.version == CURRENT_VERSION) {
            "Unsupported backup version: ${payload.version}"
        }
        return payload.snippets.map {
            SnippetRecord(
                id = it.id,
                title = it.title,
                text = it.text,
                pinned = it.pinned,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt,
                lastUsedAt = it.lastUsedAt,
            )
        }
    }
}
