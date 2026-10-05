package app.pastille.images

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val OpaqueStickerShape = RoundedCornerShape(8.dp)

/**
 * A sticker's still thumbnail, `Fit` and centred, never cropped. An opaque image (a JPEG made into a
 * sticker) is clipped to 8dp corners so a hard rectangle doesn't sit among cut-outs. The grid shows
 * only this first frame: animation plays in the preview alone.
 */
@Composable
fun StickerThumbnail(fileName: String, targetPx: Int, modifier: Modifier = Modifier) {
    val store = ImageStore.forContext(LocalContext.current)
    var bitmap by remember(fileName, targetPx) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(fileName, targetPx) {
        bitmap = withContext(Dispatchers.IO) { store.loadThumbnail(fileName, targetPx) }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val current = bitmap ?: return@Box
        Image(
            bitmap = current.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().then(
                if (current.hasAlpha()) Modifier else Modifier.clip(OpaqueStickerShape),
            ),
        )
    }
}

/**
 * The preview of a sticker. An animated GIF or WebP decodes to an [AnimatedImageDrawable] that loops
 * while shown and stops when it leaves; with [animate] false (reduced motion) it shows its first frame.
 * No image library: `ImageDecoder` and `AnimatedImageDrawable` are on every supported device.
 */
@Composable
fun AnimatedSticker(
    fileName: String,
    contentDescription: String?,
    animate: Boolean,
    targetPx: Int,
    modifier: Modifier = Modifier,
) {
    val store = ImageStore.forContext(LocalContext.current)
    var drawable by remember(fileName, targetPx) { mutableStateOf<Drawable?>(null) }
    LaunchedEffect(fileName, targetPx) {
        drawable = withContext(Dispatchers.IO) { decodeSticker(store, fileName, targetPx) }
    }
    val current = drawable
    DisposableEffect(current, animate) {
        val animated = current as? AnimatedImageDrawable
        if (animated != null) {
            animated.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
            if (animate) animated.start() else animated.stop()
        }
        onDispose { animated?.stop() }
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = ImageView.IMPORTANT_FOR_ACCESSIBILITY_YES
            }
        },
        update = { view ->
            if (view.drawable !== current) view.setImageDrawable(current)
            view.contentDescription = contentDescription
        },
    )
}

private fun decodeSticker(store: ImageStore, fileName: String, targetPx: Int): Drawable? {
    val file = store.fileFor(fileName)
    if (!file.exists()) return null
    return runCatching {
        ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val longEdge = maxOf(info.size.width, info.size.height)
            if (longEdge > targetPx && targetPx > 0) {
                val scale = targetPx.toFloat() / longEdge
                decoder.setTargetSize(
                    maxOf(1, (info.size.width * scale).roundToInt()),
                    maxOf(1, (info.size.height * scale).roundToInt()),
                )
            }
        }
    }.getOrNull()
}
