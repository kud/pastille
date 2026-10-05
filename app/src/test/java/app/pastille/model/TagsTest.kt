package app.pastille.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagsTest {

    @Test
    fun `tags are lowercased without spaces or a hash`() {
        assertEquals("email", normaliseTag("  #Email "))
        assertEquals("twowords", normaliseTag("two words"))
        assertEquals("", normaliseTag(" # "))
    }

    @Test
    fun `tags read in sentence case, never all caps`() {
        assertEquals("Email", displayTag("email"))
        assertEquals("Otp", displayTag("otp"))
    }

    @Test
    fun `a comma or a space ends a tag`() {
        assertEquals(listOf("work") to "", splitTagInput("work,"))
        assertEquals(listOf("work", "otp") to "ema", splitTagInput("Work otp,ema"))
        assertEquals(emptyList<String>() to "draft", splitTagInput("draft"))
    }

    @Test
    fun `adding keeps order and drops duplicates and blanks`() {
        assertEquals(listOf("work", "otp"), addTags(listOf("work"), listOf("OTP", "work", " ")))
    }

    @Test
    fun `filtering by several tags keeps snippets that carry all of them`() {
        val a = SnippetRecord(id = 1, text = "a", tags = listOf("work", "email"))
        val b = SnippetRecord(id = 2, text = "b", tags = listOf("work"))
        val c = SnippetRecord(id = 3, text = "c")

        assertEquals(listOf(a, b, c), filterByTags(listOf(a, b, c), emptySet()))
        assertEquals(listOf(a, b), filterByTags(listOf(a, b, c), setOf("work")))
        assertEquals(listOf(a), filterByTags(listOf(a, b, c), setOf("work", "email")))
    }

    @Test
    fun `search matches tag names, with or without the hash`() {
        val snippet = SnippetRecord(title = "Sign-off", text = "Best wishes", tags = listOf("email"))
        assertTrue(matchesQuery(snippet, "email"))
        assertTrue(matchesQuery(snippet, "#ema"))
        assertTrue(matchesQuery(snippet, "wishes"))
        assertFalse(matchesQuery(snippet, "invoice"))
    }

    @Test
    fun `suggestions start with what is typed and skip tags already there`() {
        val all = listOf("email", "emoji", "work")
        assertEquals(listOf("emoji"), tagSuggestions(all, "em", current = listOf("email")))
        assertEquals(listOf("email", "emoji", "work"), tagSuggestions(all, "", current = emptyList()))
    }
}
