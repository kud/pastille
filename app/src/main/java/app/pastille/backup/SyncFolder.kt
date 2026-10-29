package app.pastille.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Sync with a folder (Erwann, 2026-10-29): the user picks a folder once (ACTION_OPEN_DOCUMENT_TREE)
 * and Pastille keeps one file in it, [FILE_NAME]. If the file is there, this phone merges with it;
 * if not, it creates it. It never creates a second one: a provider that appends " (1)" to a name it
 * already holds only gets asked to create when the listing holds no exact match.
 *
 * Not every storage app offers folders: Google Drive's provider doesn't show in the folder picker,
 * so the single-file pick stays as the fallback.
 */
object SyncFolder {

    /** The name of the shared file: the one "Sync automatically" has always suggested. */
    const val FILE_NAME = BackupArchive.SUGGESTED_NAME

    /** One entry of the folder, as the provider lists it. */
    data class Child(val documentId: String, val name: String?, val modified: Long?, val isDirectory: Boolean = false)

    /** What the folder's listing means for the sync file. */
    sealed interface Choice {
        /** Use [documentId]; [matches] files carry the name, the newest of them is this one. */
        data class Use(val documentId: String, val matches: Int, val modified: Long?) : Choice
        /** No file carries the name yet: create it. */
        data object Create : Choice
    }

    /**
     * Pure rule: an exact, case-sensitive match on [FILE_NAME] among files (folders skipped). Several
     * matches use the most recently modified one; an unknown modified time counts as oldest, and a
     * tie keeps the provider's order.
     */
    fun choose(children: List<Child>): Choice {
        val matches = children.filter { !it.isDirectory && it.name == FILE_NAME }
        val newest = matches.withIndex()
            .maxWithOrNull(compareBy<IndexedValue<Child>> { it.value.modified ?: Long.MIN_VALUE }.thenByDescending { it.index })
            ?.value
            ?: return Choice.Create
        return Choice.Use(newest.documentId, matches.size, newest.modified)
    }

    /** The resolved sync file inside a folder. */
    data class Resolved(val uri: Uri, val created: Boolean, val matches: Int, val modified: Long?)

    /**
     * Finds or creates the sync file in the folder [tree]. Throws when the folder can't be listed:
     * a listing that failed is never read as "no file", which would create a duplicate.
     */
    suspend fun resolve(context: Context, tree: Uri): Resolved = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val parentId = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val listed = resolver.query(children, columns, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    add(
                        Child(
                            documentId = id,
                            name = cursor.getString(1),
                            modified = SyncDiagnosis.knownModified(if (cursor.isNull(2)) null else cursor.getLong(2)),
                            isDirectory = cursor.getString(3) == DocumentsContract.Document.MIME_TYPE_DIR,
                        ),
                    )
                }
            }
        } ?: throw IOException("Folder listing unavailable")
        when (val choice = choose(listed)) {
            is Choice.Use -> Resolved(DocumentsContract.buildDocumentUriUsingTree(tree, choice.documentId), false, choice.matches, choice.modified)
            Choice.Create -> {
                val parent = DocumentsContract.buildDocumentUriUsingTree(tree, parentId)
                val created = DocumentsContract.createDocument(resolver, parent, BackupArchive.MIME, FILE_NAME)
                    ?: throw FileNotFoundException("Could not create $FILE_NAME")
                Resolved(created, true, 1, null)
            }
        }
    }

    /** The folder's name as the provider shows it, or null when it doesn't say. */
    fun folderName(context: Context, tree: Uri): String? = runCatching {
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        BackupFiles.displayName(context, doc)
    }.getOrNull()
}
