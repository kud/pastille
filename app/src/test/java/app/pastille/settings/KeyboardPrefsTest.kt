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
    fun `image tiles are 4 by 3 and always leave a full row and a peek`() {
        // 412dp Pixel, two columns of 196dp: Standard and up get the full 4:3.
        assertEquals(147, imageTileHeightDp(columnWidthDp = 196, gridHeightDp = 220))
        // Compact: about 172dp under the chips, so the tile shrinks to keep a peek.
        assertEquals(136, imageTileHeightDp(columnWidthDp = 196, gridHeightDp = 172))
        // Landscape at Compact barely has room; never smaller than a text tile.
        assertEquals(88, imageTileHeightDp(columnWidthDp = 213, gridHeightDp = 60))
    }

    @Test
    fun `only screen-shaped images keep their top`() {
        assertEquals(true, cropKeepsTop(1080, 2400))
        assertEquals(false, cropKeepsTop(3000, 4000))
        assertEquals(false, cropKeepsTop(1080, 1920))
        assertEquals(false, cropKeepsTop(4000, 3000))
        assertEquals(false, cropKeepsTop(0, 0))
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
        val all = KeyboardMode.content.toSet()
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

    @Test
    fun `the clipboard leads the bar and is not a content tab`() {
        assertEquals(KeyboardMode.Clipboard, KeyboardMode.entries.first())
        assertEquals(listOf(KeyboardMode.Snippets, KeyboardMode.Stickers, KeyboardMode.Images), KeyboardMode.content)
        assertEquals(KeyboardMode.Clipboard, KeyboardMode.fromKey("clipboard"))
    }

    @Test
    fun `the keyboard reopens on the clipboard when it was the last tab used`() {
        val all = KeyboardMode.entries.toSet()
        assertEquals(KeyboardMode.Clipboard, effectiveMode(KeyboardMode.Clipboard, all))
    }

    @Test
    fun `a switched-off tab never falls back to the clipboard`() {
        val clipAndImages = setOf(KeyboardMode.Clipboard, KeyboardMode.Images)
        assertEquals(KeyboardMode.Images, effectiveMode(KeyboardMode.Snippets, clipAndImages))
        assertEquals(KeyboardMode.Snippets, effectiveMode(KeyboardMode.Clipboard, KeyboardMode.content.toSet()))
    }

    @Test
    fun `the clipboard can always go and never counts as the last tab`() {
        assertEquals(true, canSwitchOff(KeyboardMode.Clipboard, setOf(KeyboardMode.Clipboard, KeyboardMode.Snippets)))
        assertEquals(false, canSwitchOff(KeyboardMode.Snippets, setOf(KeyboardMode.Clipboard, KeyboardMode.Snippets)))
        assertEquals(true, canSwitchOff(KeyboardMode.Snippets, setOf(KeyboardMode.Clipboard, KeyboardMode.Snippets, KeyboardMode.Images)))
    }
}
