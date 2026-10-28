package app.pastille.ime

import android.content.Context
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import app.pastille.settings.isTypingKeyboard

/** The typing keyboards Pastille can switch to: enabled, not Pastille, not voice input. */
fun otherTypingKeyboards(context: Context): List<InputMethodInfo> =
    context.getSystemService(InputMethodManager::class.java)
        ?.enabledInputMethodList
        .orEmpty()
        .typingKeyboardsBesides(context)

/** Every typing keyboard installed, enabled or not, in the system's order. */
fun installedTypingKeyboards(context: Context): List<InputMethodInfo> =
    context.getSystemService(InputMethodManager::class.java)
        ?.inputMethodList
        .orEmpty()
        .typingKeyboardsBesides(context)

private fun List<InputMethodInfo>.typingKeyboardsBesides(context: Context): List<InputMethodInfo> =
    filter { it.packageName != context.packageName }
        .filter { info -> isTypingKeyboard((0 until info.subtypeCount).map { info.getSubtypeAt(it).mode }) }
