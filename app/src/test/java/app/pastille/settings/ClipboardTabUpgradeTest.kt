package app.pastille.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// The separate Clipboard switch became the Clipboard choice in "Show in the keyboard": whatever it was
// set to before the upgrade, the keyboard shows (or hides) the clipboard tab exactly as it did.
@RunWith(RobolectricTestRunner::class)
class ClipboardTabUpgradeTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences("pastille_settings", Context.MODE_PRIVATE)

    @Before fun clear() {
        prefs.edit().clear().commit()
    }

    @Test fun `a fresh install shows the clipboard`() {
        assertTrue(KeyboardMode.Clipboard in PastilleSettings.forContext(context).enabledModes())
    }

    @Test fun `the old switch left on keeps the clipboard tab`() {
        prefs.edit().putBoolean("recent_clipboard", true).commit()
        val settings = PastilleSettings.forContext(context)
        assertTrue(settings.clipboardEnabled)
        assertEquals(KeyboardMode.entries.toSet(), settings.enabledModes())
    }

    @Test fun `the old switch turned off keeps the clipboard hidden and nothing recorded`() {
        prefs.edit().putBoolean("recent_clipboard", false).putBoolean("images_enabled", false).commit()
        val settings = PastilleSettings.forContext(context)
        assertFalse(settings.clipboardEnabled)
        assertEquals(setOf(KeyboardMode.Snippets, KeyboardMode.Stickers), settings.enabledModes())
    }

    @Test fun `the new choice wins over the old switch once set`() {
        prefs.edit().putBoolean("recent_clipboard", false).commit()
        val settings = PastilleSettings.forContext(context)
        settings.setModeEnabled(KeyboardMode.Clipboard, true)
        assertTrue(settings.clipboardEnabled)
        settings.setModeEnabled(KeyboardMode.Clipboard, false)
        assertFalse(settings.clipboardEnabled)
    }

    @Test fun `the other choices are untouched by the upgrade`() {
        prefs.edit().putBoolean("recent_clipboard", true).putBoolean("stickers_enabled", false).commit()
        val settings = PastilleSettings.forContext(context)
        assertEquals(setOf(KeyboardMode.Clipboard, KeyboardMode.Snippets, KeyboardMode.Images), settings.enabledModes())
    }

    @Test fun `clipboard default follows the legacy value`() {
        assertTrue(clipboardDefault(null))
        assertTrue(clipboardDefault(true))
        assertFalse(clipboardDefault(false))
    }
}
