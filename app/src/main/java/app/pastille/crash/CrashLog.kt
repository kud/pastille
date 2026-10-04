package app.pastille.crash

import android.content.Context
import java.io.File
import java.time.Instant

class CrashLog(private val dir: File, private val maxEntries: Int = 5) {

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
        File(dir, "crash-$now.txt").writeText(body)
        crashFiles().drop(maxEntries).forEach { it.delete() }
    }

    fun entries(): List<String> {
        return crashFiles().mapNotNull { runCatching { it.readText() }.getOrNull() }
    }

    fun clear() {
        crashFiles().forEach { it.delete() }
    }

    private fun crashFiles(): List<File> {
        val files = dir.listFiles { file ->
            file.isFile && file.name.startsWith("crash-") && file.name.endsWith(".txt")
        } ?: return emptyList()
        return files.sortedByDescending {
            it.name.removePrefix("crash-").removeSuffix(".txt").toLongOrNull() ?: Long.MIN_VALUE
        }
    }

    companion object {
        fun forContext(context: Context) = CrashLog(File(context.filesDir, "crashes"))
    }
}
