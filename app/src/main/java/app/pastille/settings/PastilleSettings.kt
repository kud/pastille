package app.pastille.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

class PastilleSettings private constructor(private val prefs: SharedPreferences) {

    var returnAfterSnippet: Boolean
        get() = prefs.getBoolean(KEY_RETURN_AFTER_SNIPPET, returnDefault(legacyReturn(), image = false))
        set(value) = prefs.edit().putBoolean(KEY_RETURN_AFTER_SNIPPET, value).apply()

    var returnAfterImage: Boolean
        get() = prefs.getBoolean(KEY_RETURN_AFTER_IMAGE, returnDefault(legacyReturn(), image = true))
        set(value) = prefs.edit().putBoolean(KEY_RETURN_AFTER_IMAGE, value).apply()

    private fun legacyReturn(): Boolean? =
        if (prefs.contains(KEY_RETURN_TO_PREVIOUS_KEYBOARD)) prefs.getBoolean(KEY_RETURN_TO_PREVIOUS_KEYBOARD, true) else null

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

    var snippetsEnabled: Boolean
        get() = prefs.getBoolean(KEY_SNIPPETS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SNIPPETS_ENABLED, value).apply()

    var imagesEnabled: Boolean
        get() = prefs.getBoolean(KEY_IMAGES_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_IMAGES_ENABLED, value).apply()

    var keyboardMode: KeyboardMode
        get() = KeyboardMode.fromKey(prefs.getString(KEY_KEYBOARD_MODE, null))
        set(value) = prefs.edit().putString(KEY_KEYBOARD_MODE, value.key).apply()

    var imageSourceBucketId: Long?
        get() = readCategoryId(KEY_IMAGE_SOURCE_BUCKET_ID)
        set(value) = writeCategoryId(KEY_IMAGE_SOURCE_BUCKET_ID, value)

    var enabledImageSources: Set<Long>?
        get() = prefs.getStringSet(KEY_ENABLED_IMAGE_SOURCES, null)?.mapNotNull { it.toLongOrNull() }?.toSet()
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_ENABLED_IMAGE_SOURCES) else putStringSet(KEY_ENABLED_IMAGE_SOURCES, value.map { it.toString() }.toSet())
        }.apply()

    // Emits once on collection and again on every change, so the keyboard redraws when Settings changes.
    fun changes(): Flow<Int> = callbackFlow {
        var version = 0
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(++version) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(version)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    // null means the main keyboard (the first other one the system lists); PREVIOUS_KEYBOARD keeps Android's "previous" behaviour.
    var returnKeyboardId: String?
        get() = prefs.getString(KEY_RETURN_KEYBOARD_ID, null)
        set(value) = prefs.edit().apply { if (value == null) remove(KEY_RETURN_KEYBOARD_ID) else putString(KEY_RETURN_KEYBOARD_ID, value) }.apply()

    var previousImeId: String?
        get() = prefs.getString(KEY_PREVIOUS_IME_ID, null)
        set(value) = prefs.edit().putString(KEY_PREVIOUS_IME_ID, value).apply()

    companion object {
        private const val PREFS_NAME = "pastille_settings"
        private const val KEY_PREVIOUS_IME_ID = "previous_ime_id"
        private const val KEY_RETURN_KEYBOARD_ID = "return_keyboard_id"
        const val PREVIOUS_KEYBOARD = "previous"
        private const val KEY_RETURN_TO_PREVIOUS_KEYBOARD = "return_to_previous_keyboard"
        private const val KEY_RETURN_AFTER_SNIPPET = "return_after_snippet"
        private const val KEY_RETURN_AFTER_IMAGE = "return_after_image"
        private const val KEY_APP_CATEGORY_ID = "app_category_id"
        private const val KEY_KEYBOARD_CATEGORY_ID = "keyboard_category_id"
        private const val KEY_KEYBOARD_STYLE = "keyboard_style"
        private const val KEY_KEYBOARD_STYLE_CHOSEN = "keyboard_style_chosen"
        private const val KEY_PANEL_HEIGHT_PORTRAIT = "panel_height_portrait"
        private const val KEY_PANEL_HEIGHT_LANDSCAPE = "panel_height_landscape"
        private const val KEY_KEYBOARD_MODE = "keyboard_mode"
        private const val KEY_SNIPPETS_ENABLED = "snippets_enabled"
        private const val KEY_IMAGES_ENABLED = "images_enabled"
        private const val KEY_IMAGE_SOURCE_BUCKET_ID = "image_source_bucket_id"
        private const val KEY_ENABLED_IMAGE_SOURCES = "enabled_image_sources"

        fun forContext(context: Context): PastilleSettings =
            PastilleSettings(
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
            )
    }
}

// One switch used to cover both; an upgrade keeps whatever it was set to. Fresh installs return
// after a snippet but stay for images, so several can go in a row.
fun returnDefault(legacy: Boolean?, image: Boolean): Boolean = legacy ?: !image

fun returnKeyboardTarget(chosen: String?, otherKeyboardIds: List<String>): String? = when {
    chosen == PastilleSettings.PREVIOUS_KEYBOARD -> null
    chosen != null && chosen in otherKeyboardIds -> chosen
    else -> otherKeyboardIds.firstOrNull()
}
