package app.pastille.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** One cell of a [SegmentSquare]: its label and its glyph, drawn in the cell's content colour. */
class Segment<T>(val value: T, val label: String, val glyph: @Composable () -> Unit)

/**
 * « Un carré de segments » (Erwann, 2026-10-28): four choices as one connected, rounded block of
 * four equal cells, two by two, each a glyph over its label. Only the block's outer corners round;
 * the cells share 1dp borders in the outline colour, so it reads as one control, not four chips.
 *
 * On is never colour alone: a chosen cell takes the fill, a heavier label and a check badge in its
 * top-end corner. [multi] makes the cells checkboxes (several on), otherwise radios (exactly one).
 * [refuse] says a press would leave no valid state (the last content tab): it gets the bump, not
 * the select, and nothing changes.
 */
@Composable
fun <T> SegmentSquare(
    segments: List<Segment<T>>,
    isOn: (T) -> Boolean,
    onPress: (T) -> Unit,
    modifier: Modifier = Modifier,
    multi: Boolean = false,
    refuse: (T) -> Boolean = { false },
) {
    require(segments.size == 4) { "A segment square has four cells" }
    val haptics = rememberHaptics()
    val outer = 16.dp
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(outer))
            // Cells touch (Erwann, 2026-10-28: « des segments 2×2 collés »): the hairline between them is
            // the block's own outline colour showing through a 1dp seam, so the borders are shared.
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(outer))
            .background(MaterialTheme.colorScheme.outlineVariant)
            .padding(1.dp)
            .then(if (multi) Modifier else Modifier.selectableGroup()),
        verticalArrangement = Arrangement.spacedBy(SEAM),
    ) {
        segments.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(SEAM)) {
                row.forEach { segment ->
                    val on = isOn(segment.value)
                    val press = {
                        if (refuse(segment.value)) haptics.bump() else { haptics.select(); onPress(segment.value) }
                    }
                    val action = if (multi) {
                        Modifier.toggleable(value = on, role = Role.Checkbox, onValueChange = { press() })
                    } else {
                        Modifier.selectable(selected = on, role = Role.RadioButton, onClick = press)
                    }
                    SegmentCell(segment, on, Modifier.weight(1f).then(action))
                }
            }
        }
    }
}

@Composable
private fun <T> SegmentCell(segment: Segment<T>, on: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (on) colors.secondaryContainer else colors.surfaceContainerHigh, tween(150), label = "fill")
    val ink = if (on) colors.onSecondaryContainer else colors.onSurface
    Box(modifier = modifier.height(76.dp).background(fill)) {
        CompositionLocalProvider(LocalContentColor provides ink) {
            Column(
                modifier = Modifier.fillMaxWidth().align(Alignment.Center).padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { segment.glyph() }
                Text(
                    text = segment.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (on) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(colors.onSecondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.secondaryContainer, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

private val SEAM = 1.dp
