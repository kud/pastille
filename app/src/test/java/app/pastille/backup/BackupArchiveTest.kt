package app.pastille.backup

import app.pastille.model.CategoryRecord
import app.pastille.model.SnippetRecord
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupArchiveTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val png = "0123456789abcdef0123456789abcdef.png"
    private val gif = "fedcba9876543210fedcba9876543210.gif"

    private fun imagesDir(vararg files: Pair<String, ByteArray>): File =
        temp.newFolder().apply { files.forEach { (name, bytes) -> File(this, name).writeBytes(bytes) } }

    private val records = listOf(
        SnippetRecord(id = 1, title = "Hello", text = "hello there", categoryId = 10, tags = listOf("work")),
        SnippetRecord(id = 2, title = "Cat", text = "", imageFile = png, imageWidth = 4, imageHeight = 3, categoryId = 10),
        SnippetRecord(id = 3, title = "Wave", text = "", imageFile = gif, imageWidth = 2, imageHeight = 2, sticker = true),
    )
    private val categories = listOf(CategoryRecord(id = 10, name = "Work", position = 0))

    private fun archive(images: File): Pair<File, BackupArchive.Written> {
        val file = temp.newFile("backup.zip")
        val written = file.outputStream().use { BackupArchive.write(it, records, categories) { File(images, it) } }
        return file to written
    }

    @Test
    fun `an archive round-trips text, images, stickers, folders and tags`() {
        val pngBytes = byteArrayOf(1, 2, 3, 4)
        val gifBytes = byteArrayOf(9, 8, 7)
        val (file, written) = archive(imagesDir(png to pngBytes, gif to gifBytes))

        assertEquals(BackupArchive.Written(snippets = 1, images = 1, stickers = 1, leftOut = 0), written)
        assertTrue(BackupArchive.isZip(file))
        val contents = BackupArchive.verify(file)!!
        assertEquals(listOf("hello there", "", ""), contents.snippets.map { it.record.text })
        assertEquals(listOf(null, png, gif), contents.snippets.map { it.record.imageFile })
        assertEquals(listOf(false, false, true), contents.snippets.map { it.record.sticker })
        assertEquals(listOf("Work", "Work", null), contents.snippets.map { it.category })
        assertEquals(listOf("work"), contents.snippets[0].record.tags)
        assertEquals(4, contents.snippets[1].record.imageWidth)
        assertEquals(BackupArchive.Counts(snippets = 1, images = 1, stickers = 1), BackupArchive.summary(contents))

        val target = temp.newFolder()
        assertTrue(BackupArchive.extractImage(file, png, target))
        assertTrue(BackupArchive.extractImage(file, gif, target))
        assertArrayEquals(pngBytes, File(target, png).readBytes())
        assertArrayEquals(gifBytes, File(target, gif).readBytes())
        assertEquals("no temporary file is left behind", setOf(png, gif), target.list()!!.toSet())
    }

    @Test
    fun `a record whose image file is missing is left out rather than named without its bytes`() {
        val (file, written) = archive(imagesDir(png to byteArrayOf(1)))

        assertEquals(1, written.leftOut)
        val contents = BackupArchive.verify(file)!!
        assertEquals(listOf(null, png), contents.snippets.map { it.record.imageFile })
    }

    @Test
    fun `an old bare JSON export still reads`() {
        val old = temp.newFile("pastille-snippets.json")
        old.writeText("""{"version": 3, "snippets": [{"text": "hello", "tags": ["a"]}]}""")

        assertFalse(BackupArchive.isZip(old))
        val contents = SnippetBackup.decode(BackupArchive.readJson(old))
        assertEquals("hello", contents.snippets.single().record.text)
        assertEquals(BackupArchive.Counts(1, 0, 0), BackupArchive.summary(contents))
    }

    @Test
    fun `a zip that isn't a Pastille backup, or is cut short, fails verification`() {
        val other = temp.newFile("photos.zip")
        ZipOutputStream(other.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("holiday.jpg"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
        }
        assertNull(BackupArchive.verify(other))

        val (file, _) = archive(imagesDir(png to ByteArray(4096) { it.toByte() }, gif to byteArrayOf(1)))
        val cut = temp.newFile("cut.zip").apply { writeBytes(file.readBytes().copyOf(file.length().toInt() / 2)) }
        assertNull(BackupArchive.verify(cut))
    }

    @Test
    fun `entries outside images or with a crafted name are never extracted`() {
        val evil = temp.newFile("evil.zip")
        ZipOutputStream(evil.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(BackupArchive.JSON_ENTRY))
            zip.write("""{"version": 4, "snippets": [{"text": "", "image": "../../evil.png"}]}""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("images/../../evil.png"))
            zip.write(byteArrayOf(1))
            zip.closeEntry()
        }
        val target = temp.newFolder()

        assertFalse(BackupArchive.extractImage(evil, "../../evil.png", target))
        assertFalse(BackupArchive.isImageName("../../evil.png"))
        assertFalse(BackupArchive.isImageName("images/$png"))
        assertTrue(BackupArchive.isImageName(png))
        assertEquals(0, target.list()!!.size)
    }

    @Test
    fun `fingerprint ignores use but not content`() {
        val base = SnippetBackup.fingerprint(records, categories)
        assertEquals(base, SnippetBackup.fingerprint(records.map { it.copy(lastUsedAt = 999) }, categories))
        assertFalse(base == SnippetBackup.fingerprint(records.map { it.copy(title = it.title + "!") }, categories))
    }

    @Test
    fun `only keeps the chosen live snippets and their folders`() {
        val state = app.pastille.sync.SyncState(
            snippets = listOf(
                app.pastille.sync.SyncSnippet(uid = "a", text = "a", categoryUid = "home"),
                app.pastille.sync.SyncSnippet(uid = "b", text = "b", categoryUid = "work"),
                app.pastille.sync.SyncSnippet(uid = "c", text = "c", deletedAt = 5),
            ),
            categories = listOf(app.pastille.sync.SyncCategory("home", "Home"), app.pastille.sync.SyncCategory("work", "Work")),
            tags = listOf(app.pastille.sync.SyncTag("t", "travel")),
            tombstones = listOf(app.pastille.sync.SyncTombstone("z", "snippet", 1)),
        )
        val sent = BackupArchive.only(state, setOf("a", "c"))
        assertEquals(listOf("a"), sent.snippets.map { it.uid })
        assertEquals(listOf("Home"), sent.categories.map { it.name })
        assertTrue(sent.tags.isEmpty() && sent.tombstones.isEmpty())
    }

    @Test
    fun `send names are safe file names`() {
        assertEquals("Pastille - Wi-Fi at the cottage.zip", BackupArchive.sendName("Wi-Fi at the cottage"))
        assertEquals("Pastille - Café a b.zip", BackupArchive.sendName("Café a/b\nsecond line"))
        assertEquals("Pastille - x y.zip", BackupArchive.sendName("  x:*?  y... "))
        assertEquals("Pastille.zip", BackupArchive.sendName("  "))
        assertTrue(BackupArchive.sendName("a".repeat(200)).length <= "Pastille - .zip".length + 48)
    }
}
