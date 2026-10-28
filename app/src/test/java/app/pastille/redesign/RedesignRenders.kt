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
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
