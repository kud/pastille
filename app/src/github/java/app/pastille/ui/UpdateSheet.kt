package app.pastille.ui

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pastille.update.UpdateState
import app.pastille.update.Updater
import kotlinx.coroutines.launch

/**
 * The update sheet, over the list (Erwann, 2026-10-30: "comme tutti"), opened by the first-found
 * message's Install and the ⋮ "Update to X" item. One tap means install: it asks GitHub if this run
 * doesn't hold the build yet and starts the download at once, then shows the progress, the
 * install-permission step and failures in words; Android still asks to confirm. The work belongs to
 * the [Updater], so closing the sheet never stops a download; About › Updates shows where it is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpdateNowSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val updater = remember { Updater.forContext(context) }
    val state by updater.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Once per opening, rotation included: the tap that opened the sheet was the install.
    var started by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (started) return@LaunchedEffect
        started = true
        updater.startInstall()
    }
    // Back from Android's "Install unknown apps" page: carry on if it was allowed.
    LifecycleResumeEffect(state) {
        (state as? UpdateState.NeedsPermission)?.let {
            if (context.packageManager.canRequestPackageInstalls()) updater.install(it.update)
        }
        onPauseOrDispose { }
    }
    val close: () -> Unit = {
        scope.launch { runCatching { sheetState.hide() } }.invokeOnCompletion { onDismiss() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        UpdateSheetContent(
            installed = updater.current.toString(),
            state = state,
            canUpdate = updater.isAvailable,
            onInstall = updater::startInstall,
            onLater = { update ->
                updater.setAside(update.version)
                close()
            },
            onAllow = { update ->
                try {
                    context.startActivity(updater.permissionIntent())
                } catch (_: ActivityNotFoundException) {
                    // No such page on this phone: the install itself will ask.
                    updater.install(update)
                }
            },
            onClose = close,
        )
    }
}

/**
 * The sheet's inside, stateless so the renders can show every state. One height across states, like
 * About's row: the sentence keeps two lines, the progress bar its line, the note one line, the
 * buttons one row (Close while work runs, since it carries on without the sheet).
 */
@Composable
internal fun UpdateSheetContent(
    installed: String,
    state: UpdateState,
    canUpdate: Boolean,
    onInstall: () -> Unit,
    onLater: (app.pastille.update.Update) -> Unit,
    onAllow: (app.pastille.update.Update) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = if (canUpdate) sheetStatus(state) else "The debug build doesn't update itself"
    val spoken = if (canUpdate) spokenStatus(state) else status
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 16.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Text(
            "Update Pastille",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "Installed: $installed",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        // TalkBack says each step as it comes, the download in tens so it isn't a stream of numbers.
        Text(
            status,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            minLines = 2,
            maxLines = 2,
            // Cleared, so only the spoken sentence changing makes TalkBack speak.
            modifier = Modifier.clearAndSetSemantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = spoken
            },
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(4.dp)) {
            if (canUpdate) when (state) {
                UpdateState.Idle, UpdateState.Checking, is UpdateState.Installing ->
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                is UpdateState.Downloading -> if (state.percent != null) {
                    LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                else -> Unit
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (state is UpdateState.NeedsPermission) "On Android's page, turn on Allow from this source, then come back"
            else "Android asks you to confirm before it installs",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = 2,
            maxLines = 2,
        )
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!canUpdate) {
                Spacer(Modifier)
                FilledTonalButton(onClick = onClose) { Text("Done") }
                return@Row
            }
            when (state) {
                is UpdateState.Available -> {
                    TextButton(onClick = { onLater(state.update) }) { Text("Later") }
                    FilledTonalButton(onClick = onInstall) { Text("Download") }
                }
                is UpdateState.NeedsPermission -> {
                    TextButton(onClick = onClose) { Text("Not now") }
                    FilledTonalButton(onClick = { onAllow(state.update) }) { Text("Allow installs") }
                }
                is UpdateState.Failed -> {
                    TextButton(onClick = onClose) { Text("Close") }
                    FilledTonalButton(onClick = onInstall) { Text("Try again") }
                }
                UpdateState.UpToDate -> {
                    Spacer(Modifier)
                    FilledTonalButton(onClick = onClose) { Text("Done") }
                }
                UpdateState.Idle, UpdateState.Checking, is UpdateState.Downloading, is UpdateState.Installing -> {
                    Spacer(Modifier)
                    TextButton(onClick = onClose) { Text("Close") }
                }
            }
        }
    }
}

/** The sheet's sentence; failures read "what: try again", as in About (Iris, 2026-10-30). */
internal fun sheetStatus(state: UpdateState): String = when (state) {
    // Idle only for the instant before the sheet's tap reaches GitHub.
    UpdateState.Idle, UpdateState.Checking -> "Asking GitHub…"
    UpdateState.UpToDate -> "Up to date: nothing newer on GitHub"
    is UpdateState.Available -> "Version ${state.update.version} is ready"
    is UpdateState.Downloading -> state.percent?.let { "Downloading ${state.update.version} · $it%" }
        ?: "Downloading ${state.update.version}…"
    is UpdateState.NeedsPermission -> "Downloaded: allow installs to finish"
    is UpdateState.Installing -> "Installing ${state.update.version}…"
    is UpdateState.Failed -> failureText(state.problem)
}

/** What TalkBack says: the same sentence, with the download's percent in steps of ten. */
internal fun spokenStatus(state: UpdateState): String =
    if (state is UpdateState.Downloading && state.percent != null) {
        "Downloading ${state.update.version}, ${state.percent / 10 * 10} percent"
    } else {
        sheetStatus(state)
    }
