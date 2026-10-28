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

    private val fr = SubtypeEntry(hash = 101, name = "Français (France)", tag = "FR")
    private val gb = SubtypeEntry(hash = 202, name = "English (UK)", tag = "GB")
    private val gboard = KeyboardEntry("com.google/.Latin", "Gboard", subtypes = listOf(fr, gb))
    private val voice = KeyboardEntry("com.google/.Voice", "Google Voice Typing", typing = false)
    private val swift = KeyboardEntry("com.swift/.Ime", "SwiftKey", subtypes = listOf(fr))
    private val samsung = KeyboardEntry("com.samsung/.Ime", "Samsung Keyboard")

    @Test
    fun `previous keyboard first, typing keyboards alphabetically, voice last, languages under their keyboard`() {
        val choices = returnKeyboardChoices(null, null, listOf(voice, gboard, swift), listOf(voice, gboard, swift))
        assertEquals(
            listOf("Previous keyboard", "Gboard", "Français (France)", "English (UK)", "SwiftKey", "Google Voice Typing"),
            choices.map { it.label },
        )
        assertEquals(listOf(null, null, 101, 202, null, null), choices.map { it.subtype })
        assertEquals("FR · Gboard", choices[2].note)
        assertEquals("Whichever language it had last", choices[1].note)
        assertEquals("FR · Français (France)", choices[4].note)
        assertTrue(choices.none { it.note == "Main keyboard" })
    }

    @Test
    fun `no choice yet selects the first typing keyboard, never voice input`() {
        val choices = returnKeyboardChoices(null, null, listOf(voice, gboard), listOf(voice, gboard))
        assertEquals("Gboard", choices.single { it.selected }.label)
        assertEquals(gboard.id, choices.single { it.selected }.value)
    }

    @Test
    fun `a chosen language is selected, and one since removed selects its keyboard`() {
        assertEquals("English (UK)", returnKeyboardChoices(gboard.id, 202, listOf(gboard), listOf(gboard)).single { it.selected }.label)
        val gone = returnKeyboardChoices(gboard.id, 999, listOf(gboard), listOf(gboard))
        assertEquals("Gboard", gone.single { it.selected }.label)
    }

    @Test
    fun `voice input can be chosen`() {
        val choices = returnKeyboardChoices(voice.id, null, listOf(voice, gboard), listOf(voice, gboard))
        assertEquals("Google Voice Typing", choices.single { it.selected }.label)
    }

    @Test
    fun `previous keyboard is a real choice`() {
        val choices = returnKeyboardChoices(PastilleSettings.PREVIOUS_KEYBOARD, null, listOf(swift), listOf(swift))
        assertEquals(listOf(true, false), choices.map { it.selected })
    }

    @Test
    fun `installed but disabled keyboards are listed as unavailable`() {
        val choices = returnKeyboardChoices(null, null, listOf(swift), listOf(samsung, swift))
        val samsungRow = choices.last()
        assertEquals("Samsung Keyboard", samsungRow.label)
        assertFalse(samsungRow.available)
        assertFalse(samsungRow.selected)
        assertEquals("Not enabled", samsungRow.note)
        assertEquals(1, choices.count { it.label == "SwiftKey" })
    }

    @Test
    fun `a chosen keyboard since disabled shows the first one, as the keyboard does`() {
        val choices = returnKeyboardChoices(swift.id, null, listOf(gboard), listOf(gboard, swift))
        assertTrue(choices.first { it.label == "Gboard" }.selected)
        assertFalse(choices.first { it.label == "SwiftKey" }.selected)
    }

    @Test
    fun `with no other keyboard, previous is what happens`() {
        val choices = returnKeyboardChoices(null, null, emptyList(), emptyList())
        assertEquals(listOf("Previous keyboard"), choices.map { it.label })
        assertTrue(choices.single().selected)
    }

    @Test
    fun `exactly one row is selected`() {
        listOf(null, swift.id, gboard.id, voice.id, PastilleSettings.PREVIOUS_KEYBOARD, "gone").forEach { chosen ->
            listOf(null, 101, 202, 999).forEach { subtype ->
                val choices = returnKeyboardChoices(chosen, subtype, listOf(voice, gboard, swift), listOf(gboard, swift, samsung))
                assertEquals("chosen=$chosen subtype=$subtype", 1, choices.count { it.selected })
            }
        }
    }

    @Test
    fun `the short tag is the region, else the language`() {
        assertEquals("FR", localeTag("fr-FR"))
        assertEquals("GB", localeTag("en_GB"))
        assertEquals("DE", localeTag("de"))
        assertEquals("ES", localeTag("es-419"))
        assertEquals(null, localeTag(""))
        assertEquals(null, localeTag(null))
    }
}
