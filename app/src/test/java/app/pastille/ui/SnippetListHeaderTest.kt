package app.pastille.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.pastille.data.PastilleDatabase
import app.pastille.data.SnippetRepository
import app.pastille.model.SnippetRecord
import app.pastille.ui.theme.PastilleTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Issue #8: the large header moves only when the list scrolls, and the list never shows a void. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h800dp-mdpi")
class SnippetListHeaderTest {

    @get:Rule
    val compose = createComposeRule()

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

    private fun showList(rows: Int) {
        runBlocking {
            repeat(rows) { repository.upsert(SnippetRecord(title = "Row %02d".format(it), text = "Body of row $it")) }
        }
        compose.setContent {
            PastilleTheme(dynamicColor = false) {
                SnippetListScreen(repository = repository, onCreate = {}, onEdit = {}, onOpenSettings = {})
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) { visibleRows().isNotEmpty() }
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun visibleRows(): List<Rect> {
        val list = runCatching { bounds(SNIPPET_LIST_TAG) }.getOrNull() ?: return emptyList()
        return compose.onAllNodesWithText("Row ", substring = true).fetchSemanticsNodes()
            .map { it.boundsInRoot }
            .filter { it.height > 0f && it.overlaps(list) }
            .sortedBy { it.top }
    }

    private fun snapshot(step: String): String {
        val rows = visibleRows()
        return "$step: bar=${bounds(TOP_BAR_TAG)} list=${bounds(SNIPPET_LIST_TAG)} rows=${rows.size} " +
            "first=${rows.firstOrNull()} last=${rows.lastOrNull()}"
    }

    @Test
    fun `dragging the header does not move it`() {
        showList(rows = 40)
        val before = bounds(TOP_BAR_TAG)

        compose.onNodeWithTag(TOP_BAR_TAG).performTouchInput { swipeUp(startY = bottom - 4f, endY = top + 4f) }
        compose.waitForIdle()
        assertEquals("after an upward drag", before, bounds(TOP_BAR_TAG))

        compose.onNodeWithTag(TOP_BAR_TAG).performTouchInput { swipeDown(startY = top + 4f, endY = bottom - 4f) }
        compose.waitForIdle()
        assertEquals("after a downward drag", before, bounds(TOP_BAR_TAG))
    }

    @Test
    fun `a slightly wobbly tap on the wordmark does not move the header`() {
        showList(rows = 40)
        val before = bounds(TOP_BAR_TAG)

        compose.onNodeWithTag(WORDMARK_TAG).performTouchInput {
            down(center)
            moveBy(Offset(0f, -24f))
            up()
        }
        compose.waitForIdle()

        assertEquals(before, bounds(TOP_BAR_TAG))
    }

    @Test
    fun `scrolling the list still collapses and expands the header`() {
        showList(rows = 40)
        val expanded = bounds(TOP_BAR_TAG).height

        compose.onNodeWithTag(SNIPPET_LIST_TAG).performTouchInput { swipeUp() }
        compose.waitForIdle()
        val collapsed = bounds(TOP_BAR_TAG).height
        assertTrue("collapsed $collapsed < expanded $expanded", collapsed < expanded)

        repeat(6) { compose.onNodeWithTag(SNIPPET_LIST_TAG).performTouchInput { swipeDown() } }
        compose.waitForIdle()
        assertEquals(expanded, bounds(TOP_BAR_TAG).height)
    }

    @Test
    fun `the list never scrolls into empty space`() {
        showList(rows = 40)
        val log = StringBuilder()
        val gestures = listOf<Pair<String, () -> Unit>>(
            "slow swipe up" to { compose.onNodeWithTag(SNIPPET_LIST_TAG).performTouchInput { swipeUp(durationMillis = 800) } },
            "fling up" to { compose.onNodeWithTag(SNIPPET_LIST_TAG).performTouchInput { swipeUp(durationMillis = 60) } },
            "short drag up" to {
                compose.onNodeWithTag(SNIPPET_LIST_TAG).performTouchInput { swipeUp(startY = centerY, endY = centerY - 60f) }
            },
            "header drag up" to {
                compose.onNodeWithTag(TOP_BAR_TAG).performTouchInput { swipeUp(startY = bottom - 4f, endY = top + 4f) }
            },
            "fling down" to { compose.onNodeWithTag(SNIPPET_LIST_TAG).performTouchInput { swipeDown(durationMillis = 60) } },
        )
        repeat(4) { round ->
            gestures.forEach { (name, gesture) ->
                gesture()
                compose.waitForIdle()
                val step = "round $round, $name"
                log.appendLine(snapshot(step))
                val list = bounds(SNIPPET_LIST_TAG)
                val rows = visibleRows()
                assertTrue("no row visible\n$log", rows.isNotEmpty())
                // The list ends with 88dp of padding for the FAB, and no more.
                val gapBelow = list.bottom - rows.last().bottom
                val atEnd = compose.onAllNodesWithText("Row 00", substring = true).fetchSemanticsNodes().isNotEmpty()
                assertTrue("void of ${gapBelow}px under the rows\n$log", gapBelow <= 88f + 1f || !atEnd && gapBelow <= 1f)
                assertTrue("void above the rows\n$log", rows.first().top <= list.top + 1f || rows.first().top < list.top + 400f)
            }
        }
    }
}
