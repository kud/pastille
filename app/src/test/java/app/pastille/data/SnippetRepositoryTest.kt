package app.pastille.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.net.Uri
import app.pastille.images.ImageImportException
import app.pastille.images.ImageImporter
import app.pastille.images.StoredImage
import app.pastille.model.SnippetRecord
import app.pastille.ui.stickerImportMessage
import app.pastille.ui.movedMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SnippetRepositoryTest {

    private lateinit var db: PastilleDatabase
    private lateinit var repository: SnippetRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PastilleDatabase::class.java,
        ).build()
        repository = SnippetRepository.forDatabase(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun live(): List<SnippetRecord> = runBlocking { repository.observeSnippets().first() }

    @Test
    fun `organise moves a snippet to a folder and undo puts it back`() = runBlocking {
        val personal = repository.createCategory("Personal")
        val work = repository.createCategory("Work")
        val id = repository.upsert(SnippetRecord(title = "Address", text = "1 Example Street", categoryId = work))
        val before = repository.get(id)!!

        repository.setCategory(listOf(id), personal)
        assertEquals(personal, repository.get(id)!!.categoryId)
        assertEquals("Moved to Personal", movedMessage("Personal"))

        repository.setCategory(listOf(id), before.categoryId)
        assertEquals(work, repository.get(id)!!.categoryId)
    }

    @Test
    fun `organise can move a snippet to the top level`() = runBlocking {
        val work = repository.createCategory("Work")
        val id = repository.upsert(SnippetRecord(title = "Sign-off", text = "Best wishes", categoryId = work))

        repository.setCategory(listOf(id), null)

        assertEquals(null, live().single { it.id == id }.categoryId)
        assertEquals("Moved to top level", movedMessage(null))
    }

    // Every URI becomes the file named after it, so the same URI twice is the same image.
    private val importer = ImageImporter { uri ->
        val name = uri.lastPathSegment ?: throw ImageImportException("Unreadable image")
        if (name.startsWith("broken")) throw ImageImportException("Unreadable image")
        StoredImage(fileName = "$name.webp", width = 64, height = 64, alreadyExisted = false)
    }

    private fun uris(vararg names: String): List<Uri> = names.map { Uri.parse("content://test/$it") }

    private suspend fun importStickers(vararg names: String): ImportResult =
        repository.importImages(uris(*names), categoryId = null, sticker = true, importer = importer, titleFor = { "Sticker $it" })

    @Test
    fun `a text snippet can't become a sticker`() = runBlocking {
        val id = repository.upsert(SnippetRecord(title = "Hi", text = "hello"))

        assertFalse(repository.setSticker(id, true))
        assertFalse(repository.get(id)!!.sticker)
    }

    @Test
    fun `an image in a folder becomes a sticker at the end of the order, with no folder`() = runBlocking {
        val work = repository.createCategory("Work")
        val existing = repository.insertImage("Old", "old.png", 10, 10)
        repository.setSticker(existing, true)
        val id = repository.insertImage("Cat", "cat.webp", 10, 10, categoryId = work)

        assertTrue(repository.setSticker(id, true))

        val sticker = repository.get(id)!!
        assertTrue(sticker.sticker)
        assertNull(sticker.categoryId)
        assertEquals(listOf(existing, id), repository.observeStickers().first().map { it.id })
    }

    @Test
    fun `turning a sticker off puts it back at the top level`() = runBlocking {
        val id = repository.insertImage("Cat", "cat.webp", 10, 10)
        repository.setSticker(id, true)

        repository.setSticker(id, false)

        val row = live().single { it.id == id }
        assertFalse(row.sticker)
        assertNull(row.categoryId)
    }

    @Test
    fun `importing into Stickers flips an existing image snippet, and Undo puts it back in its folder`() = runBlocking {
        val work = repository.createCategory("Work")
        val existing = repository.insertImage("Cat", "cat.webp", 64, 64, categoryId = work)

        val result = importStickers("cat", "dog")

        assertEquals(listOf(existing), result.converted.map { it.id })
        assertEquals(1, result.created.size)
        assertTrue(repository.get(existing)!!.sticker)
        assertNull(repository.get(existing)!!.categoryId)
        assertEquals("Added 1 sticker · 1 moved from snippets", stickerImportMessage(result))

        repository.undoImport(result) {}

        val restored = repository.get(existing)!!
        assertFalse(restored.sticker)
        assertEquals(work, restored.categoryId)
    }

    @Test
    fun `a duplicate that is already a sticker counts as already there`() = runBlocking {
        importStickers("cat")

        val again = importStickers("cat")

        assertEquals(1, again.alreadyThere.size)
        assertTrue(again.created.isEmpty() && again.converted.isEmpty())
        assertEquals(1, repository.observeStickers().first().size)
    }

    @Test
    fun `importing as images never converts a sticker or an image`() = runBlocking {
        importStickers("cat")

        val result = repository.importImages(uris("cat"), null, sticker = false, importer = importer, titleFor = { "Image" })

        assertEquals(1, result.alreadyThere.size)
        assertTrue(repository.observeStickers().first().single().sticker)
    }

    @Test
    fun `34 images save 20`() = runBlocking {
        val result = importStickers(*Array(34) { "img$it" })

        assertEquals(20, result.created.size)
        assertEquals(34, result.requested)
        assertEquals(20, repository.observeStickers().first().size)
        assertEquals("Added 20 of 34 stickers", stickerImportMessage(result))
    }

    @Test
    fun `an unreadable image is reported, not saved`() = runBlocking {
        val result = importStickers("broken")

        assertEquals(listOf("Unreadable image"), result.failed)
        assertEquals("Couldn't add 1 image", stickerImportMessage(result))
    }

    @Test
    fun `undo deletes the new rows and their files`() = runBlocking {
        val result = importStickers("cat", "dog")
        val deleted = mutableListOf<String>()

        repository.undoImport(result) { deleted.add(it) }

        assertTrue(repository.observeStickers().first().isEmpty())
        assertEquals(setOf("cat.webp", "dog.webp"), deleted.toSet())
    }
}
