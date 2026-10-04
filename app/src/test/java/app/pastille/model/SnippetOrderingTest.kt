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

    @Test
    fun manualPositionWinsOverRecentUse() {
        val first = SnippetRecord(id = 1, text = "a", position = 0, lastUsedAt = 1)
        val second = SnippetRecord(id = 2, text = "b", position = 1, lastUsedAt = 999)
        assertEquals(listOf(first, second), sortSnippets(listOf(second, first)))
    }

    @Test
    fun aDragReusesTheShownPositionsOnly() {
        val shown = listOf(
            SnippetRecord(id = 1, text = "a", position = 4),
            SnippetRecord(id = 2, text = "b", position = 7),
            SnippetRecord(id = 3, text = "c", position = 9),
        )
        assertEquals(mapOf(3L to 4, 1L to 7, 2L to 9), reassignPositions(shown, listOf(3, 1, 2)))
    }

    @Test
    fun tiedPositionsAreSpreadSoTheDragSticks() {
        val shown = listOf(
            SnippetRecord(id = 1, text = "a", position = 0),
            SnippetRecord(id = 2, text = "b", position = 0),
        )
        assertEquals(mapOf(2L to 0, 1L to 1), reassignPositions(shown, listOf(2, 1)))
    }

    @Test
    fun pinnedStayAheadAfterADrag() {
        val shown = listOf(
            SnippetRecord(id = 1, text = "pinned", pinned = true),
            SnippetRecord(id = 2, text = "loose"),
        )
        assertEquals(listOf(1L, 2L), keepPinnedFirst(shown, listOf(2, 1)))
    }
}
