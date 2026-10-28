package app.pastille.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BackupMessagesTest {

    @Test
    fun `export counts are pluralised`() {
        assertEquals("Exported 1 snippet", exportMessage(1, 0))
        assertEquals("Exported 12 snippets", exportMessage(12, 0))
        assertEquals("Exported 12 snippets · 1 image isn't included", exportMessage(12, 1))
        assertEquals("Exported 0 snippets · 3 images aren't included", exportMessage(0, 3))
    }

    @Test
    fun `import counts are pluralised and zero says why`() {
        assertEquals("Imported 1 snippet", importMessage(1))
        assertEquals("Imported 8 snippets", importMessage(8))
        assertEquals("No new snippets to import", importMessage(0))
    }

    @Test
    fun `failure messages carry no exception text`() {
        listOf(EXPORT_FAILED, IMPORT_UNREADABLE, IMPORT_NOT_A_BACKUP).forEach {
            assertFalse(it, it.contains("Exception") || it.contains(":"))
        }
    }
}
