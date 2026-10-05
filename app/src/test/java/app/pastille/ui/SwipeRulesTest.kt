package app.pastille.ui

import androidx.compose.material3.SwipeToDismissBoxValue.EndToStart
import androidx.compose.material3.SwipeToDismissBoxValue.Settled
import androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeRulesTest {

    private val width = 1000f

    @Test
    fun `delete needs forty percent of the row`() {
        assertFalse(swipePastThreshold(EndToStart, -399f, width))
        assertTrue(swipePastThreshold(EndToStart, -400f, width))
        assertTrue(swipePastThreshold(EndToStart, -1000f, width))
    }

    @Test
    fun `a short fast flick never deletes`() {
        // Released at 10% of the width: whatever the velocity, the gate refuses it.
        assertFalse(swipePastThreshold(EndToStart, -100f, width))
    }

    @Test
    fun `the secondary action needs a quarter of the row`() {
        assertFalse(swipePastThreshold(StartToEnd, 249f, width))
        assertTrue(swipePastThreshold(StartToEnd, 250f, width))
    }

    @Test
    fun `nothing commits before the row has been measured or while settled`() {
        assertFalse(swipePastThreshold(EndToStart, -500f, 0f))
        assertFalse(swipePastThreshold(Settled, 0f, width))
        assertFalse(swipePastThreshold(EndToStart, Float.NaN, width))
    }
}
