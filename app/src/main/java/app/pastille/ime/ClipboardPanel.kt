package app.pastille.ime

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
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

// The keyboard's Clipboard view (Gboard's pattern, Erwann 2026-10-28): its own panel behind the
// toolbar's clipboard tab, not a row inside Snippets. Newest first; a tap pastes, a long press opens the
// clip's actions (Paste, Save as snippet, Remove). Clear lives in the toolbar's action slot.
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ClipboardContent(clips: List<RecentClip>, actions: KeyboardActions) {
    val palette = LocalKeyboardPalette.current
    if (clips.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "Things you copy appear here for an hour",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.labelSecondary,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().semantics { contentDescription = "Clipboard" },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(clips, key = { it.id }) { clip ->
            ClipCard(clip = clip, actions = actions)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClipCard(clip: RecentClip, actions: KeyboardActions) {
    val palette = LocalKeyboardPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val color by animateColorAsState(
        targetValue = if (pressed) palette.keyPressed else palette.key,
        animationSpec = tween(PastilleMotion.PRESS_MS),
        label = "clipColour",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = "Paste",
                onLongClickLabel = "More actions",
                onLongClick = { actions.onClipLongPress(clip) },
                onClick = { actions.onClipTap(clip) },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = clip.text.trim(),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.label,
            maxLines = 2,
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
            text = "Clipboard items stay on this phone for an hour, never in a backup.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.labelSecondary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
