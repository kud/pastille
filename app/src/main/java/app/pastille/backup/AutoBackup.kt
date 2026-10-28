package app.pastille.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.InvalidationTracker
import app.pastille.data.DatabaseHolder
import app.pastille.data.SnippetRepository
import app.pastille.settings.BackupProblem
import app.pastille.settings.PastilleSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.FileNotFoundException

/**
 * Settings › Backup › Back up automatically. No WorkManager and no scheduler: the app process is
 * the trigger. Any write to the collection's tables, from the app, the keyboard or the share sheet
 * (all one process), asks for a backup; requests settle for [DEBOUNCE_MS] and then rewrite the
 * file once. The app going to the background ([flush]) and every process start also ask, which
 * catches a change whose debounce the process didn't live to see.
 *
 * Nothing is written when the contents haven't changed (a used snippet only moves `lastUsedAt`).
 */
class AutoBackup(context: Context, private val scope: CoroutineScope) {

    private val app = context.applicationContext
    private val settings = PastilleSettings.forContext(app)
    private val mutex = Mutex()
    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    enum class Outcome { Written, Unchanged, Off, Failed }

    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            DatabaseHolder.get(app).invalidationTracker.addObserver(
                object : InvalidationTracker.Observer(arrayOf("snippets", "categories", "tags", "snippet_tags")) {
                    override fun onInvalidated(tables: Set<String>) {
                        if (settings.backupUri != null) requests.tryEmit(Unit)
                    }
                },
            )
        }
        scope.launch {
            requests.debounce(DEBOUNCE_MS).collect { runCatching { backUp(force = false) } }
        }
        if (settings.backupUri != null) requests.tryEmit(Unit)
    }

    /** The app left the screen: write now rather than wait for the debounce. */
    fun flush() {
        if (settings.backupUri == null) return
        scope.launch { runCatching { backUp(force = false) } }
    }

    /** Starts backing up to the file the user just created; writes it at once. */
    suspend fun choose(uri: Uri): Outcome {
        val resolver = app.contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        settings.backupUri?.let { old -> if (old != uri.toString()) release(Uri.parse(old)) }
        try {
            resolver.takePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
            // A provider that grants no persistable permission can't hold an automatic backup.
            settings.backupProblem = BackupProblem.Access
        }
        settings.backupUri = uri.toString()
        settings.backupName = BackupFiles.displayName(app, uri) ?: BackupArchive.SUGGESTED_NAME
        settings.backupLastAt = null
        settings.backupFingerprint = null
        return backUp(force = true)
    }

    /** Stops backing up; the file stays where it is. */
    fun stop() {
        settings.backupUri?.let { release(Uri.parse(it)) }
        settings.clearBackup()
    }

    suspend fun backUp(force: Boolean): Outcome = mutex.withLock {
        val uri = settings.backupUri?.let(Uri::parse) ?: return@withLock Outcome.Off
        if (!hasPermission(uri)) {
            settings.backupProblem = BackupProblem.Access
            return@withLock Outcome.Failed
        }
        val repository = SnippetRepository.forContext(app)
        val fingerprint = repository.backupFingerprint()
        if (!force && settings.backupProblem == null && fingerprint == settings.backupFingerprint) {
            return@withLock Outcome.Unchanged
        }
        var built: java.io.File? = null
        try {
            built = BackupFiles.build(app).first
            BackupFiles.writeTo(app, uri, built)
            settings.backupLastAt = System.currentTimeMillis()
            settings.backupFingerprint = fingerprint
            settings.backupProblem = null
            Outcome.Written
        } catch (_: SecurityException) {
            settings.backupProblem = BackupProblem.Access
            Outcome.Failed
        } catch (_: FileNotFoundException) {
            settings.backupProblem = BackupProblem.Access
            Outcome.Failed
        } catch (_: Exception) {
            settings.backupProblem = BackupProblem.Write
            Outcome.Failed
        } finally {
            built?.delete()
        }
    }

    private fun hasPermission(uri: Uri): Boolean =
        app.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission && it.isReadPermission }

    private fun release(uri: Uri) {
        runCatching {
            app.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    companion object {
        const val DEBOUNCE_MS = 5_000L
    }
}
