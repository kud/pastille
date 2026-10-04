package app.pastille.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SnippetTitlesTest {

    @Test
    fun `free title is kept`() {
        assertEquals("Hello", uniqueTitle("Hello", listOf("Other")))
    }

    @Test
    fun `taken title gets the next free number`() {
        assertEquals("Hello 2", uniqueTitle("Hello", listOf("Hello")))
        assertEquals("Hello 3", uniqueTitle("Hello", listOf("Hello", "Hello 2")))
    }

    @Test
    fun `comparison ignores case`() {
        assertEquals("hello 2", uniqueTitle("hello", listOf("HELLO")))
    }
}
