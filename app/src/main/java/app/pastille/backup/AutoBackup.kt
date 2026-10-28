package app.pastille.backup

import app.pastille.images.ImageStore
import app.pastille.sync.SyncMerge
import java.io.File
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
    @Volatile private var lastAttempt = 0L
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

    /**
     * Starts syncing with [uri]: a file just created, or one another phone already syncs with
     * (then its contents are merged in first). Writes it at once.
     */
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
        settings.syncRemoteSize = null
        settings.syncRemoteModified = null
        settings.syncRemoteHash = null
        // A new file starts a new sync: nothing agreed yet, so the first merge is last-edit-wins.
        SyncBase.clear(app)
        return backUp(force = true)
    }

    /** Stops backing up; the file stays where it is. */
    fun stop() {
        settings.backupUri?.let { release(Uri.parse(it)) }
        settings.clearBackup()
        SyncBase.clear(app)
    }

    /**
     * One sync with the chosen file: read it if it changed since we last saw it and merge it in
     * ([SyncMerge]), then write back whatever the file doesn't hold yet. A file that can't be read
     * stops the write: this phone never overwrites changes it hasn't seen.
     */
    suspend fun backUp(force: Boolean): Outcome = mutex.withLock {
        lastAttempt = System.currentTimeMillis()
        val uri = settings.backupUri?.let(Uri::parse) ?: return@withLock Outcome.Off
        if (!hasPermission(uri)) {
            settings.backupProblem = BackupProblem.Access
            return@withLock Outcome.Failed
        }
        val repository = SnippetRepository.forContext(app)
        try {
            pull(uri, repository)
        } catch (_: SecurityException) {
            settings.backupProblem = BackupProblem.Access
            return@withLock Outcome.Failed
        } catch (_: FileNotFoundException) {
            // Drive answers "not found" both for a deleted file and for one it can't fetch while
            // offline: a file the provider still lists is only waiting.
            settings.backupProblem = if (BackupFiles.exists(app, uri)) BackupProblem.Read else BackupProblem.Access
            return@withLock Outcome.Failed
        } catch (_: Exception) {
            settings.backupProblem = BackupProblem.Read
            return@withLock Outcome.Failed
        }
        val fingerprint = repository.backupFingerprint()
        if (!force && fingerprint == settings.backupFingerprint) {
            settings.backupLastAt = System.currentTimeMillis()
            settings.backupProblem = null
            return@withLock Outcome.Unchanged
        }
        var built: File? = null
        try {
            built = BackupFiles.build(app, withSync = true).first
            BackupFiles.writeTo(app, uri, built)
            // What the file now holds is what both phones agree on.
            val json = BackupArchive.readJson(built)
            SyncBase.save(app, json)
            remember(uri, built)
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

    /**
     * Reads the shared file when its size, modified time or hash moved since we last saw it, and
     * merges it into the database. An empty file (just created) holds nothing to merge. Throws when
     * the file can't be read or isn't a backup.
     */
    private suspend fun pull(uri: Uri, repository: SnippetRepository) {
        val (size, modified) = BackupFiles.stat(app, uri)
        if (size != null && modified != null && settings.syncRemoteHash != null &&
            size == settings.syncRemoteSize && modified == settings.syncRemoteModified
        ) {
            return
        }
        val copy = BackupFiles.copyIn(app, uri)
        try {
            val hash = BackupFiles.sha256Hex(copy)
            if (hash == settings.syncRemoteHash || copy.length() == 0L) {
                remember(uri, copy)
                return
            }
            val remote = SnippetBackup.decodeState(BackupArchive.readJson(copy))
            val result = SyncMerge.merge(SyncBase.load(app), repository.syncState(), remote, System.currentTimeMillis())
            val images = ImageStore.forContext(app).imagesDir()
            val zip = BackupArchive.isZip(copy)
            result.state.snippets.mapNotNull { it.imageFile }.distinct().forEach { name ->
                if (zip && !File(images, name).isFile) BackupArchive.extractImage(copy, name, images)
            }
            repository.applySync(result) { name -> File(images, name).isFile }
            // The file is now part of what this phone holds: the next merge starts from it.
            SyncBase.save(app, SnippetBackup.encodeState(remote))
            settings.backupFingerprint = SnippetBackup.fingerprint(remote)
            remember(uri, copy)
        } finally {
            copy.delete()
        }
    }

    private fun remember(uri: Uri, contents: File) {
        val (size, modified) = BackupFiles.stat(app, uri)
        settings.syncRemoteSize = size
        settings.syncRemoteModified = modified
        settings.syncRemoteHash = BackupFiles.sha256Hex(contents)
    }

    /** Syncs now unless one ran in the last [minIntervalMs]: the keyboard opening, the app coming back. */
    fun syncSoon(minIntervalMs: Long = 0) {
        if (settings.backupUri == null) return
        if (System.currentTimeMillis() - lastAttempt < minIntervalMs) return
        scope.launch { runCatching { backUp(force = false) } }
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
