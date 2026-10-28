package app.pastille.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.pastille.backup.BackupArchive
import app.pastille.model.SnippetRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

// The backup through the repository: what one phone exports, another imports, images and all.
@RunWith(RobolectricTestRunner::class)
class BackupImportTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val png = "0123456789abcdef0123456789abcdef.png"
    private val webp = "aaaabbbbccccddddaaaabbbbccccdddd.webp"
    private val pngBytes = byteArrayOf(1, 2, 3)
    private val webpBytes = byteArrayOf(4, 5, 6, 7)

    private lateinit var source: PastilleDatabase
    private lateinit var target: PastilleDatabase

    private fun database() = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        PastilleDatabase::class.java,
    ).allowMainThreadQueries().build()

    @Before
    fun setUp() {
        source = database()
        target = database()
    }

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    private suspend fun exportFrom(repository: SnippetRepository, images: File): File {
        val file = temp.newFile()
        file.outputStream().use { repository.exportBackup(it, images) }
        return file
    }

    @Test
    fun `a backup moves snippets, images and stickers to another phone, once`() = runBlocking {
        val sourceImages = temp.newFolder().apply {
            File(this, png).writeBytes(pngBytes)
            File(this, webp).writeBytes(webpBytes)
        }
        val from = SnippetRepository.forDatabase(source)
        val work = from.createCategory("Work")
        val text = from.upsert(SnippetRecord(title = "Sign-off", text = "Best wishes", categoryId = work))
        from.setTags(text, listOf("email"))
        from.insertImage(title = "Diagram", imageFile = png, width = 4, height = 3, categoryId = work)
        val sticker = from.insertImage(title = "Wave", imageFile = webp, width = 2, height = 2)
        assertTrue(from.setSticker(sticker, on = true))
        val binned = from.upsert(SnippetRecord(title = "Old", text = "binned text"))
        from.delete(binned)

        val file = exportFrom(from, sourceImages)
        val targetImages = temp.newFolder()
        val to = SnippetRepository.forDatabase(target)

        val counts = to.importBackup(file, targetImages)

        assertEquals(BackupArchive.Counts(snippets = 1, images = 1, stickers = 1), counts)
        val snippets = to.observeSnippets().first()
        assertEquals(setOf("Sign-off", "Diagram"), snippets.map { it.title }.toSet())
        val signOff = snippets.single { it.title == "Sign-off" }
        assertEquals(listOf("email"), signOff.tags)
        val workId = to.getCategories().single { it.name == "Work" }.id
        assertTrue(snippets.all { it.categoryId == workId })
        val diagram = snippets.single { it.title == "Diagram" }
        assertEquals(png, diagram.imageFile)
        assertEquals(4, diagram.imageWidth)
        val stickers = to.observeStickers().first()
        assertEquals(listOf(webp), stickers.map { it.imageFile })
        assertArrayEquals(pngBytes, File(targetImages, png).readBytes())
        assertArrayEquals(webpBytes, File(targetImages, webp).readBytes())
        assertTrue("the bin isn't backed up", snippets.none { it.text == "binned text" })

        assertEquals("a second import adds nothing", BackupArchive.Counts(0, 0, 0), to.importBackup(file, targetImages))
        assertEquals(2, to.observeSnippets().first().size)
        assertEquals(1, to.observeStickers().first().size)
    }

    @Test
    fun `an old JSON export still imports, skipping text already here`() = runBlocking {
        val to = SnippetRepository.forDatabase(target)
        to.upsert(SnippetRecord(title = "Hi", text = "hello"))
        val old = temp.newFile("pastille-snippets.json")
        old.writeText(
            """{"version": 3, "categories": [{"name": "Work"}], "snippets": [""" +
                """{"text": "hello"}, {"text": "new one", "category": "Work", "tags": ["x"]}]}""",
        )

        val counts = to.importBackup(old, temp.newFolder())

        assertEquals(BackupArchive.Counts(snippets = 1, images = 0, stickers = 0), counts)
        val added = to.observeSnippets().first().single { it.text == "new one" }
        assertEquals(listOf("x"), added.tags)
    }

    @Test
    fun `the fingerprint moves with content, not with use`() = runBlocking {
        val repository = SnippetRepository.forDatabase(source)
        val id = repository.upsert(SnippetRecord(title = "A", text = "a"))
        val before = repository.backupFingerprint()

        repository.recordUse(id, now = 123_456)
        assertEquals(before, repository.backupFingerprint())

        repository.upsert(SnippetRecord(title = "B", text = "b"))
        assertTrue(before != repository.backupFingerprint())
    }
}
