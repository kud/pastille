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
        assertEquals(2, snippetColumns(411))
        assertEquals(4, snippetColumns(900))
        assertEquals(3, imageColumns(411))
        assertEquals(2, imageColumns(300))
        assertEquals(6, imageColumns(900))
    }
}
