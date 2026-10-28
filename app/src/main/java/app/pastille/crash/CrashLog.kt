package app.pastille.crash

import android.content.Context
import java.io.File
import java.time.Instant

class CrashLog(private val dir: File, private val maxEntries: Int = 5, private val maxSyncEntries: Int = 3) {

    fun record(throwable: Throwable, versionName: String, androidVersion: String, now: Long = System.currentTimeMillis()) {
        dir.mkdirs()
        val body = buildString {
            appendLine("Time: ${Instant.ofEpochMilli(now)}")
            appendLine("Version: $versionName")
            appendLine("Android: $androidVersion")
            appendLine("Thread: ${Thread.currentThread().name}")
            appendLine()
            append(throwable.stackTraceToString())
        }
        File(dir, "$CRASH$now.txt").writeText(body)
        crashFiles().drop(maxEntries).forEach { it.delete() }
    }

    /**
     * A sync that failed: where and how ([report], from SyncDiagnosis, which never holds the file's
     * contents), so the next report says what the storage app did. The same [signature] as the
     * newest sync entry is not logged again: a provider failing on every trigger fills nothing.
     */
    fun recordSync(report: String, signature: String, versionName: String, androidVersion: String, now: Long = System.currentTimeMillis()) {
        dir.mkdirs()
        val newest = files(SYNC).firstOrNull()?.let { runCatching { it.readText() }.getOrNull() }
        if (newest != null && newest.lineSequence().any { it == "Signature: $signature" }) return
        val body = buildString {
            appendLine("Time: ${Instant.ofEpochMilli(now)}")
            appendLine("Version: $versionName")
            appendLine("Android: $androidVersion")
            append(report.trimEnd())
            appendLine()
            appendLine("Signature: $signature")
        }
        File(dir, "$SYNC$now.txt").writeText(body)
        files(SYNC).drop(maxSyncEntries).forEach { it.delete() }
    }

    /** Crashes and sync failures, newest first. */
    fun entries(): List<String> {
        return (crashFiles() + files(SYNC))
            .sortedByDescending { stamp(it) }
            .mapNotNull { runCatching { it.readText() }.getOrNull() }
    }

    fun clear() {
        (crashFiles() + files(SYNC)).forEach { it.delete() }
    }

    private fun crashFiles(): List<File> = files(CRASH)

    private fun files(prefix: String): List<File> {
        val files = dir.listFiles { file ->
            file.isFile && file.name.startsWith(prefix) && file.name.endsWith(".txt")
        } ?: return emptyList()
        return files.sortedByDescending { stamp(it) }
    }

    private fun stamp(file: File): Long =
        file.name.substringAfter('-').removeSuffix(".txt").toLongOrNull() ?: Long.MIN_VALUE

    companion object {
        private const val CRASH = "crash-"
        private const val SYNC = "sync-"

        fun forContext(context: Context) = CrashLog(File(context.filesDir, "crashes"))
    }
}
