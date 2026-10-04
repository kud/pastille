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
        org.junit.Assert.assertEquals(KeyboardMode.Images, effectiveMode(KeyboardMode.Snippets, snippetsEnabled = false, imagesEnabled = true))
        org.junit.Assert.assertEquals(KeyboardMode.Snippets, effectiveMode(KeyboardMode.Images, snippetsEnabled = true, imagesEnabled = false))
        org.junit.Assert.assertEquals(KeyboardMode.Images, effectiveMode(KeyboardMode.Images, snippetsEnabled = true, imagesEnabled = true))
    }
}

class ReturnKeyboardTargetTest {

    @org.junit.Test
    fun `defaults to the main keyboard, honours a choice, and can fall back to previous`() {
        val others = listOf("gboard/.Ime", "passwords/.Ime")
        org.junit.Assert.assertEquals("gboard/.Ime", returnKeyboardTarget(null, others))
        org.junit.Assert.assertEquals("passwords/.Ime", returnKeyboardTarget("passwords/.Ime", others))
        org.junit.Assert.assertEquals("gboard/.Ime", returnKeyboardTarget("uninstalled/.Ime", others))
        org.junit.Assert.assertNull(returnKeyboardTarget(PastilleSettings.PREVIOUS_KEYBOARD, others))
    }
}
