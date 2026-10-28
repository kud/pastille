package app.pastille.backup

import app.pastille.settings.BackupProblem
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Pure rules for telling one storage app's failure from another's, so Settings › Backup can say
 * what went wrong in plain words and the diagnostic log can say exactly where. No Android here:
 * [AutoBackup] and [BackupFiles] feed it what the provider did.
 */

/** The step of a sync that failed, as the diagnostic log names it. */
enum class SyncStep(val label: String) {
    Grant("keep permission"),
    Read("read"),
    Write("write"),
    ReadBack("read back"),
}

/** A sync step failed for a reason Settings can name. [problem] is what the status line shows. */
class SyncFailure(
    val step: SyncStep,
    val problem: BackupProblem,
    detail: String,
    cause: Throwable? = null,
) : IOException(detail, cause)

/** The shared file isn't a whole archive: the zip or its JSON entry can't be opened. */
class BrokenArchive(cause: Throwable) : IOException("Not a whole archive", cause)

/** The provider served the file as it was before this phone's last write. */
class StaleRead : IOException("Provider served the replaced file")

/** The provider refused both truncating write modes and the new file is shorter than the old one. */
class CantOverwrite(detail: String) : IOException(detail)

/** The file read back differs from what was written, after waiting for the provider to settle. */
class ReadBackMismatch(detail: String) : IOException(detail)

object SyncDiagnosis {

    /**
     * The storage app's name for the status line, from the document's authority. Only the ones we
     * know by name; anything else is "the storage app" rather than a guessed label.
     */
    fun providerLabel(authority: String?): String {
        val a = authority?.lowercase() ?: return "the storage app"
        return when {
            "pcloud" in a -> "pCloud"
            a.startsWith("com.google.android.apps.docs") -> "Drive"
            "dropbox" in a -> "Dropbox"
            "nextcloud" in a -> "Nextcloud"
            "onedrive" in a || "skydrive" in a -> "OneDrive"
            a == "com.android.externalstorage.documents" ||
                a == "com.android.providers.downloads.documents" ||
                a == "com.android.providers.media.documents" -> "this phone"
            else -> "the storage app"
        }
    }

    /**
     * Which write modes to try, in order, given what the provider already refused. "wt" and "rwt"
     * truncate. Plain "w" doesn't on several Android versions, so it is only safe when the new
     * archive is at least as long as what the file holds now: then nothing of the old one can be
     * left past its end. [currentLength] null (unknown) never allows it.
     */
    fun plainWriteIsSafe(newLength: Long, currentLength: Long?): Boolean =
        currentLength != null && currentLength >= 0 && newLength >= currentLength

    /**
     * What a failure means for the status line. [readable]: the provider still lists the file.
     * [canWrite]: the document's flags say it takes writes (null when the provider didn't say).
     */
    fun problemFor(step: SyncStep, error: Throwable, readable: Boolean, canWrite: Boolean?): BackupProblem = when {
        error is SyncFailure -> error.problem
        error is CantOverwrite -> BackupProblem.CantOverwrite
        error is ReadBackMismatch -> BackupProblem.Mismatch
        step == SyncStep.Grant -> if (canWrite == false) BackupProblem.ReadOnly else BackupProblem.NotKept
        error is SecurityException -> if (canWrite == false) BackupProblem.ReadOnly else BackupProblem.Access
        // Drive answers "not found" both for a deleted file and for one it can't fetch offline: a
        // file the provider still lists is only waiting.
        step == SyncStep.Read -> if (error is FileNotFoundException && !readable) BackupProblem.Access else BackupProblem.Read
        error is FileNotFoundException -> if (readable) BackupProblem.Write else BackupProblem.Access
        else -> BackupProblem.Write
    }

    /**
     * Whether a sync may write a file it couldn't read: only one the user just created with "Sync
     * automatically", which the provider reports empty or doesn't size. Some providers can't serve a
     * file they haven't uploaded yet; refusing to write it would wait forever on a file that holds
     * nothing. Any file with bytes in it is never overwritten unread.
     */
    fun mayWriteUnread(justCreated: Boolean, size: Long?): Boolean = justCreated && (size == null || size == 0L)

    /**
     * Whether a file that isn't a whole archive may be replaced: only when this phone started a
     * write to it that it never confirmed ([pendingWrite], set before the stream opens and cleared
     * once the bytes read back). Then the broken bytes are its own interrupted write, and everything
     * they were meant to hold is still in this phone's database. A broken file nobody here was
     * writing (the wrong file picked, another app's) is never touched. A whole archive from a newer
     * Pastille is not broken and never comes here.
     */
    fun mayReplaceBroken(pendingWrite: String?, error: Throwable): Boolean =
        pendingWrite != null && error is BrokenArchive

    /**
     * Whether a read is the provider serving the file as it was before this phone replaced it (a
     * stale cache, or offline): its hash is the one we overwrote and not the one we wrote. Merging
     * it would undo our own changes, so it is skipped until the provider catches up.
     */
    fun isStaleRead(hash: String, replacedHash: String?, writtenHash: String?): Boolean =
        replacedHash != null && hash == replacedHash && hash != writtenHash

    /** A modified time of 0 or less means the provider doesn't know it. */
    fun knownModified(modified: Long?): Long? = modified?.takeIf { it > 0 }

    /**
     * The diagnostic log's entry for a failed sync: where it failed and how, never what the file
     * holds. Exception classes only, cause chain included; no messages, since a parser's message
     * can quote the file's contents.
     */
    fun report(
        step: SyncStep,
        error: Throwable,
        problem: BackupProblem,
        authority: String?,
        facts: Map<String, String>,
    ): String = buildString {
        appendLine("Sync failed: ${step.label}")
        appendLine("Provider: ${authority ?: "unknown"}")
        appendLine("Problem: ${problem.key}")
        appendLine("Exception: ${causeChain(error)}")
        facts.toSortedMap().forEach { (key, value) -> appendLine("$key: $value") }
    }

    /** "SyncFailure < SecurityException": each class once, outermost first. */
    fun causeChain(error: Throwable): String {
        val seen = mutableListOf<String>()
        var current: Throwable? = error
        while (current != null && seen.size < 6) {
            seen += current.javaClass.name
            current = current.cause?.takeIf { it !== current }
        }
        return seen.joinToString(" < ")
    }

    /** What makes two failures the same, so a provider failing on every trigger logs once. */
    fun signature(step: SyncStep, error: Throwable, authority: String?): String =
        "${step.name}|${causeChain(error)}|${authority.orEmpty()}"
}
