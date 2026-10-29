package app.pastille.backup

import app.pastille.backup.SyncFolder.Child
import app.pastille.backup.SyncFolder.Choice
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncFolderTest {

    @Test fun `the fixed name is the one sync has always suggested`() {
        assertEquals("pastille-backup.zip", SyncFolder.FILE_NAME)
    }

    @Test fun `an empty folder creates the file`() {
        assertEquals(Choice.Create, SyncFolder.choose(emptyList()))
    }

    @Test fun `an existing file is used, never a second one`() {
        val children = listOf(Child("a", "notes.txt", 5), Child("b", "pastille-backup.zip", 10))
        assertEquals(Choice.Use("b", 1, 10), SyncFolder.choose(children))
    }

    @Test fun `only an exact name matches`() {
        val children = listOf(
            Child("a", "pastille-backup (1).zip", 99),
            Child("b", "Pastille-backup.zip", 99),
            Child("c", "pastille-backup.zip.tmp", 99),
            Child("d", "pastille-backup.zip", 1, isDirectory = true),
            Child("e", null, 99),
        )
        assertEquals(Choice.Create, SyncFolder.choose(children))
    }

    @Test fun `several matches use the most recently modified and count them`() {
        val children = listOf(
            Child("old", "pastille-backup.zip", 100),
            Child("new", "pastille-backup.zip", 300),
            Child("unknown", "pastille-backup.zip", null),
        )
        assertEquals(Choice.Use("new", 3, 300), SyncFolder.choose(children))
    }

    @Test fun `matches with no modified time keep the provider's order`() {
        val children = listOf(Child("first", "pastille-backup.zip", null), Child("second", "pastille-backup.zip", null))
        assertEquals(Choice.Use("first", 2, null), SyncFolder.choose(children))
    }
}
