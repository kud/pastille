package app.pastille.ui

import androidx.compose.material3.SwipeToDismissBoxValue
import kotlin.math.abs

// Deleting takes a deliberate swipe, the secondary action a shorter one.
const val DELETE_SWIPE_FRACTION = 0.4f
const val SECONDARY_SWIPE_FRACTION = 0.25f

fun swipeFraction(direction: SwipeToDismissBoxValue): Float = when (direction) {
    SwipeToDismissBoxValue.EndToStart -> DELETE_SWIPE_FRACTION
    SwipeToDismissBoxValue.StartToEnd -> SECONDARY_SWIPE_FRACTION
    SwipeToDismissBoxValue.Settled -> 1f
}

/**
 * Whether the row has travelled far enough for [direction]'s action. M3's fling threshold isn't
 * configurable, so this is also the gate in `confirmValueChange`: a fast flick that hasn't
 * covered the distance is refused, and the row snaps back.
 */
fun swipePastThreshold(direction: SwipeToDismissBoxValue, offset: Float, width: Float): Boolean {
    if (direction == SwipeToDismissBoxValue.Settled || width <= 0f || offset.isNaN()) return false
    return abs(offset) >= width * swipeFraction(direction)
}
