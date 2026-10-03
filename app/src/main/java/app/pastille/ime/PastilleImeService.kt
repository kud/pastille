package app.pastille.ime

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.lifecycle.ViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.ViewTreeSavedStateRegistryOwner
import app.pastille.MainActivity
import app.pastille.data.SnippetRepository
import app.pastille.model.SnippetRecord
import app.pastille.ui.theme.PastilleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PastilleImeService :
    InputMethodService(),
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val viewModelStoreHolder = ViewModelStore()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = viewModelStoreHolder

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onCreateInputView(): View {
        val view = ComposeView(this)
        val decorView = window.window?.decorView
        if (decorView != null) {
            ViewTreeLifecycleOwner.set(decorView, this)
            ViewTreeSavedStateRegistryOwner.set(decorView, this)
            ViewTreeViewModelStoreOwner.set(decorView, this)
        } else {
            ViewTreeLifecycleOwner.set(view, this)
            ViewTreeSavedStateRegistryOwner.set(view, this)
            ViewTreeViewModelStoreOwner.set(view, this)
        }
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        view.setContent {
            PastilleTheme {
                PanelHost()
            }
        }
        return view
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStoreHolder.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    @Composable
    private fun PanelHost() {
        val context = LocalContext.current
        val repository = remember { SnippetRepository.forContext(context) }
        val flow = remember { repository.observeSnippets() }
        val snippets by flow.collectAsState(initial = emptyList())
        var screenshots by remember { mutableStateOf(emptyList<ScreenshotItem>()) }
        var canReadScreenshots by remember {
            mutableStateOf(ScreenshotReader.hasPermission(context))
        }

        LaunchedEffect(Unit) {
            val items = withContext(Dispatchers.IO) {
                ScreenshotReader.readRecent(context)
            }
            screenshots = items
            canReadScreenshots = ScreenshotReader.hasPermission(context)
        }

        KeyboardPanel(
            snippets = snippets,
            screenshots = screenshots,
            hasScreenshotPermission = canReadScreenshots,
            onSaveClipboard = ::saveClipboardAsSnippet,
            onOpenApp = ::openApp,
            onOpenPermissions = ::openApp,
            onBack = ::switchBack,
            onSnippetTap = ::pasteSnippet,
            onSnippetLongPress = ::openSnippetInEditor,
            onScreenshotTap = ::shareScreenshot,
        )
    }

    private fun pasteSnippet(snippet: SnippetRecord) {
        currentInputConnection?.commitText(snippet.text, 1)
        serviceScope.launch { SnippetRepository.forContext(this@PastilleImeService).recordUse(snippet.id) }
    }

    private fun openSnippetInEditor(snippet: SnippetRecord) {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_EDIT_SNIPPET_ID, snippet.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun saveClipboardAsSnippet() {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        val clip = clipboard.primaryClip ?: return
        val text = (0 until clip.itemCount)
            .mapNotNull { clip.getItemAt(it)?.coerceToText(this)?.toString() }
            .firstOrNull { it.isNotBlank() }
            ?: return
        serviceScope.launch {
            SnippetRepository.forContext(this@PastilleImeService).saveClipboardText(text)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@PastilleImeService, "Snippet saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareScreenshot(item: ScreenshotItem) {
        val connection = currentInputConnection
        val editorInfo = currentInputEditorInfo
        val mimeTypes = if (editorInfo != null) {
            EditorInfoCompat.getContentMimeTypes(editorInfo).toSet()
        } else {
            emptySet()
        }
        val acceptsImage = mimeTypes.any { it == "image/*" || it.startsWith("image/") }
        if (connection != null && editorInfo != null && acceptsImage) {
            try {
                val contentUri = copyToSharedCache(item)
                val targetPackage = editorInfo.packageName
                if (targetPackage != null) {
                    grantUriPermission(
                        targetPackage,
                        contentUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                val info = InputContentInfoCompat(
                    contentUri,
                    ClipDescription("Pastille screenshot", arrayOf("image/png")),
                )
                if (InputConnectionCompat.commitContent(connection, editorInfo, info, 0, null)) {
                    return
                }
            } catch (_: Exception) {
                // Fall through to the clipboard fallback below.
            }
        }
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newUri(contentResolver, "Pastille screenshot", item.uri)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Copied, long-press to paste", Toast.LENGTH_SHORT).show()
    }

    private fun copyToSharedCache(item: ScreenshotItem): Uri {
        val dir = File(cacheDir, "shared").apply { mkdirs() }
        val out = File(dir, "screenshot-${System.currentTimeMillis()}.png")
        contentResolver.openInputStream(item.uri)?.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return FileProvider.getUriForFile(this, "$packageName.fileprovider", out)
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun switchBack() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            switchToPreviousInputMethod()
        } else {
            getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        }
    }
}
