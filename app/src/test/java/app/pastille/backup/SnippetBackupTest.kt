package app.pastille.backup

import app.pastille.model.CategoryRecord
import app.pastille.model.SnippetRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SnippetBackupTest {

    private val categories = listOf(
        CategoryRecord(id = 10, name = "Work", position = 0),
        CategoryRecord(id = 20, name = "Personal", position = 1),
    )

    private val samples = listOf(
        SnippetRecord(
            id = 1,
            title = "greeting",
            text = "hello there",
            pinned = true,
            createdAt = 10,
            updatedAt = 20,
            lastUsedAt = 30,
            categoryId = 10,
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
        val decoded = SnippetBackup.decode(SnippetBackup.encode(samples, categories))

        assertEquals(samples.map { it.copy(categoryId = null) }, decoded.snippets.map { it.record })
        assertEquals("Work", decoded.snippets[0].category)
        assertNull(decoded.snippets[1].category)
    }

    @Test
    fun roundTripPreservesCategories() {
        val decoded = SnippetBackup.decode(SnippetBackup.encode(samples, categories))

        assertEquals(
            listOf(BackupCategory(name = "Work", position = 0), BackupCategory(name = "Personal", position = 1)),
            decoded.categories,
        )
    }

    @Test
    fun unknownCategoryIdEncodesAsNull() {
        val decoded = SnippetBackup.decode(SnippetBackup.encode(samples, emptyList()))

        assertTrue(decoded.snippets.all { it.category == null })
    }

    @Test
    fun emptyListRoundTrips() {
        val decoded = SnippetBackup.decode(SnippetBackup.encode(emptyList(), emptyList()))

        assertTrue(decoded.snippets.isEmpty())
        assertTrue(decoded.categories.isEmpty())
    }

    @Test
    fun v1PayloadDecodesWithNoCategoriesAndNullCategory() {
        val raw = """{"version": 1, "snippets": [{"title":"a","text":"hello","pinned":true,"createdAt":1,"updatedAt":2,"lastUsedAt":3}]}"""

        val decoded = SnippetBackup.decode(raw)

        assertTrue(decoded.categories.isEmpty())
        assertEquals(1, decoded.snippets.size)
        assertNull(decoded.snippets[0].category)
        assertEquals("hello", decoded.snippets[0].record.text)
        assertTrue(decoded.snippets[0].record.pinned)
    }

    @Test
    fun unknownVersionIsRejected() {
        val raw = """{"version": 3, "snippets": []}"""

        try {
            SnippetBackup.decode(raw)
            fail("expected IllegalArgumentException for unknown version")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("3"))
        }
    }

    @Test
    fun futurePayloadShapeStillRejectedOnVersion() {
        val raw = SnippetBackup.encode(samples, categories).replace("\"version\": 2", "\"version\": 99")

        try {
            SnippetBackup.decode(raw)
            fail("expected IllegalArgumentException for unknown version")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("99"))
        }
    }

    @Test
    fun encodeExcludesImageSnippets() {
        val records = samples + SnippetRecord(
            id = 3,
            title = "screenshot",
            text = "",
            createdAt = 12,
            updatedAt = 22,
            lastUsedAt = 32,
            imageFile = "abc123.jpg",
            imageWidth = 100,
            imageHeight = 200,
        )

        val decoded = SnippetBackup.decode(SnippetBackup.encode(records, categories))

        assertEquals(2, decoded.snippets.size)
        assertTrue(decoded.snippets.none { it.record.imageFile != null })
    }
}
