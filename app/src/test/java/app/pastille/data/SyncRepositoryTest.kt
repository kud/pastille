package app.pastille.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.pastille.backup.SnippetBackup
import app.pastille.model.SnippetRecord
import app.pastille.sync.SyncMerge
import app.pastille.sync.SyncState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Sync through the real database: two phones, one file (the JSON as the archive holds it).
@RunWith(RobolectricTestRunner::class)
class SyncRepositoryTest {

    private lateinit var dbA: PastilleDatabase
    private lateinit var dbB: PastilleDatabase
    private lateinit var a: SnippetRepository
    private lateinit var b: SnippetRepository
    private var baseA: SyncState? = null
    private var baseB: SyncState? = null
    private var file: String? = null

    private fun database() = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        PastilleDatabase::class.java,
    ).allowMainThreadQueries().build()

    @Before
    fun setUp() {
        dbA = database()
        dbB = database()
        a = SnippetRepository.forDatabase(dbA)
        b = SnippetRepository.forDatabase(dbB)
    }

    @After
    fun tearDown() {
        dbA.close()
        dbB.close()
    }

    // What AutoBackup does: merge the file in (it becomes the base), then write ours (the new base).
    private suspend fun sync(repository: SnippetRepository, isA: Boolean) {
        file?.let { raw ->
            val remote = SnippetBackup.decodeState(raw)
            val result = SyncMerge.merge(if (isA) baseA else baseB, repository.syncState(), remote, NOW)
            repository.applySync(result) { true }
        }
        val written = SnippetBackup.encodeState(repository.syncState())
        file = written
        val agreed = SnippetBackup.decodeState(written)
        if (isA) baseA = agreed else baseB = agreed
    }

    private fun SyncState.comparable() = copy(snippets = snippets.map { it.copy(lastUsedAt = 0) }.sortedBy { it.uid })

    @Test
    fun `two phones converge through the file`() = runBlocking {
        val work = a.createCategory("Work", now = 1)
        val hello = a.upsert(SnippetRecord(title = "Hi", text = "hello", categoryId = work), now = 2)
        a.setTags(hello, listOf("otp"), now = 3)
        val bye = a.upsert(SnippetRecord(title = "Bye", text = "goodbye"), now = 4)
        b.upsert(SnippetRecord(title = "Mine", text = "only on B"), now = 5)

        sync(a, isA = true)
        sync(b, isA = false)
        assertEquals(setOf("hello", "goodbye", "only on B"), b.syncState().snippets.map { it.text }.toSet())
        // The keyboard and the share sheet read the same flows as the app.
        val shown = b.observeSnippets().first().single { it.text == "hello" }
        assertEquals(listOf("otp"), shown.tags)
        assertTrue(shown.categoryId != null)

        // B edits and deletes for good; A renames the folder.
        val helloOnB = b.observeSnippets().first().single { it.text == "hello" }
        b.upsert(helloOnB.copy(text = "hello from B"), now = 10)
        val byeOnB = b.observeSnippets().first().single { it.text == "goodbye" }
        b.delete(byeOnB.id, now = 11)
        b.emptyBin()
        a.renameCategory(work, "Job", now = 12)

        sync(b, isA = false)
        sync(a, isA = true)
        sync(b, isA = false)

        assertEquals(a.syncState().comparable(), b.syncState().comparable())
        assertEquals(setOf("hello from B", "only on B"), a.syncState().snippets.map { it.text }.toSet())
        assertEquals(listOf("Job"), b.getCategories().map { it.name })
        assertTrue(a.observeSnippets().first().none { it.id == bye })

        // Merging the same file again changes nothing in the database.
        val before = a.syncState()
        val again = SyncMerge.merge(baseA, before, SnippetBackup.decodeState(file!!), NOW)
        a.applySync(again) { true }
        assertEquals(before.comparable(), a.syncState().comparable())
    }

    @Test
    fun `the same snippet saved on both phones before syncing stays one`() = runBlocking {
        a.upsert(SnippetRecord(text = "otp 1234"), now = 1)
        b.upsert(SnippetRecord(text = "otp 1234"), now = 2)
        sync(a, isA = true)
        sync(b, isA = false)
        sync(a, isA = true)
        assertEquals(1, a.syncState().snippets.size)
        assertEquals(a.syncState().snippets.single().uid, b.syncState().snippets.single().uid)
    }

    private companion object {
        const val NOW = 1_000_000L
    }
}
