package app.pastille.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SnippetKindTest {

    @Test
    fun `a bare url is a link`() {
        assertEquals(SnippetKind.Link, snippetKind(isImage = false, text = " https://example.com/docs \n"))
        assertEquals("https://example.com/docs", linkOnly("https://example.com/docs"))
    }

    @Test
    fun `text around a url stays text`() {
        assertEquals(SnippetKind.Text, snippetKind(isImage = false, text = "See https://example.com"))
        assertNull(linkOnly("See https://example.com"))
    }

    @Test
    fun `images win over their text`() {
        assertEquals(SnippetKind.Image, snippetKind(isImage = true, text = "https://example.com"))
    }

    @Test
    fun `auto-titled link shows its domain`() {
        val url = "https://www.example.com/blog/a-very-long-article-name-that-goes-on"
        assertEquals("example.com/blog", rowTitle(title = "", text = url))
        assertEquals("example.com/blog", rowTitle(title = "https://www.example.com/blog/a-very-long…", text = url))
        assertEquals("example.com/blog", rowTitle(title = "https://www.example.com/blog/a-very-long… 2", text = url))
    }

    @Test
    fun `typed titles are kept`() {
        assertEquals("Docs", rowTitle(title = "Docs", text = "https://example.com/docs"))
        assertEquals("Hello", rowTitle(title = "Hello", text = "Hello there"))
    }

    @Test
    fun `blank title falls back to the first line`() {
        assertEquals("First line", rowTitle(title = "", text = "\nFirst line\nSecond"))
    }
}
