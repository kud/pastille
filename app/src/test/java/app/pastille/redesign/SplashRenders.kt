package app.pastille.redesign

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Animatable
import android.os.Looper
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import app.pastille.R
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/**
 * The launch screen as Theme.Pastille.Starting lays it out: the ground, then splash_icon (mark, navy
 * disc and "Pastille") at 288dp in the centre, the Android 12+ assembly run to its end. A render of
 * the drawables, not a device capture. Runs only with PASTILLE_RENDERS=<dir>.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class SplashRenders(private val name: String) {
    @Test fun render() {
        val dir = System.getenv("PASTILLE_RENDERS")
        assumeTrue(dir != null)
        val themed = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_Pastille_Starting)
        val metrics = themed.resources.displayMetrics
        val bitmap = Bitmap.createBitmap(metrics.widthPixels, metrics.heightPixels, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(themed.getColor(R.color.splash_background))
        val icon = requireNotNull(themed.getDrawable(R.drawable.splash_icon))
        val size = (288 * metrics.density).toInt()
        val left = (bitmap.width - size) / 2
        val top = (bitmap.height - size) / 2
        icon.setBounds(left, top, left + size, top + size)
        (icon as? Animatable)?.let {
            it.start()
            icon.draw(canvas) // a software canvas runs the animation on the main looper
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
            canvas.drawColor(themed.getColor(R.color.splash_background))
        }
        icon.draw(canvas)
        writePng(bitmap, File(dir!!, "redesign-splash-$name.png"))
    }
}

@Config(qualifiers = "w411dp-h914dp-notnight-xxhdpi") class SplashLight : SplashRenders("light")
@Config(qualifiers = "w411dp-h914dp-night-xxhdpi") class SplashDark : SplashRenders("dark")
