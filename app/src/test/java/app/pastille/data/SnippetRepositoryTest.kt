package app.pastille.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.pastille.model.SnippetRecord
import app.pastille.ui.movedMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
}
