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
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import app.pastille.settings.KeyboardMode
import app.pastille.settings.KeyboardStyle
import app.pastille.settings.PanelHeight
import app.pastille.settings.PastilleSettings
import app.pastille.settings.TOOLBAR_HEIGHT_DP
import app.pastille.settings.panelHeightDp
import app.pastille.settings.effectiveMode
import app.pastille.share.MAX_TEXT_CHARS
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
    ViewModelStoreOwner,
    KeyboardActions {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val viewModelStoreHolder = ViewModelStore()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var refreshJob: Job? = null

    // Bumped each time the keyboard opens, so the image grid picks up new ones.
    private val openCount = mutableIntStateOf(0)

    // Mirrors the service configuration, so the panel follows dark mode and wallpaper colours without recreating the view.
    private val configuration = mutableStateOf(Configuration(), neverEqualPolicy())

    private var destroyed = false

    private val settings by lazy { PastilleSettings.forContext(this) }

    private val panelState = mutableStateOf<PanelState>(PanelState.Browse)
    private val mode = mutableStateOf(KeyboardMode.Snippets)
    private val folderId = mutableStateOf<Long?>(null)
    private val sourceId = mutableStateOf<Long?>(null)
    private val pickerStyle = mutableStateOf(KeyboardStyle.Auto)
    private val reduceMotion = mutableStateOf(false)
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
        mode.value = settings.keyboardMode
        folderId.value = settings.keyboardCategoryId
        sourceId.value = settings.imageSourceBucketId
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
        view.setContent { PanelHost() }
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        openCount.intValue++
        reduceMotion.value = PastilleMotion.reduceMotion()
        if (!restarting) {
            if (settings.keyboardStyleChosen) {
                panelState.value = PanelState.Browse
            } else {
                pickerStyle.value = settings.keyboardStyle
                panelState.value = PanelState.Style
            }
        }
        hasSelection = info != null &&
            info.initialSelStart >= 0 &&
            info.initialSelEnd >= 0 &&
            info.initialSelStart != info.initialSelEnd
        moveLifecycleTo(Lifecycle.State.RESUMED)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && isInputViewShown) {
            when {
                panelState.value == PanelState.Style -> {
                    onStyleNotNow()
                    return true
                }
                panelState.value == PanelState.ImageFolders -> {
                    panelState.value = PanelState.Settings
                    return true
                }
                panelState.value != PanelState.Browse -> {
                    panelState.value = PanelState.Browse
                    return true
                }
                mode.value == KeyboardMode.Snippets && folderId.value != null -> {
                    onOpenFolder(null)
                    return true
                }
            }
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

    // Gboard paints the gesture-bar area with its tray colour; do the same so there's no black band.
    private fun applyNavigationBar(palette: KeyboardPalette) {
        val window = window.window ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            @Suppress("DEPRECATION")
            window.navigationBarColor = palette.tray.toArgb()
        }
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = palette.isLight
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

    private fun showSnippet(id: Long, categoryId: Long?) {
        mode.value = KeyboardMode.Snippets
        settings.keyboardMode = KeyboardMode.Snippets
        onOpenFolder(categoryId)
        highlightedSnippetId.value = id
    }

    override fun onModeChange(mode: KeyboardMode) {
        this.mode.value = mode
        settings.keyboardMode = mode
    }

    override fun onOpenFolder(categoryId: Long?) {
        folderId.value = categoryId
        settings.keyboardCategoryId = categoryId
    }

    override fun onBack() {
        when (panelState.value) {
            PanelState.Style -> onStyleNotNow()
            PanelState.ImageFolders -> panelState.value = PanelState.Settings
            else -> panelState.value = PanelState.Browse
        }
    }

    override fun onOpenAdd() {
        refreshJob?.cancel()
        panelState.value = PanelState.Add
        addSources.value = AddSources(readHostText(), readClipboard())
    }

    override fun onAddFrom(source: AddSource, categoryId: Long?) {
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
                        onAction = { showSnippet(duplicate.id, duplicate.categoryId) },
                    )
                }
                return@launch
            }
            val id = repository.upsert(SnippetRecord(text = raw, categoryId = categoryId))
            val title = repository.get(id)?.title.orEmpty()
            withContext(Dispatchers.Main) {
                if (destroyed) return@withContext
                panelState.value = PanelState.Browse
                showStrip(
                    message = "Saved \"$title\"",
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
                showSnippet(id, categoryId)
            }
        }
    }

    override fun onEditSnippet(snippet: SnippetRecord) {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_EDIT_SNIPPET_ID, snippet.id)
            .addFlags(MainActivity.LAUNCH_FLAGS)
        startActivity(intent)
        panelState.value = PanelState.Browse
    }

    override fun onDeleteSnippet(snippet: SnippetRecord) {
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

    override fun onMoveToCategory(snippetId: Long, categoryId: Long?) {
        serviceScope.launch {
            val repository = SnippetRepository.forContext(this@PastilleImeService)
            repository.setCategory(listOf(snippetId), categoryId)
            val name = categoryId?.let { id -> repository.getCategories().find { it.id == id }?.name }
            withContext(Dispatchers.Main) {
                if (destroyed) return@withContext
                showStrip(if (name != null) "Moved to $name" else "Moved to the top level")
            }
        }
    }

    override fun onSnippetLongPress(snippet: SnippetRecord) {
        panelState.value = PanelState.Actions(snippet.id)
    }

    override fun onSelectSource(bucketId: Long) {
        sourceId.value = bucketId
        settings.imageSourceBucketId = bucketId
    }

    override fun onImageLongPress(image: ImageItem) {
        panelState.value = PanelState.Preview(image)
    }

    override fun onStripDismiss(key: Long) {
        if (strip.value?.key == key) strip.value = null
    }

    override fun onHighlightShown() {
        highlightedSnippetId.value = null
    }

    override fun onPickStyle(style: KeyboardStyle) {
        pickerStyle.value = style
    }

    override fun onStyleDone() {
        settings.keyboardStyle = pickerStyle.value
        settings.keyboardStyleChosen = true
        panelState.value = PanelState.Browse
    }

    override fun onStyleNotNow() {
        settings.keyboardStyle = KeyboardStyle.Auto
        settings.keyboardStyleChosen = true
        panelState.value = PanelState.Browse
    }

    override fun onOpenSettings() {
        refreshJob?.cancel()
        panelState.value = PanelState.Settings
    }

    override fun onOpenAllSettings() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(MainActivity.LAUNCH_FLAGS)
            .putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true)
        panelState.value = PanelState.Browse
        startActivity(intent)
        requestHideSelf(0)
    }

    override fun onSetStyle(style: KeyboardStyle) {
        settings.keyboardStyle = style
        settings.keyboardStyleChosen = true
    }

    override fun onSetHeight(height: PanelHeight) {
        val landscape = configuration.value.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (landscape) settings.panelHeightLandscape = height else settings.panelHeightPortrait = height
    }

    override fun onSetReturn(image: Boolean, enabled: Boolean) {
        if (image) settings.returnAfterImage = enabled else settings.returnAfterSnippet = enabled
    }

    override fun onSetModeEnabled(mode: KeyboardMode, enabled: Boolean) {
        when (mode) {
            KeyboardMode.Snippets -> settings.snippetsEnabled = enabled
            KeyboardMode.Images -> settings.imagesEnabled = enabled
        }
    }

    override fun onOpenReorder() {
        panelState.value = PanelState.Reorder
    }

    override fun onReorderSnippets(shown: List<SnippetRecord>, newOrder: List<Long>) {
        serviceScope.launch { SnippetRepository.forContext(this@PastilleImeService).setSnippetOrder(shown, newOrder) }
    }

    override fun onOpenImageFolders() {
        panelState.value = PanelState.ImageFolders
    }

    override fun onSetShownSources(bucketIds: Set<Long>) {
        settings.enabledImageSources = bucketIds
    }

    @Composable
    private fun PanelHost() {
        val context = LocalContext.current
        val repository = remember { SnippetRepository.forContext(context) }
        val snippetsFlow = remember { repository.observeSnippets() }
        val snippets by snippetsFlow.collectAsState(initial = emptyList())
        val categoriesFlow = remember { repository.observeCategories() }
        val categories by categoriesFlow.collectAsState(initial = emptyList())
        val settingsFlow = remember { settings.changes() }
        val settingsTick by settingsFlow.collectAsState(initial = Unit)
        var imageSources by remember { mutableStateOf(emptyList<ImageSource>()) }
        var images by remember { mutableStateOf(emptyList<ImageItem>()) }
        var hasImagePermission by remember { mutableStateOf(ImageSourceReader.hasPermission(context)) }

        val config = configuration.value
        val darkTheme = config.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
        val savedStyle = remember(settingsTick) { settings.keyboardStyle }
        val heightPreset = remember(settingsTick, landscape) {
            if (landscape) settings.panelHeightLandscape else settings.panelHeightPortrait
        }
        val style = if (panelState.value == PanelState.Style) pickerStyle.value else savedStyle
        val palette = remember(style, darkTheme, config) { keyboardPalette(context, style, darkTheme) }
        val totalHeight = panelHeightDp(heightPreset, landscape, config.screenHeightDp)

        SideEffect { applyNavigationBar(palette) }

        val snippetsOn = remember(settingsTick) { settings.snippetsEnabled }
        val imagesOn = remember(settingsTick) { settings.imagesEnabled }
        val shownMode = effectiveMode(mode.value, snippetsOn, imagesOn)
        val enabledSourceIds = remember(settingsTick) { settings.enabledImageSources }
        val needsSources = shownMode == KeyboardMode.Images ||
            panelState.value == PanelState.Settings ||
            panelState.value == PanelState.ImageFolders
        LaunchedEffect(openCount.intValue, needsSources, enabledSourceIds) {
            if (!needsSources) return@LaunchedEffect
            hasImagePermission = ImageSourceReader.hasPermission(context)
            imageSources = withContext(Dispatchers.IO) {
                runCatching { ImageSourceReader.listSources(context) }.getOrDefault(emptyList())
            }
            val resolved = resolveSource(visibleSources(imageSources, enabledSourceIds), sourceId.value)
            if (resolved?.bucketId != sourceId.value) sourceId.value = resolved?.bucketId
        }

        LaunchedEffect(openCount.intValue, shownMode, sourceId.value) {
            if (shownMode != KeyboardMode.Images) return@LaunchedEffect
            val bucket = sourceId.value ?: return@LaunchedEffect
            images = withContext(Dispatchers.IO) {
                runCatching { ImageSourceReader.readRecent(context, bucket) }.getOrDefault(emptyList())
            }
        }

        LaunchedEffect(categories, folderId.value) {
            val current = folderId.value ?: return@LaunchedEffect
            if (categories.isNotEmpty() && categories.none { it.id == current }) onOpenFolder(null)
        }

        val state = KeyboardUiState(
            mode = shownMode,
            snippetsEnabled = snippetsOn,
            imagesEnabled = imagesOn,
            snippets = snippets,
            categories = categories,
            folderId = folderId.value,
            imageSources = imageSources,
            sourceId = sourceId.value,
            images = images,
            hasImagePermission = hasImagePermission,
            panelState = panelState.value,
            addSources = addSources.value,
            strip = strip.value,
            highlightedSnippetId = highlightedSnippetId.value,
            pickerStyle = pickerStyle.value,
            darkTheme = darkTheme,
            savedStyle = savedStyle,
            heightPreset = heightPreset,
            landscape = landscape,
            returnAfterSnippet = remember(settingsTick) { settings.returnAfterSnippet },
            returnAfterImage = remember(settingsTick) { settings.returnAfterImage },
            enabledSourceIds = enabledSourceIds,
        )
        KeyboardTheme(palette = palette) {
            CompositionLocalProvider(LocalReduceMotion provides reduceMotion.value) {
                KeyboardPanel(
                    state = state,
                    actions = this@PastilleImeService,
                    contentHeight = (totalHeight - TOOLBAR_HEIGHT_DP).dp,
                )
            }
        }
    }

    override fun onSnippetTap(snippet: SnippetRecord) {
        if (snippet.isImage) {
            insertImageSnippet(snippet)
            return
        }
        val committed = currentInputConnection?.commitText(snippet.text, 1) == true
        serviceScope.launch { SnippetRepository.forContext(this@PastilleImeService).recordUse(snippet.id) }
        if (committed) returnToPreviousKeyboardIfWanted(image = false)
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

    override fun onWriteInApp(categoryId: Long?) {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_NEW_SNIPPET, true)
            .addFlags(MainActivity.LAUNCH_FLAGS)
        if (categoryId != null) {
            intent.putExtra(MainActivity.EXTRA_CATEGORY_ID, categoryId)
        }
        startActivity(intent)
        panelState.value = PanelState.Browse
    }

    override fun onImageTap(image: ImageItem) {
        serviceScope.launch {
            val mimeType = try {
                contentResolver.getType(image.uri)
            } catch (_: Exception) {
                null
            } ?: "image/png"
            val contentUri = try {
                copyToSharedCache(image, mimeType)
            } catch (_: Exception) {
                null
            }
            withContext(Dispatchers.Main) {
                if (destroyed) return@withContext
                if (contentUri == null) {
                    showStrip("Couldn't read that image")
                    return@withContext
                }
                if (panelState.value is PanelState.Preview) panelState.value = PanelState.Browse
                insertImage(contentUri, mimeType, "Pastille image")
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
                returnToPreviousKeyboardIfWanted(image = true)
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

    private fun copyToSharedCache(image: ImageItem, mimeType: String): Uri {
        val dir = File(cacheDir, "shared").apply { mkdirs() }
        val extension = if (mimeType == "image/jpeg") "jpg" else mimeType.substringAfter('/')
        val out = File(dir, "image-${System.currentTimeMillis()}.$extension")
        val input = contentResolver.openInputStream(image.uri)
            ?: throw IOException("Cannot open ${image.uri}")
        input.use { stream ->
            out.outputStream().use { output -> stream.copyTo(output) }
        }
        return FileProvider.getUriForFile(this, "$packageName.fileprovider", out)
    }

    override fun onOpenApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(MainActivity.LAUNCH_FLAGS)
        startActivity(intent)
    }

    private fun returnToPreviousKeyboardIfWanted(image: Boolean) {
        val wanted = if (image) settings.returnAfterImage else settings.returnAfterSnippet
        if (wanted) onSwitchKeyboard()
    }

    override fun onSwitchKeyboard() {
        if (destroyed) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            switchToPreviousInputMethod()
        } else {
            getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        }
    }
}
