package app.pastille.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * pastille's one haptic vocabulary (Iris, 2026-10-28, after tutti's Feedback). Call sites name what
 * happened, never a constant: [select] for a toggle, chip or segment; [confirm] when something went
 * where it was sent (copied, inserted, undone); [bump] for a delete or a refusal's wall; [pickUp] and
 * [drop] at the two ends of a drag. Everything goes through the View, so Android's touch-vibration
 * switch and intensity apply, and nothing here vibrates on its own.
 */
@Stable
class Haptics internal constructor(private val view: View) {
    fun select() = play(HapticFeedbackConstants.VIRTUAL_KEY)

    fun confirm() = play(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.VIRTUAL_KEY,
    )

    fun bump() = play(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
        else HapticFeedbackConstants.CONTEXT_CLICK,
    )

    fun pickUp() = play(HapticFeedbackConstants.LONG_PRESS)

    fun drop() = play(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.GESTURE_END
        else HapticFeedbackConstants.VIRTUAL_KEY_RELEASE,
    )

    /** A drag crossing a neighbour: the lightest tick there is. */
    fun step() = play(HapticFeedbackConstants.CLOCK_TICK)

    private fun play(constant: Int) {
        view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
