package app.pastille.settings

import android.content.Context
import android.content.SharedPreferences

class PastilleSettings private constructor(private val prefs: SharedPreferences) {

    var returnToPreviousKeyboard: Boolean
        get() = prefs.getBoolean(KEY_RETURN_TO_PREVIOUS_KEYBOARD, true)
        set(value) = prefs.edit().putBoolean(KEY_RETURN_TO_PREVIOUS_KEYBOARD, value).apply()

    var previousImeId: String?
        get() = prefs.getString(KEY_PREVIOUS_IME_ID, null)
        set(value) = prefs.edit().putString(KEY_PREVIOUS_IME_ID, value).apply()

    companion object {
        private const val PREFS_NAME = "pastille_settings"
        private const val KEY_PREVIOUS_IME_ID = "previous_ime_id"
        private const val KEY_RETURN_TO_PREVIOUS_KEYBOARD = "return_to_previous_keyboard"

        fun forContext(context: Context): PastilleSettings =
            PastilleSettings(
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
            )
    }
}
