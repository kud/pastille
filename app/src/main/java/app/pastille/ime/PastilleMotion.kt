package app.pastille.ime

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

import android.animation.ValueAnimator
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

object PastilleMotion {
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    // In-place state changes: colour, selection, swipe feedback.
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    const val SHORT_MS = 150
    const val ENTER_MS = 220
    const val EXIT_MS = 120

    // A key or cell colouring under the finger.
    const val PRESS_MS = 60

    // The outgoing content fades first, so the two never overlap muddily.
    const val FADE_IN_DELAY_MS = 60

    // "Remove animations" sets the animator scale to 0; Compose's own clock already follows other scales.
    // A one-off read, for code outside composition; screens follow it live through rememberReducedMotion().
    fun reduceMotion(): Boolean = !ValueAnimator.areAnimatorsEnabled()

    // ---- The app's own screens (design spec §20). The keyboard panel keeps the tweens above. ----

    /**
     * Critically damped, so nothing overshoots: with a natural frequency of sqrt(800) ≈ 28/s a spring
     * settles to 1% of its travel in about 6.6/28 ≈ 235ms, inside the 250ms ceiling, whatever the distance.
     */
    const val SCREEN_STIFFNESS = 800f

    fun <T> screenSpring(visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = SCREEN_STIFFNESS, visibilityThreshold = visibilityThreshold)

    // Reduced motion: opacity only, the outgoing side first, done in 210ms.
    private fun reducedSwap(): ContentTransform =
        fadeIn(tween(SHORT_MS, delayMillis = FADE_IN_DELAY_MS)) togetherWith fadeOut(tween(EXIT_MS))

    /**
     * Moving between screens of different depth (list → editor, Settings → Style): a short shared-axis
     * slide, a tenth of the width, in the direction of travel, with the outgoing screen fading out first.
     */
    fun screenTransition(forward: Boolean, reduceMotion: Boolean): ContentTransform {
        if (reduceMotion) return reducedSwap()
        val direction = if (forward) 1 else -1
        val offset = screenSpring(IntOffset.VisibilityThreshold)
        return (slideInHorizontally(offset) { width -> direction * width / 10 } + fadeIn(screenSpring())) togetherWith
            (slideOutHorizontally(offset) { width -> -direction * width / 10 } + fadeOut(tween(EXIT_MS)))
    }

    /** Swapping peers in place (Snippets | Stickers): a fade-through with a slight settle, no travel. */
    fun peerTransition(reduceMotion: Boolean): ContentTransform {
        if (reduceMotion) return reducedSwap()
        return (fadeIn(screenSpring()) + scaleIn(screenSpring(), initialScale = 0.96f)) togetherWith fadeOut(tween(EXIT_MS))
    }

    /** A row of controls that comes and goes with a mode (the folder chips). */
    fun rowEnter(reduceMotion: Boolean): EnterTransition =
        if (reduceMotion) fadeIn(tween(SHORT_MS)) else expandVertically(screenSpring(IntSize.VisibilityThreshold)) + fadeIn(screenSpring())

    fun rowExit(reduceMotion: Boolean): ExitTransition =
        if (reduceMotion) fadeOut(tween(EXIT_MS)) else shrinkVertically(screenSpring(IntSize.VisibilityThreshold)) + fadeOut(tween(EXIT_MS))

    /** A glyph that marks a selection (a chip's check or folder): it grows in beside the label. */
    fun glyphEnter(reduceMotion: Boolean): EnterTransition =
        if (reduceMotion) fadeIn(tween(SHORT_MS)) else expandHorizontally(screenSpring(IntSize.VisibilityThreshold)) + fadeIn(screenSpring())

    fun glyphExit(reduceMotion: Boolean): ExitTransition =
        if (reduceMotion) fadeOut(tween(EXIT_MS)) else shrinkHorizontally(screenSpring(IntSize.VisibilityThreshold)) + fadeOut(tween(EXIT_MS))
}

val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * True while the system's "Remove animations" is on, followed live through a [ContentObserver] on
 * the animator scale (tutti's ReducedMotion.kt): flipping it in Settings while Pastille is open
 * takes effect on the next frame, without reopening the app.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = read()
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        reduced = read()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}
