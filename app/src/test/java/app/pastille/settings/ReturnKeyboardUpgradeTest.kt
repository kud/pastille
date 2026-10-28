package app.pastille.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Switch back to gained an optional language. What older versions stored (nothing, "previous", or
// an IME id) reads and resolves exactly as before, with no language.
@RunWith(RobolectricTestRunner::class)
class ReturnKeyboardUpgradeTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences("pastille_settings", Context.MODE_PRIVATE)
    private val keyboards = listOf(
        KeyboardEntry("voice/.Ime", "Voice", typing = false),
        KeyboardEntry("gboard/.Ime", "Gboard", subtypes = listOf(SubtypeEntry(7, "Français", "FR"), SubtypeEntry(8, "English", "GB"))),
        KeyboardEntry("keepass/.Magikeyboard", "Magikeyboard"),
    )

    @Before fun clear() {
        prefs.edit().clear().commit()
    }

    private fun resolved(): ReturnTarget? = PastilleSettings.forContext(context).let {
        returnKeyboardTarget(it.returnKeyboardId, it.returnKeyboardSubtype, keyboards)
    }

    @Test fun `nothing stored still means the first typing keyboard`() {
        assertNull(PastilleSettings.forContext(context).returnKeyboardSubtype)
        assertEquals(ReturnTarget("gboard/.Ime"), resolved())
    }

    @Test fun `a stored previous still means previous`() {
        prefs.edit().putString("return_keyboard_id", "previous").commit()
        assertNull(resolved())
    }

    @Test fun `a stored id still means that keyboard, in its last language`() {
        prefs.edit().putString("return_keyboard_id", "keepass/.Magikeyboard").commit()
        assertNull(PastilleSettings.forContext(context).returnKeyboardSubtype)
        assertEquals(ReturnTarget("keepass/.Magikeyboard"), resolved())
    }

    @Test fun `a language is stored with its keyboard and cleared with it`() {
        val settings = PastilleSettings.forContext(context)
        settings.setReturnKeyboard("gboard/.Ime", 8)
        assertEquals(ReturnTarget("gboard/.Ime", 8), resolved())
        settings.setReturnKeyboard("keepass/.Magikeyboard", null)
        assertNull(settings.returnKeyboardSubtype)
        settings.setReturnKeyboard("gboard/.Ime", 7)
        settings.setReturnKeyboard(PastilleSettings.PREVIOUS_KEYBOARD, null)
        assertNull(settings.returnKeyboardSubtype)
        assertNull(resolved())
    }
}
