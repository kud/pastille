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
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.DocumentsContract
import app.pastille.crash.CrashLog

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
     * Starts syncing with [uri]: a file just created ([justCreated], from "Sync automatically"), or
     * one another phone already syncs with (then its contents are merged in first). Writes it at once.
     */
    suspend fun choose(uri: Uri, justCreated: Boolean = false): Outcome {
        val resolver = app.contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        settings.backupUri?.let { old -> if (old != uri.toString()) release(Uri.parse(old)) }
        var grantProblem: Throwable? = null
        try {
            resolver.takePersistableUriPermission(uri, flags)
            // Some providers return without persisting anything; that only shows after a restart.
            if (!isPersisted(uri)) grantProblem = SecurityException("takePersistableUriPermission returned but nothing was persisted")
        } catch (error: SecurityException) {
            grantProblem = error
        }
        settings.backupUri = uri.toString()
        settings.backupName = BackupFiles.displayName(app, uri) ?: BackupArchive.SUGGESTED_NAME
        settings.backupLastAt = null
        settings.backupFingerprint = null
        settings.backupProblem = null
        settings.syncRemoteSize = null
        settings.syncRemoteModified = null
        settings.syncRemoteHash = null
        // A new file starts a new sync: nothing agreed yet, so the first merge is last-edit-wins.
        SyncBase.clear(app)
        val outcome = backUp(force = true, justCreated = justCreated)
        // The picker's own grant lasts until the phone restarts, so this sync may well have worked;
        // the status still says the storage app didn't keep it, which is what will stop it later.
        grantProblem?.let { fail(SyncStep.Grant, it, uri, mapOf("Outcome now" to outcome.name)) }
        return outcome
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
     * stops the write: this phone never overwrites changes it hasn't seen. The one exception is a
     * file the user just created and the provider reports empty ([SyncDiagnosis.mayWriteUnread]).
     */
    suspend fun backUp(force: Boolean, justCreated: Boolean = false): Outcome = mutex.withLock {
        lastAttempt = System.currentTimeMillis()
        val uri = settings.backupUri?.let(Uri::parse) ?: return@withLock Outcome.Off
        val persisted = isPersisted(uri)
        if (!persisted && !hasGrant(uri)) {
            // A provider that never kept the permission keeps saying so, rather than "can't reach".
            val keep = settings.backupProblem == BackupProblem.NotKept || settings.backupProblem == BackupProblem.ReadOnly
            val error = SecurityException("No persisted or current permission")
            if (keep) {
                fail(SyncStep.Grant, SyncFailure(SyncStep.Grant, settings.backupProblem!!, "Permission not kept", error), uri)
            } else {
                fail(SyncStep.Grant, SyncFailure(SyncStep.Grant, BackupProblem.Access, "Permission gone", error), uri)
            }
            return@withLock Outcome.Failed
        }
        val repository = SnippetRepository.forContext(app)
        var seenLength: Long? = null
        var mustWrite = force
        try {
            seenLength = pull(uri, repository)
        } catch (error: Exception) {
            val size = BackupFiles.stat(app, uri).first
            if (SyncDiagnosis.mayReplaceBroken(settings.syncPendingWrite, error)) {
                // Our own write was cut off (process killed, upload dropped): put the whole file back.
                mustWrite = true
            } else if (!SyncDiagnosis.mayWriteUnread(justCreated, size)) {
                fail(SyncStep.Read, error, uri)
                return@withLock Outcome.Failed
            }
            seenLength = size ?: 0L
        }
        val fingerprint = repository.backupFingerprint()
        if (!mustWrite && fingerprint == settings.backupFingerprint) {
            settings.backupLastAt = System.currentTimeMillis()
            settings.backupProblem = if (persisted) null else BackupProblem.NotKept
            return@withLock Outcome.Unchanged
        }
        var built: File? = null
        try {
            built = BackupFiles.build(app, withSync = true).first
            // What the file holds now, as long as anyone has reported it: plain "w" is only safe
            // over a file no longer than the new one.
            val current = listOfNotNull(BackupFiles.stat(app, uri).first, seenLength, settings.syncRemoteSize).maxOrNull()
            // Before the first byte: what the file held (a stale cache may serve it again) and what
            // it is about to hold (until it reads back, a broken file is our own cut-off write).
            settings.syncReplacedHash = settings.syncRemoteHash
            settings.syncPendingWrite = BackupFiles.sha256Hex(built)
            BackupFiles.writeTo(app, uri, built, current)
            settings.syncPendingWrite = null
            // What the file now holds is what both phones agree on.
            val json = BackupArchive.readJson(built)
            SyncBase.save(app, json)
            remember(uri, built)
            settings.backupLastAt = System.currentTimeMillis()
            settings.backupFingerprint = fingerprint
            settings.backupProblem = if (persisted) null else BackupProblem.NotKept
            Outcome.Written
        } catch (error: Exception) {
            // Refused before anything was opened: the file is as it was.
            if (error is CantOverwrite) settings.syncPendingWrite = null
            fail(if (error is ReadBackMismatch) SyncStep.ReadBack else SyncStep.Write, error, uri)
            Outcome.Failed
        } finally {
            built?.delete()
        }
    }

    /**
     * Records why a sync failed: the plain reason for Settings › Backup, and where and how for the
     * diagnostic log (provider authority, step, exception classes, what the provider says it
     * supports; never the file's contents).
     */
    private fun fail(step: SyncStep, error: Throwable, uri: Uri, extra: Map<String, String> = emptyMap()) {
        val flags = BackupFiles.flags(app, uri)
        // The flags when the provider gives them; else a grant that reads but doesn't write says it.
        val canWrite = flags?.let { it and DocumentsContract.Document.FLAG_SUPPORTS_WRITE != 0 }
            ?: if (hasGrant(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) && !hasGrant(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)) false else null
        val listed = BackupFiles.exists(app, uri)
        val problem = SyncDiagnosis.problemFor(step, error, listed, canWrite)
        settings.backupProblem = problem
        runCatching {
            val (size, modified) = BackupFiles.stat(app, uri)
            val permission = app.contentResolver.persistedUriPermissions.firstOrNull { it.uri == uri }
            val facts = extra + mapOf(
                "Listed" to listed.toString(),
                "Flags" to (flags?.let { "0x" + Integer.toHexString(it) + (if (canWrite == true) " write" else "") + (if (it and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0) " delete" else "") } ?: "unknown"),
                "Size known" to (size != null).toString(),
                "Modified known" to (modified != null).toString(),
                "Persisted" to (permission?.let { "read=${it.isReadPermission} write=${it.isWritePermission}" } ?: "none"),
            )
            CrashLog.forContext(app).recordSync(
                SyncDiagnosis.report(step, error, problem, uri.authority, facts),
                SyncDiagnosis.signature(step, error, uri.authority),
                versionName(),
                "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            )
        }
    }

    /**
     * Reads the shared file when its size, modified time or hash moved since we last saw it, and
     * merges it into the database. An empty file (just created) holds nothing to merge. Throws when
     * the file can't be read or isn't a backup. Returns how many bytes it read, or null when the
     * file was unchanged and not read.
     */
    private suspend fun pull(uri: Uri, repository: SnippetRepository): Long? {
        val (size, modified) = BackupFiles.stat(app, uri)
        if (size != null && modified != null && settings.syncRemoteHash != null &&
            size == settings.syncRemoteSize && modified == settings.syncRemoteModified
        ) {
            return null
        }
        val copy = BackupFiles.copyIn(app, uri)
        try {
            val length = copy.length()
            val hash = BackupFiles.sha256Hex(copy)
            if (hash == settings.syncRemoteHash || length == 0L) {
                remember(uri, copy)
                return length
            }
            if (SyncDiagnosis.isStaleRead(hash, settings.syncReplacedHash, settings.syncRemoteHash)) {
                // The provider's cache, not news: neither merge it nor write over what it hides.
                throw StaleRead()
            }
            val json = try {
                BackupArchive.readJson(copy)
            } catch (error: Exception) {
                throw BrokenArchive(error)
            }
            val remote = SnippetBackup.decodeState(json)
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
            return length
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

    private fun isPersisted(uri: Uri): Boolean =
        app.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission && it.isReadPermission }

    /** The picker's grant, which outlives the activity but not a restart. */
    private fun hasGrant(
        uri: Uri,
        flags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
    ): Boolean = app.checkUriPermission(uri, Process.myPid(), Process.myUid(), flags) == PackageManager.PERMISSION_GRANTED

    private fun versionName(): String = try {
        @Suppress("DEPRECATION")
        app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "unknown"
    } catch (_: Exception) {
        "unknown"
    }

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
