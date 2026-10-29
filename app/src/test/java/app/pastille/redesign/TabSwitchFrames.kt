package app.pastille.redesign

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import app.pastille.ime.KeyboardTheme
import app.pastille.ime.LocalKeyboardPalette
import app.pastille.ime.ModeSwitch
import app.pastille.ime.keyboardPalette
import app.pastille.settings.KeyboardMode
import app.pastille.settings.KeyboardStyle
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Frames of one tab switch in the keyboard toolbar (Snippets → Stickers, then Stickers → Clipboard) at
 * 411dp, the width where the selected tab shows its label, stacked top to bottom into one PNG.
 * Runs only with PASTILLE_RENDERS=<dir>.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h860dp-xxhdpi")
class TabSwitchFrames {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun frames() {
        val dir = System.getenv("PASTILLE_RENDERS")
        assumeTrue(dir != null)
        var mode by mutableStateOf(KeyboardMode.Snippets)
        compose.setContent {
            KeyboardTheme(palette = keyboardPalette(compose.activity, KeyboardStyle.Auto, true)) {
                Box(Modifier.fillMaxSize().background(LocalKeyboardPalette.current.tray)) {
                    Row(
                        Modifier.fillMaxWidth().height(48.dp).background(LocalKeyboardPalette.current.strip),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.weight(1f))
                        ModeSwitch(
                            mode = mode,
                            enabled = KeyboardMode.entries.toSet(),
                            onChange = { mode = it },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val strips = mutableListOf<Bitmap>()
        fun grab() {
            compose.waitForIdle()
            strips += compose.runOnUiThread {
                val v = compose.activity.window.decorView
                val full = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
                val top = findStripTop(full)
                Bitmap.createBitmap(full, 0, top, full.width, (48 * v.resources.displayMetrics.density).toInt())
            }
        }
        grab()
        for (target in listOf(KeyboardMode.Stickers, KeyboardMode.Clipboard)) {
            compose.runOnUiThread { mode = target }
            var t = 0L
            for (at in listOf(0L, 16L, 32L, 48L, 80L, 120L, 160L, 240L, 400L)) {
                compose.mainClock.advanceTimeBy(at - t); t = at
                grab()
            }
        }
        val w = strips.first().width
        val gap = 6
        val out = Bitmap.createBitmap(w, strips.sumOf { it.height + gap }, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(0xFF808080.toInt())
        var y = 0f
        strips.forEach { canvas.drawBitmap(it, 0f, y, null); y += it.height + gap }
        writePng(out, File(dir, "tab-switch-frames.png"))
    }

    // The strip is drawn under the status bar; find the first row that isn't the tray colour.
    private fun findStripTop(bitmap: Bitmap): Int {
        val probe = bitmap.getPixel(2, bitmap.height - 2)
        for (y in 0 until bitmap.height) if (bitmap.getPixel(2, y) != probe) return y
        return 0
    }
}
