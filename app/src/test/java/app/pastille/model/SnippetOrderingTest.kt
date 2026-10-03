package app.pastille.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SnippetOrderingTest {

    @Test
    fun pinnedComeFirstThenMostRecentlyUsed() {
        val old = SnippetRecord(id = 1, text = "old", pinned = false, lastUsedAt = 100)
        val fresh = SnippetRecord(id = 2, text = "fresh", pinned = false, lastUsedAt = 300)
        val pinnedStale = SnippetRecord(id = 3, text = "pinned stale", pinned = true, lastUsedAt = 50)
        val pinnedFresh = SnippetRecord(id = 4, text = "pinned fresh", pinned = true, lastUsedAt = 200)

        val sorted = sortSnippets(listOf(old, fresh, pinnedStale, pinnedFresh))

        assertEquals(listOf(pinnedFresh, pinnedStale, fresh, old), sorted)
    }

    @Test
    fun neverUsedSortsLastAmongUnpinned() {
        val never = SnippetRecord(id = 1, text = "never", pinned = false, lastUsedAt = 0)
        val used = SnippetRecord(id = 2, text = "used", pinned = false, lastUsedAt = 5)

        assertEquals(listOf(used, never), sortSnippets(listOf(never, used)))
    }

    @Test
    fun emptyListStaysEmpty() {
        assertEquals(emptyList<SnippetRecord>(), sortSnippets(emptyList()))
    }
}
