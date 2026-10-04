package app.pastille.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SnippetOrderingTest {

    @Test
    fun equalPositionsFallBackToMostRecentlyUsed() {
        val never = SnippetRecord(id = 1, text = "never", lastUsedAt = 0)
        val used = SnippetRecord(id = 2, text = "used", lastUsedAt = 5)

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
    fun aSecondDragBeforeTheListRefreshesUsesStoredPositions() {
        // The screen still shows A=0, B=1, C=2, but the first drag already stored B=0, A=1.
        val shown = listOf(
            SnippetRecord(id = 1, text = "a", position = 0),
            SnippetRecord(id = 2, text = "b", position = 1),
            SnippetRecord(id = 3, text = "c", position = 2),
        )
        val stored = mapOf(1L to 1, 2L to 0, 3L to 2)

        val writes = positionWrites(shown, stored, listOf(3, 2, 1))

        val result = stored + writes
        assertEquals(listOf(3L, 2L, 1L), result.entries.sortedBy { it.value }.map { it.key })
        assertEquals(3, result.values.toSet().size)
    }

    @Test
    fun unchangedPositionsAreNotRewritten() {
        val shown = listOf(
            SnippetRecord(id = 1, text = "a", position = 0),
            SnippetRecord(id = 2, text = "b", position = 1),
        )
        assertEquals(emptyMap<Long, Int>(), positionWrites(shown, mapOf(1L to 0, 2L to 1), listOf(1, 2)))
    }

}
