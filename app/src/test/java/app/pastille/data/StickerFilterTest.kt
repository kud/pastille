package app.pastille.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.pastille.model.SnippetRecord
import app.pastille.model.matchesQuery
import app.pastille.picker.snippetMatches
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Stickers never appear under Snippets: one test per snippet reader, each going through the DAO.
@RunWith(RobolectricTestRunner::class)
class StickerFilterTest {

    private lateinit var db: PastilleDatabase
    private lateinit var repository: SnippetRepository
    private var folderId = 0L
    private var stickerId = 0L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PastilleDatabase::class.java,
        ).build()
        repository = SnippetRepository.forDatabase(db)
        runBlocking {
            folderId = repository.createCategory("Reactions")
            repository.upsert(SnippetRecord(title = "Hello", text = "hello there", categoryId = folderId))
            // Written straight to the DAO, in a folder and with text, so each reader's `sticker = 0`
            // is what keeps it out, not its missing folder or its image.
            stickerId = db.snippets().upsert(
                SnippetEntity(
                    title = "Thumbs up hello",
                    text = "hello there",
                    createdAt = 1,
                    updatedAt = 1,
                    lastUsedAt = 1,
                    categoryId = folderId,
                    sticker = true,
                ),
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun live(): List<SnippetRecord> = runBlocking { repository.observeSnippets().first() }

    @Test
    fun `the All list leaves stickers out`() {
        assertEquals(listOf("Hello"), live().map { it.title })
    }

    @Test
    fun `folder contents leave stickers out`() {
        assertEquals(listOf("Hello"), live().filter { it.categoryId == folderId }.map { it.title })
    }

    @Test
    fun `search leaves stickers out`() {
        assertEquals(listOf("Hello"), live().filter { matchesQuery(it, "hello") }.map { it.title })
    }

    @Test
    fun `the picker's Snippets list leaves stickers out`() {
        assertTrue(live().none { snippetMatches(it, "thumbs") })
    }

    @Test
    fun `the text duplicate check leaves stickers out`() = runBlocking {
        repository.delete(live().single().id)

        assertNull(repository.findTextDuplicate("hello there"))
    }

    @Test
    fun `observeStickers returns image stickers in position order, and a binned one only in the bin`() = runBlocking {
        val first = repository.insertImage("Wave", "b.webp", 64, 64)
        val second = repository.insertImage("Grin", "a.png", 64, 64)
        repository.setSticker(first, true)
        repository.setSticker(second, true)

        assertEquals(listOf("Wave", "Grin"), repository.observeStickers().first().map { it.title })

        repository.delete(first)

        assertEquals(listOf("Grin"), repository.observeStickers().first().map { it.title })
        assertTrue(repository.observeBin().first().any { it.id == first })
        assertTrue(stickerId !in repository.observeStickers().first().map { it.id })
    }
}
