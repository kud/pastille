package app.pastille.picker

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import app.pastille.MainActivity
import app.pastille.PastilleApplication
import app.pastille.clipboard.ClipContent
import app.pastille.clipboard.clipContentFor
import app.pastille.clipboard.copySnippet
import app.pastille.data.SnippetRepository
import app.pastille.ime.ImageItem
import app.pastille.ime.KeyboardActions
import app.pastille.ime.KeyboardTheme
import app.pastille.ime.LocalReduceMotion
import app.pastille.ime.PastilleMotion
import app.pastille.ime.displayTitle
import app.pastille.ime.keyboardPalette
import app.pastille.images.copyToSharedCache
import app.pastille.images.mimeTypeOf
import app.pastille.model.SnippetRecord
import app.pastille.settings.KeyboardMode
import app.pastille.settings.PastilleSettings
import app.pastille.tile.ImePickerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Opened by the Quick Settings tile: a sheet of snippets and images to copy, over whatever app is open.
class PickerActivity : ComponentActivity(), KeyboardActions {

    private val settings by lazy { PastilleSettings.forContext(this) }
    private val repository by lazy { SnippetRepository.forContext(this) }
    private val backgroundScope: CoroutineScope get() = (application as PastilleApplication).backgroundScope

    private val mode = mutableStateOf(KeyboardMode.Snippets)
    private val folderId = mutableStateOf<Long?>(null)
    private val sourceId = mutableStateOf<Long?>(null)
    private val target = mutableStateOf<PickerTarget?>(null)
    private val notice = mutableStateOf<PickerNotice?>(null)
    private val closing = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        mode.value = settings.keyboardMode
        folderId.value = settings.keyboardCategoryId
        sourceId.value = settings.imageSourceBucketId
        val reduceMotion = PastilleMotion.reduceMotion()
        setContent {
            val context = LocalContext.current
            val configuration = LocalConfiguration.current
            val dark = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            val style = remember { settings.keyboardStyle }
            val palette = remember(style, dark) { keyboardPalette(context, style, dark) }
            KeyboardTheme(palette = palette) {
                CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
                    PickerSheet(
                        state = PickerState(
                            mode = mode.value,
                            folderId = folderId.value,
                            sourceId = sourceId.value,
                            target = target.value,
                            notice = notice.value,
                            closing = closing.value,
                        ),
                        actions = this,
                        onSourcesResolved = { resolved -> if (resolved != sourceId.value) sourceId.value = resolved },
                        onCloseTarget = { target.value = null },
                        onCopyImage = ::onImageTap,
                        onShareSnippet = ::shareSnippet,
                        onShareImage = ::shareImage,
                        onNoticeDismiss = { key -> if (notice.value?.key == key) notice.value = null },
                        onDismissed = ::finish,
                    )
                }
            }
        }
    }

    private fun close() {
        closing.value = true
    }

    private fun confirmCopied() {
        // Android 13+ shows its own clipboard confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(applicationContext, "Copied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onModeChange(mode: KeyboardMode) {
        this.mode.value = mode
    }

    override fun onOpenFolder(categoryId: Long?) {
        folderId.value = categoryId
    }

    override fun onSelectSource(bucketId: Long) {
        sourceId.value = bucketId
    }

    override fun onSnippetTap(snippet: SnippetRecord) {
        val content = clipContentFor(this, snippet)
        if (content == null) {
            notice.value = PickerNotice("That image is gone")
            return
        }
        copySnippet(this, content)
        backgroundScope.launch { repository.recordUse(snippet.id) }
        confirmCopied()
        close()
    }

    override fun onImageTap(image: ImageItem) {
        lifecycleScope.launch {
            val uri = withContext(Dispatchers.IO) {
                runCatching { copyToSharedCache(this@PickerActivity, image.uri, mimeTypeOf(this@PickerActivity, image.uri)) }
                    .getOrNull()
            }
            if (uri == null) {
                notice.value = PickerNotice("Couldn't read that image")
                return@launch
            }
            copySnippet(this@PickerActivity, ClipContent.Image(uri))
            confirmCopied()
            close()
        }
    }

    override fun onSnippetLongPress(snippet: SnippetRecord) {
        target.value = PickerTarget.Snippet(snippet.id)
    }

    override fun onImageLongPress(image: ImageItem) {
        target.value = PickerTarget.Image(image)
    }

    override fun onEditSnippet(snippet: SnippetRecord) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_EDIT_SNIPPET_ID, snippet.id)
                .addFlags(MainActivity.LAUNCH_FLAGS),
        )
        finish()
    }

    override fun onDeleteSnippet(snippet: SnippetRecord) {
        target.value = null
        backgroundScope.launch {
            repository.delete(snippet.id)
            withContext(Dispatchers.Main) {
                notice.value = PickerNotice(
                    message = "Deleted \"${displayTitle(snippet.title, snippet.text).ifBlank { "Image" }}\"",
                    actionLabel = "Undo",
                    onAction = { backgroundScope.launch { repository.restore(snippet) } },
                )
            }
        }
    }

    override fun onMoveToCategory(snippetId: Long, categoryId: Long?) {
        backgroundScope.launch {
            repository.setCategory(listOf(snippetId), categoryId)
            val name = categoryId?.let { id -> repository.getCategories().find { it.id == id }?.name }
            withContext(Dispatchers.Main) {
                notice.value = PickerNotice(if (name != null) "Moved to $name" else "Moved to the top level")
            }
        }
    }

    override fun onOpenApp() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(MainActivity.LAUNCH_FLAGS))
        finish()
    }

    // The keyboard switch lives here now: the system keyboard picker needs a focused window, which ImePickerActivity provides.
    override fun onSwitchKeyboard() {
        startActivity(Intent(this, ImePickerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun shareSnippet(snippet: SnippetRecord) {
        val content = clipContentFor(this, snippet) as? ClipContent.Image
        if (content == null) {
            notice.value = PickerNotice("That image is gone")
            return
        }
        share(content.uri)
    }

    private fun shareImage(image: ImageItem) {
        lifecycleScope.launch {
            val uri = withContext(Dispatchers.IO) {
                runCatching { copyToSharedCache(this@PickerActivity, image.uri, mimeTypeOf(this@PickerActivity, image.uri)) }
                    .getOrNull()
            }
            if (uri == null) {
                notice.value = PickerNotice("Couldn't read that image")
                return@launch
            }
            share(uri)
        }
    }

    private fun share(uri: Uri) {
        val send = Intent(Intent.ACTION_SEND)
            .setType(contentResolver.getType(uri) ?: "image/*")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, null))
        finish()
    }
}
