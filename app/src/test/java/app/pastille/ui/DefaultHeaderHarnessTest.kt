package app.pastille.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #8, characterised on Material 3's own default: the same layout as the snippet list
 * (collapsing large bar, a 48dp chip row, a padded list), with `exitUntilCollapsedScrollBehavior()`
 * untouched. It shows why the screen wraps the behaviour (see `rememberListDrivenScrollBehavior`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h800dp-mdpi")
class DefaultHeaderHarnessTest {

    @get:Rule
    val compose = createComposeRule()

    @Composable
    private fun Harness(behaviour: TopAppBarScrollBehavior) {
        MaterialTheme {
            Scaffold(
                modifier = Modifier.nestedScroll(behaviour.nestedScrollConnection),
                topBar = {
                    LargeTopAppBar(
                        title = { Text("Pastille") },
                        modifier = Modifier.testTag("bar"),
                        scrollBehavior = behaviour,
                    )
                },
            ) { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    LazyRow(modifier = Modifier.fillMaxWidth().height(48.dp)) { item { Text("All") } }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag("list"),
                        contentPadding = PaddingValues(bottom = 88.dp),
                    ) {
                        item(key = "onboarding") { Column {} }
                        items((0 until 40).toList(), key = { it }) { index ->
                            Column(modifier = Modifier.animateItem().fillMaxWidth().height(72.dp).semantics(mergeDescendants = true) {}) {
                                Text("Row %02d".format(39 - index))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    @Test
    fun `with the default behaviour, dragging the empty header moves it`() {
        compose.setContent { Harness(TopAppBarDefaults.exitUntilCollapsedScrollBehavior()) }
        val before = bounds("bar")

        compose.onNodeWithTag("bar").performTouchInput { swipeUp(startY = bottom - 4f, endY = top + 4f) }
        compose.waitForIdle()

        assertNotEquals(before.height, bounds("bar").height)
    }

    private val listGestures: List<Pair<String, () -> Unit>> = listOf(
        "slow swipe up" to { compose.onNodeWithTag("list").performTouchInput { swipeUp(durationMillis = 800) } },
        "fling up" to { compose.onNodeWithTag("list").performTouchInput { swipeUp(durationMillis = 60) } },
        "short drag up" to { compose.onNodeWithTag("list").performTouchInput { swipeUp(startY = centerY, endY = centerY - 60f) } },
        "fling down" to { compose.onNodeWithTag("list").performTouchInput { swipeDown(durationMillis = 60) } },
    )

    private val headerGestures: List<Pair<String, () -> Unit>> = listOf(
        "header drag up" to { compose.onNodeWithTag("bar").performTouchInput { swipeUp(startY = bottom - 4f, endY = top + 4f) } },
        "header drag down" to { compose.onNodeWithTag("bar").performTouchInput { swipeDown(startY = top + 4f, endY = bottom - 4f) } },
    )

    @Test
    fun `default behaviour, list gestures only`() {
        compose.setContent { Harness(TopAppBarDefaults.exitUntilCollapsedScrollBehavior()) }
        assertNoVoid(listGestures)
    }

    @Test
    fun `default behaviour without snap, list gestures only`() {
        compose.setContent { Harness(TopAppBarDefaults.exitUntilCollapsedScrollBehavior(snapAnimationSpec = null)) }
        assertNoVoid(listGestures)
    }

    @Test
    fun `default behaviour, list and header gestures`() {
        compose.setContent { Harness(TopAppBarDefaults.exitUntilCollapsedScrollBehavior()) }
        assertNoVoid(listGestures + headerGestures)
    }

    private fun assertNoVoid(gestures: List<Pair<String, () -> Unit>>) {
        val log = StringBuilder()
        repeat(4) { round ->
            gestures.forEach { (name, gesture) ->
                gesture()
                compose.waitForIdle()
                val list = bounds("list")
                val rows = compose.onAllNodesWithText("Row ", substring = true).fetchSemanticsNodes()
                    .map { it.boundsInRoot }.filter { it.height > 0f && it.overlaps(list) }.sortedBy { it.top }
                log.appendLine("round $round, $name: bar=${bounds("bar")} list=$list rows=${rows.size} last=${rows.lastOrNull()}")
                assertTrue("no row visible\n$log", rows.isNotEmpty())
                val atEnd = compose.onAllNodesWithText("Row 00").fetchSemanticsNodes().isNotEmpty()
                val gap = list.bottom - rows.last().bottom
                assertTrue("void of ${gap}px under the rows\n$log", if (atEnd) gap <= 89f else gap <= 1f)
            }
        }
    }
}
