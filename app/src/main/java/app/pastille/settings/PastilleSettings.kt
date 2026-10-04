package app.pastille.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

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

    var keyboardStyle: KeyboardStyle
        get() = KeyboardStyle.fromKey(prefs.getString(KEY_KEYBOARD_STYLE, null))
        set(value) = prefs.edit().putString(KEY_KEYBOARD_STYLE, value.key).apply()

    var keyboardStyleChosen: Boolean
        get() = prefs.getBoolean(KEY_KEYBOARD_STYLE_CHOSEN, false)
        set(value) = prefs.edit().putBoolean(KEY_KEYBOARD_STYLE_CHOSEN, value).apply()

    var panelHeightPortrait: PanelHeight
        get() = PanelHeight.fromKey(prefs.getString(KEY_PANEL_HEIGHT_PORTRAIT, null))
        set(value) = prefs.edit().putString(KEY_PANEL_HEIGHT_PORTRAIT, value.key).apply()

    var panelHeightLandscape: PanelHeight
        get() = PanelHeight.fromKey(prefs.getString(KEY_PANEL_HEIGHT_LANDSCAPE, null))
        set(value) = prefs.edit().putString(KEY_PANEL_HEIGHT_LANDSCAPE, value.key).apply()

    var keyboardMode: KeyboardMode
        get() = KeyboardMode.fromKey(prefs.getString(KEY_KEYBOARD_MODE, null))
        set(value) = prefs.edit().putString(KEY_KEYBOARD_MODE, value.key).apply()

    var imageSourceBucketId: Long?
        get() = readCategoryId(KEY_IMAGE_SOURCE_BUCKET_ID)
        set(value) = writeCategoryId(KEY_IMAGE_SOURCE_BUCKET_ID, value)

    // Emits once on collection and again on every change, so the keyboard redraws when Settings changes.
    fun changes(): Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(Unit)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    var previousImeId: String?
        get() = prefs.getString(KEY_PREVIOUS_IME_ID, null)
        set(value) = prefs.edit().putString(KEY_PREVIOUS_IME_ID, value).apply()

    companion object {
        private const val PREFS_NAME = "pastille_settings"
        private const val KEY_PREVIOUS_IME_ID = "previous_ime_id"
        private const val KEY_RETURN_TO_PREVIOUS_KEYBOARD = "return_to_previous_keyboard"
        private const val KEY_APP_CATEGORY_ID = "app_category_id"
        private const val KEY_KEYBOARD_CATEGORY_ID = "keyboard_category_id"
        private const val KEY_KEYBOARD_STYLE = "keyboard_style"
        private const val KEY_KEYBOARD_STYLE_CHOSEN = "keyboard_style_chosen"
        private const val KEY_PANEL_HEIGHT_PORTRAIT = "panel_height_portrait"
        private const val KEY_PANEL_HEIGHT_LANDSCAPE = "panel_height_landscape"
        private const val KEY_KEYBOARD_MODE = "keyboard_mode"
        private const val KEY_IMAGE_SOURCE_BUCKET_ID = "image_source_bucket_id"

        fun forContext(context: Context): PastilleSettings =
            PastilleSettings(
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
            )
    }
}
