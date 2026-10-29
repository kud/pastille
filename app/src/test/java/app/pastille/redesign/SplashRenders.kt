package app.pastille.redesign

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import app.pastille.R
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The launch screen as the window draws it before Android 12 (drawable/splash_window.xml under
 * Theme.Pastille.Starting): ground, navy disc, mark and the wordmark 60dp above the bottom, where
 * Android 12+ puts its branding image. A render of the drawable, not a device capture: the system's
 * own Android 12+ splash isn't drawn here. Runs only with PASTILLE_RENDERS=<dir>.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class SplashRenders(private val name: String) {
    @Test fun render() {
        val dir = System.getenv("PASTILLE_RENDERS")
        assumeTrue(dir != null)
        val themed = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_Pastille_Starting)
        val metrics = themed.resources.displayMetrics
        // The pre-12 layer list, layer for layer from the same drawables: the offline Robolectric jars are
        // API 31+, where drawable-v31/splash_window.xml (the plain ground) wins.
        val bitmap = Bitmap.createBitmap(metrics.widthPixels, metrics.heightPixels, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val compat = requireNotNull(themed.getDrawable(androidx.core.splashscreen.R.drawable.compat_splash_screen))
        compat.setBounds(0, 0, bitmap.width, bitmap.height)
        compat.draw(canvas)
        val dp = metrics.density
        val word = requireNotNull(themed.getDrawable(R.drawable.splash_wordmark))
        val w = (200 * dp).toInt()
        val h = (80 * dp).toInt()
        val left = (bitmap.width - w) / 2
        val bottom = bitmap.height - (60 * dp).toInt()
        word.setBounds(left, bottom - h, left + w, bottom)
        word.draw(canvas)
        writePng(bitmap, File(dir!!, "redesign-splash-$name.png"))
    }
}

@Config(qualifiers = "w411dp-h914dp-notnight-xxhdpi") class SplashLight : SplashRenders("light")
@Config(qualifiers = "w411dp-h914dp-night-xxhdpi") class SplashDark : SplashRenders("dark")
