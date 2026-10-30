package app.pastille.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import app.pastille.redesign.writePng
import app.pastille.ui.theme.PastilleTheme
import app.pastille.update.UpdateProblem
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Updates row in every state, stacked, and the once-per-build message. Runs only with PASTILLE_RENDERS=<dir>. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// 360dp wide like the other renders, tall enough for every state.
@Config(qualifiers = "w360dp-h1700dp-xhdpi")
class UpdateRowRenders {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun render() {
        val dir = System.getenv("PASTILLE_RENDERS")
        assumeTrue(dir != null)
        val states = listOf(
            "Not checked yet" to UpdateSlot.Action("Check"),
            "Asking GitHub…" to UpdateSlot.Busy(null),
            "Up to date · checked just now" to UpdateSlot.Action("Check"),
            "Version 0.1.6-dev.10 is ready" to UpdateSlot.Reserved,
            "Downloading 0.1.6-dev.10 · 42%" to UpdateSlot.Busy(42),
            "Downloaded: allow installs to finish" to UpdateSlot.Action("Allow installs"),
            "Installing…" to UpdateSlot.Busy(null),
        ) + UpdateProblem.entries.map { failureText(it) to UpdateSlot.Action("Try again") }
        for (dark in listOf(false, true)) {
            compose.setContentOnce(dark) {
                Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                    states.forEach { (subtitle, slot) ->
                        UpdateRowContent(subtitle, slot, onAction = {}, onLater = {}.takeIf { slot == UpdateSlot.Reserved })
                        SettingsHairline()
                    }
                    Snackbar(
                        modifier = Modifier.padding(12.dp),
                        action = { TextButton(onClick = {}) { Text("Install") } },
                    ) { Text(announcement("0.1.6-dev.10")) }
                }
            }
            compose.waitForIdle()
            val bitmap = compose.runOnUiThread {
                val v = compose.activity.window.decorView
                Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
            }
            writePng(bitmap, File(dir, "updates-row-${if (dark) "dark" else "light"}.png"))
        }
    }

    private var content: ((Boolean) -> Unit)? = null
    private val dark = androidx.compose.runtime.mutableStateOf(false)

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, ComponentActivity>.setContentOnce(
        isDark: Boolean,
        body: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        dark.value = isDark
        if (content == null) {
            content = {}
            setContent { PastilleTheme(darkTheme = dark.value) { body() } }
        }
    }
}
