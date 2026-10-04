package app.pastille.ime

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.ExtractedTextRequest
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
import app.pastille.images.ImageStore
import app.pastille.images.mimeTypeForFile
import app.pastille.model.SnippetRecord
import app.pastille.settings.PastilleSettings
import app.pastille.share.MAX_TEXT_CHARS
import app.pastille.share.autoTitle
import app.pastille.ui.theme.PastilleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var refreshJob: Job? = null

    // Bumped each time the keyboard opens, so the screenshot row picks up new ones.
    private val openCount = mutableIntStateOf(0)

    // Mirrors the service configuration, so the panel follows dark mode and wallpaper colours without recreating the view.
    private val configuration = mutableStateOf(Configuration(), neverEqualPolicy())

    private var destroyed = false

    private val settings by lazy { PastilleSettings.forContext(this) }

    private val panelState = mutableStateOf<PanelState>(PanelState.Browse)
    private val addSources = mutableStateOf<AddSources?>(null)
    private val strip = mutableStateOf<StatusStrip?>(null)
    private val highlightedSnippetId = mutableStateOf<Long?>(null)
    private var hasSelection = false

    private val clipChangedListener =
        ClipboardManager.OnPrimaryClipChangedListener {
            if (panelState.value == PanelState.Add) {
                val current = addSources.value
                if (current != null) {
                    addSources.value = current.copy(clip = readClipboard())
                }
            }
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
        if (!restarting) {
            panelState.value = PanelState.Browse
        }
        hasSelection = info != null &&
            info.initialSelStart >= 0 &&
            info.initialSelEnd >= 0 &&
            info.initialSelStart != info.initialSelEnd
        moveLifecycleTo(Lifecycle.State.RESUMED)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && isInputViewShown && panelState.value != PanelState.Browse) {
            panelState.value = PanelState.Browse
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        hasSelection = newSelStart != newSelEnd
        if (panelState.value == PanelState.Add) {
            refreshJob?.cancel()
            refreshJob = mainScope.launch {
                delay(300)
                if (panelState.value == PanelState.Add) {
                    addSources.value = AddSources(readHostText(), readClipboard())
                }
            }
        }
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
        refreshJob?.cancel()
        mainScope.cancel()
        getSystemService(ClipboardManager::class.java)?.removePrimaryClipChangedListener(clipChangedListener)
        moveLifecycleTo(Lifecycle.State.DESTROYED)
        viewModelStoreHolder.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun showStrip(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        strip.value = StatusStrip(message = message, actionLabel = actionLabel, onAction = onAction)
    }

    private fun readHostText(): HostRead {
        return try {
            val info = currentInputEditorInfo ?: return HostRead.Unavailable
            val ic = currentInputConnection ?: return HostRead.Unavailable
            if (isSensitiveField(info.inputType, info.imeOptions)) return HostRead.Sensitive
            if (hasSelection) {
                val selected = ic.getSelectedText(0)?.toString()
                if (!selected.isNullOrBlank()) return HostRead.Text(selected, fromSelection = true)
            }
            val extracted = ic.getExtractedText(
                ExtractedTextRequest().apply { hintMaxChars = MAX_TEXT_CHARS },
                0,
            )?.text?.toString()
            if (extracted != null) {
                return if (extracted.isBlank()) HostRead.Empty else HostRead.Text(extracted, fromSelection = false)
            }
            val surrounding = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ic.getSurroundingText(10_000, 10_000, 0)?.text?.toString()
            } else {
                val before = ic.getTextBeforeCursor(10_000, 0)?.toString()
                val after = ic.getTextAfterCursor(10_000, 0)?.toString()
                if (before == null && after == null) null else (before ?: "") + (after ?: "")
            }
            if (surrounding == null) {
                HostRead.Unavailable
            } else if (surrounding.isBlank()) {
                HostRead.Empty
            } else {
                HostRead.Text(surrounding, fromSelection = false)
            }
        } catch (_: Exception) {
            HostRead.Unavailable
        }
    }

    private fun readClipboard(): ClipRead {
        return try {
            val clipboard = getSystemService(ClipboardManager::class.java) ?: return ClipRead.Empty
            val clip = clipboard.primaryClip ?: return ClipRead.Empty
            val text = (0 until clip.itemCount)
                .mapNotNull { clip.getItemAt(it)?.coerceToText(this)?.toString() }
                .firstOrNull { it.isNotBlank() }
                ?: return ClipRead.Empty
            val sensitive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                clip.description?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true
            } else {
                false
            }
            ClipRead.Text(text, sensitive)
        } catch (_: Exception) {
            ClipRead.Empty
        }
    }

    private fun openAdd() {
        refreshJob?.cancel()
        panelState.value = PanelState.Add
        addSources.value = AddSources(readHostText(), readClipboard())
    }

    private fun saveFrom(source: AddSource, categoryId: Long?) {
        val raw = when (source) {
            AddSource.Field -> (readHostText() as? HostRead.Text)?.text
            AddSource.Clipboard -> (readClipboard() as? ClipRead.Text)?.text
        }?.take(MAX_TEXT_CHARS)
        if (raw.isNullOrBlank()) {
            showStrip("Nothing to save")
            return
        }
        serviceScope.launch {
            val repository = SnippetRepository.forContext(this@PastilleImeService)
            val duplicate = repository.findTextDuplicate(raw)
            if (duplicate != null) {
                withContext(Dispatchers.Main) {
                    if (destroyed) return@withContext
                    panelState.value = PanelState.Browse
                    showStrip(
                        message = "Already saved as \"${displayTitle(duplicate.title, duplicate.text)}\"",
                        actionLabel = "Show",
                        onAction = { highlightedSnippetId.value = duplicate.id },
                    )
                }
                return@launch
            }
            val title = autoTitle(raw)
            val id = repository.upsert(SnippetRecord(title = title, text = raw, categoryId = categoryId))
            withContext(Dispatchers.Main) {
                if (destroyed) return@withContext
                panelState.value = PanelState.Browse
                showStrip(
                    message = "Saved \"${displayTitle(title, raw)}\"",
                    actionLabel = "Undo",
                    onAction = {
                        serviceScope.launch {
                            SnippetRepository.forContext(this@PastilleImeService).delete(id)
                            withContext(Dispatchers.Main) {
                                if (destroyed) return@withContext
                                showStrip("Removed")
                            }
                        }
                    },
                )
                highlightedSnippetId.value = id
            }
        }
    }

    private fun togglePin(snippet: SnippetRecord) {
        serviceScope.launch {
            SnippetRepository.forContext(this@PastilleImeService).setPinned(listOf(snippet.id), !snippet.pinned)
            withContext(Dispatchers.Main) {
                if (destroyed) return@withContext
                showStrip(if (snippet.pinned) "Unpinned" else "Pinned")
            }
        }
    }

    private fun editSnippet(snippet: SnippetRecord) {
        openSnippetInEditor(snippet)
        panelState.value = PanelState.Browse
    }

    private fun deleteSnippet(snippet: SnippetRecord) {
        serviceScope.launch {
            SnippetRepository.forContext(this@PastilleImeService).delete(snippet.id)
            withContext(Dispatchers.Main) {
                if (destroyed) return@withContext
                panelState.value = PanelState.Browse
                showStrip(
                    message = "Deleted \"${displayTitle(snippet.title, snippet.text)}\"",
                    actionLabel = "Undo",
                    onAction = {
                        serviceScope.launch {
                            SnippetRepository.forContext(this@PastilleImeService).restore(snippet)
                        }
                    },
                )
            }
        }
    }

    private fun moveSnippet(id: Long, categoryId: Long?) {
        serviceScope.launch {
            val repository = SnippetRepository.forContext(this@PastilleImeService)
            repository.setCategory(listOf(id), categoryId)
            val name = categoryId?.let { repository.getCategories().find { it.id == categoryId }?.name }
            withContext(Dispatchers.Main) {
                if (destroyed) return@withContext
                showStrip(if (name != null) "Moved to $name" else "Moved to All")
            }
        }
    }

    @Composable
    private fun PanelHost() {
        val context = LocalContext.current
        val repository = remember { SnippetRepository.forContext(context) }
        val flow = remember { repository.observeSnippets() }
        val snippets by flow.collectAsState(initial = emptyList())
        val categoriesFlow = remember { repository.observeCategories() }
        val categories by categoriesFlow.collectAsState(initial = emptyList())
        var selectedCategoryId by remember { mutableStateOf(settings.keyboardCategoryId) }
        val effectiveCategoryId = selectedCategoryId?.takeIf { id -> categories.any { it.id == id } }
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
        }

        LaunchedEffect(categories, selectedCategoryId) {
            if (selectedCategoryId != null && categories.isNotEmpty() &&
                categories.none { it.id == selectedCategoryId }
            ) {
                selectedCategoryId = null
                settings.keyboardCategoryId = null
            }
        }

        KeyboardPanel(
            snippets = snippets,
            categories = categories,
            selectedCategoryId = effectiveCategoryId,
            openCount = openCount.intValue,
            screenshots = screenshots,
            hasScreenshotPermission = canReadScreenshots,
            panelState = panelState.value,
            addSources = addSources.value,
            strip = strip.value,
            highlightedSnippetId = highlightedSnippetId.value,
            onBack = { panelState.value = PanelState.Browse },
            onOpenAdd = { openAdd() },
            onSelectCategory = {
                selectedCategoryId = it
                settings.keyboardCategoryId = it
            },
            onOpenApp = ::openApp,
            onOpenPermissions = ::openApp,
            onSwitchKeyboard = ::switchKeyboard,
            onSnippetTap = ::pasteSnippet,
            onSnippetLongPress = { panelState.value = PanelState.Actions(it.id) },
            onScreenshotTap = ::shareScreenshot,
            onAddFrom = ::saveFrom,
            onWriteInApp = ::openNewSnippet,
            onPinToggle = ::togglePin,
            onEditSnippet = ::editSnippet,
            onDeleteSnippet = ::deleteSnippet,
            onMoveToCategory = ::moveSnippet,
            onStripDismiss = { key ->
                if (strip.value?.key == key) strip.value = null
            },
            onHighlightShown = { highlightedSnippetId.value = null },
            onHighlightNotInFilter = {
                selectedCategoryId = null
                settings.keyboardCategoryId = null
            },
        )
    }

    private fun pasteSnippet(snippet: SnippetRecord) {
        if (snippet.isImage) {
            insertImageSnippet(snippet)
            return
        }
        val committed = currentInputConnection?.commitText(snippet.text, 1) == true
        serviceScope.launch { SnippetRepository.forContext(this@PastilleImeService).recordUse(snippet.id) }
        if (committed) returnToPreviousKeyboardIfWanted()
    }

    private fun insertImageSnippet(snippet: SnippetRecord) {
        val name = snippet.imageFile
        if (name == null) {
            showStrip("That image is gone")
            return
        }
        val file = ImageStore.forContext(this).fileFor(name)
        if (!file.exists()) {
            showStrip("That image is gone")
            return
        }
        val uri = try {
            FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        } catch (_: Exception) {
            showStrip("That image is gone")
            return
        }
        serviceScope.launch { SnippetRepository.forContext(this@PastilleImeService).recordUse(snippet.id) }
        insertImage(uri, mimeTypeForFile(name), snippet.title.ifBlank { "Pastille image" })
    }

    private fun openSnippetInEditor(snippet: SnippetRecord) {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_EDIT_SNIPPET_ID, snippet.id)
            .addFlags(MainActivity.LAUNCH_FLAGS)
        startActivity(intent)
    }

    private fun openNewSnippet(categoryId: Long?) {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_NEW_SNIPPET, true)
            .addFlags(MainActivity.LAUNCH_FLAGS)
        if (categoryId != null) {
            intent.putExtra(MainActivity.EXTRA_CATEGORY_ID, categoryId)
        }
        startActivity(intent)
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
                    showStrip("Couldn't read that screenshot")
                    return@withContext
                }
                insertImage(contentUri, mimeType, "Pastille screenshot")
            }
        }
    }

    private fun insertImage(contentUri: Uri, mimeType: String, label: String) {
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
                ClipDescription(label, arrayOf(mimeType)),
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
                return
            }
        }
        // Only Pastille's own FileProvider URI travels with the clip's read grant;
        // a MediaStore URI would be unreadable to the app that pastes it.
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newUri(contentResolver, label, contentUri)
        clipboard.setPrimaryClip(clip)
        showStrip("Copied, long-press to paste")
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
