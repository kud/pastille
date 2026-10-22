package app.pastille.ui

import android.app.Application
import android.content.Context
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Issue #8: the large header moves only when the list scrolls. And the list never shows a void,
 * checked on every frame of a gesture rather than once it has settled: the v0.1.2 bug emptied the
 * list mid-fling and put it back by the time anything was idle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h800dp-mdpi")
class SnippetListHeaderTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

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

    /** The keyboard enabled and photos allowed, as on a set-up phone: the onboarding card has nothing to show. */
    private fun markSetUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(photoPermission())
        val pastille = InputMethodInfo(app.packageName, "app.pastille.ime.PastilleIme", "Pastille", null)
        listOf<Context>(app, compose.activity).forEach { context ->
            shadowOf(context.getSystemService(InputMethodManager::class.java))
                .setEnabledInputMethodInfoList(listOf(pastille))
        }
    }

    private fun showList(rows: Int, setUp: Boolean = true) {
        if (setUp) markSetUp()
        runBlocking {
            repeat(rows) { repository.upsert(SnippetRecord(title = "Row %02d".format(it), text = "Body of row $it")) }
        }
        compose.setContent {
            PastilleTheme(dynamicColor = false) {
                SnippetListScreen(repository = repository, onCreate = {}, onEdit = {}, onOpenSettings = {})
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) { visibleRows().isNotEmpty() }
        // Probe: the set-up state really is the one under test, or the void checks below prove nothing.
        assertEquals(
            "onboarding card shown",
            !setUp,
            compose.onAllNodesWithText("Set up Pastille").fetchSemanticsNodes().isNotEmpty(),
        )
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    /** Whole rows (not their titles), so a gap above the first one is a real void. */
    private fun visibleRows(): List<Rect> {
        val list = runCatching { bounds(SNIPPET_LIST_TAG) }.getOrNull() ?: return emptyList()
        return compose.onAllNodesWithTag(SNIPPET_ROW_TAG).fetchSemanticsNodes()
            .map { it.boundsInRoot }
            .filter { it.height > 0f && it.overlaps(list) }
            .sortedBy { it.top }
    }

    // Newest first: the last row added heads the list and "Row 00" ends it.
    private fun atTop(rows: Int): Boolean =
        compose.onAllNodesWithText("Row %02d".format(rows - 1)).fetchSemanticsNodes().isNotEmpty()

    private fun atEnd(): Boolean = compose.onAllNodesWithText("Row 00").fetchSemanticsNodes().isNotEmpty()

    /** Fails on any void: no row at all, space above the first row, or more than the FAB's 88dp under the last. */
    private fun assertNoVoid(step: String, rows: Int, log: StringBuilder) {
        val list = bounds(SNIPPET_LIST_TAG)
        val visible = visibleRows()
        log.appendLine(
            "$step: bar=${bounds(TOP_BAR_TAG)} list=$list rows=${visible.size} " +
                "first=${visible.firstOrNull()} last=${visible.lastOrNull()}",
        )
        assertTrue("no row visible\n$log", visible.isNotEmpty())
        assertTrue("void of ${visible.first().top - list.top}px above the rows\n$log", visible.first().top <= list.top + 1f)
        val gapBelow = list.bottom - visible.last().bottom
        assertTrue("void of ${gapBelow}px under the rows\n$log", gapBelow <= 1f || atEnd() && gapBelow <= 88f + 1f)
    }

    /**
     * A finger drag driven one frame at a time, then the fling it leaves, checking for a void on
     * every frame. `step` is the move per 16ms frame, so 40px is a quick flick.
     */
    private fun dragFrameByFrame(name: String, rows: Int, step: Float, moves: Int, log: StringBuilder) {
        compose.mainClock.autoAdvance = false
        try {
            val list = compose.onNodeWithTag(SNIPPET_LIST_TAG)
            val start = if (step > 0f) 0.2f else 0.8f
            list.performTouchInput { down(Offset(centerX, top + height * start)) }
            compose.mainClock.advanceTimeByFrame()
            repeat(moves) { frame ->
                list.performTouchInput { moveBy(Offset(0f, step)) }
                compose.mainClock.advanceTimeByFrame()
                assertNoVoid("$name, move $frame", rows, log)
            }
            list.performTouchInput { up() }
            repeat(120) { frame ->
                compose.mainClock.advanceTimeByFrame()
                assertNoVoid("$name, fling frame $frame", rows, log)
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.waitForIdle()
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
    fun `the onboarding card still shows until the keyboard is set up`() {
        showList(rows = 40, setUp = false)
    }

    /**
     * The v0.1.2 report: scrolling back up, the list went empty, then snapped back to its start.
     * Once set up, the onboarding item composed to nothing, and a zero-height first item left the
     * list claiming it could still scroll back at its top: it swallowed the downward drag, moved
     * nothing, and `animateItem()` pushed every row down by the swallowed distance.
     */
    @Test
    fun `scrolling back to the top never empties the list`() {
        val rows = 40
        showList(rows)
        val expanded = bounds(TOP_BAR_TAG).height
        val log = StringBuilder()

        dragFrameByFrame("down the list", rows, step = -40f, moves = 12, log = log)
        assertTrue("left the top\n$log", !atTop(rows))
        assertTrue("header collapsed\n$log", bounds(TOP_BAR_TAG).height < expanded)

        // Quick flicks back up, each one a finger's length, until the list is at its top again.
        var flicks = 0
        while (!atTop(rows) && flicks < 12) {
            dragFrameByFrame("back to the top, flick $flicks", rows, step = 40f, moves = 12, log = log)
            flicks++
        }
        assertTrue("back at the top\n$log", atTop(rows))

        // At the top, a further pull belongs to the header, all of it.
        dragFrameByFrame("pull at the top", rows, step = 12f, moves = 20, log = log)
        assertEquals("header fully open\n$log", expanded, bounds(TOP_BAR_TAG).height)
    }

    @Test
    fun `the list never scrolls into empty space`() {
        val rows = 40
        showList(rows)
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
            "fling down again" to { compose.onNodeWithTag(SNIPPET_LIST_TAG).performTouchInput { swipeDown(durationMillis = 60) } },
        )
        repeat(4) { round ->
            gestures.forEach { (name, gesture) ->
                gesture()
                compose.waitForIdle()
                assertNoVoid("round $round, $name", rows, log)
            }
        }
    }
}
