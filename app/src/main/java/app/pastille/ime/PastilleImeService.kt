package app.pastille.ime

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
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
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.pastille.MainActivity
import app.pastille.data.SnippetRepository
import app.pastille.model.SnippetRecord
import app.pastille.settings.PastilleSettings
import app.pastille.ui.theme.PastilleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class PastilleImeService :
    InputMethodService(),
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val viewModelStoreHolder = ViewModelStore()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Bumped each time the keyboard opens, so the screenshot row picks up new ones.
    private val openCount = mutableIntStateOf(0)

    // Mirrors the service configuration, so the panel follows dark mode and wallpaper colours without recreating the view.
    private val configuration = mutableStateOf(Configuration(), neverEqualPolicy())

    private var destroyed = false

    private val settings by lazy { PastilleSettings.forContext(this) }

    private val statusMessage = mutableStateOf<String?>(null)
    private val clipboardHasText = mutableStateOf(false)

    private val clipChangedListener =
        ClipboardManager.OnPrimaryClipChangedListener {
            clipboardHasText.value = readClipboardText() != null
        }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = viewModelStoreHolder

    private fun moveLifecycleTo(state: Lifecycle.State) {
        if (lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) return
        lifecycleRegistry.currentState = state
    }

    override fun onCreate() {
        super.onCreate()
        configuration.value = Configuration(resources.configuration)
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        moveLifecycleTo(Lifecycle.State.CREATED)
        getSystemService(ClipboardManager::class.java)?.addPrimaryClipChangedListener(clipChangedListener)
    }

    override fun onCreateInputView(): View {
        val view = ComposeView(this)
        val decorView = window.window?.decorView
        if (decorView != null) {
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
            decorView.setViewTreeViewModelStoreOwner(this)
        } else {
            view.setViewTreeLifecycleOwner(this)
            view.setViewTreeSavedStateRegistryOwner(this)
            view.setViewTreeViewModelStoreOwner(this)
        }
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        view.setContent {
            val nightMode = configuration.value.uiMode and Configuration.UI_MODE_NIGHT_MASK
            PastilleTheme(darkTheme = nightMode == Configuration.UI_MODE_NIGHT_YES) {
                PanelHost()
            }
        }
        return view
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        openCount.intValue++
        moveLifecycleTo(Lifecycle.State.RESUMED)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            moveLifecycleTo(Lifecycle.State.STARTED)
        }
        super.onFinishInputView(finishingInput)
    }

    override fun onWindowHidden() {
        moveLifecycleTo(Lifecycle.State.CREATED)
        super.onWindowHidden()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        configuration.value = Configuration(newConfig)
    }

    override fun onDestroy() {
        destroyed = true
        getSystemService(ClipboardManager::class.java)?.removePrimaryClipChangedListener(clipChangedListener)
        moveLifecycleTo(Lifecycle.State.DESTROYED)
        viewModelStoreHolder.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun readClipboardText(): String? {
        return try {
            val clipboard = getSystemService(ClipboardManager::class.java) ?: return null
            val clip = clipboard.primaryClip ?: return null
            (0 until clip.itemCount)
                .mapNotNull { clip.getItemAt(it)?.coerceToText(this)?.toString() }
                .firstOrNull { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
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

        LaunchedEffect(openCount.intValue) {
            val items = withContext(Dispatchers.IO) {
                runCatching { ScreenshotReader.readRecent(context) }.getOrDefault(emptyList())
            }
            screenshots = items
            canReadScreenshots = ScreenshotReader.hasPermission(context)
            clipboardHasText.value = readClipboardText() != null
        }

        KeyboardPanel(
            snippets = snippets,
            screenshots = screenshots,
            hasScreenshotPermission = canReadScreenshots,
            clipboardHasText = clipboardHasText.value,
            statusMessage = statusMessage.value,
            onStatusShown = { statusMessage.value = null },
            onNewSnippet = ::openNewSnippet,
            onSaveClipboard = ::saveClipboardAsSnippet,
            onOpenApp = ::openApp,
            onOpenPermissions = ::openApp,
            onSwitchKeyboard = ::switchKeyboard,
            onSnippetTap = ::pasteSnippet,
            onSnippetLongPress = ::openSnippetInEditor,
            onScreenshotTap = ::shareScreenshot,
        )
    }

    private fun pasteSnippet(snippet: SnippetRecord) {
        val committed = currentInputConnection?.commitText(snippet.text, 1) == true
        serviceScope.launch { SnippetRepository.forContext(this@PastilleImeService).recordUse(snippet.id) }
        if (committed) returnToPreviousKeyboardIfWanted()
    }

    private fun openSnippetInEditor(snippet: SnippetRecord) {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_EDIT_SNIPPET_ID, snippet.id)
            .addFlags(MainActivity.LAUNCH_FLAGS)
        startActivity(intent)
    }

    private fun openNewSnippet() {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_NEW_SNIPPET, true)
            .addFlags(MainActivity.LAUNCH_FLAGS)
        startActivity(intent)
    }

    private fun saveClipboardAsSnippet() {
        val text = readClipboardText()
        if (text == null) {
            statusMessage.value = "Clipboard is empty"
            return
        }
        serviceScope.launch {
            SnippetRepository.forContext(this@PastilleImeService).saveClipboardText(text)
            withContext(Dispatchers.Main) {
                if (destroyed) return@withContext
                statusMessage.value = "Saved from clipboard"
            }
        }
    }

    private fun shareScreenshot(item: ScreenshotItem) {
        serviceScope.launch {
            val mimeType = try {
                contentResolver.getType(item.uri)
            } catch (_: Exception) {
                null
            } ?: "image/png"
            val contentUri = try {
                copyToSharedCache(item, mimeType)
            } catch (_: Exception) {
                null
            }
            withContext(Dispatchers.Main) {
                if (destroyed) return@withContext
                if (contentUri == null) {
                    statusMessage.value = "Couldn't read that screenshot"
                    return@withContext
                }
                val connection = currentInputConnection
                val editorInfo = currentInputEditorInfo
                val mimeTypes = if (editorInfo != null) {
                    EditorInfoCompat.getContentMimeTypes(editorInfo).toSet()
                } else {
                    emptySet()
                }
                val acceptsImage = mimeTypes.any { ClipDescription.compareMimeTypes(mimeType, it) }
                if (connection != null && editorInfo != null && acceptsImage) {
                    val info = InputContentInfoCompat(
                        contentUri,
                        ClipDescription("Pastille screenshot", arrayOf(mimeType)),
                        null,
                    )
                    val flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
                    val committed = try {
                        InputConnectionCompat.commitContent(connection, editorInfo, info, flags, null)
                    } catch (_: Exception) {
                        false
                    }
                    if (committed) {
                        returnToPreviousKeyboardIfWanted()
                        return@withContext
                    }
                }
                // Only Pastille's own FileProvider URI travels with the clip's read grant;
                // a MediaStore URI would be unreadable to the app that pastes it.
                val clipboard = getSystemService(ClipboardManager::class.java) ?: return@withContext
                val clip = ClipData.newUri(contentResolver, "Pastille screenshot", contentUri)
                clipboard.setPrimaryClip(clip)
                statusMessage.value = "Copied, long-press to paste"
            }
        }
    }

    private fun copyToSharedCache(item: ScreenshotItem, mimeType: String): Uri {
        val dir = File(cacheDir, "shared").apply { mkdirs() }
        val extension = if (mimeType == "image/jpeg") "jpg" else mimeType.substringAfter('/')
        val out = File(dir, "screenshot-${System.currentTimeMillis()}.$extension")
        val input = contentResolver.openInputStream(item.uri)
            ?: throw IOException("Cannot open ${item.uri}")
        input.use { stream ->
            out.outputStream().use { output -> stream.copyTo(output) }
        }
        return FileProvider.getUriForFile(this, "$packageName.fileprovider", out)
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(MainActivity.LAUNCH_FLAGS)
        startActivity(intent)
    }

    private fun returnToPreviousKeyboardIfWanted() {
        if (settings.returnToPreviousKeyboard) switchKeyboard()
    }

    private fun switchKeyboard() {
        if (destroyed) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            switchToPreviousInputMethod()
        } else {
            getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        }
    }
}
