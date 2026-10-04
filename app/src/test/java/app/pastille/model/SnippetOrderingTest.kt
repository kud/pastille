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

}
