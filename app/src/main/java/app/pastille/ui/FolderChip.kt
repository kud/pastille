package app.pastille.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pastille.ime.LocalReduceMotion
import app.pastille.ime.PastilleMotion

/**
 * The folder chip of §20, shared by the list's folder row and the share sheet: 36dp tall in a 48dp
 * target, filled `secondaryContainer` and a heavier label when selected, the glyph shown when
 * selected (or always, for "All"), so the selection is a shape as well as a fill.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FolderChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    alwaysIcon: Boolean = false,
    role: Role = Role.Tab,
) {
    val colors = MaterialTheme.colorScheme
    val reduceMotion = LocalReduceMotion.current
    val container by animateColorAsState(
        targetValue = if (selected) colors.secondaryContainer else colors.surfaceContainerHigh,
        animationSpec = PastilleMotion.screenSpring(),
        label = "tabContainer",
    )
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(36.dp)
            .clip(CircleShape)
            .background(container)
            .semantics { this.selected = selected }
            .combinedClickable(role = role, onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (selected) colors.onSecondaryContainer else colors.onSurface
        // Selecting never changes the chip's width, so the row never reflows (Erwann, 2026-10-28:
        // « ça jump »). Both looks are laid out on top of each other, the box takes the wider (the
        // selected one, glyph and heavier label), and only their opacity moves. Sacha: "All" keeps its
        // grid glyph; a folder shows its glyph only when selected, so selection is a shape and a heavier
        // label as well as a fill, never colour alone.
        val selectedAlpha by animateFloatAsState(
            targetValue = if (selected) 1f else 0f,
            animationSpec = if (reduceMotion) tween(150) else PastilleMotion.screenSpring(),
            label = "chipSelection",
        )
        Box(contentAlignment = Alignment.Center) {
            ChipFace(label, icon, showIcon = true, bold = true, tint = tint, modifier = Modifier.alpha(selectedAlpha))
            ChipFace(label, icon, showIcon = alwaysIcon, bold = false, tint = tint, modifier = Modifier.alpha(1f - selectedAlpha).clearAndSetSemantics {})
        }
    }
}


@Composable
private fun ChipFace(label: String, icon: ImageVector, showIcon: Boolean, bold: Boolean, tint: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (showIcon) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal),
            color = tint,
            maxLines = 1,
        )
    }
}
