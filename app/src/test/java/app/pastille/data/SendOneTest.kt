package app.pastille.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.pastille.backup.BackupArchive
import app.pastille.backup.SnippetBackup
import app.pastille.model.SnippetRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

// "Send to another phone": a one-item archive in the backup format, imported by "Import to Pastille".
@RunWith(RobolectricTestRunner::class)
class SendOneTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val webp = "aaaabbbbccccddddaaaabbbbccccdddd.webp"
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

    private suspend fun send(repository: SnippetRepository, images: File, id: Long): Pair<File, BackupArchive.Written> {
        val file = temp.newFile()
        val written = file.outputStream().use { repository.exportItems(it, images, listOf(id)) }
        return file to written
    }

    private suspend fun uidOf(repository: SnippetRepository, id: Long): String = repository.get(id)!!.uid

    @Test
    fun `one snippet travels alone, with its folder and uid, and lands once`() = runBlocking {
        val from = SnippetRepository.forDatabase(source)
        val home = from.createCategory("Home")
        val wifi = from.upsert(SnippetRecord(title = "Wi-Fi at the cottage", text = "password: hunter2", categoryId = home))
        from.setTags(wifi, listOf("travel"))
        from.upsert(SnippetRecord(title = "Other", text = "not sent"))
        val binned = from.upsert(SnippetRecord(title = "Old", text = "binned"))
        from.delete(binned)

        val (file, written) = send(from, temp.newFolder(), wifi)

        assertEquals(BackupArchive.Written(snippets = 1, images = 0, stickers = 0, leftOut = 0), written)
        assertTrue("verifies like a backup", BackupArchive.verify(file) != null)
        val contents = SnippetBackup.decode(BackupArchive.readJson(file))
        val single = BackupArchive.single(contents)!!
        assertEquals("Wi-Fi at the cottage", single.record.title)
        assertEquals(listOf("Home"), contents.categories.map { it.name })
        val payload = BackupArchive.readJson(file)
        assertFalse("no bin, no tombstones", payload.contains("binned"))

        val to = SnippetRepository.forDatabase(target)
        assertFalse(to.holds(single.record))
        assertEquals(BackupArchive.Counts(1, 0, 0), to.importBackup(file, temp.newFolder()))
        val landed = to.observeSnippets().first().single()
        assertEquals("password: hunter2", landed.text)
        assertEquals(listOf("travel"), landed.tags)
        assertEquals("Home", to.getCategories().single { it.id == landed.categoryId }.name)

        val sentUid = uidOf(from, wifi)
        assertEquals("same uid on both phones, so sync sees one item", sentUid, to.syncState().snippets.single().uid)
        assertEquals(
            "same folder uid too",
            from.syncState().categories.single().uid,
            to.syncState().categories.single().uid,
        )

        assertTrue(to.holds(single.record))
        assertEquals("sending it again adds nothing", BackupArchive.Counts(0, 0, 0), to.importBackup(file, temp.newFolder()))
    }

    @Test
    fun `an edited copy already here counts as there, by uid`() = runBlocking {
        val from = SnippetRepository.forDatabase(source)
        val id = from.upsert(SnippetRecord(title = "Door", text = "1234"))
        val (file, _) = send(from, temp.newFolder(), id)
        val to = SnippetRepository.forDatabase(target)
        to.importBackup(file, temp.newFolder())
        val here = to.observeSnippets().first().single()
        to.upsert(here.copy(text = "5678"))

        val record = BackupArchive.single(SnippetBackup.decode(BackupArchive.readJson(file)))!!.record
        assertTrue(to.holds(record))
        assertEquals(BackupArchive.Counts(0, 0, 0), to.importBackup(file, temp.newFolder()))
        assertEquals(listOf("5678"), to.observeSnippets().first().map { it.text })
    }

    @Test
    fun `a uid taken by a binned row gets a fresh one, and the bin is left alone`() = runBlocking {
        val from = SnippetRepository.forDatabase(source)
        val id = from.upsert(SnippetRecord(title = "Door", text = "1234"))
        val (file, _) = send(from, temp.newFolder(), id)
        val to = SnippetRepository.forDatabase(target)
        to.importBackup(file, temp.newFolder())
        to.delete(to.observeSnippets().first().single().id)

        assertEquals(BackupArchive.Counts(1, 0, 0), to.importBackup(file, temp.newFolder()))
        val rows = to.syncState().snippets
        assertEquals(2, rows.size)
        assertEquals(1, rows.count { it.deletedAt != null })
        assertNotEquals(rows[0].uid, rows[1].uid)
    }

    @Test
    fun `a sticker travels with its image and comes back a sticker`() = runBlocking {
        val images = temp.newFolder().apply { File(this, webp).writeBytes(webpBytes) }
        val from = SnippetRepository.forDatabase(source)
        val sticker = from.insertImage(title = "Wave", imageFile = webp, width = 2, height = 2)
        assertTrue(from.setSticker(sticker, on = true))

        val (file, written) = send(from, images, sticker)
        assertEquals(1, written.stickers)

        val to = SnippetRepository.forDatabase(target)
        val targetImages = temp.newFolder()
        assertEquals(BackupArchive.Counts(0, 0, 1), to.importBackup(file, targetImages))
        assertEquals(listOf(webp), to.observeStickers().first().map { it.imageFile })
        assertArrayEquals(webpBytes, File(targetImages, webp).readBytes())
    }

    @Test
    fun `an image whose file is gone sends nothing`() = runBlocking {
        val from = SnippetRepository.forDatabase(source)
        val id = from.insertImage(title = "Gone", imageFile = webp, width = 2, height = 2)
        val (_, written) = send(from, temp.newFolder(), id)
        assertEquals(0, written.snippets + written.images + written.stickers)
        assertEquals(1, written.leftOut)
    }
}
