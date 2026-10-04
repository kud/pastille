package app.pastille.tile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import app.pastille.settings.PastilleSettings

object ImeSwitcher {

    const val PASTILLE_IME_ID = "app.pastille/.ime.PastilleImeService"

    fun hasWriteSecureSettings(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    fun isPastilleCurrent(context: Context): Boolean =
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD,
        ) == PASTILLE_IME_ID

    fun toggle(context: Context): Boolean {
        if (!hasWriteSecureSettings(context)) {
            return false
        }
        try {
            val cr = context.contentResolver
            val current = Settings.Secure.getString(cr, Settings.Secure.DEFAULT_INPUT_METHOD)
            val enabled = parseEnabledIds(
                Settings.Secure.getString(cr, Settings.Secure.ENABLED_INPUT_METHODS),
            )
            val settings = PastilleSettings.forContext(context)
            val target = chooseTarget(current, enabled, settings.previousImeId) ?: return false
            if (current != PASTILLE_IME_ID && !current.isNullOrBlank()) {
                settings.previousImeId = current
            }
            Settings.Secure.putString(cr, Settings.Secure.DEFAULT_INPUT_METHOD, target)
            return true
        } catch (e: SecurityException) {
            return false
        }
    }

    internal fun parseEnabledIds(raw: String?): List<String> {
        if (raw.isNullOrEmpty()) {
            return emptyList()
        }
        return raw.split(":")
            .map { it.substringBefore(";").trim() }
            .filter { it.isNotEmpty() }
    }

    internal fun chooseTarget(
        current: String?,
        enabled: List<String>,
        previous: String?,
    ): String? {
        if (current != PASTILLE_IME_ID) {
            return if (enabled.contains(PASTILLE_IME_ID)) PASTILLE_IME_ID else null
        }
        if (previous != null && previous != PASTILLE_IME_ID && enabled.contains(previous)) {
            return previous
        }
        return enabled.firstOrNull { it != PASTILLE_IME_ID }
    }
}
