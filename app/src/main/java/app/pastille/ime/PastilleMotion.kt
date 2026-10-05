package app.pastille.ime

import android.animation.ValueAnimator
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.staticCompositionLocalOf

object PastilleMotion {
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    // In-place state changes: colour, selection, swipe feedback.
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    const val SHORT_MS = 150
    const val ENTER_MS = 220
    const val EXIT_MS = 120

    // The outgoing content fades first, so the two never overlap muddily.
    const val FADE_IN_DELAY_MS = 60

    // "Remove animations" sets the animator scale to 0; Compose's own clock already follows other scales.
    fun reduceMotion(): Boolean = !ValueAnimator.areAnimatorsEnabled()
}

val LocalReduceMotion = staticCompositionLocalOf { false }
