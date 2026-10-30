package app.pastille.ui.theme

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.remember
import app.pastille.ime.LocalReduceMotion
import app.pastille.ime.PastilleMotion
import app.pastille.ime.rememberReducedMotion
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
 * Sacha's dark mock-up (docs/design/pastille-sacha-ui-2026-10-28.png), read into Material 3 roles,
 * then quietened (design spec §25, 2026-10-30: « on peut faire juste plus élégant »): structure in
 * ink, a whisper of blue. The light scheme is derived: the same ladder of surfaces turned over.
 *
 * Roles as the screens use them:
 * - surface / background: the ground. surfaceContainer: cards and grouped rows.
 * - surfaceContainerHigh: unselected chips, the editor's body, the search pill.
 * - primaryContainer: the selected half of Snippets | Stickers, a slate value step (with the check).
 * - secondaryContainer: the selected folder chip, tonal buttons; a near-neutral, light in dark.
 * - inverseSurface: the extended FAB, the one strong thing on a screen, in ink rather than colour.
 * - primary: switches, links, the cursor; a muted steel, the only blue left.
 */
private val Dark = darkColorScheme(
    primary = Color(0xFFA9BFD9),
    onPrimary = Color(0xFF1A2A3D),
    primaryContainer = Color(0xFF434E5D),
    onPrimaryContainer = Color(0xFFEEF1F5),
    secondary = Color(0xFFB4C6E2),
    onSecondary = Color(0xFF1E3048),
    secondaryContainer = Color(0xFFD3D9E1),
    onSecondaryContainer = Color(0xFF161B22),
    tertiary = Color(0xFFB4C6E2),
    background = Color(0xFF0F1318),
    onBackground = Color(0xFFE4E7EC),
    surface = Color(0xFF0F1318),
    onSurface = Color(0xFFE4E7EC),
    surfaceVariant = Color(0xFF262C35),
    onSurfaceVariant = Color(0xFFAAB2BE),
    surfaceTint = Color(0xFFA9BFD9),
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
    inversePrimary = Color(0xFF3D5A7C),
)

private val Light = lightColorScheme(
    primary = Color(0xFF3D5A7C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3DAE4),
    onPrimaryContainer = Color(0xFF161B22),
    secondary = Color(0xFF4F607A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC3CBD6),
    onSecondaryContainer = Color(0xFF161B22),
    tertiary = Color(0xFF4F607A),
    background = Color(0xFFF2F5F9),
    onBackground = Color(0xFF161B22),
    surface = Color(0xFFF2F5F9),
    onSurface = Color(0xFF161B22),
    surfaceVariant = Color(0xFFE1E6EE),
    onSurfaceVariant = Color(0xFF4A5361),
    surfaceTint = Color(0xFF3D5A7C),
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
    inversePrimary = Color(0xFFA9BFD9),
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
            // The system's "Remove animations", followed live: screens fade instead of travel.
            val reduceMotion = rememberReducedMotion()
            // MaterialTheme leaves LocalContentColor at its default, black. Only a Surface or Scaffold sets
            // it, so an icon on a screen drawn on a plain background (the editor's header) came out black
            // on the dark ground. onSurface is the ground's own content colour, in both themes.
            CompositionLocalProvider(
                LocalReduceMotion provides reduceMotion,
                LocalContentColor provides colors.onSurface,
                // The ripple everywhere, plus a 3dp focus ring inside the pressed shape, so focus
                // from a keyboard or switch access is never only a faint tint.
                LocalIndication provides rememberPastilleIndication(RectangleShape, ripple = true, ringInset = true),
                content = content,
            )
        },
    )
}
