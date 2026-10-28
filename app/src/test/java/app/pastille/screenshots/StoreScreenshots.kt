package app.pastille.screenshots

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.pastille.data.PastilleDatabase
import app.pastille.data.SnippetRepository
import app.pastille.images.ImageStore
import app.pastille.ime.KeyboardPanel
import app.pastille.ime.KeyboardTheme
import app.pastille.ime.KeyboardUiState
import app.pastille.ime.LocalReduceMotion
import app.pastille.ime.NoKeyboardActions
import app.pastille.ime.keyboardPalette
import app.pastille.model.CategoryRecord
import app.pastille.model.SnippetRecord
import app.pastille.settings.KeyboardMode
import app.pastille.settings.KeyboardStyle
import app.pastille.settings.PanelHeight
import app.pastille.settings.PastilleSettings
import app.pastille.settings.TOOLBAR_HEIGHT_DP
import app.pastille.settings.panelHeightDp
import app.pastille.ui.SettingsScreen
import app.pastille.ui.SnippetEditorScreen
import app.pastille.ui.SnippetListScreen
import app.pastille.ui.photoPermission
import app.pastille.ui.theme.PastilleTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

/**
 * The store and README screenshots, rendered from the real composables on invented fixtures.
 * Run with `./scripts/screenshots.sh`; excluded from ordinary test runs (see app/build.gradle.kts).
 * Every name, place and line of text here is made up: never put real data in this file.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class StoreScreenshots {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var db: PastilleDatabase
    private lateinit var repository: SnippetRepository
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(app, PastilleDatabase::class.java).allowMainThreadQueries().build()
        repository = SnippetRepository.forDatabase(db)
        // A set-up phone: keyboard enabled, photos allowed, so no onboarding card covers the list.
        shadowOf(app).grantPermissions(photoPermission())
        val pastille = InputMethodInfo(app.packageName, "app.pastille.ime.PastilleIme", "Pastille", null)
        listOf<Context>(app, compose.activity).forEach { context ->
            shadowOf(context.getSystemService(InputMethodManager::class.java))
                .setEnabledInputMethodInfoList(listOf(pastille))
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun snippetList() {
        seedSnippets()
        PastilleSettings.forContext(app).appStickersMode = false
        compose.setContent {
            PastilleTheme(darkTheme = false, dynamicColor = false) {
                SnippetListScreen(repository = repository, onCreate = {}, onEdit = {}, onOpenSettings = {})
            }
        }
        compose.waitForText("Running late")
        compose.capture("1-snippet-list")
    }

    @Test
    fun keyboardSnippets() {
        val folders = listOf(
            CategoryRecord(id = 1, name = "Work", position = 0),
            CategoryRecord(id = 2, name = "Travel", position = 1),
            CategoryRecord(id = 3, name = "Replies", position = 2),
        )
        val snippets = invented.mapIndexed { index, (title, text, folder) ->
            SnippetRecord(id = index + 1L, title = title, text = text, categoryId = folder, position = index)
        }
        keyboardScene(
            message = "Here’s the Wi-Fi for tonight: ",
            state = KeyboardUiState(mode = KeyboardMode.Snippets, snippets = snippets, categories = folders),
        )
        compose.waitForText("Running late")
        compose.capture("2-keyboard-snippets")
    }

    @Test
    fun stickerList() {
        seedStickers()
        PastilleSettings.forContext(app).appStickersMode = true
        compose.setContent {
            PastilleTheme(darkTheme = false, dynamicColor = false) {
                SnippetListScreen(repository = repository, onCreate = {}, onEdit = {}, onOpenSettings = {})
            }
        }
        compose.settle()
        compose.capture("3-stickers")
    }

    @Test
    fun keyboardStickers() {
        val stickers = writeStickers().mapIndexed { index, (name, file) ->
            SnippetRecord(
                id = index + 1L, title = name, text = "", imageFile = file,
                imageWidth = STICKER_PX, imageHeight = STICKER_PX, sticker = true, position = index,
            )
        }
        keyboardScene(
            message = "Happy birthday!! ",
            state = KeyboardUiState(mode = KeyboardMode.Stickers, stickers = stickers),
        )
        compose.settle()
        compose.capture("4-keyboard-stickers")
    }

    @Test
    fun snippetEditor() {
        val ids = seedSnippets()
        compose.setContent {
            PastilleTheme(darkTheme = false, dynamicColor = false) {
                SnippetEditorScreen(snippetId = ids.getValue("Out of office"), repository = repository, onDone = {}, onDeleted = {})
            }
        }
        compose.waitForText("Out of office")
        compose.capture("5-snippet-editor")
    }

    @Test
    fun settings() {
        compose.setContent {
            PastilleTheme(darkTheme = false, dynamicColor = false) {
                SettingsScreen(onBack = {})
            }
        }
        compose.settle()
        compose.capture("6-settings")
    }

    // A host app above the keyboard: a plain message field, as any chat app would show.
    private fun keyboardScene(message: String, state: KeyboardUiState) {
        compose.setContent {
            val context = LocalContext.current
            Column(modifier = Modifier.fillMaxSize()) {
                PastilleTheme(darkTheme = false, dynamicColor = false) {
                    Surface(modifier = Modifier.fillMaxWidth().weight(1f), color = MaterialTheme.colorScheme.surface) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            Spacer(Modifier.weight(1f))
                            OutlinedTextField(
                                value = message,
                                onValueChange = {},
                                label = { Text("Message") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                val palette = keyboardPalette(context, KeyboardStyle.GboardDark, dark = true)
                val total = panelHeightDp(PanelHeight.Default, landscape = false, windowHeightDp = 800)
                KeyboardTheme(palette = palette) {
                    CompositionLocalProvider(LocalReduceMotion provides true) {
                        KeyboardPanel(
                            state = state,
                            actions = NoKeyboardActions,
                            contentHeight = (total - TOOLBAR_HEIGHT_DP).dp,
                            previewMode = true,
                        )
                    }
                }
            }
        }
    }

    /** Seeds the invented snippets, oldest first, and returns their ids by title. */
    private fun seedSnippets(): Map<String, Long> = runBlocking {
        val now = System.currentTimeMillis()
        val folders = listOf("Work", "Travel", "Replies").associateWith { repository.createCategory(it, now - DAY) }
        val folderNames = mapOf(1L to "Work", 2L to "Travel", 3L to "Replies")
        invented.reversed().mapIndexed { index, (title, text, folder) ->
            val id = repository.upsert(
                SnippetRecord(title = title, text = text, categoryId = folder?.let { folders.getValue(folderNames.getValue(it)) }),
                now = now - (index + 1) * 3 * HOUR,
            )
            title to id
        }.toMap()
    }

    private fun seedStickers() = runBlocking {
        writeStickers().forEach { (name, file) ->
            val id = repository.insertImage(name, file, STICKER_PX, STICKER_PX)
            repository.setSticker(id, true)
        }
    }

    /** Invented sticker art, drawn here so nothing in the store listing is anyone's real image. */
    private fun writeStickers(): List<Pair<String, String>> {
        val dir = ImageStore.forContext(app).imagesDir().apply { mkdirs() }
        return stickerArt.map { (name, draw) ->
            val bitmap = Bitmap.createBitmap(STICKER_PX, STICKER_PX, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).draw()
            val file = "sticker-${name.lowercase().replace(' ', '-')}.png"
            writePng(bitmap, File(dir, file))
            name to file
        }
    }

    private companion object {
        const val OUT_DIR_PROPERTY = "pastille.screenshots.dir"
        const val STICKER_PX = 384
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR

        private fun paint(colour: Long) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour.toInt() }

        private fun Canvas.outlined(path: Path, fill: Long) {
            drawPath(path, paint(0xFFFFFFFF).apply { style = Paint.Style.STROKE; strokeWidth = 28f; strokeJoin = Paint.Join.ROUND })
            drawPath(path, paint(fill))
        }

        private fun Canvas.label(text: String, fill: Long, ink: Long, size: Float, tilt: Float) {
            save()
            rotate(tilt, 192f, 192f)
            val p = paint(ink).apply { textSize = size; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
            val width = p.measureText(text) + 64f
            val box = RectF(192f - width / 2, 192f - size * 0.9f, 192f + width / 2, 192f + size * 0.6f)
            drawRoundRect(box, 36f, 36f, paint(0xFFFFFFFF).apply { style = Paint.Style.STROKE; strokeWidth = 24f })
            drawRoundRect(box, 36f, 36f, paint(fill))
            drawText(text, 192f, 192f + size * 0.2f, p)
            restore()
        }

        val stickerArt: List<Pair<String, Canvas.() -> Unit>> = listOf(
            "Heart" to {
                val heart = Path().apply {
                    moveTo(192f, 320f)
                    cubicTo(40f, 220f, 40f, 70f, 140f, 70f)
                    cubicTo(170f, 70f, 185f, 90f, 192f, 110f)
                    cubicTo(199f, 90f, 214f, 70f, 244f, 70f)
                    cubicTo(344f, 70f, 344f, 220f, 192f, 320f)
                    close()
                }
                outlined(heart, 0xFFF04452)
            },
            "Yay" to { label("YAY!", 0xFFFFCF5C, 0xFF2B1D14, 96f, -8f) },
            "Sun" to {
                val rays = paint(0xFFF5A524).apply { strokeWidth = 26f; strokeCap = Paint.Cap.ROUND }
                for (i in 0 until 8) {
                    save(); rotate(i * 45f, 192f, 192f); drawLine(192f, 30f, 192f, 80f, rays); restore()
                }
                outlined(Path().apply { addCircle(192f, 192f, 96f, Path.Direction.CW) }, 0xFFFFCF5C)
                val eye = paint(0xFF2B1D14)
                drawCircle(160f, 175f, 12f, eye); drawCircle(224f, 175f, 12f, eye)
                drawArc(RectF(150f, 180f, 234f, 240f), 20f, 140f, false, eye.apply { style = Paint.Style.STROKE; strokeWidth = 10f; strokeCap = Paint.Cap.ROUND })
            },
            "Thanks" to { label("thanks!", 0xFF2F7EF7, 0xFFFFF8ED, 80f, 6f) },
            "Star" to {
                val star = Path()
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) 160f else 66f
                    val a = Math.toRadians((i * 36 - 90).toDouble())
                    val x = 192f + (r * Math.cos(a)).toFloat()
                    val y = 200f + (r * Math.sin(a)).toFloat()
                    if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
                }
                star.close()
                outlined(star, 0xFFFFCF5C)
            },
            "Nope" to { label("nope", 0xFF0B3A78, 0xFFFFF8ED, 96f, -4f) },
            "Coffee" to {
                outlined(Path().apply { addRoundRect(RectF(96f, 130f, 260f, 310f), 28f, 28f, Path.Direction.CW) }, 0xFFFFF8ED)
                drawRoundRect(RectF(96f, 130f, 260f, 170f), 20f, 20f, paint(0xFF8C3214))
                drawArc(RectF(230f, 170f, 320f, 260f), -90f, 180f, false, paint(0xFFFFF8ED).apply { style = Paint.Style.STROKE; strokeWidth = 24f })
                val steam = paint(0xFFFFFFFF).apply { style = Paint.Style.STROKE; strokeWidth = 14f; strokeCap = Paint.Cap.ROUND }
                drawLine(140f, 110f, 150f, 60f, steam); drawLine(190f, 110f, 200f, 50f, steam); drawLine(240f, 110f, 230f, 60f, steam)
            },
            "OK" to { label("OK!", 0xFF3DDC84, 0xFF0B2A14, 110f, 4f) },
        )
    }
}
