package app.pastille.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.pastille.model.SnippetRecord
import app.pastille.model.filterByTags
import app.pastille.model.matchesQuery
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TagRepositoryTest {

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

    private suspend fun snippets() = repository.observeSnippets().first()
    private suspend fun tagNames() = repository.observeTagNames().first()

    @Test
    fun `tags are added normalised and come back on the snippet`() = runBlocking {
        val id = repository.upsert(SnippetRecord(title = "Sign-off", text = "Best wishes"))

        repository.setTags(id, listOf("Email", "#work", "email"))

        assertEquals(listOf("email", "work"), snippets().single().tags)
        assertEquals(listOf("email", "work"), repository.get(id)!!.tags)
    }

    @Test
    fun `removing a tag drops it, and an unused tag leaves the suggestions`() = runBlocking {
        val first = repository.upsert(SnippetRecord(title = "One", text = "one"))
        val second = repository.upsert(SnippetRecord(title = "Two", text = "two"))
        repository.setTags(first, listOf("work", "otp"))
        repository.setTags(second, listOf("work"))

        repository.setTags(first, listOf("work"))

        assertEquals(listOf("work"), repository.get(first)!!.tags)
        assertEquals(listOf("work"), tagNames())
    }

    @Test
    fun `filter and search see the tags`() = runBlocking {
        val a = repository.upsert(SnippetRecord(title = "Invoice reply", text = "Thanks, paid"))
        val b = repository.upsert(SnippetRecord(title = "Code", text = "123456"))
        repository.setTags(a, listOf("email", "work"))
        repository.setTags(b, listOf("otp", "work"))
        val all = snippets()

        assertEquals(setOf(a, b), filterByTags(all, setOf("work")).map { it.id }.toSet())
        assertEquals(listOf(b), filterByTags(all, setOf("work", "otp")).map { it.id })
        assertEquals(listOf(b), all.filter { matchesQuery(it, "otp") }.map { it.id })
    }

    @Test
    fun `binned snippets keep their tags, and delete forever removes the links`() = runBlocking {
        val id = repository.upsert(SnippetRecord(title = "Old", text = "old"))
        repository.setTags(id, listOf("archive"))

        repository.delete(id)
        assertEquals(listOf("archive"), repository.get(id)!!.tags)
        assertEquals(listOf("archive"), tagNames())

        repository.deleteForever(id)
        assertEquals(emptyList<String>(), tagNames())
        assertEquals(0, db.query("SELECT * FROM snippet_tags", null).use { it.count })
    }

    @Test
    fun `backup v3 round-trips tags through export and import`() = runBlocking {
        val id = repository.upsert(SnippetRecord(title = "Sign-off", text = "Best wishes"))
        repository.setTags(id, listOf("email"))
        val json = repository.exportJson().json
        repository.emptyBin()
        repository.delete(id)
        repository.emptyBin()

        repository.importJson(json)

        assertEquals(listOf("email"), snippets().single().tags)
    }
}
