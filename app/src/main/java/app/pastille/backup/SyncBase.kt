package app.pastille.backup

import android.content.Context
import app.pastille.sync.SyncState
import java.io.File

/**
 * What this phone and the shared file last agreed on: the base of the three-way merge. Kept as the
 * file's own JSON in filesDir/sync/, written to a temporary name and renamed, so a crash never
 * leaves half a base (a missing or unreadable base only makes the next merge last-edit-wins).
 */
object SyncBase {
    private fun file(context: Context) = File(File(context.filesDir, "sync"), "base.json")

    fun load(context: Context): SyncState? = try {
        file(context).takeIf { it.isFile }?.readText()?.let(SnippetBackup::decodeState)
    } catch (_: Exception) {
        null
    }

    fun save(context: Context, json: String) {
        val target = file(context)
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "base.json.tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(target)) {
            target.writeText(json)
            tmp.delete()
        }
    }

    fun clear(context: Context) {
        file(context).delete()
    }
}
