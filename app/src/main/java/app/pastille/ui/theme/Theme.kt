package app.pastille.ui.theme

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import app.pastille.ime.LocalReduceMotion
import app.pastille.ime.PastilleMotion
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/*
 * Sacha's dark mock-up (docs/design/pastille-sacha-ui-2026-10-28.png), read into Material 3 roles.
 * The light scheme is derived: same blue hue, the same ladder of surfaces turned over (cards a step
 * lighter than the ground), never a second accent.
 *
 * Roles as the screens use them:
 * - surface / background: the ground. surfaceContainer: cards and grouped rows.
 * - surfaceContainerHigh: unselected chips, the editor's body, the search pill.
 * - primaryContainer: the selected half of Snippets | Stickers.
 * - secondaryContainer: the selected folder chip and the extended FAB (Sacha's light tonal fill
 *   with dark text in the dark theme).
 * - primary: switches, links, the one strong accent.
 */
private val Dark = darkColorScheme(
    primary = Color(0xFF8EC1FF),
    onPrimary = Color(0xFF00315F),
    primaryContainer = Color(0xFF1C4C82),
    onPrimaryContainer = Color(0xFFD5E5FF),
    secondary = Color(0xFFB4C6E2),
    onSecondary = Color(0xFF1E3048),
    secondaryContainer = Color(0xFF9CCBFF),
    onSecondaryContainer = Color(0xFF062A4F),
    tertiary = Color(0xFFB4C6E2),
    background = Color(0xFF0F1318),
    onBackground = Color(0xFFE4E7EC),
    surface = Color(0xFF0F1318),
    onSurface = Color(0xFFE4E7EC),
    surfaceVariant = Color(0xFF262C35),
    onSurfaceVariant = Color(0xFFAAB2BE),
    surfaceTint = Color(0xFF8EC1FF),
    surfaceContainerLowest = Color(0xFF0A0D11),
    surfaceContainerLow = Color(0xFF141920),
    surfaceContainer = Color(0xFF181E26),
    surfaceContainerHigh = Color(0xFF1F2630),
    surfaceContainerHighest = Color(0xFF29313C),
    surfaceBright = Color(0xFF2F3743),
    outline = Color(0xFF6B7482),
    outlineVariant = Color(0xFF323A45),
    inverseSurface = Color(0xFFE4E7EC),
    inverseOnSurface = Color(0xFF1C2128),
    inversePrimary = Color(0xFF1662C4),
)

private val Light = lightColorScheme(
    primary = Color(0xFF1662C4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E3FB),
    onPrimaryContainer = Color(0xFF062A4F),
    secondary = Color(0xFF4F607A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC4DBFA),
    onSecondaryContainer = Color(0xFF062A4F),
    tertiary = Color(0xFF4F607A),
    background = Color(0xFFF2F5F9),
    onBackground = Color(0xFF161B22),
    surface = Color(0xFFF2F5F9),
    onSurface = Color(0xFF161B22),
    surfaceVariant = Color(0xFFE1E6EE),
    onSurfaceVariant = Color(0xFF4A5361),
    surfaceTint = Color(0xFF1662C4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFE6EBF2),
    surfaceContainerHighest = Color(0xFFDCE2EB),
    surfaceBright = Color(0xFFFFFFFF),
    outline = Color(0xFF737C89),
    outlineVariant = Color(0xFFD3D9E2),
    inverseSurface = Color(0xFF2A3038),
    inverseOnSurface = Color(0xFFEEF1F5),
    inversePrimary = Color(0xFF8EC1FF),
)

/** Tabular figures wherever numbers sit in a column or tick over: times, counts, sizes. */
private fun TextStyle.tabular() = copy(fontFeatureSettings = "tnum")

private val PastilleTypography = Typography().let {
    it.copy(
        bodySmall = it.bodySmall.tabular(),
        labelLarge = it.labelLarge.tabular(),
        labelMedium = it.labelMedium.tabular(),
        labelSmall = it.labelSmall.tabular(),
    )
}

/**
 * The brand palette by default: the redesign gives Pastille its own look, so the wallpaper no
 * longer recolours it. `dynamicColor` stays as a parameter for callers that still pass it.
 */
@Composable
fun PastilleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) Dark else Light
    MaterialTheme(
        colorScheme = colors,
        typography = PastilleTypography,
        content = {
            // Every activity reads the system's "Remove animations" once; screens fade instead of travel.
            val reduceMotion = remember { PastilleMotion.reduceMotion() }
            // MaterialTheme leaves LocalContentColor at its default, black. Only a Surface or Scaffold sets
            // it, so an icon on a screen drawn on a plain background (the editor's header) came out black
            // on the dark ground. onSurface is the ground's own content colour, in both themes.
            CompositionLocalProvider(
                LocalReduceMotion provides reduceMotion,
                LocalContentColor provides colors.onSurface,
                content = content,
            )
        },
    )
}
