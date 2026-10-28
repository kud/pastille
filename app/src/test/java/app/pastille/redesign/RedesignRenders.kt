package app.pastille.redesign

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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
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
        listOf<Context>(app, compose.activity).forEach {
            shadowOf(it.getSystemService(InputMethodManager::class.java)).setEnabledInputMethodInfoList(listOf(pastille))
        }
    }
    @Test fun render() = when (screen) {
        "list" -> list()
        "editor" -> editor()
        "settings" -> settings()
        "stickers" -> stickers()
        "keyboard" -> keyboard()
        "clipboard" -> keyboard(clipboard = true)
        "share" -> share()
        "qr" -> qr()
        "import" -> importOne()
        "import-backup" -> importBackup()
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
        wait("Running late"); capture("list")
    }
    private fun editor() {
        val ids = seed()
        compose.setContent { PastilleTheme(darkTheme = dark) { SnippetEditorScreen(snippetId = ids.getValue("Wi-Fi at the cottage"), repository = repository, onDone = {}, onDeleted = {}) } }
        wait("Wi-Fi at the cottage"); capture("editor")
    }
    private fun settings() {
        compose.setContent { PastilleTheme(darkTheme = dark) { SettingsScreen(onBack = {}) } }
        settle(); capture("settings")
        compose.onNodeWithText("Licence").performScrollTo(); settle(); capture("settings-lower")
    }
    private fun stickers() {
        val dir = ImageStore.forContext(app).imagesDir().apply { mkdirs() }
        val colours = listOf(0xFF1A7BF2, 0xFFF2B33D, 0xFF3DBE8B, 0xFFE8EFF9, 0xFFB46CE0, 0xFFF06A5A)
        runBlocking {
            colours.forEachIndexed { i, c ->
                val bmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
                Canvas(bmp).drawCircle(128f, 128f, 110f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c.toInt() })
                val file = "sticker-$i.png"
                File(dir, file).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                repository.setSticker(repository.insertImage("Sticker $i", file, 256, 256), true)
            }
        }
        PastilleSettings.forContext(app).appStickersMode = true
        compose.setContent { PastilleTheme(darkTheme = dark) { SnippetListScreen(repository = repository, onCreate = {}, onEdit = {}, onOpenSettings = {}) } }
        settle(); settle(); capture("stickers")
    }
    private fun keyboard(clipboard: Boolean = false) {
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
        )
        compose.setContent {
            PastilleTheme(darkTheme = dark) {
                val palette = keyboardPalette(compose.activity, KeyboardStyle.Auto, dark)
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.surface),
                    contentAlignment = androidx.compose.ui.Alignment.BottomCenter,
                ) {
                    KeyboardTheme(palette = palette) {
                        KeyboardPanel(state = state, actions = NoKeyboardActions, contentHeight = 300.dp)
                    }
                }
            }
        }
        val wide = if (compose.activity.resources.configuration.screenWidthDp >= 400) "-wide" else ""
        if (clipboard) {
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
class EditorDark : RedesignRenders(true, "editor")
class EditorLight : RedesignRenders(false, "editor")
class SettingsDark : RedesignRenders(true, "settings")
class SettingsLight : RedesignRenders(false, "settings")
class StickersDark : RedesignRenders(true, "stickers")
class StickersLight : RedesignRenders(false, "stickers")
class Icon : RedesignRenders(true, "icon")
class KeyboardDark : RedesignRenders(true, "keyboard")
class KeyboardLight : RedesignRenders(false, "keyboard")
class ClipboardDark : RedesignRenders(true, "clipboard")
class ClipboardLight : RedesignRenders(false, "clipboard")
@Config(qualifiers = "w411dp-h860dp-xxhdpi") class KeyboardWideDark : RedesignRenders(true, "keyboard")
class ShareDark : RedesignRenders(true, "share")
class ShareLight : RedesignRenders(false, "share")
class QrLight : RedesignRenders(false, "qr")
class ImportDark : RedesignRenders(true, "import")
class ImportLight : RedesignRenders(false, "import")
class ImportBackupLight : RedesignRenders(false, "import-backup")

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
