package app.pastille.screenshots

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import app.pastille.ime.KeyboardPanel
import app.pastille.ime.KeyboardTheme
import app.pastille.ime.KeyboardUiState
import app.pastille.ime.LocalReduceMotion
import app.pastille.ime.NoKeyboardActions
import app.pastille.ime.keyboardPalette
import app.pastille.model.SnippetRecord
import app.pastille.settings.KeyboardMode
import app.pastille.settings.KeyboardStyle
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F-Droid's listing art: the feature graphic and the icon, plus the README icon.
 * Its own class, so its own JVM (forkEvery counts classes): with the six phone screens in one JVM, the
 * extra native renders aborted the process (exit 134). The icon is drawn once, at 1024px, and reused.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1024dp-h500dp-mdpi")
class StoreListingArt {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun listingArt() {
        val icon = launcherIcon(app, 1024)
        writePng(icon, File(README_ICON_PATH))
        writePng(Bitmap.createScaledBitmap(icon, 512, 512, true), File(listingImagesDir, "icon.png"))
        featureGraphic(Bitmap.createScaledBitmap(icon, 112, 112, true))
    }

    private fun featureGraphic(icon: Bitmap) {
        // No folder row: the graphic is about the snippets, and four rows of them fit its height.
        val snippets = invented.mapIndexed { index, (title, text) ->
            SnippetRecord(id = index + 1L, title = title, text = text, position = index)
        }
        val iconImage = icon.asImageBitmap()
        compose.setContent {
            val context = LocalContext.current
            Row(
                modifier = Modifier.fillMaxSize().background(BRAND_INK).padding(start = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.width(380.dp)) {
                    Image(bitmap = iconImage, contentDescription = null, modifier = Modifier.size(112.dp))
                    Spacer(Modifier.height(20.dp))
                    Text("Pastille", color = BRAND_CREAM, fontSize = 56.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("A keyboard for pasting, not typing.", color = BRAND_CREAM.copy(alpha = 0.85f), fontSize = 22.sp)
                }
                Spacer(Modifier.width(32.dp))
                Box(
                    modifier = Modifier
                        .padding(top = 64.dp)
                        .fillMaxHeight()
                        .weight(1f)
                        .clip(RoundedCornerShape(topStart = 24.dp)),
                ) {
                    KeyboardTheme(palette = keyboardPalette(context, KeyboardStyle.GboardDark, dark = true)) {
                        CompositionLocalProvider(LocalReduceMotion provides true) {
                            KeyboardPanel(
                                state = KeyboardUiState(mode = KeyboardMode.Snippets, snippets = snippets),
                                actions = NoKeyboardActions,
                                contentHeight = 436.dp,
                                previewMode = true,
                            )
                        }
                    }
                }
            }
        }
        compose.waitForText("Running late")
        compose.capture("featureGraphic", listingImagesDir)
    }
}
