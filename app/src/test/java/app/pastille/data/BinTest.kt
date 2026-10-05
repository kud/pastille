package app.pastille.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.pastille.backup.SnippetBackup
import app.pastille.model.SnippetRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BinTest {

    private lateinit var db: PastilleDatabase
    private lateinit var repository: SnippetRepository
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

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

    private suspend fun liveIds() = repository.observeSnippets().first().map { it.id }
    private suspend fun binIds() = repository.observeBin().first().map { it.id }

    @Test
    fun `delete moves a snippet to the bin, out of every live query`() = runBlocking {
        val folder = repository.createCategory("Personal")
        val id = repository.upsert(SnippetRecord(title = "Wi-Fi", text = "example-passphrase", categoryId = folder))

        repository.delete(id, now)

        assertEquals(emptyList<Long>(), liveIds())
        assertEquals(listOf(id), binIds())
        assertNull(repository.findTextDuplicate("example-passphrase"))
        assertTrue(SnippetBackup.decode(repository.exportJson().json).snippets.isEmpty())
        val binned = repository.get(id)!!
        assertEquals(now, binned.deletedAt)
        assertEquals("a binned snippet keeps its folder", folder, binned.categoryId)
    }

    @Test
    fun `undo after a delete takes the snippet out of the bin`() = runBlocking {
        val id = repository.upsert(SnippetRecord(title = "Sign-off", text = "Best wishes"))
        val before = repository.get(id)!!
        repository.delete(id, now)

        repository.restore(before)

        assertEquals(listOf(id), liveIds())
        assertEquals(emptyList<Long>(), binIds())
    }

    @Test
    fun `restore from the bin keeps the folder, or falls back to the top level`() = runBlocking {
        val kept = repository.createCategory("Kept")
        val gone = repository.createCategory("Gone")
        val a = repository.upsert(SnippetRecord(title = "A", text = "alpha", categoryId = kept))
        val b = repository.upsert(SnippetRecord(title = "B", text = "beta", categoryId = gone))
        repository.delete(a, now)
        repository.delete(b, now)
        repository.deleteCategory(gone)

        repository.restoreFromBin(a)
        repository.restoreFromBin(b)

        assertEquals(kept, repository.get(a)!!.categoryId)
        assertNull(repository.get(b)!!.categoryId)
        assertEquals(setOf(a, b), liveIds().toSet())
    }

    @Test
    fun `delete forever only touches binned snippets`() = runBlocking {
        val live = repository.upsert(SnippetRecord(title = "Live", text = "still here"))
        val binned = repository.upsert(SnippetRecord(title = "Binned", text = "on its way"))
        repository.delete(binned, now)

        repository.deleteForever(live)
        repository.deleteForever(binned)

        assertNotNull(repository.get(live))
        assertNull(repository.get(binned))
    }

    @Test
    fun `empty bin deletes every binned snippet and nothing else`() = runBlocking {
        val live = repository.upsert(SnippetRecord(title = "Live", text = "one"))
        val first = repository.upsert(SnippetRecord(title = "First", text = "two"))
        val second = repository.upsert(SnippetRecord(title = "Second", text = "three"))
        repository.delete(first, now)
        repository.delete(second, now)

        repository.emptyBin()

        assertEquals(listOf(live), liveIds())
        assertEquals(emptyList<Long>(), binIds())
    }

    @Test
    fun `the purge removes only what has been in the bin for more than 30 days`() = runBlocking {
        val old = repository.upsert(SnippetRecord(title = "Old", text = "old"))
        val recent = repository.upsert(SnippetRecord(title = "Recent", text = "recent"))
        repository.delete(old, now - 31 * day)
        repository.delete(recent, now - 29 * day)

        repository.purgeExpiredBin(now)

        assertNull(repository.get(old))
        assertEquals(listOf(recent), binIds())
    }

    @Test
    fun `at start the purge runs before the sweep, so binned files survive and purged ones go`() = runBlocking {
        val expired = repository.insertImage("Expired", "expired.png", 10, 10)
        val binned = repository.insertImage("Binned", "binned.png", 10, 10)
        repository.insertImage("Live", "live.png", 10, 10)
        repository.delete(expired, now - 31 * day)
        repository.delete(binned, now - 1 * day)
        val calls = mutableListOf<String>()
        var swept: Set<String>? = null

        cleanUpAtStart(
            purgeExpiredBin = {
                calls += "purge"
                repository.purgeExpiredBin(now)
            },
            referencedImageFiles = {
                calls += "referenced"
                repository.referencedImageFiles()
            },
            sweepOrphans = {
                calls += "sweep"
                swept = it
            },
        )

        assertEquals(listOf("purge", "referenced", "sweep"), calls)
        assertEquals(setOf("binned.png", "live.png"), swept)
    }
}
