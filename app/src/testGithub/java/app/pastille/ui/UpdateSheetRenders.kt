package app.pastille.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import app.pastille.redesign.writePng
import app.pastille.ui.theme.PastilleTheme
import app.pastille.update.AppVersion
import app.pastille.update.ReleaseAsset
import app.pastille.update.Update
import app.pastille.update.UpdateProblem
import app.pastille.update.UpdateState
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The update sheet's inside in each state, stacked, each on the sheet's surface, shape and handle.
 * Not the real popup: Robolectric doesn't draw ModalBottomSheet's window, so no scrim or list behind.
 * Runs only with PASTILLE_RENDERS=<dir>.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h2900dp-xhdpi")
class UpdateSheetRenders {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun render() {
        val dir = System.getenv("PASTILLE_RENDERS")
        assumeTrue(dir != null)
        val update = Update(
            AppVersion.parse("0.1.6-dev.10")!!,
            "v0.1.6-dev.10",
            ReleaseAsset("pastille-0.1.6-dev.10.apk", "https://github.com/kud/pastille/releases/download/x", null, 1),
            ByteArray(32),
        )
        val states = listOf(
            UpdateState.Checking,
            UpdateState.Downloading(update, 42),
            UpdateState.Downloading(update, null),
            UpdateState.NeedsPermission(update),
            UpdateState.Installing(update),
            UpdateState.Available(update),
            UpdateState.UpToDate,
            UpdateState.Failed(UpdateProblem.NETWORK, null),
            UpdateState.Failed(UpdateProblem.DIGEST, update),
        )
        val dark = mutableStateOf(false)
        compose.setContent {
            PastilleTheme(darkTheme = dark.value) {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceDim)) {
                    states.forEach { state ->
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = BottomSheetDefaults.ExpandedShape,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                BottomSheetDefaults.DragHandle()
                                UpdateSheetContent(
                                    installed = "0.1.6-dev.9",
                                    state = state,
                                    canUpdate = true,
                                    onInstall = {},
                                    onLater = {},
                                    onAllow = {},
                                    onClose = {},
                                )
                            }
                        }
                    }
                }
            }
        }
        for (isDark in listOf(false, true)) {
            dark.value = isDark
            compose.waitForIdle()
            val bitmap = compose.runOnUiThread {
                val v = compose.activity.window.decorView
                Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
            }
            writePng(bitmap, File(dir, "update-sheet-${if (isDark) "dark" else "light"}.png"))
        }
    }
}
