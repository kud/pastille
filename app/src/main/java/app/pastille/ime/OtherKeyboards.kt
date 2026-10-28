package app.pastille.ime

import android.content.Context
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import app.pastille.settings.KeyboardEntry
import app.pastille.settings.SubtypeEntry
import app.pastille.settings.isTypingKeyboard
import app.pastille.settings.localeTag

/**
 * Every enabled input method but Pastille, in the system's order, with the languages the system's
 * picker shows for each (enabled, implicit ones included, auxiliary ones left out). Voice input is
 * listed, marked not [KeyboardEntry.typing]. [labelled] false skips the labels for the keyboard's
 * own switch, which only needs ids and subtypes.
 */
fun otherKeyboards(context: Context, labelled: Boolean = true): List<KeyboardEntry> {
    val imm = context.getSystemService(InputMethodManager::class.java) ?: return emptyList()
    return imm.enabledInputMethodList
        .filter { it.packageName != context.packageName }
        .map { info ->
            val subtypes = runCatching { imm.getEnabledInputMethodSubtypeList(info, true) }.getOrDefault(emptyList())
                .filterNot { it.isAuxiliary }
                .distinctBy { it.hashCode() }
                .map { subtype ->
                    val tag = localeTag(subtype.languageOrLocale())
                    SubtypeEntry(
                        hash = subtype.hashCode(),
                        name = if (labelled) subtype.label(context, info) ?: tag.orEmpty() else "",
                        tag = tag,
                    )
                }
            KeyboardEntry(
                id = info.id,
                label = if (labelled) info.loadLabel(context.packageManager).toString() else info.id,
                typing = info.isTyping(),
                subtypes = subtypes,
            )
        }
}

/** Every input method installed but Pastille, enabled or not, labelled, without languages. */
fun installedKeyboards(context: Context): List<KeyboardEntry> =
    context.getSystemService(InputMethodManager::class.java)
        ?.inputMethodList
        .orEmpty()
        .filter { it.packageName != context.packageName }
        .map { KeyboardEntry(it.id, it.loadLabel(context.packageManager).toString(), it.isTyping()) }

/** The enabled subtype of [id] whose hash code is [hash], to hand to `switchInputMethod`. */
fun enabledSubtype(context: Context, id: String, hash: Int): InputMethodSubtype? {
    val imm = context.getSystemService(InputMethodManager::class.java) ?: return null
    val info = imm.enabledInputMethodList.firstOrNull { it.id == id } ?: return null
    return runCatching { imm.getEnabledInputMethodSubtypeList(info, true) }.getOrDefault(emptyList())
        .firstOrNull { it.hashCode() == hash }
}

private fun InputMethodInfo.isTyping(): Boolean =
    isTypingKeyboard((0 until subtypeCount).map { getSubtypeAt(it).mode })

@Suppress("DEPRECATION")
private fun InputMethodSubtype.languageOrLocale(): String? =
    languageTag.takeIf { it.isNotBlank() } ?: locale.takeIf { it.isNotBlank() }

private fun InputMethodSubtype.label(context: Context, info: InputMethodInfo): String? =
    runCatching {
        getDisplayName(context, info.packageName, info.serviceInfo.applicationInfo).toString()
    }.getOrNull()?.takeIf { it.isNotBlank() }
