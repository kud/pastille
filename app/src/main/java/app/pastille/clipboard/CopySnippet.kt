package app.pastille.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import app.pastille.PastilleApplication
import app.pastille.images.ImageStore
import app.pastille.images.fileProviderUri
import app.pastille.model.SnippetRecord
import app.pastille.settings.PastilleSettings

sealed interface ClipContent {
    data class Text(val text: String) : ClipContent
    data class Image(val uri: Uri) : ClipContent
}

// What a caller needs to offer "Clear now": the clip's label, and the timer, if one is running.
data class CopiedClip(val label: String, val clearAfterSeconds: Int?)

// Null when an image snippet's file is gone.
fun clipContentFor(context: Context, snippet: SnippetRecord): ClipContent? {
    val imageFile = snippet.imageFile
    if (!snippet.isImage || imageFile == null) return ClipContent.Text(snippet.text)
    val file = ImageStore.forContext(context).fileFor(imageFile)
    if (!file.exists()) return null
    return runCatching { ClipContent.Image(fileProviderUri(context, file)) }.getOrNull()
}

// Every copy, from the app and from the picker sheet, goes through here, so the clipboard timer hooks in once.
// The keyboard commits text straight into the field and never comes through here.
fun copySnippet(context: Context, content: ClipContent): CopiedClip? {
    val appContext = context.applicationContext
    val clipboard = appContext.getSystemService(ClipboardManager::class.java) ?: return null
    val clearAfter = PastilleSettings.forContext(appContext).clipboardClearDelay.seconds
    val label = newClipLabel()
    val clip = when (content) {
        is ClipContent.Text -> ClipData.newPlainText(label, content.text)
        is ClipContent.Image -> ClipData.newUri(appContext.contentResolver, label, content.uri)
    }
    if (clearAfter != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    if (runCatching { clipboard.setPrimaryClip(clip) }.isFailure) return null
    val clearer = (appContext as? PastilleApplication)?.clipboardClearer
    if (clearAfter != null) {
        clearer?.schedule(label, clearAfter * 1_000L)
    } else {
        clearer?.cancel()
    }
    return CopiedClip(label, clearAfter)
}

fun clearCopiedClip(context: Context, copied: CopiedClip): Boolean =
    (context.applicationContext as? PastilleApplication)?.clipboardClearer?.clearNow(copied.label) == true
