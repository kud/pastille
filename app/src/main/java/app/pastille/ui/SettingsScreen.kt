package app.pastille.ui

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.activity.compose.BackHandler
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pastille.ime.LocalReduceMotion
import app.pastille.ime.PastilleMotion
import app.pastille.ime.ImageSourceReader
import app.pastille.ime.KeyboardHeightPreview
import app.pastille.ime.KeyboardStylePreview
import app.pastille.ime.installedTypingKeyboards
import app.pastille.ime.otherTypingKeyboards
import app.pastille.settings.PhotoAccessAction
import app.pastille.settings.photoAccessAction
import app.pastille.settings.returnKeyboardChoices
import app.pastille.settings.ClipboardClearDelay
import app.pastille.settings.KeyboardMode
import app.pastille.settings.KeyboardStyle
import app.pastille.settings.canSwitchOff
import app.pastille.settings.PanelHeight
import app.pastille.settings.PastilleSettings
import app.pastille.settings.BackupProblem
import app.pastille.PastilleApplication
import app.pastille.backup.AutoBackup
import app.pastille.backup.BackupArchive
import app.pastille.backup.BackupFiles
import app.pastille.backup.SyncDiagnosis
import app.pastille.backup.ImportConfirmDialog
import app.pastille.backup.importInBackground
import android.widget.Toast
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { PastilleSettings.forContext(context) }
    // Style and Height are their own pages (Iris, 2026-10-28): the main list stays one line per
    // setting, like Sacha's, and each page has the room for its live preview.
    var page by rememberSaveable { mutableStateOf(SettingsPage.Main) }
    val reduceMotion = LocalReduceMotion.current
    BackHandler(enabled = page != SettingsPage.Main) { page = SettingsPage.Main }
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(page.title) },
                navigationIcon = {
                    IconButton(onClick = { if (page == SettingsPage.Main) onBack() else page = SettingsPage.Main }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = page,
            transitionSpec = { PastilleMotion.screenTransition(forward = targetState != SettingsPage.Main, reduceMotion = reduceMotion) },
            label = "settingsPage",
            modifier = Modifier.padding(padding),
        ) { shown ->
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                when (shown) {
                    SettingsPage.Style -> StylePage(settings)
                    SettingsPage.Height -> HeightPage(settings)
                    SettingsPage.Main -> {
                        KeyboardGroup(settings, onOpen = { page = it })
                        ClipboardGroup(settings)
                        PhotosGroup(settings)
                        BackupGroup(settings)
                        AboutGroup()
                    }
                }
            }
        }
    }
}

private val TapToChooseProblems = setOf(
    BackupProblem.Access,
    BackupProblem.Write,
    BackupProblem.NotKept,
    BackupProblem.ReadOnly,
    BackupProblem.CantOverwrite,
)

private enum class SettingsPage(val title: String) { Main("Settings"), Style("Style"), Height("Height") }

@Composable
private fun StylePage(settings: PastilleSettings) {
    var selectedStyle by remember { mutableStateOf(settings.keyboardStyle) }
    SettingsGroup(title = null) {
        SettingsRowDetail(modifier = Modifier.padding(top = 16.dp)) {
            Crossfade(targetState = selectedStyle, animationSpec = tween(150)) { style ->
                KeyboardStylePreview(style = style, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    SettingsGroup(title = "Keyboard style") {
        StyleOptions(
            selected = selectedStyle,
            onSelect = { style ->
                selectedStyle = style
                settings.keyboardStyle = style
                settings.keyboardStyleChosen = true
            },
        )
    }
}

@Composable
private fun HeightPage(settings: PastilleSettings) {
    var panelPortrait by remember { mutableStateOf(settings.panelHeightPortrait) }
    var panelLandscape by remember { mutableStateOf(settings.panelHeightLandscape) }
    var previewLandscape by remember { mutableStateOf(false) }
    SettingsGroup(title = null) {
        SettingsRowDetail(modifier = Modifier.padding(top = 16.dp)) {
            KeyboardHeightPreview(
                style = settings.keyboardStyle,
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
        }
    }
}

@Composable
private fun KeyboardGroup(settings: PastilleSettings, onOpen: (SettingsPage) -> Unit) {
    var selectedStyle by remember { mutableStateOf(settings.keyboardStyle) }
    var panelPortrait by remember { mutableStateOf(settings.panelHeightPortrait) }
    var panelLandscape by remember { mutableStateOf(settings.panelHeightLandscape) }
    var enabledModes by remember { mutableStateOf(settings.enabledModes()) }
    var returnAfterSnippet by remember { mutableStateOf(settings.returnAfterSnippet) }
    var recentClipboard by remember { mutableStateOf(settings.recentClipboard) }
    val context = LocalContext.current
    SettingsGroup(title = "Keyboard") {
        SettingsRow(
            title = "Style",
            subtitle = selectedStyle.label,
            icon = Icons.Rounded.Palette,
            modifier = Modifier.clickable(role = Role.Button, onClick = { onOpen(SettingsPage.Style) }),
            trailing = { SettingsChevron() },
        )
        SettingsHairline()
        SettingsRow(
            title = "Height",
            subtitle = if (panelPortrait == panelLandscape) panelPortrait.label
            else "${panelPortrait.label} upright · ${panelLandscape.label} sideways",
            icon = Icons.Rounded.Height,
            modifier = Modifier.clickable(role = Role.Button, onClick = { onOpen(SettingsPage.Height) }),
            trailing = { SettingsChevron() },
        )
        SettingsHairline()
        SettingsRow(title = "Show in the keyboard", subtitle = "At least one stays on", icon = Icons.Rounded.Dashboard)
        SettingsRowDetail {
            ModesRow(
                enabled = enabledModes,
                onChange = { mode, on ->
                    settings.setModeEnabled(mode, on)
                    enabledModes = settings.enabledModes()
                },
            )
        }
        SettingsHairline()
        // Off clears what's kept at once; clips are memory only, so there is nothing else to delete.
        ToggleRow(
            title = "Clipboard",
            subtitle = "Your last 10 copies, for an hour, behind the keyboard's clipboard button. Memory only, never backed up; passwords are skipped",
            icon = Icons.Rounded.ContentPaste,
            checked = recentClipboard,
            onChange = {
                recentClipboard = it
                settings.recentClipboard = it
                if (!it) (context.applicationContext as? app.pastille.PastilleApplication)?.recentClipboard?.clear()
            },
        )
        SettingsHairline()
        ToggleRow(
            title = "Return after a snippet",
            subtitle = "Switch back to the keyboard you were using",
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
            title = "Clear after copying",
            subtitle = delay.label,
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
                    Text(
                        text = "Only clears when Pastille can confirm the clipboard still holds its own copy.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
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
    // Follows Settings › Show in the keyboard › Images live, which another group writes.
    val version by remember { settings.changes() }.collectAsStateWithLifecycle(initialValue = 0)
    val imagesShown = remember(version) { settings.imagesEnabled }
    SettingsGroup(title = "Photos") {
        PhotoAccessRow(imagesShown)
        SettingsHairline()
        ToggleRow(
            title = "Return after an image",
            subtitle = "Leave it off to paste several images in a row",
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
private fun PhotoAccessRow(imagesShown: Boolean) {
    val context = LocalContext.current
    val settings = remember { PastilleSettings.forContext(context) }
    var granted by remember { mutableStateOf(ImageSourceReader.hasPermission(context)) }
    var partialOnly by remember { mutableStateOf(ImageSourceReader.hasOnlyPartialAccess(context)) }
    var answered by remember { mutableStateOf(settings.photoPromptAnswered) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        settings.photoPromptAnswered = true
        answered = true
        granted = ImageSourceReader.hasPermission(context)
        partialOnly = ImageSourceReader.hasOnlyPartialAccess(context)
    }
    LifecycleResumeEffect(Unit) {
        granted = ImageSourceReader.hasPermission(context)
        partialOnly = ImageSourceReader.hasOnlyPartialAccess(context)
        onPauseOrDispose { }
    }
    // Not a switch: an app can't grant itself a permission, only ask, and the Images tab already
    // has its own on/off under "Show in the keyboard". The row says where access stands and opens
    // the one place that changes it.
    val status = when {
        partialOnly -> "Selected photos only, so the keyboard can't see your latest screenshot. Tap to allow all."
        granted -> "Allowed. Your recent images appear in the keyboard."
        answered -> "Not allowed. Tap to change it in system settings."
        else -> "Tap to show your recent screenshots in the keyboard."
    }
    SettingsRow(
        title = "Photo access",
        subtitle = if (imagesShown) status else "Only used by the Images tab, which is hidden.",
        icon = Icons.Rounded.PhotoLibrary,
        modifier = Modifier.clickable {
            when (photoAccessAction(granted, partialOnly, answered)) {
                PhotoAccessAction.Ask -> launcher.launch(photoPermission())
                PhotoAccessAction.OpenSystemSettings -> context.startActivity(
                    Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            }
        },
        trailing = { SettingsChevron() },
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
        trailing = { PastilleSwitch(checked = checked) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModesRow(
    enabled: Set<KeyboardMode>,
    onChange: (KeyboardMode, Boolean) -> Unit,
) {
    val modes = KeyboardMode.content
    MultiChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, mode ->
            val on = mode in enabled
            SegmentedButton(
                checked = on,
                onCheckedChange = { if (it || canSwitchOff(mode, enabled)) onChange(mode, it) },
                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                icon = { SegmentedButtonDefaults.Icon(active = on) },
                label = { Text(mode.label, maxLines = 1) },
            )
        }
    }
}

@Composable
private fun ReturnKeyboardRow(settings: PastilleSettings) {
    val context = LocalContext.current
    fun labelled(list: List<android.view.inputmethod.InputMethodInfo>) =
        list.map { it.id to it.loadLabel(context.packageManager).toString() }
    // Read again on resume: the way back from Android's keyboard settings is the usual reason it changed.
    var enabled by remember { mutableStateOf(labelled(otherTypingKeyboards(context))) }
    var installed by remember { mutableStateOf(labelled(installedTypingKeyboards(context))) }
    LifecycleResumeEffect(Unit) {
        enabled = labelled(otherTypingKeyboards(context))
        installed = labelled(installedTypingKeyboards(context))
        onPauseOrDispose { }
    }
    var chosen by remember { mutableStateOf(settings.returnKeyboardId) }
    val choices = returnKeyboardChoices(chosen, enabled, installed)
    SettingsRow(
        title = "Switch back to",
        subtitle = "After a snippet or image goes in",
        icon = Icons.Rounded.Keyboard,
    )
    Column(Modifier.selectableGroup()) {
        choices.forEach { choice ->
            StyleRow(
                headline = choice.label,
                supporting = choice.note,
                selected = choice.selected,
                enabled = choice.available,
                onClick = {
                    chosen = choice.value
                    settings.returnKeyboardId = choice.value
                },
            )
        }
    }
    if (choices.any { !it.available }) {
        SettingsRowDetail {
            TextButton(
                onClick = { context.startActivity(Intent(AndroidSettings.ACTION_INPUT_METHOD_SETTINGS)) },
                modifier = Modifier.align(Alignment.End),
            ) { Text("Enable keyboards") }
        }
    }
}

@Composable
private fun BackupGroup(settings: PastilleSettings) {
    val context = LocalContext.current
    val autoBackup = (context.applicationContext as PastilleApplication).autoBackup
    val scope = rememberCoroutineScope()
    // Follows writes made in the background (the status line changes after every backup).
    val version by remember { settings.changes() }.collectAsStateWithLifecycle(initialValue = 0)
    val uri = remember(version) { settings.backupUri }
    val name = remember(version) { settings.backupName ?: BackupArchive.SUGGESTED_NAME }
    val lastAt = remember(version) { settings.backupLastAt }
    val problem = remember(version) { settings.backupProblem }
    var busy by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf<Pair<File, BackupArchive.Counts>?>(null) }

    fun say(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    // ACTION_CREATE_DOCUMENT rather than a folder: Google Drive offers no folder picker, but it does
    // create and keep a single file, and that file's grant can be made persistable.
    val chooser = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupArchive.MIME),
    ) { picked ->
        if (picked != null) {
            scope.launch {
                busy = true
                val outcome = autoBackup.choose(picked, justCreated = true)
                busy = false
                if (outcome == AutoBackup.Outcome.Written) say("Synced")
            }
        }
    }
    // The second phone picks the file the first one created, so both read and write the same one.
    val existing = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        if (picked != null) {
            scope.launch {
                busy = true
                val outcome = autoBackup.choose(picked)
                busy = false
                if (outcome == AutoBackup.Outcome.Written || outcome == AutoBackup.Outcome.Unchanged) say("Synced")
            }
        }
    }
    val zipTypes = arrayOf(BackupArchive.MIME, "application/x-zip-compressed", "application/x-zip", "application/octet-stream")

    SettingsGroup(title = "Backup") {
        // Sacha's switch, with a check on its thumb (Iris, 2026-10-28). On opens the file chooser,
        // since there is nothing to sync until a file exists; off forgets the file and leaves it be.
        val on = uri != null
        val status = uri?.let { backupStatus(name, lastAt, problem, System.currentTimeMillis(), SyncDiagnosis.providerLabel(Uri.parse(it).authority)) }
        // A problem whose message says "Tap to choose…" lives on the row that does that, never on the
        // switch, where a tap would turn syncing off instead.
        val actionable = on && problem in TapToChooseProblems
        SettingsRow(
            title = "Sync automatically",
            subtitle = when {
                !on -> "Off"
                actionable -> "$name · Not syncing"
                else -> status
            },
            icon = if (on && problem != null) Icons.Rounded.ErrorOutline else Icons.Rounded.Sync,
            destructive = on && problem != null,
            modifier = Modifier.toggleable(value = on, role = Role.Switch) { want ->
                if (want) chooser.launch(BackupArchive.SUGGESTED_NAME) else autoBackup.stop()
            },
            trailing = { PastilleSwitch(checked = on) },
        )
        SettingsHairline()
        if (uri == null) {
            SettingsRow(
                title = "Use an existing file",
                subtitle = "The file your other phone already syncs with",
                icon = Icons.Rounded.FileOpen,
                modifier = Modifier.clickable { existing.launch(zipTypes) },
                trailing = { SettingsChevron() },
            )
        } else {
            SettingsRow(
                title = "Sync now",
                icon = Icons.Rounded.Refresh,
                enabled = !busy,
                modifier = Modifier.clickable(enabled = !busy) {
                    scope.launch {
                        busy = true
                        val outcome = autoBackup.backUp(force = false)
                        busy = false
                        if (outcome == AutoBackup.Outcome.Written || outcome == AutoBackup.Outcome.Unchanged) say("Synced")
                    }
                },
            )
            SettingsHairline()
            SettingsRow(
                title = "Restore from this file",
                subtitle = "Adds what isn't already in Pastille",
                icon = Icons.Rounded.Restore,
                enabled = !busy && problem != BackupProblem.Access,
                modifier = Modifier.clickable(enabled = !busy && problem != BackupProblem.Access) {
                    scope.launch {
                        val file = runCatching { BackupFiles.copyIn(context, Uri.parse(uri)) }.getOrNull()
                        val counts = file?.let { runCatching { BackupFiles.peek(it) }.getOrNull() }
                        when {
                            file == null -> say(IMPORT_UNREADABLE)
                            counts == null -> {
                                file.delete()
                                say(IMPORT_NOT_A_BACKUP)
                            }
                            else -> restoring = file to counts
                        }
                    }
                },
            )
            SettingsHairline()
            SettingsRow(
                title = "Choose another file",
                subtitle = if (actionable) status else "Start a new file, or fix access to this one",
                icon = if (actionable) Icons.Rounded.ErrorOutline else Icons.Rounded.Backup,
                destructive = actionable,
                modifier = Modifier.clickable { chooser.launch(BackupArchive.SUGGESTED_NAME) },
                trailing = { SettingsChevron() },
            )
        }
    }
    Text(
        text = SYNC_NOTE,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 32.dp),
    )
    Text(
        text = PRIVACY_NOTE,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 32.dp),
    )
    restoring?.let { (file, counts) ->
        ImportConfirmDialog(
            counts = counts,
            onImport = {
                restoring = null
                importInBackground(context, file)
            },
            onCancel = {
                restoring = null
                file.delete()
            },
        )
    }
}
