package app.pastille.picker

import app.pastille.model.SnippetRecord
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PickerSearchTest {

    private val snippet = SnippetRecord(title = "Home address", text = "12 Example Street")

    @Test
    fun `a blank query matches everything`() {
        assertTrue(snippetMatches(snippet, ""))
        assertTrue(snippetMatches(snippet, "   "))
    }

    @Test
    fun `matches the title or the text, ignoring case`() {
        assertTrue(snippetMatches(snippet, "ADDRESS"))
        assertTrue(snippetMatches(snippet, "example st"))
        assertTrue(snippetMatches(snippet, " home "))
    }

    @Test
    fun `anything else does not match`() {
        assertFalse(snippetMatches(snippet, "invoice"))
    }

    @Test
    fun `a sticker's title matches under Stickers (StickerFilterTest keeps it out of Snippets)`() {
        val sticker = SnippetRecord(title = "Thumbs up", text = "", imageFile = "a.webp", sticker = true)

        assertTrue(stickerMatches(sticker, "thumbs"))
        assertFalse(stickerMatches(sticker, "invoice"))
        assertTrue(stickerMatches(sticker, ""))
    }
}
