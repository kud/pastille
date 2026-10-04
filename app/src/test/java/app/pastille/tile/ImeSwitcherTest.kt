package app.pastille.tile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImeSwitcherTest {

    @Test
    fun parseStripsSubtypes() {
        val raw = "com.example/.Ime;1;2:app.pastille/.ime.PastilleImeService;0:com.other/.Ime"
        assertEquals(
            listOf("com.example/.Ime", ImeSwitcher.PASTILLE_IME_ID, "com.other/.Ime"),
            ImeSwitcher.parseEnabledIds(raw),
        )
    }

    @Test
    fun parseDropsBlankEntries() {
        assertEquals(
            listOf("com.example/.Ime"),
            ImeSwitcher.parseEnabledIds(":com.example/.Ime::"),
        )
    }

    @Test
    fun parseEmptyAndNullGiveEmptyList() {
        assertEquals(emptyList<String>(), ImeSwitcher.parseEnabledIds(""))
        assertEquals(emptyList<String>(), ImeSwitcher.parseEnabledIds(null))
    }

    @Test
    fun switchToPastilleWhenEnabled() {
        val target = ImeSwitcher.chooseTarget(
            "com.example/.Ime",
            listOf("com.example/.Ime", ImeSwitcher.PASTILLE_IME_ID),
            null,
        )
        assertEquals(ImeSwitcher.PASTILLE_IME_ID, target)
    }

    @Test
    fun nullWhenPastilleNotEnabled() {
        val target = ImeSwitcher.chooseTarget(
            "com.example/.Ime",
            listOf("com.example/.Ime", "com.other/.Ime"),
            null,
        )
        assertNull(target)
    }

    @Test
    fun backToPreviousWhenEnabled() {
        val target = ImeSwitcher.chooseTarget(
            ImeSwitcher.PASTILLE_IME_ID,
            listOf(ImeSwitcher.PASTILLE_IME_ID, "com.example/.Ime"),
            "com.example/.Ime",
        )
        assertEquals("com.example/.Ime", target)
    }

    @Test
    fun previousGoneFallsBackToFirstOther() {
        val target = ImeSwitcher.chooseTarget(
            ImeSwitcher.PASTILLE_IME_ID,
            listOf(ImeSwitcher.PASTILLE_IME_ID, "com.example/.Ime", "com.other/.Ime"),
            "com.gone/.Ime",
        )
        assertEquals("com.example/.Ime", target)
    }

    @Test
    fun previousIsPastilleItselfFallsBackToFirstOther() {
        val target = ImeSwitcher.chooseTarget(
            ImeSwitcher.PASTILLE_IME_ID,
            listOf(ImeSwitcher.PASTILLE_IME_ID, "com.example/.Ime"),
            ImeSwitcher.PASTILLE_IME_ID,
        )
        assertEquals("com.example/.Ime", target)
    }

    @Test
    fun onlyPastilleEnabledGivesNull() {
        val target = ImeSwitcher.chooseTarget(
            ImeSwitcher.PASTILLE_IME_ID,
            listOf(ImeSwitcher.PASTILLE_IME_ID),
            null,
        )
        assertNull(target)
    }
}
