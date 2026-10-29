package app.pastille

import android.os.Build
import android.view.animation.PathInterpolator
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.pastille.ui.Entrance
import app.pastille.ui.LocalEntrance

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.CompositionLocalProvider
import app.pastille.ime.LocalReduceMotion
import app.pastille.ime.PastilleMotion
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.pastille.data.SnippetRepository
import app.pastille.model.SnippetRecord
import app.pastille.ui.SettingsScreen
import app.pastille.ui.SnippetEditorScreen
import app.pastille.ui.BinScreen
import app.pastille.ui.SnippetListScreen
import app.pastille.ui.theme.PastilleTheme

sealed interface LaunchRequest {
    data class NewSnippet(val categoryId: Long? = null) : LaunchRequest
    data class Edit(val id: Long) : LaunchRequest
    data object Settings : LaunchRequest
    data object AddStickers : LaunchRequest
}

fun Intent?.toLaunchRequest(): LaunchRequest? {
    if (this == null) return null
    if (getBooleanExtra(MainActivity.EXTRA_OPEN_SETTINGS, false)) return LaunchRequest.Settings
    if (getBooleanExtra(MainActivity.EXTRA_ADD_STICKERS, false)) return LaunchRequest.AddStickers
    if (getBooleanExtra(MainActivity.EXTRA_NEW_SNIPPET, false)) {
        val categoryId = if (hasExtra(MainActivity.EXTRA_CATEGORY_ID)) {
            getLongExtra(MainActivity.EXTRA_CATEGORY_ID, -1).takeIf { it >= 0 }
        } else {
            null
        }
        return LaunchRequest.NewSnippet(categoryId)
    }
    if (hasExtra(MainActivity.EXTRA_EDIT_SNIPPET_ID)) {
        val id = getLongExtra(MainActivity.EXTRA_EDIT_SNIPPET_ID, -1)
        if (id >= 0) return LaunchRequest.Edit(id)
    }
    return null
}

class MainActivity : ComponentActivity() {

    private var launchRequest by mutableStateOf<LaunchRequest?>(null)
    private var fromKeyboard by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            launchRequest = intent.toLaunchRequest()
            fromKeyboard = intent.getBooleanExtra(EXTRA_FROM_KEYBOARD, false)
        } else {
            fromKeyboard = savedInstanceState.getBoolean(EXTRA_FROM_KEYBOARD, false)
        }
        // The list settles in as the splash leaves; not after a rotation, nor when the launch opens
        // the editor or Settings straight away.
        val entrance = Entrance(armed = savedInstanceState == null && launchRequest == null)
        holdSplash(splash, entrance)
        setContent {
            PastilleTheme {
                CompositionLocalProvider(LocalEntrance provides entrance) {
                    PastilleApp(
                        launchRequest = launchRequest,
                        onLaunchRequestHandled = { launchRequest = null },
                        fromKeyboard = fromKeyboard,
                        onReturnToKeyboard = {
                            fromKeyboard = false
                            finish()
                        },
                    )
                }
            }
        }
    }

    /**
     * Keeps the system splash up until the mark has assembled ([SPLASH_MARK_MS], Android 12+ with
     * animations on) and the list has its data, so the hand-over never shows an empty list; never
     * past [SPLASH_CAP_MS]. Then a short exit: the mark shrinks a little and fades, the ground fades
     * a beat later onto the list's own ground, and the list's entrance starts underneath. Reduced
     * motion: the splash just fades.
     */
    private fun holdSplash(splash: SplashScreen, entrance: Entrance) {
        val animatedMark = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !PastilleMotion.reduceMotion()
        val markMs = if (animatedMark) SPLASH_MARK_MS else 0L
        splash.setKeepOnScreenCondition {
            val age = entrance.ageMs()
            age < SPLASH_CAP_MS && (age < markMs || (entrance.armed && !entrance.listReady))
        }
        splash.setOnExitAnimationListener { provider ->
            entrance.start()
            val ground = provider.view
            keepWordmark(ground)
            if (PastilleMotion.reduceMotion()) {
                ground.animate().alpha(0f).setDuration(PastilleMotion.EXIT_MS.toLong())
                    .withEndAction { provider.remove() }.start()
                return@setOnExitAnimationListener
            }
            val accelerate = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
            provider.iconView.animate().scaleX(SPLASH_EXIT_SCALE).scaleY(SPLASH_EXIT_SCALE).alpha(0f)
                .setDuration(SPLASH_EXIT_MS).setInterpolator(accelerate).start()
            ground.animate().alpha(0f).setStartDelay(SPLASH_EXIT_DELAY_MS).setDuration(SPLASH_EXIT_MS)
                .setInterpolator(accelerate).withEndAction { provider.remove() }.start()
        }
        // A safety net: should the exit listener never run, the list must not stay invisible.
        window.decorView.postDelayed({ entrance.start() }, SPLASH_CAP_MS + 400)
    }

    /**
     * Before Android 12 the wordmark lives in the launch window's drawing (drawable/splash_window.xml),
     * but core-splashscreen's exit view redraws only the ground and the mark, so the word would vanish
     * the moment the exit starts. Put it back in the same spot, so it leaves with the ground. Android
     * 12+ keeps its branding image in the exit view on its own.
     */
    private fun keepWordmark(ground: android.view.View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        val frame = ground as? android.widget.FrameLayout ?: return
        val dp = resources.displayMetrics.density
        val word = android.widget.ImageView(this).apply {
            setImageResource(R.drawable.splash_wordmark)
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        frame.addView(
            word,
            android.widget.FrameLayout.LayoutParams(
                (200 * dp).toInt(),
                (80 * dp).toInt(),
                android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL,
            ).apply { bottomMargin = (60 * dp).toInt() },
        )
    }

    // Coming back reads the synced file, so changes from the other phone show up.
    override fun onStart() {
        super.onStart()
        (application as PastilleApplication).autoBackup.syncSoon()
    }

    // Android 10+ lets an app read the clipboard only while it has focus, so this is the app's moment
    // to note a recent clip (the keyboard does the same when it opens).
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        (application as PastilleApplication).recentClipboard.recordCurrent(this)
    }

    // Leaving the app writes the automatic backup now rather than after its debounce.
    override fun onStop() {
        super.onStop()
        (application as PastilleApplication).autoBackup.flush()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(EXTRA_FROM_KEYBOARD, fromKeyboard)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchRequest = intent.toLaunchRequest()
        fromKeyboard = intent.getBooleanExtra(EXTRA_FROM_KEYBOARD, false)
    }

    companion object {
        /** How long the Android 12+ mark takes to assemble (drawable-v31/splash_icon.xml). */
        private const val SPLASH_MARK_MS = 520L
        private const val SPLASH_CAP_MS = 1_000L
        private const val SPLASH_EXIT_MS = 180L
        private const val SPLASH_EXIT_DELAY_MS = 60L
        private const val SPLASH_EXIT_SCALE = 0.85f
        const val EXTRA_EDIT_SNIPPET_ID = "app.pastille.EXTRA_EDIT_SNIPPET_ID"
        const val EXTRA_NEW_SNIPPET = "app.pastille.EXTRA_NEW_SNIPPET"
        const val EXTRA_CATEGORY_ID = "app.pastille.EXTRA_CATEGORY_ID"
        const val EXTRA_OPEN_SETTINGS = "app.pastille.EXTRA_OPEN_SETTINGS"
        const val EXTRA_FROM_KEYBOARD = "app.pastille.EXTRA_FROM_KEYBOARD"
        const val EXTRA_ADD_STICKERS = "app.pastille.EXTRA_ADD_STICKERS"
        val LAUNCH_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
}

@Composable
private fun PastilleApp(
    launchRequest: LaunchRequest?,
    onLaunchRequestHandled: () -> Unit,
    fromKeyboard: Boolean,
    onReturnToKeyboard: () -> Unit,
) {
    var editingId: Long? by rememberSaveable { mutableStateOf<Long?>(null) }
    var creating: Boolean by rememberSaveable { mutableStateOf(false) }
    var newCategoryId: Long? by rememberSaveable { mutableStateOf<Long?>(null) }
    var showingSettings by rememberSaveable { mutableStateOf(false) }
    var showingBin by rememberSaveable { mutableStateOf(false) }
    var pendingUndo by remember { mutableStateOf<SnippetRecord?>(null) }
    var addStickersRequested by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val repository = remember { SnippetRepository.forContext(context) }
    val screenStates = rememberSaveableStateHolder()

    LaunchedEffect(launchRequest) {
        when (launchRequest) {
            is LaunchRequest.NewSnippet -> {
                editingId = null
                creating = true
                newCategoryId = launchRequest.categoryId
            }
            is LaunchRequest.Edit -> {
                editingId = launchRequest.id
                creating = false
                newCategoryId = null
            }
            LaunchRequest.AddStickers -> {
                editingId = null
                creating = false
                newCategoryId = null
                showingBin = false
                addStickersRequested = true
            }
            LaunchRequest.Settings -> {
                editingId = null
                creating = false
                newCategoryId = null
                showingSettings = true
                onLaunchRequestHandled()
                return@LaunchedEffect
            }
            null -> return@LaunchedEffect
        }
        showingSettings = false
        onLaunchRequestHandled()
    }

    fun closeEditor() {
        editingId = null
        creating = false
        newCategoryId = null
        if (fromKeyboard) onReturnToKeyboard()
    }

    fun closeSettings() {
        showingSettings = false
        if (fromKeyboard) onReturnToKeyboard()
    }

    BackHandler(enabled = editingId != null || creating || showingSettings || showingBin) {
        if (editingId != null || creating) {
            closeEditor()
        } else if (showingSettings) {
            closeSettings()
        } else {
            showingBin = false
        }
    }

    val screen: AppScreen = when {
        editingId != null || creating -> AppScreen.Editor(editingId, newCategoryId)
        showingSettings -> AppScreen.Settings
        showingBin -> AppScreen.Bin
        else -> AppScreen.List
    }
    val reduceMotion = LocalReduceMotion.current
    AnimatedContent(
        targetState = screen,
        transitionSpec = { PastilleMotion.screenTransition(forward = targetState.depth > initialState.depth, reduceMotion = reduceMotion) },
        contentKey = { it::class },
        label = "screen",
    ) { shown ->
        when (shown) {
            is AppScreen.Editor -> SnippetEditorScreen(
                snippetId = shown.id,
                repository = repository,
                onDone = ::closeEditor,
                onDeleted = { pendingUndo = it; closeEditor() },
                initialCategoryId = shown.categoryId,
            )
            AppScreen.Settings -> SettingsScreen(onBack = ::closeSettings)
            AppScreen.Bin -> BinScreen(repository = repository, onBack = { showingBin = false })
            // Keeps the list's open folder tab and search while the editor or settings are on screen.
            AppScreen.List -> screenStates.SaveableStateProvider("list") {
                SnippetListScreen(
                    repository = repository,
                    onCreate = { categoryId ->
                        creating = true
                        newCategoryId = categoryId
                    },
                    onEdit = { snippet: SnippetRecord -> editingId = snippet.id },
                    onOpenSettings = { showingSettings = true },
                    onOpenBin = { showingBin = true },
                    deletedSnippet = pendingUndo,
                    onDeletedShown = { pendingUndo = null },
                    addStickersRequested = addStickersRequested,
                    onAddStickersHandled = { addStickersRequested = false },
                )
            }
        }
    }
}

/** The app's screens, by depth: going deeper slides forward, coming back slides back. */
private sealed class AppScreen(val depth: Int) {
    data object List : AppScreen(0)
    data object Settings : AppScreen(1)
    data object Bin : AppScreen(1)
    data class Editor(val id: Long?, val categoryId: Long?) : AppScreen(1)
}
