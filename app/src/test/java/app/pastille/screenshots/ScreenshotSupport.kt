package app.pastille.screenshots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

/*
 * Shared by the store renders. Every name, place and line of text here is made up: never put real data
 * in this file.
 */

internal typealias ScreenRule = AndroidComposeTestRule<*, ComponentActivity>

/** fastlane's phoneScreenshots folder, handed over by app/build.gradle.kts under -Pscreenshots. */
internal val phoneScreenshotsDir: File by lazy {
    val path = requireNotNull(System.getProperty("pastille.screenshots.dir")) { "run through scripts/screenshots.sh" }
    File(path).apply { mkdirs() }
}

/** fastlane's images folder, home of icon.png and featureGraphic.png. */
internal val listingImagesDir: File get() = requireNotNull(phoneScreenshotsDir.parentFile)

/** The README icon, relative to the app module, where Gradle runs the tests. */
internal const val README_ICON_PATH = "../assets/icon.png"

internal val BRAND_INK = Color(0xFF0B3A78)
internal val BRAND_CREAM = Color(0xFFFFF8ED)

/** Invented snippets: title, text, folder (1 Work, 2 Travel, 3 Replies). */
internal val invented: List<Triple<String, String, Long?>> = listOf(
    Triple("Running late", "Running about ten minutes late, sorry! Start without me.", 3L),
    Triple("Wi-Fi at the cottage", "Network: Bluebell-Guest\nPassword: kettle-on-at-four", 2L),
    Triple("Out of office", "Thanks for your message. I’m away until Monday and will reply when I’m back.", 1L),
    Triple("Weekly call link", "https://meet.example.com/weekly-catch-up", 1L),
    Triple("Thank you", "Thank you so much, that was really kind of you.", 3L),
    Triple("Packing list", "Charger, passport, adapter, sun cream, the good socks.", 2L),
    Triple("Sign-off", "Best wishes,\nAlex", 1L),
    Triple("On my way", "On my way! See you in twenty minutes.", 3L),
    Triple("Train times", "Back on the 18:42, platform 3, change at the junction.", 2L),
)

internal fun ScreenRule.waitForText(text: String) {
    waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    settle()
}

/** Lets images load and animations land before a capture. */
internal fun ScreenRule.settle() {
    repeat(4) {
        waitForIdle()
        Thread.sleep(250)
        mainClock.advanceTimeBy(500)
    }
    waitForIdle()
}

internal fun ScreenRule.capture(name: String, dir: File = phoneScreenshotsDir) {
    val bitmap = runOnUiThread {
        val view = activity.window.decorView
        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }
    writePng(bitmap, File(dir, "$name.png"))
}

/** The adaptive launcher icon, background and foreground, clipped to a circle. */
internal fun launcherIcon(context: Context, px: Int): Bitmap {
    val icon = context.getDrawable(context.applicationInfo.icon) as AdaptiveIconDrawable
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.clipPath(Path().apply { addCircle(px / 2f, px / 2f, px / 2f, Path.Direction.CW) })
    // Adaptive layers are 108dp with a 72dp safe zone: draw them a quarter larger on every side.
    listOfNotNull(icon.background, icon.foreground).forEach { layer ->
        layer.setBounds(-px / 4, -px / 4, px + px / 4, px + px / 4)
        layer.draw(canvas)
    }
    return bitmap
}

/**
 * A plain RGBA PNG encoder. Robolectric's native Bitmap.compress is avoided on purpose: it is one more
 * native path in a JVM that already aborts easily.
 */
internal fun writePng(bitmap: Bitmap, file: File) {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height).also { bitmap.getPixels(it, 0, width, 0, 0, width, height) }
    val raw = ByteArrayOutputStream()
    DeflaterOutputStream(raw).use { deflate ->
        val row = ByteArray(1 + width * 4)
        for (y in 0 until height) {
            row[0] = 0
            for (x in 0 until width) {
                val argb = pixels[y * width + x]
                val i = 1 + x * 4
                row[i] = (argb shr 16).toByte()
                row[i + 1] = (argb shr 8).toByte()
                row[i + 2] = argb.toByte()
                row[i + 3] = (argb ushr 24).toByte()
            }
            deflate.write(row)
        }
    }
    file.parentFile?.mkdirs()
    DataOutputStream(file.outputStream().buffered()).use { out ->
        out.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
        val header = ByteArrayOutputStream().also {
            DataOutputStream(it).apply {
                writeInt(width); writeInt(height)
                writeByte(8); writeByte(6); writeByte(0); writeByte(0); writeByte(0)
            }
        }
        out.chunk("IHDR", header.toByteArray())
        out.chunk("IDAT", raw.toByteArray())
        out.chunk("IEND", ByteArray(0))
    }
}

private fun DataOutputStream.chunk(type: String, data: ByteArray) {
    val typeBytes = type.toByteArray(Charsets.US_ASCII)
    writeInt(data.size)
    write(typeBytes)
    write(data)
    writeInt(CRC32().apply { update(typeBytes); update(data) }.value.toInt())
}
