package app.pastille.ui

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.pastille.ime.KeyboardHeightPreview
import app.pastille.ime.KeyboardStylePreview
import app.pastille.settings.KeyboardStyle
import app.pastille.settings.PanelHeight
import app.pastille.settings.PastilleSettings
import app.pastille.tile.ImeSwitcher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { PastilleSettings.forContext(context) }
    var enabled by remember { mutableStateOf(settings.returnToPreviousKeyboard) }
    var selectedStyle by remember { mutableStateOf(settings.keyboardStyle) }
    var panelPortrait by remember { mutableStateOf(settings.panelHeightPortrait) }
    var panelLandscape by remember { mutableStateOf(settings.panelHeightLandscape) }
    var previewLandscape by remember { mutableStateOf(false) }
    val materialYouSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val clipboard = LocalClipboardManager.current
    val command = "adb shell pm grant app.pastille android.permission.WRITE_SECURE_SETTINGS"
    var granted by remember { mutableStateOf(ImeSwitcher.hasWriteSecureSettings(context)) }
    LifecycleResumeEffect(Unit) {
        granted = ImeSwitcher.hasWriteSecureSettings(context)
        onPauseOrDispose { }
    }
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            Text(
                text = "Keyboard style",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            )
            Crossfade(targetState = selectedStyle, animationSpec = tween(150)) { style ->
                KeyboardStylePreview(
                    style = style,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            Column(Modifier.selectableGroup()) {
                StyleRow(
                    headline = "Auto",
                    supporting = "Gboard's default for this Android version",
                    selected = selectedStyle == KeyboardStyle.Auto,
                    enabled = true,
                    onClick = {
                        selectedStyle = KeyboardStyle.Auto
                        settings.keyboardStyle = KeyboardStyle.Auto
                        settings.keyboardStyleChosen = true
                    },
                )
                StyleRow(
                    headline = "Match Gboard: Dark",
                    supporting = null,
                    selected = selectedStyle == KeyboardStyle.GboardDark,
                    enabled = true,
                    onClick = {
                        selectedStyle = KeyboardStyle.GboardDark
                        settings.keyboardStyle = KeyboardStyle.GboardDark
                        settings.keyboardStyleChosen = true
                    },
                )
                StyleRow(
                    headline = "Match Gboard: Light",
                    supporting = null,
                    selected = selectedStyle == KeyboardStyle.GboardLight,
                    enabled = true,
                    onClick = {
                        selectedStyle = KeyboardStyle.GboardLight
                        settings.keyboardStyle = KeyboardStyle.GboardLight
                        settings.keyboardStyleChosen = true
                    },
                )
                StyleRow(
                    headline = "Match Gboard: Material You",
                    supporting = if (materialYouSupported) {
                        "Your wallpaper's colours"
                    } else {
                        "Needs Android 12 or later"
                    },
                    selected = selectedStyle == KeyboardStyle.MaterialYou,
                    enabled = materialYouSupported,
                    onClick = {
                        selectedStyle = KeyboardStyle.MaterialYou
                        settings.keyboardStyle = KeyboardStyle.MaterialYou
                        settings.keyboardStyleChosen = true
                    },
                )
            }
            Text(
                text = "Keyboard height",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            )
            KeyboardHeightPreview(
                style = selectedStyle,
                height = if (previewLandscape) panelLandscape else panelPortrait,
                landscape = previewLandscape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )
            HeightRow(
                label = "Portrait",
                selected = panelPortrait,
                onSelect = { height ->
                    panelPortrait = height
                    settings.panelHeightPortrait = height
                    previewLandscape = false
                },
            )
            HeightRow(
                label = "Landscape",
                selected = panelLandscape,
                onSelect = { height ->
                    panelLandscape = height
                    settings.panelHeightLandscape = height
                    previewLandscape = true
                },
            )
            ListItem(
                headlineContent = { Text("Return to previous keyboard after inserting") },
                supportingContent = {
                    Text("After you insert a snippet or a screenshot, switch back to the keyboard you were using.")
                },
                trailingContent = { Switch(checked = enabled, onCheckedChange = null) },
                modifier = Modifier.toggleable(
                    value = enabled,
                    role = Role.Switch,
                    onValueChange = {
                        enabled = it
                        settings.returnToPreviousKeyboard = it
                    },
                ),
            )
            Text(
                text = "Quick Settings tile",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            )
            Text(
                text = "Add the Pastille tile to Quick Settings to change keyboard in one tap. Without extra permission it opens the keyboard picker. To switch straight to Pastille and back, grant one permission over adb, once:",
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = command,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                )
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(command)) },
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy command")
                }
            }
            ListItem(
                headlineContent = { Text("One-tap switching") },
                supportingContent = {
                    Text(
                        if (granted) {
                            "Granted"
                        } else {
                            "Not granted. The tile opens the keyboard picker."
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun HeightRow(
    label: String,
    selected: PanelHeight,
    onSelect: (PanelHeight) -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        PanelHeight.entries.forEachIndexed { index, height ->
            SegmentedButton(
                selected = height == selected,
                onClick = { onSelect(height) },
                shape = SegmentedButtonDefaults.itemShape(index, PanelHeight.entries.size),
                icon = {},
                label = {
                    Text(
                        text = height.label,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
private fun StyleRow(
    headline: String,
    supporting: String?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(headline) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = { RadioButton(selected = selected, onClick = null, enabled = enabled) },
        modifier = Modifier.selectable(
            selected = selected,
            enabled = enabled,
            role = Role.RadioButton,
            onClick = onClick,
        ),
    )
}
