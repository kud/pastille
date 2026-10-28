package app.pastille.ui

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Switch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val GroupShape = RoundedCornerShape(20.dp)
private val SideMargin = 16.dp
private val TileSize = 36.dp
private val TileIconSize = 24.dp

/** Where row text starts inside a card: 16dp padding, the 36dp tile, a 20dp gap. Hairlines start here too. */
val SettingsTextInset = 72.dp

@Composable
fun SettingsGroup(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = SideMargin)) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp, end = SideMargin, bottom = 8.dp).semantics { heading() },
            )
        }
        Surface(
            shape = GroupShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    }
}

/** Sacha's rows read as connected cards: a 2dp gap of the ground between them, not a hairline. */
@Composable
fun SettingsHairline() {
    Spacer(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.surface))
}

/** Sacha's switch: the thumb carries a check when on, so the state is a shape as well as a colour. */
@Composable
fun PastilleSwitch(checked: Boolean, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = null,
        enabled = enabled,
        thumbContent = if (checked) {
            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
        } else {
            null
        },
    )
}

@Composable
fun SettingsIconTile(icon: ImageVector, destructive: Boolean = false) {
    // Sacha: a plain monochrome glyph, no filled tile.
    val content = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier = Modifier.size(TileSize), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(TileIconSize))
    }
}

/**
 * One settings row: a leading slot 36dp wide (an icon tile, or a radio), the title and subtitle, then a trailing control.
 * Pass the clickable/toggleable/selectable behaviour in [modifier] so the whole row is the target and ripples.
 */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val titleColor = when {
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    val alpha = if (enabled) 1f else 0.38f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = SideMargin, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(Modifier.size(TileSize), contentAlignment = Alignment.Center) {
            when {
                leading != null -> leading()
                icon != null -> SettingsIconTile(icon, destructive)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor.copy(alpha = alpha),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
            }
        }
        trailing?.invoke(this)
    }
}

/** The trailing chevron, only for rows that open something (a menu, a screen, a system dialog). */
@Composable
fun SettingsChevron() {
    Icon(
        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Content that belongs to the row above it (a preview, segmented buttons). Under a row with a glyph,
 * pass [atText] so it starts at the text column: the leading column holds the row's glyph and
 * nothing else (Gamme spec §4). Without one, it uses the card's full width.
 */
@Composable
fun SettingsRowDetail(
    modifier: Modifier = Modifier,
    atText: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(start = if (atText) SettingsTextInset else SideMargin, end = SideMargin, bottom = 12.dp),
        content = content,
    )
}

/**
 * A one-of-many choice under a row with a glyph: the radio starts at the text column, never in the
 * glyph column (Gamme spec §4). The whole row is the target. The radio's own 2dp padding is taken
 * back so its ring lines up with the row text and the chips above.
 */
private val ChoiceGap = 12.dp
/** The radio (20dp ring plus 2dp padding each side) and the gap after it. */
private val ChoiceStep = 24.dp + ChoiceGap

@Composable
fun SettingsChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    nested: Boolean = false,
) {
    val alpha = if (enabled) 1f else 0.38f
    // A nested choice (a keyboard's language) steps in by one radio plus its gap, so its radio sits
    // under the parent's name: the grouping is a shape, like Android's own picker, not a colour.
    val start = SettingsTextInset - 2.dp + if (nested) ChoiceStep else 0.dp
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(start = start, end = SideMargin, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChoiceGap),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
            }
        }
    }
}
