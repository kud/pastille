package app.pastille.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReturnDefaultTest {

    @Test
    fun `fresh installs return after a snippet but stay after an image`() {
        assertTrue(returnDefault(legacy = null, image = false))
        assertFalse(returnDefault(legacy = null, image = true))
    }

    @Test
    fun `an upgrade keeps the old single switch for both`() {
        assertTrue(returnDefault(legacy = true, image = true))
        assertFalse(returnDefault(legacy = false, image = false))
    }
}

class EffectiveModeTest {

    @org.junit.Test
    fun `a switched-off mode is never shown`() {
        org.junit.Assert.assertEquals(KeyboardMode.Images, effectiveMode(KeyboardMode.Snippets, setOf(KeyboardMode.Images)))
        org.junit.Assert.assertEquals(KeyboardMode.Snippets, effectiveMode(KeyboardMode.Images, setOf(KeyboardMode.Snippets)))
        org.junit.Assert.assertEquals(KeyboardMode.Images, effectiveMode(KeyboardMode.Images, KeyboardMode.entries.toSet()))
    }
}

class ReturnKeyboardTargetTest {

    private val gboard = KeyboardEntry("gboard/.Ime", "Gboard", subtypes = listOf(SubtypeEntry(1, "Français", "FR"), SubtypeEntry(2, "English", "GB")))
    private val voice = KeyboardEntry("voice/.Ime", "Voice", typing = false)
    private val passwords = KeyboardEntry("passwords/.Ime", "Passwords")

    @org.junit.Test
    fun `defaults to the first typing keyboard, honours a choice, and can fall back to previous`() {
        val others = listOf(voice, gboard, passwords)
        org.junit.Assert.assertEquals(ReturnTarget("gboard/.Ime"), returnKeyboardTarget(null, null, others))
        org.junit.Assert.assertEquals(ReturnTarget("passwords/.Ime"), returnKeyboardTarget("passwords/.Ime", null, others))
        org.junit.Assert.assertEquals(ReturnTarget("voice/.Ime"), returnKeyboardTarget("voice/.Ime", null, others))
        org.junit.Assert.assertEquals(ReturnTarget("gboard/.Ime"), returnKeyboardTarget("uninstalled/.Ime", 2, others))
        org.junit.Assert.assertNull(returnKeyboardTarget(PastilleSettings.PREVIOUS_KEYBOARD, null, others))
        org.junit.Assert.assertNull(returnKeyboardTarget(null, null, listOf(voice)))
    }

    @org.junit.Test
    fun `a language goes with its keyboard, and one that's gone falls back to the keyboard's last`() {
        val others = listOf(gboard, passwords)
        org.junit.Assert.assertEquals(ReturnTarget("gboard/.Ime", 2), returnKeyboardTarget("gboard/.Ime", 2, others))
        org.junit.Assert.assertEquals(ReturnTarget("gboard/.Ime"), returnKeyboardTarget("gboard/.Ime", 99, others))
    }
}

class TypingKeyboardTest {

    @org.junit.Test
    fun `voice input is not a keyboard to switch back to`() {
        org.junit.Assert.assertFalse(isTypingKeyboard(listOf("voice")))
        org.junit.Assert.assertTrue(isTypingKeyboard(listOf("keyboard", "voice")))
        org.junit.Assert.assertTrue(isTypingKeyboard(emptyList()))
    }

    @org.junit.Test
    fun `a keyboard whose subtypes set no mode still counts`() {
        org.junit.Assert.assertTrue(isTypingKeyboard(listOf("", "")))
        org.junit.Assert.assertTrue(isTypingKeyboard(listOf("", "voice")))
    }
}
