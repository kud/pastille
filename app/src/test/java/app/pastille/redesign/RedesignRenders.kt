package app.pastille.redesign

import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.performTouchInput
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.AdaptiveIconDrawable
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.pastille.backup.AddOneDialog
import app.pastille.backup.BackupArchive
import app.pastille.backup.ImportConfirmDialog
import app.pastille.clipboard.RecentClip
import app.pastille.ime.KeyboardPanel
import app.pastille.ime.KeyboardTheme
import app.pastille.ime.KeyboardUiState
import app.pastille.ime.NoKeyboardActions
import app.pastille.ime.keyboardPalette
import app.pastille.model.CategoryRecord
import app.pastille.qr.QrScreen
import app.pastille.qr.SnippetQr
import app.pastille.ime.PanelState
import app.pastille.settings.KeyboardMode
import app.pastille.settings.KeyboardStyle
import app.pastille.share.ShareSheet
import app.pastille.share.ShareStatus
import app.pastille.share.ShareUiState
import app.pastille.ui.itemPhrase
import app.pastille.data.PastilleDatabase
import app.pastille.data.SnippetRepository
import app.pastille.images.ImageStore
import app.pastille.model.SnippetRecord
import app.pastille.settings.PastilleSettings
import app.pastille.ui.SettingsScreen
import app.pastille.ui.SnippetEditorScreen
import app.pastille.ui.SnippetListScreen
import app.pastille.ui.photoPermission
import app.pastille.ui.theme.PastilleTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

/** Local design renders, invented data only. Runs only with PASTILLE_RENDERS=<dir>. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
abstract class RedesignRenders(private val dark: Boolean, private val screen: String) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var db: PastilleDatabase
    private lateinit var repository: SnippetRepository
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val out get() = File(System.getenv("PASTILLE_RENDERS")!!)
    private val suffix get() = if (dark) "dark" else "light"

    @Before fun setUp() {
        assumeTrue(System.getenv("PASTILLE_RENDERS") != null)
        db = Room.inMemoryDatabaseBuilder(app, PastilleDatabase::class.java).allowMainThreadQueries().build()
        repository = SnippetRepository.forDatabase(db)
        shadowOf(app).grantPermissions(photoPermission())
        val pastille = InputMethodInfo(app.packageName, "app.pastille.ime.PastilleIme", "Pastille", null)
        // Settings › Switch back to lists the other enabled keyboards: two invented ones there.
        val others = if (screen.startsWith("settings")) listOf(
            InputMethodInfo("com.example.keys", "com.example.keys.Ime", "Gboard", null),
            InputMethodInfo("com.example.voice", "com.example.voice.Ime", "Voice typing", null),
        ) else emptyList()
        listOf<Context>(app, compose.activity).forEach {
            shadowOf(it.getSystemService(InputMethodManager::class.java)).setEnabledInputMethodInfoList(listOf(pastille) + others)
        }
    }
    @Test fun render() = when (screen) {
        "list" -> list()
        "list-wide" -> list()
        "list-menu" -> listMenu()
        "editor" -> editor()
        "settings" -> settings()
        "settings-wide" -> settings(name = "settings-wide")
        "settings-height" -> settingsHeight("settings-height")
        "settings-height-wide" -> settingsHeight("settings-height-wide")
        "stickers" -> stickers()
        "keyboard" -> keyboard()
        "clipboard" -> keyboard(clipboard = true)
        "keyboard-settings" -> keyboard(settingsPanel = true)
        "share" -> share()
        "qr" -> qr()
        "import" -> importOne()
        "import-backup" -> importBackup()
        "store-keyboard" -> keyboard(host = true)
        "store-clipboard" -> keyboard(clipboard = true, host = true)
        "store-icon" -> storeIcon()
        else -> icon()
    }

    @After fun tearDown() { if (::db.isInitialized) db.close() }

    private fun seed(): Map<String, Long> = runBlocking {
        val now = System.currentTimeMillis()
        val f = listOf("Work", "Travel", "Replies").associateWith { repository.createCategory(it, now - 86_400_000L) }
        listOf(
            Triple("Thanks!", "Thank you so much, that's really kind.", "Replies") to 30 * 3_600_000L,
            Triple("Home address", "12 Example Street, Springfield", "Travel") to 28 * 3_600_000L,
            Triple("Meeting link", "https://meet.example.com/abc-defg-hij", "Work") to 26 * 3_600_000L,
            Triple("Wi-Fi at the cottage", "Network: Cottage-Guest · Password: blue-heron-42", "Travel") to 25 * 3_600_000L,
            Triple("Running late", "Sorry, running about ten minutes late, start without me.", "Work") to 5 * 60_000L,
        ).associate { (t, ago) ->
            t.first to repository.upsert(SnippetRecord(title = t.first, text = t.second, categoryId = f.getValue(t.third)), now = now - ago)
        }
    }

    private fun wait(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        settle()
    }
    private fun settle() = repeat(4) { compose.waitForIdle(); Thread.sleep(200); compose.mainClock.advanceTimeBy(500) }

    private fun capture(name: String) {
        val bitmap = compose.runOnUiThread {
            val v = compose.activity.window.decorView
            Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
        }
        writePng(bitmap, File(out, "redesign-$name-$suffix.png"))
    }

    private fun list() {
        seed(); PastilleSettings.forContext(app).appStickersMode = false
        compose.setContent { PastilleTheme(darkTheme = dark) { SnippetListScreen(repository = repository, onCreate = {}, onEdit = {}, onOpenSettings = {}) } }
        val wide = compose.activity.resources.configuration.screenWidthDp >= 400
        wait("Running late"); capture(if (wide) "list-wide" else "list")
        if (wide) {
            // The folder row's end, where the new-folder chip sits beside the folders.
            compose.onAllNodesWithText("Replies")[0].performTouchInput { swipeLeft() }
            settle(); capture("list-wide-end")
        }
    }
    private fun listMenu() {
        seed(); PastilleSettings.forContext(app).appStickersMode = false
        compose.setContent { PastilleTheme(darkTheme = dark) { SnippetListScreen(repository = repository, onCreate = {}, onEdit = {}, onOpenSettings = {}) } }
        wait("Running late")
        compose.onNodeWithContentDescription("More options").performClick()
        wait("Reorder"); captureWindows("list-menu")
    }
    private fun editor() {
        val ids = seed()
        compose.setContent { PastilleTheme(darkTheme = dark) { SnippetEditorScreen(snippetId = ids.getValue("Wi-Fi at the cottage"), repository = repository, onDone = {}, onDeleted = {}) } }
        wait("Wi-Fi at the cottage"); capture("editor")
    }
    private fun settings(name: String = "settings") {
        compose.setContent { PastilleTheme(darkTheme = dark) { SettingsScreen(onBack = {}) } }
        settle(); capture(name)
        compose.onNodeWithText("Licence").performScrollTo(); settle(); capture("$name-lower")
    }
    private fun settingsHeight(name: String) {
        compose.setContent { PastilleTheme(darkTheme = dark) { SettingsScreen(onBack = {}) } }
        settle(); compose.onNodeWithText("Height").performClick(); settle(); capture(name)
        // The landscape row drives the preview: change it to see the preview turn sideways.
        compose.onAllNodesWithText("Tall")[1].performClick(); settle(); capture("$name-landscape")
    }
    private fun stickers() {
        val dir = ImageStore.forContext(app).imagesDir().apply { mkdirs() }
        // Invented die-cut stickers: a word on a coloured badge with a white edge. Written with writePng,
        // not Bitmap.compress, to keep one native path out of a JVM that aborts easily.
        val stickers = listOf(
            "Thanks!" to 0xFF1A7BF2, "On it" to 0xFFF2B33D, "Yay!" to 0xFF3DBE8B,
            "Soon" to 0xFFB46CE0, "OK!" to 0xFFF06A5A, "Brb" to 0xFF0B3A78,
            "Nice!" to 0xFF3DBE8B, "Hi!" to 0xFFF06A5A, "Same" to 0xFF1A7BF2,
            "Wow" to 0xFF0B3A78, "Ta!" to 0xFFB46CE0, "Lol" to 0xFFF2B33D,
        )
        runBlocking {
            stickers.forEachIndexed { i, (word, c) ->
                val bmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                val round = i % 2 == 0
                fun shape(inset: Float) = if (round) canvas.drawCircle(128f, 128f, 118f - inset, paint)
                else canvas.drawRoundRect(10f + inset, 34f + inset, 246f - inset, 222f - inset, 48f, 48f, paint)
                paint.color = 0x33000000; canvas.save(); canvas.translate(0f, 6f); shape(0f); canvas.restore()
                paint.color = 0xFFFFFFFF.toInt(); shape(0f)
                paint.color = c.toInt(); shape(12f)
                paint.color = if (c == 0xFFF2B33DL) 0xFF0B3A78.toInt() else 0xFFFFFFFF.toInt()
                paint.textAlign = Paint.Align.CENTER
                paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                paint.textSize = if (word.length > 4) 50f else 64f
                canvas.drawText(word, 128f, 128f - (paint.ascent() + paint.descent()) / 2, paint)
                val file = "sticker-$i.png"
                writePng(bmp, File(dir, file))
                repository.setSticker(repository.insertImage(word, file, 256, 256), true)
            }
        }
        // Decode once on the test thread first. The cards decode on Dispatchers.IO, and the first native
        // decode on a worker thread aborted the JVM (exit 134, JniConstants "Class not found:
        // java/nio/FloatBuffer"): run here, the JNI class table is set up from a thread that can load it.
        stickers.indices.forEach { ImageStore.forContext(app).loadThumbnail("sticker-$it.png", 512) }
        PastilleSettings.forContext(app).appStickersMode = true
        compose.setContent { PastilleTheme(darkTheme = dark) { SnippetListScreen(repository = repository, onCreate = {}, onEdit = {}, onOpenSettings = {}) } }
        repeat(4) { settle() }; capture("stickers")
    }
    private fun keyboard(clipboard: Boolean = false, settingsPanel: Boolean = false, host: Boolean = false) {
        val now = System.currentTimeMillis()
        val state = KeyboardUiState(
            darkTheme = dark,
            categories = listOf(
                CategoryRecord(id = 1, name = "Work"),
                CategoryRecord(id = 2, name = "Travel"),
                CategoryRecord(id = 3, name = "Replies"),
            ),
            snippets = listOf(
                SnippetRecord(id = 11, title = "Running late", text = "Sorry, running about ten minutes late, start without me.", categoryId = 1),
                SnippetRecord(id = 12, title = "Wi-Fi at the cottage", text = "Network: Cottage-Guest · Password: blue-heron-42", categoryId = 2),
                SnippetRecord(id = 13, title = "Meeting link", text = "https://meet.example.com/abc-defg-hij", categoryId = 1),
                SnippetRecord(id = 14, title = "Thanks!", text = "Thank you so much, that's really kind.", categoryId = 3),
                SnippetRecord(id = 15, title = "Home address", text = "12 Example Street, Springfield", categoryId = 2),
                SnippetRecord(id = 16, title = "On my way", text = "On my way!", categoryId = 3),
            ),
            recentClips = listOf(
                RecentClip(1, "Order #48213 ships Thursday", now - 60_000),
                RecentClip(2, "alex@example.com", now - 300_000),
                RecentClip(3, "See you at the station at 6", now - 900_000),
            ),
            clipboardEnabled = true,
            mode = if (clipboard) KeyboardMode.Clipboard else KeyboardMode.Snippets,
            panelState = if (settingsPanel) PanelState.Settings else PanelState.Browse,
            stickersEnabled = !settingsPanel,
        )
        compose.setContent {
            PastilleTheme(darkTheme = dark) {
                val palette = keyboardPalette(compose.activity, KeyboardStyle.Auto, dark)
                if (host) {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                        ChatHost(
                            field = if (clipboard) "Order #48213 ships Thursday" else "Sorry, running about ten minutes late, start without me.",
                            modifier = Modifier.weight(1f),
                        )
                        KeyboardTheme(palette = palette) {
                            KeyboardPanel(state = state, actions = NoKeyboardActions, contentHeight = 300.dp)
                        }
                    }
                    return@PastilleTheme
                }
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.surface),
                    contentAlignment = androidx.compose.ui.Alignment.BottomCenter,
                ) {
                    KeyboardTheme(palette = palette) {
                        // The settings panel scrolls in 300dp; the render opens it tall to show every row at once.
                        KeyboardPanel(state = state, actions = NoKeyboardActions, contentHeight = if (settingsPanel) 600.dp else 300.dp)
                    }
                }
            }
        }
        val wide = if (compose.activity.resources.configuration.screenWidthDp >= 400) "-wide" else ""
        if (host) {
            wait("Sam"); capture(if (clipboard) "store-clipboard" else "store-keyboard")
        } else if (settingsPanel) {
            wait("Show in the keyboard"); capture("keyboard-settings$wide")
        } else if (clipboard) {
            wait("Order #48213 ships Thursday"); capture("clipboard$wide")
        } else {
            wait("Running late"); capture("keyboard$wide")
        }
    }
    private fun share() {
        val state = ShareUiState(
            saving = false,
            status = ShareStatus.entries.first { it.name == "SAVED" },
            textTitle = "Wi-Fi at the cottage",
            textBody = "Network: Cottage-Guest · Password: blue-heron-42",
            selectedCategoryId = 2,
            canUndo = true,
        )
        val folders = listOf("Work", "Travel", "Replies").mapIndexed { i, n -> CategoryRecord(id = i + 1L, name = n) }
        compose.setContent {
            PastilleTheme(darkTheme = dark) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF7A8794)))
                ShareSheet(state, folders, {}, {}, {}, {}, {}, {})
            }
        }
        wait("Saved to Pastille"); captureWindows("share")
    }
    private fun qr() {
        val code = SnippetQr.encode("Network: Cottage-Guest · Password: blue-heron-42")!!
        compose.setContent { PastilleTheme(darkTheme = dark) { QrScreen(title = "Wi-Fi at the cottage", code = code, onClose = {}) } }
        wait("Wi-Fi at the cottage"); capture("qr")
    }
    private fun importOne() {
        compose.setContent {
            PastilleTheme(darkTheme = dark) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.surface))
                AddOneDialog(
                    phrase = itemPhrase("Wi-Fi at the cottage", "", false, false),
                    preview = "Network: Cottage-Guest · Password: blue-heron-42",
                    onAdd = {}, onCancel = {},
                )
            }
        }
        wait("Add"); captureWindows("import")
    }
    private fun importBackup() {
        compose.setContent {
            PastilleTheme(darkTheme = dark) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.surface))
                ImportConfirmDialog(counts = BackupArchive.Counts(24, 3, 6), onImport = {}, onCancel = {})
            }
        }
        wait("Import"); captureWindows("import-backup")
    }

    /** Every window, bottom to top: dialogs and sheets live in their own, outside the activity's decor view. */
    private fun captureWindows(name: String) {
        val bitmap = compose.runOnUiThread {
            val wmg = Class.forName("android.view.WindowManagerGlobal")
            val global = wmg.getMethod("getInstance").invoke(null)
            @Suppress("UNCHECKED_CAST")
            val views = (wmg.getDeclaredField("mViews").apply { isAccessible = true }.get(global) as List<android.view.View>).toList()
            val base = compose.activity.window.decorView
            val bmp = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            base.draw(c)
            views.filter { it !== base && it.width > 0 && it.isShown }.forEach { v ->
                val small = v.width < base.width || v.height < base.height
                if (small) c.drawColor(0x99000000.toInt())
                c.save(); c.translate(((base.width - v.width) / 2).toFloat(), ((base.height - v.height) / 2).toFloat())
                v.draw(c); c.restore()
            }
            bmp
        }
        writePng(bitmap, File(out, "redesign-$name-$suffix.png"))
    }

    /**
     * The listing and README icons from the adaptive launcher icon, full bleed and unmasked (the store and
     * the launcher apply their own mask), the same geometry as assets/icon.svg: colour at 512 (fastlane)
     * and 1024 (assets), and the monochrome layer in black on transparent at 1024.
     */
    private fun storeIcon() {
        val icon = app.getDrawable(app.applicationInfo.icon) as AdaptiveIconDrawable
        fun draw(px: Int, layers: List<android.graphics.drawable.Drawable>): Bitmap {
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            layers.forEach { it.setBounds(0, 0, px, px); it.draw(c) }
            return bmp
        }
        val colour = listOfNotNull(icon.background, icon.foreground)
        writePng(draw(512, colour), File(out, "redesign-store-icon-512.png"))
        writePng(draw(1024, colour), File(out, "redesign-store-icon-1024.png"))
        val mono = requireNotNull(icon.monochrome).mutate().apply { setTint(0xFF000000.toInt()) }
        writePng(draw(1024, listOf(mono)), File(out, "redesign-store-icon-monochrome-1024.png"))
    }

    private fun icon() {
        assumeTrue(dark)
        val icon = app.getDrawable(app.applicationInfo.icon) as AdaptiveIconDrawable
        val px = 432
        val bmp = Bitmap.createBitmap(px * 2, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        listOf(listOfNotNull(icon.background, icon.foreground), listOfNotNull(icon.background, icon.monochrome)).forEachIndexed { i, layers ->
            c.save(); c.translate(i * px.toFloat(), 0f)
            c.clipPath(Path().apply { addRoundRect(0f, 0f, px.toFloat(), px.toFloat(), 90f, 90f, Path.Direction.CW) })
            layers.forEach { it.setBounds(-px / 4, -px / 4, px + px / 4, px + px / 4); it.draw(c) }
            c.restore()
        }
        writePng(bmp, File(out, "redesign-icon.png"))
        // The store and README icons: the launcher's background and foreground, full bleed, no mask,
        // the same geometry as assets/icon.svg (copy them to assets/icon.png and fastlane's icon.png).
        listOf(1024, 512).forEach { size ->
            val square = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val sc = Canvas(square)
            listOfNotNull(icon.background, icon.foreground).forEach { it.setBounds(0, 0, size, size); it.draw(sc) }
            writePng(square, File(out, "icon-$size.png"))
        }
    }
}

// One class per render: the build forks a JVM per class when PASTILLE_RENDERS is set, because
// Robolectric's native canvas aborts the process when several renders share one.
class ListDark : RedesignRenders(true, "list")
class ListLight : RedesignRenders(false, "list")
class ListMenuLight : RedesignRenders(false, "list-menu")
class ListMenuDark : RedesignRenders(true, "list-menu")
@Config(qualifiers = "w411dp-h860dp-xxhdpi") class ListWideDark : RedesignRenders(true, "list-wide")
class EditorDark : RedesignRenders(true, "editor")
class EditorLight : RedesignRenders(false, "editor")
class SettingsDark : RedesignRenders(true, "settings")
class SettingsLight : RedesignRenders(false, "settings")
@Config(qualifiers = "w411dp-h860dp-xxhdpi") class SettingsWideDark : RedesignRenders(true, "settings-wide")
@Config(qualifiers = "w411dp-h860dp-xxhdpi") class SettingsWideLight : RedesignRenders(false, "settings-wide")
class SettingsHeightDark : RedesignRenders(true, "settings-height")
class SettingsHeightLight : RedesignRenders(false, "settings-height")
@Config(qualifiers = "w411dp-h860dp-xxhdpi") class SettingsHeightWideDark : RedesignRenders(true, "settings-height-wide")
@Config(qualifiers = "w411dp-h860dp-xxhdpi") class SettingsHeightWideLight : RedesignRenders(false, "settings-height-wide")
class StickersDark : RedesignRenders(true, "stickers")
class StickersLight : RedesignRenders(false, "stickers")
class Icon : RedesignRenders(true, "icon")
class KeyboardDark : RedesignRenders(true, "keyboard")
class KeyboardLight : RedesignRenders(false, "keyboard")
class ClipboardDark : RedesignRenders(true, "clipboard")
class ClipboardLight : RedesignRenders(false, "clipboard")
@Config(qualifiers = "w411dp-h860dp-xxhdpi") class KeyboardWideDark : RedesignRenders(true, "keyboard")
class KeyboardSettingsDark : RedesignRenders(true, "keyboard-settings")
class KeyboardSettingsLight : RedesignRenders(false, "keyboard-settings")
@Config(qualifiers = "w411dp-h860dp-xxhdpi") class KeyboardSettingsWideDark : RedesignRenders(true, "keyboard-settings")
class ShareDark : RedesignRenders(true, "share")
class ShareLight : RedesignRenders(false, "share")
class QrLight : RedesignRenders(false, "qr")
class ImportDark : RedesignRenders(true, "import")
class ImportLight : RedesignRenders(false, "import")
class ImportBackupLight : RedesignRenders(false, "import-backup")
// The store listing (scripts/store-images.sh): the keyboard over an invented chat, and the icons.
class StoreKeyboard : RedesignRenders(false, "store-keyboard")
class StoreClipboard : RedesignRenders(false, "store-clipboard")
class StoreIcon : RedesignRenders(false, "store-icon")

/** An invented messaging screen, so the keyboard reads as a keyboard. Not any real app's chrome. */
@androidx.compose.runtime.Composable
private fun ChatHost(field: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(scheme.tertiaryContainer), contentAlignment = Alignment.Center) {
                Text("S", style = MaterialTheme.typography.titleMedium, color = scheme.onTertiaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Text("Sam", style = MaterialTheme.typography.titleLarge, color = scheme.onSurface)
        }
        Spacer(Modifier.weight(1f))
        Bubble("Are we still on for tonight?", mine = false)
        Bubble("Yes! Leaving now", mine = true)
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()
                .clip(RoundedCornerShape(24.dp)).background(scheme.surfaceContainerHigh)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) { Text(field, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface) }
    }
}

@androidx.compose.runtime.Composable
private fun Bubble(text: String, mine: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (mine) scheme.onPrimaryContainer else scheme.onSurface,
            modifier = Modifier.clip(RoundedCornerShape(20.dp))
                .background(if (mine) scheme.primaryContainer else scheme.surfaceContainerHighest)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

private fun writePng(bitmap: Bitmap, file: File) {
    val w = bitmap.width; val h = bitmap.height
    val px = IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
    val raw = ByteArrayOutputStream()
    DeflaterOutputStream(raw).use { d ->
        val row = ByteArray(1 + w * 4)
        for (y in 0 until h) { row[0] = 0
            for (x in 0 until w) { val a = px[y * w + x]; val i = 1 + x * 4
                row[i] = (a shr 16).toByte(); row[i + 1] = (a shr 8).toByte(); row[i + 2] = a.toByte(); row[i + 3] = (a ushr 24).toByte() }
            d.write(row) }
    }
    file.parentFile?.mkdirs()
    DataOutputStream(file.outputStream().buffered()).use { o ->
        o.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
        val hd = ByteArrayOutputStream().also { DataOutputStream(it).apply { writeInt(w); writeInt(h); writeByte(8); writeByte(6); writeByte(0); writeByte(0); writeByte(0) } }
        fun chunk(t: String, data: ByteArray) { val tb = t.toByteArray(); o.writeInt(data.size); o.write(tb); o.write(data); o.writeInt(CRC32().apply { update(tb); update(data) }.value.toInt()) }
        chunk("IHDR", hd.toByteArray()); chunk("IDAT", raw.toByteArray()); chunk("IEND", ByteArray(0))
    }
}
