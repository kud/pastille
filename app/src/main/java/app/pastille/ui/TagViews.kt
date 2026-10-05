package app.pastille.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.pastille.model.addTags
import app.pastille.model.displayTag
import app.pastille.model.splitTagInput
import app.pastille.model.tagSuggestions

private val PillHeight = 18.dp
private val PillShape = RoundedCornerShape(6.dp)

/** A row's folder, shown in "All": filled, the same colours as the selected filter pill. */
@Composable
fun FolderPill(name: String) {
    Surface(
        shape = PillShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.height(PillHeight).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp),
            )
        }
    }
}

/** A tag on a row: outlined, "#" prefix, no icon. Also used for the "+N" overflow. */
@Composable
fun TagPill(label: String) {
    Surface(
        shape = PillShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(modifier = Modifier.height(PillHeight).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

const val MAX_ROW_TAGS = 2

/**
 * The fixed part of a row's meta line (time, folder pill) always stays whole. Tags follow, at most
 * [MAX_ROW_TAGS] then "+N"; when the line runs out of room, tags fold into "+N" first.
 */
@Composable
fun MetaLine(tags: List<String>, modifier: Modifier = Modifier, fixed: @Composable () -> Unit) {
    val gap = 6.dp
    SubcomposeLayout(modifier) { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val fixedPlaceables = subcompose("fixed") { Row(verticalAlignment = Alignment.CenterVertically) { fixed() } }
            .map { it.measure(loose) }
        val gapPx = gap.roundToPx()
        val fixedWidth = fixedPlaceables.sumOf { it.width }
        val available = if (constraints.hasBoundedWidth) constraints.maxWidth - fixedWidth else Int.MAX_VALUE
        val tagPlaceables = tags.take(MAX_ROW_TAGS).mapIndexed { index, tag ->
            subcompose("tag-$index") { TagPill("#${displayTag(tag)}") }.single().measure(Constraints())
        }
        var shown = tagPlaceables.size
        var plus: androidx.compose.ui.layout.Placeable? = null
        while (true) {
            val hidden = tags.size - shown
            plus = if (hidden > 0) {
                subcompose("plus-$hidden") { TagPill("+$hidden") }.single().measure(Constraints())
            } else {
                null
            }
            val needed = tagPlaceables.take(shown).sumOf { it.width + gapPx } + (plus?.let { it.width + gapPx } ?: 0)
            if (needed <= available || shown == 0) break
            shown--
        }
        val height = (fixedPlaceables + tagPlaceables.take(shown) + listOfNotNull(plus)).maxOfOrNull { it.height } ?: 0
        val width = (fixedWidth + tagPlaceables.take(shown).sumOf { it.width + gapPx } + (plus?.let { it.width + gapPx } ?: 0))
            .coerceAtMost(if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE)
        layout(width, height) {
            var x = 0
            fixedPlaceables.forEach {
                it.place(x, (height - it.height) / 2)
                x += it.width
            }
            (tagPlaceables.take(shown) + listOfNotNull(plus)).forEach {
                x += gapPx
                it.place(x, (height - it.height) / 2)
                x += it.width
            }
        }
    }
}

/**
 * The editor's and the Organise sheet's tag field: one input chip per tag with ✕, and a text
 * field that suggests existing tags. Enter, a comma or a space adds a tag.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditor(
    tags: List<String>,
    allTags: List<String>,
    onTagsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var typing by rememberSaveable { mutableStateOf("") }
    fun commit(text: String) {
        val added = addTags(tags, listOf(text))
        if (added != tags) onTagsChange(added)
        typing = ""
    }
    Column(modifier = modifier) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            tags.forEach { tag ->
                InputChip(
                    selected = false,
                    onClick = { onTagsChange(tags - tag) },
                    label = { Text("#${displayTag(tag)}") },
                    trailingIcon = {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Remove ${displayTag(tag)}",
                            modifier = Modifier.size(InputChipDefaults.IconSize),
                        )
                    },
                )
            }
            BasicTextField(
                value = typing,
                onValueChange = { input ->
                    val (finished, rest) = splitTagInput(input)
                    if (finished.isNotEmpty()) {
                        val added = addTags(tags, finished)
                        if (added != tags) onTagsChange(added)
                    }
                    typing = rest
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit(typing) }),
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .widthIn(min = 96.dp)
                    .heightIn(min = 32.dp)
                    .padding(vertical = 8.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.Enter) {
                            commit(typing)
                            true
                        } else {
                            false
                        }
                    },
                decorationBox = { inner ->
                    if (typing.isEmpty()) {
                        Text(
                            text = if (tags.isEmpty()) "Add tags" else "Add",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
            )
        }
        val suggestions = remember(allTags, typing, tags) { tagSuggestions(allTags, typing, tags) }
        if (suggestions.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(suggestions, key = { it }) { tag ->
                    SuggestionChip(onClick = { commit(tag) }, label = { Text("#${displayTag(tag)}") })
                }
            }
        }
    }
}

/** The second chip row under the folders: outlined, multi-select; several narrow to all of them. */
@Composable
fun TagFilterRow(tags: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(tags, key = { it }) { tag ->
            FilterChip(
                selected = tag in selected,
                onClick = { onToggle(tag) },
                label = { Text("#${displayTag(tag)}") },
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}
