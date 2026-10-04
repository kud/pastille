package app.pastille.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import android.view.inputmethod.InputMethodManager
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
import app.pastille.ime.ImageSourceReader
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
    var returnAfterSnippet by remember { mutableStateOf(settings.returnAfterSnippet) }
    var returnAfterImage by remember { mutableStateOf(settings.returnAfterImage) }
    var snippetsOn by remember { mutableStateOf(settings.snippetsEnabled) }
    var imagesOn by remember { mutableStateOf(settings.imagesEnabled) }
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
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
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
            TryItField(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp))
            Text(
                text = "Show in the keyboard",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            )
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
            Text(
                text = "After inserting",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            )
            ToggleRow(
                headline = "Return after a snippet",
                supporting = "Switch back to the keyboard you were using.",
                checked = returnAfterSnippet,
                onChange = {
                    returnAfterSnippet = it
                    settings.returnAfterSnippet = it
                },
            )
            ToggleRow(
                headline = "Return after an image",
                supporting = "Leave it off to paste several images in a row.",
                checked = returnAfterImage,
                onChange = {
                    returnAfterImage = it
                    settings.returnAfterImage = it
                },
            )
            ReturnKeyboardRow(settings)
            PhotoAccessRow()
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
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy command")
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
    ListItem(
        headlineContent = { Text("Photo access") },
        supportingContent = {
            Text(
                when {
                    partialOnly -> "Selected photos only, so the keyboard can't see your latest screenshot."
                    granted -> "Granted. Your recent images appear in the keyboard."
                    else -> "Needed to show your recent screenshots in the keyboard."
                },
            )
        },
        trailingContent = if (granted && !partialOnly) {
            null
        } else {
            { TextButton(onClick = { launcher.launch(photoPermission()) }) { Text("Allow") } }
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
    headline: String,
    supporting: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(headline) },
        supportingContent = { Text(supporting) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        modifier = Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
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
    MultiChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
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
        val manager = context.getSystemService(InputMethodManager::class.java)
        manager?.enabledInputMethodList
            ?.filter { it.packageName != context.packageName }
            ?.map { it.id to it.loadLabel(context.packageManager).toString() }
            .orEmpty()
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
        ListItem(
            headlineContent = { Text("Switch back to") },
            supportingContent = { Text(current) },
            modifier = Modifier.clickable { open = true },
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
