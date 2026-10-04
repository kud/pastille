package app.pastille.ui.theme

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource

@Composable
fun PastilleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) {
                dynamicDarkColorScheme(context).withQuietPrimary(dark = true)
            } else {
                dynamicLightColorScheme(context).withQuietPrimary(dark = false)
            }
        }
        darkTheme -> darkColorScheme(
            primary = Color(0xFFCCC2DC),
            onPrimary = Color(0xFF332D41),
            primaryContainer = Color(0xFF4A4458),
            onPrimaryContainer = Color(0xFFE8DEF8),
            surfaceTint = Color(0xFFCCC2DC),
        )
        else -> lightColorScheme(
            primary = Color(0xFF625B71),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFE8DEF8),
            onPrimaryContainer = Color(0xFF1D192B),
            surfaceTint = Color(0xFF625B71),
        )
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

// M3 draws primary from accent1, the most chromatic palette, and API 34+ roles are more
// vivid still. accent2 is the wallpaper's own quieter palette.
@RequiresApi(Build.VERSION_CODES.S)
@Composable
private fun ColorScheme.withQuietPrimary(dark: Boolean): ColorScheme {
    val accent0 = colorResource(android.R.color.system_accent2_0)
    val accent100 = colorResource(android.R.color.system_accent2_100)
    val accent200 = colorResource(android.R.color.system_accent2_200)
    val accent600 = colorResource(android.R.color.system_accent2_600)
    val accent700 = colorResource(android.R.color.system_accent2_700)
    val accent800 = colorResource(android.R.color.system_accent2_800)
    val accent900 = colorResource(android.R.color.system_accent2_900)
    return if (dark) {
        copy(
            primary = accent200,
            onPrimary = accent800,
            primaryContainer = accent700,
            onPrimaryContainer = accent100,
            inversePrimary = accent600,
            surfaceTint = accent200,
        )
    } else {
        copy(
            primary = accent600,
            onPrimary = accent0,
            primaryContainer = accent100,
            onPrimaryContainer = accent900,
            inversePrimary = accent200,
            surfaceTint = accent600,
        )
    }
}
