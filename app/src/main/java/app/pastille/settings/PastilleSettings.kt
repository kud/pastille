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

    // The app's main list: true when it last showed Stickers rather than Snippets, so that mode reopens.
    var appStickersMode: Boolean
        get() = prefs.getBoolean(KEY_APP_STICKERS_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_APP_STICKERS_MODE, value).apply()

    // True once the system photo prompt has been answered, either way. The setup card asks once;
    // after that, Settings › Photo access is where it changes (the system stops asking after two refusals).
    var photoPromptAnswered: Boolean
        get() = prefs.getBoolean(KEY_PHOTO_PROMPT_ANSWERED, false)
        set(value) = prefs.edit().putBoolean(KEY_PHOTO_PROMPT_ANSWERED, value).apply()

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

    var stickersEnabled: Boolean
        get() = prefs.getBoolean(KEY_STICKERS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_STICKERS_ENABLED, value).apply()

    fun enabledModes(): Set<KeyboardMode> = buildSet {
        if (clipboardEnabled) add(KeyboardMode.Clipboard)
        if (snippetsEnabled) add(KeyboardMode.Snippets)
        if (stickersEnabled) add(KeyboardMode.Stickers)
        if (imagesEnabled) add(KeyboardMode.Images)
    }

    fun setModeEnabled(mode: KeyboardMode, enabled: Boolean) {
        when (mode) {
            KeyboardMode.Clipboard -> clipboardEnabled = enabled
            KeyboardMode.Snippets -> snippetsEnabled = enabled
            KeyboardMode.Stickers -> stickersEnabled = enabled
            KeyboardMode.Images -> imagesEnabled = enabled
        }
    }

    // The share sheet's last "Save as" choice for images.
    var saveImagesAsStickers: Boolean
        get() = prefs.getBoolean(KEY_SAVE_AS_STICKERS, false)
        set(value) = prefs.edit().putBoolean(KEY_SAVE_AS_STICKERS, value).apply()

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

    // null means the first typing keyboard the system lists (what "Main keyboard" stored); PREVIOUS_KEYBOARD
    // keeps Android's "previous" behaviour; anything else is an IME id.
    val returnKeyboardId: String?
        get() = prefs.getString(KEY_RETURN_KEYBOARD_ID, null)

    // The language (subtype hash code) chosen under that keyboard; null means the keyboard's own last one.
    // Added after the id, so older installs simply have none.
    val returnKeyboardSubtype: Int?
        get() = if (prefs.contains(KEY_RETURN_KEYBOARD_SUBTYPE)) prefs.getInt(KEY_RETURN_KEYBOARD_SUBTYPE, 0) else null

    // Written together, so a subtype never outlives the keyboard it belongs to.
    fun setReturnKeyboard(id: String?, subtype: Int?) {
        prefs.edit().apply {
            if (id == null) remove(KEY_RETURN_KEYBOARD_ID) else putString(KEY_RETURN_KEYBOARD_ID, id)
            if (id == null || subtype == null) remove(KEY_RETURN_KEYBOARD_SUBTYPE) else putInt(KEY_RETURN_KEYBOARD_SUBTYPE, subtype)
        }.apply()
    }

    // Show in the keyboard › Clipboard: the tab and the recording are one switch. On by default: the clips
    // stay in memory for an hour at most, are only read while Pastille's keyboard or app is in front, and
    // skip sensitive and password-field copies. An upgrade keeps the old separate Clipboard switch's value.
    var clipboardEnabled: Boolean
        get() = prefs.getBoolean(KEY_CLIPBOARD_ENABLED, clipboardDefault(legacyClipboard()))
        set(value) = prefs.edit().putBoolean(KEY_CLIPBOARD_ENABLED, value).apply()

    private fun legacyClipboard(): Boolean? =
        if (prefs.contains(KEY_RECENT_CLIPBOARD)) prefs.getBoolean(KEY_RECENT_CLIPBOARD, true) else null

    var clipboardClearDelay: ClipboardClearDelay
        get() = ClipboardClearDelay.fromKey(prefs.getString(KEY_CLIPBOARD_CLEAR_DELAY, null))
        set(value) = prefs.edit().putString(KEY_CLIPBOARD_CLEAR_DELAY, value.key).apply()

    // Automatic backup (Settings › Backup): the file the user chose through the system picker, its
    // name as the picker gave it, when it was last written, what was in it then, and what went wrong
    // on the last attempt (null when it worked).
    var backupUri: String?
        get() = prefs.getString(KEY_BACKUP_URI, null)
        set(value) = prefs.edit().apply { if (value == null) remove(KEY_BACKUP_URI) else putString(KEY_BACKUP_URI, value) }.apply()

    var backupName: String?
        get() = prefs.getString(KEY_BACKUP_NAME, null)
        set(value) = prefs.edit().apply { if (value == null) remove(KEY_BACKUP_NAME) else putString(KEY_BACKUP_NAME, value) }.apply()

    var backupLastAt: Long?
        get() = if (prefs.contains(KEY_BACKUP_LAST_AT)) prefs.getLong(KEY_BACKUP_LAST_AT, 0) else null
        set(value) = prefs.edit().apply { if (value == null) remove(KEY_BACKUP_LAST_AT) else putLong(KEY_BACKUP_LAST_AT, value) }.apply()

    var backupFingerprint: String?
        get() = prefs.getString(KEY_BACKUP_FINGERPRINT, null)
        set(value) = prefs.edit().apply { if (value == null) remove(KEY_BACKUP_FINGERPRINT) else putString(KEY_BACKUP_FINGERPRINT, value) }.apply()

    var backupProblem: BackupProblem?
        get() = BackupProblem.fromKey(prefs.getString(KEY_BACKUP_PROBLEM, null))
        set(value) = prefs.edit().apply { if (value == null) remove(KEY_BACKUP_PROBLEM) else putString(KEY_BACKUP_PROBLEM, value.key) }.apply()

    // Sync: what the shared file looked like when we last read or wrote it, so an unchanged file
    // isn't read again. Size and modified time come from the provider; the hash is ours.
    var syncRemoteSize: Long?
        get() = if (prefs.contains(KEY_SYNC_SIZE)) prefs.getLong(KEY_SYNC_SIZE, 0) else null
        set(value) = prefs.edit().apply { if (value == null) remove(KEY_SYNC_SIZE) else putLong(KEY_SYNC_SIZE, value) }.apply()

    var syncRemoteModified: Long?
        get() = if (prefs.contains(KEY_SYNC_MODIFIED)) prefs.getLong(KEY_SYNC_MODIFIED, 0) else null
        set(value) = prefs.edit().apply { if (value == null) remove(KEY_SYNC_MODIFIED) else putLong(KEY_SYNC_MODIFIED, value) }.apply()

    var syncRemoteHash: String?
        get() = prefs.getString(KEY_SYNC_HASH, null)
        set(value) = prefs.edit().apply { if (value == null) remove(KEY_SYNC_HASH) else putString(KEY_SYNC_HASH, value) }.apply()

    // A write to the shared file that started and was never confirmed by reading it back: the hash
    // of what it was meant to hold. While it is set, a file that isn't a whole archive any more is
    // this phone's own interrupted write, which the next sync may replace.
    // Committed, not applied: it has to be on disk before the first byte goes out, or a process
    // killed mid-write would forget it.
    var syncPendingWrite: String?
        get() = prefs.getString(KEY_SYNC_PENDING, null)
        @android.annotation.SuppressLint("ApplySharedPref")
        set(value) {
            prefs.edit().apply { if (value == null) remove(KEY_SYNC_PENDING) else putString(KEY_SYNC_PENDING, value) }.commit()
        }

    // The hash of what the file held just before this phone last replaced it. A cloud provider's
    // cache can serve those old bytes again for a while; reading them back as news would undo the
    // write, so they are skipped.
    var syncReplacedHash: String?
        get() = prefs.getString(KEY_SYNC_REPLACED, null)
        set(value) = prefs.edit().apply { if (value == null) remove(KEY_SYNC_REPLACED) else putString(KEY_SYNC_REPLACED, value) }.apply()

    fun clearBackup() {
        prefs.edit()
            .remove(KEY_BACKUP_URI)
            .remove(KEY_BACKUP_NAME)
            .remove(KEY_BACKUP_LAST_AT)
            .remove(KEY_BACKUP_FINGERPRINT)
            .remove(KEY_BACKUP_PROBLEM)
            .remove(KEY_SYNC_SIZE)
            .remove(KEY_SYNC_MODIFIED)
            .remove(KEY_SYNC_HASH)
            .remove(KEY_SYNC_PENDING)
            .remove(KEY_SYNC_REPLACED)
            .apply()
    }

    companion object {
        private const val KEY_BACKUP_URI = "backup_uri"
        private const val KEY_BACKUP_NAME = "backup_name"
        private const val KEY_BACKUP_LAST_AT = "backup_last_at"
        private const val KEY_BACKUP_FINGERPRINT = "backup_fingerprint"
        private const val KEY_BACKUP_PROBLEM = "backup_problem"
        private const val KEY_SYNC_SIZE = "sync_remote_size"
        private const val KEY_SYNC_MODIFIED = "sync_remote_modified"
        private const val KEY_SYNC_HASH = "sync_remote_hash"
        private const val KEY_SYNC_PENDING = "sync_pending_write"
        private const val KEY_SYNC_REPLACED = "sync_replaced_hash"
        private const val PREFS_NAME = "pastille_settings"
        private const val KEY_CLIPBOARD_CLEAR_DELAY = "clipboard_clear_delay"
        private const val KEY_RECENT_CLIPBOARD = "recent_clipboard"
        private const val KEY_CLIPBOARD_ENABLED = "clipboard_enabled"
        private const val KEY_RETURN_KEYBOARD_ID = "return_keyboard_id"
        private const val KEY_RETURN_KEYBOARD_SUBTYPE = "return_keyboard_subtype"
        const val PREVIOUS_KEYBOARD = "previous"
        private const val KEY_RETURN_TO_PREVIOUS_KEYBOARD = "return_to_previous_keyboard"
        private const val KEY_RETURN_AFTER_SNIPPET = "return_after_snippet"
        private const val KEY_RETURN_AFTER_IMAGE = "return_after_image"
        private const val KEY_APP_CATEGORY_ID = "app_category_id"
        private const val KEY_APP_STICKERS_MODE = "app_stickers_mode"
        private const val KEY_PHOTO_PROMPT_ANSWERED = "photo_prompt_answered"
        private const val KEY_KEYBOARD_CATEGORY_ID = "keyboard_category_id"
        private const val KEY_KEYBOARD_STYLE = "keyboard_style"
        private const val KEY_KEYBOARD_STYLE_CHOSEN = "keyboard_style_chosen"
        private const val KEY_PANEL_HEIGHT_PORTRAIT = "panel_height_portrait"
        private const val KEY_PANEL_HEIGHT_LANDSCAPE = "panel_height_landscape"
        private const val KEY_KEYBOARD_MODE = "keyboard_mode"
        private const val KEY_SNIPPETS_ENABLED = "snippets_enabled"
        private const val KEY_IMAGES_ENABLED = "images_enabled"
        private const val KEY_STICKERS_ENABLED = "stickers_enabled"
        private const val KEY_SAVE_AS_STICKERS = "save_images_as_stickers"
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

// The Clipboard choice in "Show in the keyboard" starts from the old Clipboard switch; fresh installs have it on.
fun clipboardDefault(legacy: Boolean?): Boolean = legacy ?: true

/** Where the switch-back button goes: an enabled IME and, optionally, one of its languages. */
data class ReturnTarget(val id: String, val subtype: Int? = null)

/**
 * Resolves the stored choice against the enabled keyboards (Pastille excluded); null means
 * Android's previous keyboard. A keyboard since disabled, or no choice at all, goes to the first
 * typing keyboard, as "Main keyboard" always did. A language that's gone leaves the subtype out,
 * so the keyboard opens in whichever language it used last.
 */
fun returnKeyboardTarget(chosen: String?, chosenSubtype: Int?, keyboards: List<KeyboardEntry>): ReturnTarget? {
    if (chosen == PastilleSettings.PREVIOUS_KEYBOARD) return null
    val picked = keyboards.firstOrNull { it.id == chosen }
    if (picked != null) {
        val subtype = chosenSubtype?.takeIf { hash -> picked.subtypes.any { it.hash == hash } }
        return ReturnTarget(picked.id, subtype)
    }
    return keyboards.firstOrNull { it.typing }?.let { ReturnTarget(it.id) }
}

/** An enabled or installed input method other than Pastille, as Settings and the keyboard see it. */
data class KeyboardEntry(
    val id: String,
    val label: String,
    // False for voice input: listed and choosable, but never the fallback.
    val typing: Boolean = true,
    // Its enabled, non-auxiliary languages, as the system's picker lists them.
    val subtypes: List<SubtypeEntry> = emptyList(),
)

data class SubtypeEntry(val hash: Int, val name: String, val tag: String?)

/**
 * The short tag the system picker shows beside a language: the region when there is one
 * ("fr-FR" → "FR", "en_GB" → "GB"), otherwise the language ("de" → "DE"); null when unknown.
 */
fun localeTag(languageTag: String?): String? {
    val parts = languageTag.orEmpty().split('-', '_').filter { it.isNotBlank() }
    val language = parts.firstOrNull() ?: return null
    val region = parts.drop(1).firstOrNull { it.length == 2 && it.all(Char::isLetter) }
    return (region ?: language).uppercase(java.util.Locale.ROOT)
}

// Voice input (Gboard's speech IME, for one) registers as an input method too. It is listed like the
// system picker lists it, but the default never lands on it: a keyboard may declare subtypes without a mode.
fun isTypingKeyboard(subtypeModes: List<String>): Boolean =
    subtypeModes.isEmpty() || subtypeModes.any { it.isBlank() || it == "keyboard" }

/** Why the last automatic backup didn't happen. */
enum class BackupProblem(val key: String) {
    // The file's permission is gone: it was deleted, moved, or the app holding it was signed out.
    Access("access"),
    // The file was there but the write or its check failed.
    Write("write"),
    // The file couldn't be read (Drive offline, still downloading, or not a backup yet). Nothing is
    // written until it reads, so this phone never overwrites changes it hasn't seen.
    Read("read"),
    // The storage app didn't keep Pastille's permission to the file (takePersistableUriPermission
    // threw, or returned without persisting it): syncing works only until the phone restarts.
    NotKept("not-kept"),
    // The storage app granted reading but not writing this file.
    ReadOnly("read-only"),
    // The storage app refuses every write mode that empties the file first, and the new backup is
    // shorter than the old one: writing it would leave the old one's end behind, so nothing was written.
    CantOverwrite("cant-overwrite"),
    // The file read back differs from what was written, even after waiting for the storage app.
    Mismatch("mismatch"),
    ;

    companion object {
        fun fromKey(key: String?): BackupProblem? = entries.firstOrNull { it.key == key }
    }
}
