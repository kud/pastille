package app.pastille.ime

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import app.pastille.settings.PanelHeight

// A phone outline with the keyboard as a bar rising from its foot, one step taller per preset, so
// the Height choices read by shape: Compact is a low bar, Tall nearly fills the screen. Drawn in
// black and tinted by Icon, like any Material glyph.
private val glyphs: Map<PanelHeight, ImageVector> = PanelHeight.entries.associateWith { height ->
    val step = PanelHeight.entries.indexOf(height)
    val barTop = 16.5f - step * 3.5f
    ImageVector.Builder(
        name = "Height.${height.name}",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // The screen: a 12 by 18 outline, 1.5 wide, corners 2.
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f) {
            moveTo(8f, 3.75f)
            horizontalLineTo(16f)
            arcTo(1.25f, 1.25f, 0f, false, true, 17.25f, 5f)
            verticalLineTo(19f)
            arcTo(1.25f, 1.25f, 0f, false, true, 16f, 20.25f)
            horizontalLineTo(8f)
            arcTo(1.25f, 1.25f, 0f, false, true, 6.75f, 19f)
            verticalLineTo(5f)
            arcTo(1.25f, 1.25f, 0f, false, true, 8f, 3.75f)
            close()
        }
        // The keyboard: a solid bar inside it, from the foot up.
        path(fill = SolidColor(Color.Black)) {
            moveTo(8.5f, barTop)
            horizontalLineTo(15.5f)
            verticalLineTo(18.5f)
            horizontalLineTo(8.5f)
            close()
        }
    }.build()
}

internal fun heightGlyph(height: PanelHeight): ImageVector = glyphs.getValue(height)
