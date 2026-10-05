package app.pastille.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import app.pastille.images.ImageStore
import app.pastille.images.fileProviderUri
import app.pastille.model.SnippetRecord

sealed interface ClipContent {
    data class Text(val text: String) : ClipContent
    data class Image(val uri: Uri) : ClipContent
}

// Null when an image snippet's file is gone.
fun clipContentFor(context: Context, snippet: SnippetRecord): ClipContent? {
    val imageFile = snippet.imageFile
    if (!snippet.isImage || imageFile == null) return ClipContent.Text(snippet.text)
    val file = ImageStore.forContext(context).fileFor(imageFile)
    if (!file.exists()) return null
    return runCatching { ClipContent.Image(fileProviderUri(context, file)) }.getOrNull()
}

// Every copy, from the app and from the picker sheet, goes through here.
fun copySnippet(context: Context, content: ClipContent): Boolean {
    val appContext = context.applicationContext
    val label = "Pastille"
    val clipboard = appContext.getSystemService(ClipboardManager::class.java) ?: return false
    val clip = when (content) {
        is ClipContent.Text -> ClipData.newPlainText(label, content.text)
        is ClipContent.Image -> ClipData.newUri(appContext.contentResolver, label, content.uri)
    }
    return runCatching { clipboard.setPrimaryClip(clip) }.isSuccess
}
