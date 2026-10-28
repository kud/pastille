package app.pastille.qr

import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.heading
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import io.nayuki.qrcodegen.QrCode
import kotlin.math.floor

// Fixed ink on paper, whatever the theme: a dark-mode code (light modules on black) is one many
// camera apps refuse to read.
private val Paper = Color.White
private val Ink = Color.Black
private val InkSoft = Color(0xFF4A4A4A)

/** Under a disabled "Show QR code": why, and what to use instead. */
const val QR_TOO_LONG = "Too long for a QR code: send it to another phone instead"

/**
 * The snippet as a QR code, full screen on white, with the screen kept on and at full brightness
 * while it shows. Nothing is drawn when [text] does not fit; the menu never offers it then.
 */
@Composable
fun SnippetQrDialog(title: String, text: String, onDismiss: () -> Unit) {
    val code = remember(text) { SnippetQr.encode(text) } ?: return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            window?.let {
                it.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                it.attributes = it.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                }
            }
            onDispose { }
        }
        QrScreen(title = title, code = code, onClose = onDismiss)
    }
}

/** The dialog's content on its own, so it can be rendered without a window. */
@Composable
fun QrScreen(title: String, code: QrCode, onClose: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = Paper, contentColor = Ink) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The editor's page header (§20): close first, then the title as a headline. The paper and
            // ink stay whatever the theme, since a scanner reads dark on light best.
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Ink)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            }
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                QrImage(
                    code = code,
                    contentDescription = "QR code for $title",
                    modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().aspectRatio(1f),
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Scan with the other phone's camera, then share the text to Pastille",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * The code with its quiet zone, modules snapped to whole pixels so no edge blurs into grey.
 */
@Composable
fun QrImage(code: QrCode, contentDescription: String, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier.semantics {
            this.contentDescription = contentDescription
            role = Role.Image
        },
    ) {
        drawRect(Paper)
        val modules = code.size + 2 * SnippetQr.QUIET_ZONE
        val side = minOf(size.width, size.height)
        val raw = side / modules
        val cell = if (raw >= 1f) floor(raw) else raw
        val origin = Offset(
            (size.width - cell * code.size) / 2f,
            (size.height - cell * code.size) / 2f,
        ).let { Offset(floor(it.x), floor(it.y)) }
        val module = Size(cell, cell)
        for (y in 0 until code.size) {
            for (x in 0 until code.size) {
                if (code.getModule(x, y)) {
                    drawRect(Ink, topLeft = Offset(origin.x + x * cell, origin.y + y * cell), size = module)
                }
            }
        }
    }
}
