package app.pastille.ime

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pastille.clipboard.RecentClip

// Recent clipboard: one row at the top of Snippets, only while there are clips. A tap pastes,
// a long press opens the clip's actions (Paste, Save as snippet, Remove); Clear empties the row.
@Composable
internal fun RecentClipsRow(clips: List<RecentClip>, actions: KeyboardActions) {
    val palette = LocalKeyboardPalette.current
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "recent-heading") {
            Icon(
                Icons.Rounded.History,
                contentDescription = null,
                tint = palette.icon,
                modifier = Modifier
                    .size(20.dp)
                    .semantics {
                        contentDescription = "Recent clipboard"
                        heading()
                    },
            )
        }
        items(clips, key = { it.id }) { clip ->
            ClipChip(
                text = oneLine(clip.text),
                icon = null,
                onClick = { actions.onClipTap(clip) },
                onClickLabel = "Paste",
                onLongClick = { actions.onClipLongPress(clip) },
            )
        }
        item(key = "recent-clear") {
            ClipChip(
                text = "Clear",
                icon = Icons.Rounded.ClearAll,
                onClick = actions::onClearClips,
                onClickLabel = "Clear recent clipboard",
                onLongClick = null,
            )
        }
    }
}

private fun oneLine(text: String): String {
    val joined = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
    return if (joined.length > 80) joined.take(80) + "…" else joined
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClipChip(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    onClickLabel: String,
    onLongClick: (() -> Unit)?,
) {
    val palette = LocalKeyboardPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val color by animateColorAsState(
        targetValue = if (pressed) palette.keyPressed else palette.key,
        animationSpec = tween(60),
        label = "clipColour",
    )
    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .height(32.dp)
            .widthIn(max = 180.dp)
            .clip(RoundedCornerShape(50))
            .background(color)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = onClickLabel,
                onLongClickLabel = if (onLongClick != null) "More actions" else null,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = palette.icon, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = palette.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// A clip's actions, laid out like a snippet's: the text (tap pastes), then three buttons.
@Composable
internal fun ClipActionsContent(clip: RecentClip?, actions: KeyboardActions) {
    if (clip == null) {
        // Expired or cleared while open.
        LaunchedEffect(Unit) { actions.onBack() }
        Box(modifier = Modifier.fillMaxSize())
        return
    }
    val palette = LocalKeyboardPalette.current
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.key)
                .clickable(onClickLabel = "Paste") { actions.onClipTap(clip) }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = clip.text,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.icon,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PanelActionButton(
                icon = Icons.Rounded.ContentPaste,
                label = "Paste",
                onClick = { actions.onClipTap(clip) },
                modifier = Modifier.weight(1f),
            )
            PanelActionButton(
                icon = Icons.Rounded.BookmarkAdd,
                label = "Save as snippet",
                onClick = { actions.onSaveClip(clip) },
                modifier = Modifier.weight(1f),
            )
            PanelActionButton(
                icon = Icons.Rounded.Delete,
                label = "Remove",
                onClick = { actions.onRemoveClip(clip) },
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = "Recent clips stay on this phone for an hour, never in a backup.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.labelSecondary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
