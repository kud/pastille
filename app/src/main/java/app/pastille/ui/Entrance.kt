package app.pastille.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.pastille.ime.LocalReduceMotion
import app.pastille.ime.PastilleMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * The first screen settling in as the splash leaves (Iris, 2026-10-28): the header, then the mode
 * switch, the folder chips, the tags and the first cards, each rising [RISE] and fading in, a
 * [STAGGER] apart, all done within about 300 ms. Opacity and a layer offset only, so nothing is
 * laid out twice and nothing jumps. Reduced motion: one opacity fade for everything, no travel.
 *
 * Played once per launch of the list. A recreated activity (rotation, dark mode) or a launch
 * straight into the editor or Settings is not [armed], and every [settleIn] is then a no-op.
 */
@Stable
class Entrance(val armed: Boolean) {
    /** Set when the splash starts to leave; until then the list is drawn at opacity 0 under it. */
    var started by mutableStateOf(!armed)
        private set

    /** The list has its first data, so the splash may leave without showing an empty frame. */
    var listReady by mutableStateOf(false)

    private var startedAt = 0L
    private val createdAt = SystemClock.uptimeMillis()

    fun start() {
        if (started) return
        startedAt = SystemClock.uptimeMillis()
        started = true
    }

    /** Time since the activity was created, for the splash's keep-on-screen condition. */
    fun ageMs(): Long = SystemClock.uptimeMillis() - createdAt

    /** Anything composed this long after the start (scrolled to, added) arrives as usual. */
    internal fun over(): Boolean = started && SystemClock.uptimeMillis() - startedAt > WINDOW_MS

    companion object {
        /** Slot order: header, mode switch, folder chips, tag row, then cards from [CARDS]. */
        const val HEADER = 0
        const val MODES = 1
        const val FOLDERS = 2
        const val TAGS = 3
        const val CARDS = 4

        /** Past this slot everything arrives together with it, so the whole settle stays short. */
        const val LAST = 6
        const val STAGGER = 25
        const val SETTLE_MS = 200
        const val WINDOW_MS = 600L
        val RISE = 12.dp
    }
}

val LocalEntrance = staticCompositionLocalOf<Entrance?> { null }

/** Settles this element in at [order] in the launch entrance; no-op outside one. */
fun Modifier.settleIn(order: Int): Modifier = composed {
    val entrance = LocalEntrance.current
    if (entrance == null || !entrance.armed || entrance.over()) return@composed Modifier
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(entrance) {
        snapshotFlow { entrance.started }.first { it }
        if (reduce) {
            progress.animateTo(1f, tween(PastilleMotion.SHORT_MS))
        } else {
            delay(order.coerceIn(0, Entrance.LAST) * Entrance.STAGGER.toLong())
            progress.animateTo(1f, tween(Entrance.SETTLE_MS, easing = PastilleMotion.EmphasizedDecelerate))
        }
    }
    val rise = with(LocalDensity.current) { Entrance.RISE.toPx() }
    Modifier.graphicsLayer {
        alpha = progress.value
        translationY = if (reduce) 0f else (1f - progress.value) * rise
    }
}
