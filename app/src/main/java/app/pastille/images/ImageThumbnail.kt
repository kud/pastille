package app.pastille.images

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface StoredThumbState {
    data object Loading : StoredThumbState
    data object Failed : StoredThumbState
    data class Loaded(val bitmap: Bitmap) : StoredThumbState
}

/** A stored image snippet's thumbnail: shimmer while decoding, broken-image glyph on failure. */
@Composable
fun ImageThumbnail(
    fileName: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    targetPx: Int = 256,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val store = ImageStore.forContext(LocalContext.current)
    var state by remember(fileName, targetPx) { mutableStateOf<StoredThumbState>(StoredThumbState.Loading) }
    LaunchedEffect(fileName, targetPx) {
        val bitmap = withContext(Dispatchers.IO) { store.loadThumbnail(fileName, targetPx) }
        state = if (bitmap != null) StoredThumbState.Loaded(bitmap) else StoredThumbState.Failed
    }
    Box(modifier) {
        Crossfade(targetState = state, animationSpec = tween(150), label = "stored-thumb") { current ->
            when (current) {
                is StoredThumbState.Loading -> ThumbnailPlaceholder(shimmer = true)
                is StoredThumbState.Loaded -> Image(
                    bitmap = current.bitmap.asImageBitmap(),
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize(),
                )
                is StoredThumbState.Failed -> ThumbnailPlaceholder(shimmer = false) {
                    Icon(
                        Icons.Outlined.BrokenImage,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun ThumbnailPlaceholder(shimmer: Boolean, content: @Composable () -> Unit = {}) {
    val alpha = if (shimmer) {
        val pulse by rememberInfiniteTransition(label = "shimmer").animateFloat(
            initialValue = 0.6f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
            label = "alpha",
        )
        pulse
    } else {
        1f
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
