package app.pastille.backup

import app.pastille.model.SnippetRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SnippetBackupTest {

    private val samples = listOf(
        SnippetRecord(
            id = 1,
            title = "greeting",
            text = "hello there",
            pinned = true,
            createdAt = 10,
            updatedAt = 20,
            lastUsedAt = 30,
        ),
        SnippetRecord(
            id = 2,
            title = "",
            text = "multi\nline ☃ snippet",
            pinned = false,
            createdAt = 11,
            updatedAt = 21,
            lastUsedAt = 31,
        ),
    )

    @Test
    fun roundTripPreservesEveryField() {
        val decoded = SnippetBackup.decode(SnippetBackup.encode(samples))

        assertEquals(samples, decoded)
    }

    @Test
    fun emptyListRoundTrips() {
        assertTrue(SnippetBackup.decode(SnippetBackup.encode(emptyList())).isEmpty())
    }

    @Test
    fun unknownVersionIsRejected() {
        val raw = """{"version": 2, "snippets": []}"""

        try {
            SnippetBackup.decode(raw)
            fail("expected IllegalArgumentException for unknown version")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("2"))
        }
    }

    @Test
    fun futurePayloadShapeStillRejectedOnVersion() {
        val raw = SnippetBackup.encode(samples).replace("\"version\": 1", "\"version\": 99")

        try {
            SnippetBackup.decode(raw)
            fail("expected IllegalArgumentException for unknown version")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("99"))
        }
    }
}
