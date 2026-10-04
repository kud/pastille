package app.pastille

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import app.pastille.crash.CrashLog

class PastilleApplication : Application() {

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
