package app.pastille.settings

import android.content.Context
import android.content.SharedPreferences

class PastilleSettings private constructor(private val prefs: SharedPreferences) {

    var returnToPreviousKeyboard: Boolean
        get() = prefs.getBoolean(KEY_RETURN_TO_PREVIOUS_KEYBOARD, true)
        set(value) = prefs.edit().putBoolean(KEY_RETURN_TO_PREVIOUS_KEYBOARD, value).apply()

    var appCategoryId: Long?
        get() = readCategoryId(KEY_APP_CATEGORY_ID)
        set(value) = writeCategoryId(KEY_APP_CATEGORY_ID, value)

    var keyboardCategoryId: Long?
        get() = readCategoryId(KEY_KEYBOARD_CATEGORY_ID)
        set(value) = writeCategoryId(KEY_KEYBOARD_CATEGORY_ID, value)

    private fun readCategoryId(key: String): Long? =
        if (prefs.contains(key)) prefs.getLong(key, 0) else null

    private fun writeCategoryId(key: String, value: Long?) {
        prefs.edit().apply { if (value == null) remove(key) else putLong(key, value) }.apply()
    }

    var previousImeId: String?
        get() = prefs.getString(KEY_PREVIOUS_IME_ID, null)
        set(value) = prefs.edit().putString(KEY_PREVIOUS_IME_ID, value).apply()

    companion object {
        private const val PREFS_NAME = "pastille_settings"
        private const val KEY_PREVIOUS_IME_ID = "previous_ime_id"
        private const val KEY_RETURN_TO_PREVIOUS_KEYBOARD = "return_to_previous_keyboard"
        private const val KEY_APP_CATEGORY_ID = "app_category_id"
        private const val KEY_KEYBOARD_CATEGORY_ID = "keyboard_category_id"

        fun forContext(context: Context): PastilleSettings =
            PastilleSettings(
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
            )
    }
}
