package app.pastille.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardPrefsTest {

    @Test
    fun `portrait height is the preset, capped at half the window`() {
        assertEquals(368, panelHeightDp(PanelHeight.Roomy, landscape = false, windowHeightDp = 891))
        assertEquals(300, panelHeightDp(PanelHeight.Tall, landscape = false, windowHeightDp = 600))
    }

    @Test
    fun `landscape height is a fraction of the window`() {
        assertEquals(205, panelHeightDp(PanelHeight.Roomy, landscape = true, windowHeightDp = 411))
    }

    @Test
    fun `unknown keys fall back to defaults`() {
        assertEquals(PanelHeight.Roomy, PanelHeight.fromKey("huge"))
        assertEquals(KeyboardStyle.Auto, KeyboardStyle.fromKey(null))
        assertEquals(KeyboardMode.Snippets, KeyboardMode.fromKey("nope"))
    }

    @Test
    fun `renamed presets keep their stored keys`() {
        assertEquals(PanelHeight.Standard, PanelHeight.fromKey("gboard"))
        assertEquals(PanelHeight.Roomy, PanelHeight.fromKey("comfortable"))
    }

    @Test
    fun `grid columns follow the width`() {
        assertEquals(2, tileColumns(411))
        assertEquals(2, tileColumns(300))
        assertEquals(4, tileColumns(891))
        assertEquals(4, tileColumns(1400))
    }

    @Test
    fun `sticker columns follow the width, between 4 and 10`() {
        assertEquals(4, stickerColumns(360))
        assertEquals(5, stickerColumns(411))
        assertEquals(10, stickerColumns(890))
        assertEquals(10, stickerColumns(2000))
        assertEquals(4, stickerColumns(300))
    }

    @Test
    fun `each mode switched off on its own gives way to the first enabled one`() {
        val all = KeyboardMode.entries.toSet()
        assertEquals(KeyboardMode.Stickers, effectiveMode(KeyboardMode.Snippets, all - KeyboardMode.Snippets))
        assertEquals(KeyboardMode.Snippets, effectiveMode(KeyboardMode.Stickers, all - KeyboardMode.Stickers))
        assertEquals(KeyboardMode.Snippets, effectiveMode(KeyboardMode.Images, all - KeyboardMode.Images))
        assertEquals(KeyboardMode.Stickers, effectiveMode(KeyboardMode.Stickers, all))
    }

    @Test
    fun `two modes off leave only the third`() {
        assertEquals(KeyboardMode.Images, effectiveMode(KeyboardMode.Snippets, setOf(KeyboardMode.Images)))
        assertEquals(KeyboardMode.Stickers, effectiveMode(KeyboardMode.Images, setOf(KeyboardMode.Stickers)))
        assertEquals(KeyboardMode.Snippets, effectiveMode(KeyboardMode.Stickers, setOf(KeyboardMode.Snippets)))
    }

    @Test
    fun `a saved Stickers mode that is switched off falls to the first enabled one in bar order`() {
        assertEquals(KeyboardMode.Snippets, effectiveMode(KeyboardMode.Stickers, setOf(KeyboardMode.Snippets, KeyboardMode.Images)))
        assertEquals(KeyboardMode.Images, effectiveMode(KeyboardMode.Stickers, setOf(KeyboardMode.Images)))
    }

    @Test
    fun `the last enabled mode cannot be switched off`() {
        assertEquals(false, canSwitchOff(KeyboardMode.Stickers, setOf(KeyboardMode.Stickers)))
        assertEquals(true, canSwitchOff(KeyboardMode.Stickers, setOf(KeyboardMode.Stickers, KeyboardMode.Images)))
        assertEquals(true, canSwitchOff(KeyboardMode.Images, setOf(KeyboardMode.Stickers)))
    }

    @Test
    fun `unknown keys and the stickers key resolve`() {
        assertEquals(KeyboardMode.Stickers, KeyboardMode.fromKey("stickers"))
    }
}
