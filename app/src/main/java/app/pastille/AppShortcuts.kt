package app.pastille

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * The shortcuts a long press on the app icon offers: for now one, "New snippet", which opens the
 * editor on an empty snippet. Dynamic rather than res/xml/shortcuts.xml because a static shortcut
 * names its target package, and the debug build's ".debug" suffix would leave it pointing at the
 * release app. Published at every start; setting the same list again is cheap and idempotent.
 */
object AppShortcuts {

    const val NEW_SNIPPET_ID = "new_snippet"

    fun newSnippetIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_NEW_SNIPPET)
            .addFlags(MainActivity.LAUNCH_FLAGS)

    fun newSnippet(context: Context): ShortcutInfoCompat {
        val label = context.getString(R.string.shortcut_new_snippet)
        return ShortcutInfoCompat.Builder(context, NEW_SNIPPET_ID)
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_new_snippet))
            .setIntent(newSnippetIntent(context))
            .build()
    }

    /** Never fails the app start: a launcher that refuses shortcuts just shows none. */
    fun publish(context: Context) {
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, listOf(newSnippet(context))) }
    }
}
