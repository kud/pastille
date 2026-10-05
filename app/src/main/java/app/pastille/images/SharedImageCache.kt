package app.pastille.images

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

// A MediaStore URI is unreadable to the app that pastes it, so recent images are copied under
// cache/shared/ and served through Pastille's own FileProvider, whose grant travels with the clip.
fun copyToSharedCache(context: Context, source: Uri, mimeType: String): Uri {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val extension = if (mimeType == "image/jpeg") "jpg" else mimeType.substringAfter('/')
    val out = File(dir, "image-${System.currentTimeMillis()}.$extension")
    val input = context.contentResolver.openInputStream(source)
        ?: throw IOException("Cannot open $source")
    input.use { stream ->
        out.outputStream().use { output -> stream.copyTo(output) }
    }
    return fileProviderUri(context, out)
}

fun fileProviderUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

fun mimeTypeOf(context: Context, uri: Uri): String =
    runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: "image/png"
