package app.pastille.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.pastille.ime.ImageSourceReader
import app.pastille.ime.KeyboardHeightPreview
import app.pastille.ime.KeyboardStylePreview
import app.pastille.ime.otherTypingKeyboards
import app.pastille.settings.ClipboardClearDelay
import app.pastille.settings.KeyboardStyle
import app.pastille.settings.PanelHeight
import app.pastille.settings.PastilleSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { PastilleSettings.forContext(context) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    FilledTonalIconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KeyboardGroup(settings)
            ClipboardGroup(settings)
            PhotosGroup(settings)
        }
    }
}

@Composable
private fun KeyboardGroup(settings: PastilleSettings) {
    var selectedStyle by remember { mutableStateOf(settings.keyboardStyle) }
    var panelPortrait by remember { mutableStateOf(settings.panelHeightPortrait) }
    var panelLandscape by remember { mutableStateOf(settings.panelHeightLandscape) }
    var previewLandscape by remember { mutableStateOf(false) }
    var snippetsOn by remember { mutableStateOf(settings.snippetsEnabled) }
    var imagesOn by remember { mutableStateOf(settings.imagesEnabled) }
    var returnAfterSnippet by remember { mutableStateOf(settings.returnAfterSnippet) }
    fun chooseStyle(style: KeyboardStyle) {
        selectedStyle = style
        settings.keyboardStyle = style
        settings.keyboardStyleChosen = true
    }
    SettingsGroup(title = "Keyboard") {
        SettingsRow(title = "Style", subtitle = selectedStyle.label, icon = Icons.Rounded.Palette)
        SettingsRowDetail {
            Crossfade(targetState = selectedStyle, animationSpec = tween(150)) { style ->
                KeyboardStylePreview(style = style, modifier = Modifier.fillMaxWidth())
            }
        }
        StyleOptions(selected = selectedStyle, onSelect = ::chooseStyle)
        SettingsHairline()
        SettingsRow(title = "Height", subtitle = "Portrait and landscape", icon = Icons.Rounded.Height)
        SettingsRowDetail {
            KeyboardHeightPreview(
                style = selectedStyle,
                height = if (previewLandscape) panelLandscape else panelPortrait,
                landscape = previewLandscape,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
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
            TryItField(modifier = Modifier.padding(top = 16.dp))
        }
        SettingsHairline()
        SettingsRow(title = "Show in the keyboard", subtitle = "At least one stays on", icon = Icons.Rounded.Dashboard)
        SettingsRowDetail {
            ModesRow(
                snippetsOn = snippetsOn,
                imagesOn = imagesOn,
                onSnippets = {
                    snippetsOn = it
                    settings.snippetsEnabled = it
                },
                onImages = {
                    imagesOn = it
                    settings.imagesEnabled = it
                },
            )
        }
        SettingsHairline()
        ToggleRow(
            title = "Return after a snippet",
            subtitle = "Switch back to the keyboard you were using.",
            icon = Icons.AutoMirrored.Rounded.KeyboardReturn,
            checked = returnAfterSnippet,
            onChange = {
                returnAfterSnippet = it
                settings.returnAfterSnippet = it
            },
        )
        SettingsHairline()
        ReturnKeyboardRow(settings)
    }
}

@Composable
private fun ClipboardGroup(settings: PastilleSettings) {
    var delay by remember { mutableStateOf(settings.clipboardClearDelay) }
    var open by remember { mutableStateOf(false) }
    SettingsGroup(title = "Clipboard") {
        SettingsRow(
            title = "Clear clipboard after copying",
            subtitle = "${delay.label}. Only clears when Pastille can confirm the clipboard still holds its own copy.",
            icon = Icons.Rounded.ContentPaste,
            modifier = Modifier.clickable { open = true },
            trailing = { SettingsChevron() },
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Clear clipboard after copying") },
            text = {
                Column(Modifier.selectableGroup()) {
                    ClipboardClearDelay.entries.forEach { choice ->
                        StyleRow(
                            headline = choice.label,
                            supporting = null,
                            selected = choice == delay,
                            enabled = true,
                            onClick = {
                                delay = choice
                                settings.clipboardClearDelay = choice
                                open = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PhotosGroup(settings: PastilleSettings) {
    var returnAfterImage by remember { mutableStateOf(settings.returnAfterImage) }
    SettingsGroup(title = "Photos") {
        PhotoAccessRow()
        SettingsHairline()
        ToggleRow(
            title = "Return after an image",
            subtitle = "Leave it off to paste several images in a row.",
            icon = Icons.Rounded.Image,
            checked = returnAfterImage,
            onChange = {
                returnAfterImage = it
                settings.returnAfterImage = it
            },
        )
    }
}

private val KeyboardStyle.label: String
    get() = when (this) {
        KeyboardStyle.Auto -> "Auto"
        KeyboardStyle.GboardDark -> "Match Gboard: Dark"
        KeyboardStyle.GboardLight -> "Match Gboard: Light"
        KeyboardStyle.MaterialYou -> "Match Gboard: Material You"
    }

@Composable
private fun StyleOptions(selected: KeyboardStyle, onSelect: (KeyboardStyle) -> Unit) {
    val materialYouSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Column(Modifier.selectableGroup()) {
        KeyboardStyle.entries.forEach { style ->
            val enabled = style != KeyboardStyle.MaterialYou || materialYouSupported
            val supporting = when (style) {
                KeyboardStyle.Auto -> "Gboard's default for this Android version"
                KeyboardStyle.MaterialYou -> if (materialYouSupported) "Your wallpaper's colours" else "Needs Android 12 or later"
                else -> null
            }
            StyleRow(
                headline = style.label,
                supporting = supporting,
                selected = selected == style,
                enabled = enabled,
                onClick = { onSelect(style) },
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
    SettingsRow(
        title = headline,
        subtitle = supporting,
        enabled = enabled,
        leading = { RadioButton(selected = selected, onClick = null, enabled = enabled) },
        modifier = Modifier.selectable(
            selected = selected,
            enabled = enabled,
            role = Role.RadioButton,
            onClick = onClick,
        ),
    )
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
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
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
private fun PhotoAccessRow() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ImageSourceReader.hasPermission(context)) }
    var partialOnly by remember { mutableStateOf(ImageSourceReader.hasOnlyPartialAccess(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LifecycleResumeEffect(Unit) {
        granted = ImageSourceReader.hasPermission(context)
        partialOnly = ImageSourceReader.hasOnlyPartialAccess(context)
        onPauseOrDispose { }
    }
    val canAsk = !granted || partialOnly
    SettingsRow(
        title = "Photo access",
        subtitle = when {
            partialOnly -> "Selected photos only, so the keyboard can't see your latest screenshot. Tap to allow all."
            granted -> "Granted. Your recent images appear in the keyboard."
            else -> "Tap to show your recent screenshots in the keyboard."
        },
        icon = Icons.Rounded.PhotoLibrary,
        modifier = if (canAsk) Modifier.clickable { launcher.launch(photoPermission()) } else Modifier,
        trailing = if (canAsk) {
            { SettingsChevron() }
        } else {
            null
        },
    )
}

internal fun photoPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        trailing = { Switch(checked = checked, onCheckedChange = null) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModesRow(
    snippetsOn: Boolean,
    imagesOn: Boolean,
    onSnippets: (Boolean) -> Unit,
    onImages: (Boolean) -> Unit,
) {
    MultiChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            checked = snippetsOn,
            onCheckedChange = { if (it || imagesOn) onSnippets(it) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
            icon = { SegmentedButtonDefaults.Icon(active = snippetsOn) },
            label = { Text("Snippets") },
        )
        SegmentedButton(
            checked = imagesOn,
            onCheckedChange = { if (it || snippetsOn) onImages(it) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
            icon = { SegmentedButtonDefaults.Icon(active = imagesOn) },
            label = { Text("Images") },
        )
    }
}

@Composable
private fun ReturnKeyboardRow(settings: PastilleSettings) {
    val context = LocalContext.current
    val keyboards = remember {
        otherTypingKeyboards(context).map { it.id to it.loadLabel(context.packageManager).toString() }
    }
    var chosen by remember { mutableStateOf(settings.returnKeyboardId) }
    var open by remember { mutableStateOf(false) }
    val mainName = keyboards.firstOrNull()?.second
    val current = when {
        chosen == PastilleSettings.PREVIOUS_KEYBOARD -> "Previous keyboard"
        chosen != null && keyboards.any { it.first == chosen } -> keyboards.first { it.first == chosen }.second
        else -> mainName?.let { "Main keyboard ($it)" } ?: "Main keyboard"
    }
    fun choose(value: String?) {
        chosen = value
        settings.returnKeyboardId = value
        open = false
    }
    Box {
        SettingsRow(
            title = "Switch back to",
            subtitle = current,
            icon = Icons.Rounded.Keyboard,
            modifier = Modifier.clickable { open = true },
            trailing = { SettingsChevron() },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(mainName?.let { "Main keyboard ($it)" } ?: "Main keyboard") },
                onClick = { choose(null) },
            )
            keyboards.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { choose(id) })
            }
            DropdownMenuItem(
                text = { Text("Previous keyboard") },
                onClick = { choose(PastilleSettings.PREVIOUS_KEYBOARD) },
            )
        }
    }
}
