package app.pastille.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupRulesTest {

    @Test
    fun `the setup card asks for photos once`() {
        assertTrue(setupNeedsPhotos(granted = false, partialOnly = false, answered = false))
        assertFalse("declined", setupNeedsPhotos(granted = false, partialOnly = false, answered = true))
        assertFalse("selected photos only", setupNeedsPhotos(granted = false, partialOnly = true, answered = false))
        assertFalse("granted", setupNeedsPhotos(granted = true, partialOnly = false, answered = false))
    }

    @Test
    fun `photo access asks first, then opens system settings`() {
        assertEquals(PhotoAccessAction.Ask, photoAccessAction(granted = false, partialOnly = false, answered = false))
        assertEquals(PhotoAccessAction.Ask, photoAccessAction(granted = false, partialOnly = true, answered = true))
        assertEquals(PhotoAccessAction.OpenSystemSettings, photoAccessAction(granted = false, partialOnly = false, answered = true))
        assertEquals(PhotoAccessAction.OpenSystemSettings, photoAccessAction(granted = true, partialOnly = false, answered = true))
    }

    private val gboard = "com.google/.Latin" to "Gboard"
    private val swift = "com.swift/.Ime" to "SwiftKey"
    private val samsung = "com.samsung/.Ime" to "Samsung Keyboard"

    @Test
    fun `the main keyboard appears once and stores null`() {
        val choices = returnKeyboardChoices(null, listOf(gboard, swift), listOf(gboard, swift))
        assertEquals(listOf("Gboard", "SwiftKey", "Previous keyboard"), choices.map { it.label })
        assertEquals(listOf(null, swift.first, PastilleSettings.PREVIOUS_KEYBOARD), choices.map { it.value })
        assertEquals("Main keyboard", choices[0].note)
        assertEquals(listOf(true, false, false), choices.map { it.selected })
    }

    @Test
    fun `previous keyboard is a real choice`() {
        val choices = returnKeyboardChoices(PastilleSettings.PREVIOUS_KEYBOARD, listOf(gboard), listOf(gboard))
        assertEquals(listOf(false, true), choices.map { it.selected })
    }

    @Test
    fun `installed but disabled keyboards are listed as unavailable`() {
        val choices = returnKeyboardChoices(null, listOf(gboard), listOf(samsung, gboard))
        val samsungRow = choices.last()
        assertEquals("Samsung Keyboard", samsungRow.label)
        assertFalse(samsungRow.available)
        assertFalse(samsungRow.selected)
        assertEquals("Not enabled", samsungRow.note)
        assertEquals(1, choices.count { it.label == "Gboard" })
    }

    @Test
    fun `a chosen keyboard since disabled shows the main one, as the keyboard does`() {
        val choices = returnKeyboardChoices(swift.first, listOf(gboard), listOf(gboard, swift))
        assertTrue(choices.first { it.label == "Gboard" }.selected)
        assertFalse(choices.first { it.label == "SwiftKey" }.selected)
    }

    @Test
    fun `with no other keyboard, previous is what happens`() {
        val choices = returnKeyboardChoices(null, emptyList(), emptyList())
        assertEquals(listOf("Previous keyboard"), choices.map { it.label })
        assertTrue(choices.single().selected)
    }

    @Test
    fun `exactly one row is selected`() {
        listOf(null, swift.first, PastilleSettings.PREVIOUS_KEYBOARD, "gone").forEach { chosen ->
            val choices = returnKeyboardChoices(chosen, listOf(gboard, swift), listOf(gboard, swift, samsung))
            assertEquals("chosen=$chosen", 1, choices.count { it.selected })
        }
    }
}
