package app.pastille

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import app.pastille.backup.AutoBackup
import app.pastille.clipboard.ClipboardClearer
import app.pastille.clipboard.RecentClipboard
import app.pastille.clipboard.SystemClipboardAccess
import app.pastille.crash.CrashLog
import app.pastille.data.cleanUpAtStart
import app.pastille.data.SnippetRepository
import app.pastille.images.ImageStore
import app.pastille.settings.PastilleSettings
import app.pastille.share.MAX_TEXT_CHARS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PastilleApplication : Application() {

    // Outlives any one screen: startup chores, and writes a closing sheet must not cancel.
    val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Settings › Backup: rewrites the chosen file after changes; see AutoBackup.
    val autoBackup by lazy { AutoBackup(this, backgroundScope) }

    val clipboardClearer by lazy {
        ClipboardClearer(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            access = SystemClipboardAccess(this),
        )
    }

    // Recent clipboard: memory only, shared by the keyboard and the app; see RecentClips.kt.
    val recentClipboard by lazy {
        RecentClipboard(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            enabled = { PastilleSettings.forContext(this).recentClipboard },
            maxChars = MAX_TEXT_CHARS,
        )
    }

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
        autoBackup.start()
        backgroundScope.launch {
            runCatching { SnippetRepository.forContext(this@PastilleApplication).backfillTitles() }
        }
        backgroundScope.launch {
            runCatching {
                val repository = SnippetRepository.forContext(this@PastilleApplication)
                val now = System.currentTimeMillis()
                cleanUpAtStart(
                    purgeExpiredBin = { repository.purgeExpiredBin(now) },
                    referencedImageFiles = { repository.referencedImageFiles() },
                    sweepOrphans = { ImageStore.forContext(this@PastilleApplication).sweepOrphans(it, now) },
                )
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
