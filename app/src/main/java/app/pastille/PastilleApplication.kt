package app.pastille

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import app.pastille.crash.CrashLog
import app.pastille.data.DatabaseHolder
import app.pastille.data.SnippetRepository
import app.pastille.images.ImageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PastilleApplication : Application() {

    // Outlives any one screen: startup chores, and writes a closing sheet must not cancel.
    val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val crashLog = CrashLog.forContext(this)
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                crashLog.record(
                    throwable,
                    versionName(),
                    "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                )
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, throwable)
        }
        backgroundScope.launch {
            runCatching { SnippetRepository.forContext(this@PastilleApplication).backfillTitles() }
        }
        backgroundScope.launch {
            runCatching {
                val referenced =
                    DatabaseHolder.get(this@PastilleApplication).snippets().allImageFiles().toSet()
                ImageStore.forContext(this@PastilleApplication)
                    .sweepOrphans(referenced, System.currentTimeMillis())
            }
        }
    }

    private fun versionName(): String {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            info.versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }
}
