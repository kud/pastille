package app.pastille.ime

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.pastille.settings.KeyboardStyle

@Immutable
data class KeyboardPalette(
    val strip: Color,
    val tray: Color,
    val key: Color,
    val keyPressed: Color,
    val stripButton: Color,
    val label: Color,
    val labelSecondary: Color,
    val icon: Color,
    val accent: Color,
    val onAccent: Color,
    val isLight: Boolean,
    // M3 Expressive key shapes: only Material You on Android 16+.
    val expressive: Boolean = false,
)

// Sampled from Gboard's Dark theme on a 1080×2340 screenshot.
val GboardDark = KeyboardPalette(
    strip = Color(0xFF292E32),
    tray = Color(0xFF363B3F),
    key = Color(0xFF565B5F),
    keyPressed = Color(0xFF646A6E),
    stripButton = Color(0xFF45494C),
    label = Color(0xFFFFFFFF),
    labelSecondary = Color(0xFFB1B2B5),
    icon = Color(0xFFACADB1),
    accent = Color(0xFF5F97F6),
    onAccent = Color(0xFFFFFFFF),
    isLight = false,
)

// Provisional: from recollection of Gboard's default Light theme, not sampled yet.
val GboardLight = KeyboardPalette(
    strip = Color(0xFFE8EAED),
    tray = Color(0xFFF1F3F4),
    key = Color(0xFFFFFFFF),
    keyPressed = Color(0xFFE3E5E8),
    stripButton = Color(0xFFDADCE0),
    label = Color(0xFF202124),
    labelSecondary = Color(0xFF5F6368),
    icon = Color(0xFF5F6368),
    accent = Color(0xFF1A73E8),
    onAccent = Color(0xFFFFFFFF),
    isLight = true,
)

// Gboard's dynamic theme: tinted neutral tray, neutral keys, secondary function keys, primary Enter.
fun gboardMaterialYou(scheme: ColorScheme, dark: Boolean, expressive: Boolean): KeyboardPalette {
    val tray = if (dark) scheme.surfaceContainerLow else scheme.surfaceContainer
    return KeyboardPalette(
        strip = tray,
        tray = tray,
        key = if (dark) scheme.surfaceContainerHighest else scheme.surfaceContainerLowest,
        keyPressed = if (dark) scheme.surfaceBright else scheme.surfaceContainerHigh,
        stripButton = scheme.secondaryContainer,
        label = scheme.onSurface,
        labelSecondary = scheme.onSurfaceVariant,
        icon = scheme.onSurfaceVariant,
        accent = scheme.primary,
        onAccent = scheme.onPrimary,
        isLight = !dark,
        expressive = expressive,
    )
}

fun materialYouAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

// Auto means Gboard's own default for this Android version: dynamic on 12+, Dark/Light below.
fun resolveStyle(style: KeyboardStyle, materialYouAvailable: Boolean): KeyboardStyle = when {
    style == KeyboardStyle.Auto && materialYouAvailable -> KeyboardStyle.MaterialYou
    style == KeyboardStyle.MaterialYou && !materialYouAvailable -> KeyboardStyle.Auto
    else -> style
}

fun keyboardPalette(context: Context, style: KeyboardStyle, dark: Boolean): KeyboardPalette =
    when (resolveStyle(style, materialYouAvailable())) {
        KeyboardStyle.GboardDark -> GboardDark
        KeyboardStyle.GboardLight -> GboardLight
        KeyboardStyle.MaterialYou -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            gboardMaterialYou(scheme, dark, expressive = Build.VERSION.SDK_INT >= 36)
        } else {
            if (dark) GboardDark else GboardLight
        }
        KeyboardStyle.Auto -> if (dark) GboardDark else GboardLight
    }

fun KeyboardPalette.toColorScheme(): ColorScheme {
    val base = if (isLight) lightColorScheme() else darkColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        surface = tray,
        onSurface = label,
        onSurfaceVariant = icon,
        surfaceContainerLowest = key,
        surfaceContainerLow = tray,
        surfaceContainer = tray,
        surfaceContainerHigh = key,
        surfaceContainerHighest = key,
        surfaceBright = key,
        outline = key,
        outlineVariant = key,
        secondaryContainer = stripButton,
        onSecondaryContainer = label,
    )
}

val LocalKeyboardPalette = staticCompositionLocalOf { GboardDark }

@Composable
fun KeyboardTheme(palette: KeyboardPalette, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = palette.toColorScheme()) {
        CompositionLocalProvider(
            LocalKeyboardPalette provides palette,
            LocalContentColor provides palette.label,
            content = content,
        )
    }
}
