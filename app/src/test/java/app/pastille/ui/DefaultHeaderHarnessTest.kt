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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #8, characterised on Material 3's own default: the same layout as the snippet list
 * (collapsing large bar, a 48dp chip row, a padded list), with `exitUntilCollapsedScrollBehavior()`
 * untouched. It shows why the screen wraps the behaviour (see `rememberListDrivenScrollBehavior`):
 * the bar itself is draggable. If a Material update stops that, the wrapper can go.
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
}
