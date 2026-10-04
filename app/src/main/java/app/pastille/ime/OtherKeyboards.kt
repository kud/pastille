package app.pastille.ime

import android.content.Context
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import app.pastille.settings.isTypingKeyboard

fun otherTypingKeyboards(context: Context): List<InputMethodInfo> =
    context.getSystemService(InputMethodManager::class.java)
        ?.enabledInputMethodList
        ?.filter { it.packageName != context.packageName }
        ?.filter { info -> isTypingKeyboard((0 until info.subtypeCount).map { info.getSubtypeAt(it).mode }) }
        .orEmpty()
