package app.pastille.ui

import app.pastille.backup.BackupArchive
import app.pastille.settings.BackupProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupMessagesTest {

    @Test
    fun `export counts are pluralised and joined`() {
        assertEquals("Exported 1 snippet", exportMessage(BackupArchive.Written(1, 0, 0, 0)))
        assertEquals("Exported 12 snippets and 3 stickers", exportMessage(BackupArchive.Written(12, 0, 3, 0)))
        assertEquals(
            "Exported 12 snippets, 1 sticker and 2 images",
            exportMessage(BackupArchive.Written(snippets = 12, images = 2, stickers = 1, leftOut = 0)),
        )
        assertEquals("Exported nothing", exportMessage(BackupArchive.Written(0, 0, 0, 0)))
    }

    @Test
    fun `import counts are pluralised and zero says why`() {
        assertEquals("Imported 1 snippet", importMessage(BackupArchive.Counts(1, 0, 0)))
        assertEquals("Imported 8 snippets and 1 image", importMessage(BackupArchive.Counts(8, 1, 0)))
        assertEquals("Imported 2 stickers", importMessage(BackupArchive.Counts(0, 0, 2)))
        assertEquals("Nothing new to import", importMessage(BackupArchive.Counts(0, 0, 0)))
    }

    @Test
    fun `the import question names what the file holds`() {
        assertEquals(
            "This backup holds 3 snippets and 1 sticker. Anything already in Pastille is skipped.",
            importQuestion(BackupArchive.Counts(snippets = 3, images = 0, stickers = 1)),
        )
    }

    @Test
    fun `backup status says when, or what to do`() {
        val now = 10L * 24 * 60 * 60 * 1000
        assertEquals("pastille-backup.zip · Not synced yet", backupStatus("pastille-backup.zip", null, null, now))
        assertEquals("pastille-backup.zip · Synced just now", backupStatus("pastille-backup.zip", now - 5_000, null, now))
        assertEquals("pastille-backup.zip · Synced 5 min ago", backupStatus("pastille-backup.zip", now - 5 * 60_000, null, now))
        val access = backupStatus("pastille-backup.zip", now, BackupProblem.Access, now)
        assertTrue(access, access.startsWith("Can't reach pastille-backup.zip") && access.endsWith("choose a file again."))
        assertTrue(backupStatus("b.zip", now, BackupProblem.Write, now).contains("failed"))
        assertEquals("b.zip · Waiting for Drive", backupStatus("b.zip", now, BackupProblem.Read, now, "Drive"))
        assertEquals("b.zip · Waiting for pCloud", backupStatus("b.zip", now, BackupProblem.Read, now, "pCloud"))
        assertTrue(backupStatus("b.zip", now, BackupProblem.NotKept, now, "pCloud").startsWith("pCloud didn't keep permission to b.zip"))
        assertTrue(backupStatus("b.zip", now, BackupProblem.CantOverwrite, now, "pCloud").startsWith("pCloud can't overwrite b.zip"))
        assertTrue(backupStatus("b.zip", now, BackupProblem.ReadOnly, now, "the storage app").startsWith("The storage app only lets Pastille read"))
        assertTrue(backupStatus("b.zip", now, BackupProblem.Mismatch, now, "pCloud").startsWith("pCloud didn't save b.zip"))
    }

    @Test
    fun `failure messages carry no exception text`() {
        listOf(EXPORT_FAILED, IMPORT_UNREADABLE, IMPORT_NOT_A_BACKUP, SHARE_FAILED).forEach {
            assertFalse(it, it.contains("Exception") || it.contains(":"))
        }
    }

    @Test
    fun `one item is named by its title, first line or kind`() {
        assertEquals("“Wi-Fi at the cottage”", itemPhrase("Wi-Fi at the cottage", "pw", isImage = false, sticker = false))
        assertEquals("“first line”", itemPhrase("", "\n first line \nsecond", isImage = false, sticker = false))
        assertEquals("this sticker", itemPhrase("", "", isImage = true, sticker = true))
        assertEquals("this image", itemPhrase(" ", "", isImage = true, sticker = false))
        assertEquals("“${"a".repeat(39)}…”", itemPhrase("a".repeat(60), "", isImage = false, sticker = false))
        assertEquals("Add “Door” to Pastille?", addOneQuestion("“Door”"))
        assertEquals("Added “Door”", addOneMessage("“Door”", added = true))
        assertEquals("This sticker is already in Pastille", addOneMessage("this sticker", added = false))
    }
}
