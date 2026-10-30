package app.pastille.ui

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.rounded.Science
import androidx.compose.foundation.selection.toggleable
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pastille.update.Channel
import app.pastille.update.UpdateCue
import app.pastille.update.UpdateProblem
import app.pastille.update.UpdateState
import app.pastille.update.Updater
import kotlinx.coroutines.launch

/**
 * The automatic check, when the list comes up: 3 s after it starts, at most every 15 minutes on a
 * test build, daily otherwise. A build found for the first time says so once (Erwann, 2026-10-30,
 * after tutti; copy Iris): "Version X is out" with Install, which opens the update sheet over the
 * list, and the sheet starts the download at once. Skipped when [canAnnounce] says the list is busy or another message is up;
 * the ⋮ item carries it after that. Never an install by itself: Android still asks.
 */
@Composable
internal fun UpdatesOnOpen(snackbar: SnackbarHostState, canAnnounce: () -> Boolean, onInstall: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updater = remember { Updater.forContext(context) }
    val currentCanAnnounce by rememberUpdatedState(canAnnounce)
    val currentOnInstall by rememberUpdatedState(onInstall)
    LifecycleStartEffect(updater) {
        val job = scope.launch {
            delay(ANNOUNCE_DELAY_MS)
            val update = updater.checkIfDue() ?: return@launch
            if (!currentCanAnnounce() || snackbar.currentSnackbarData != null) return@launch
            val result = snackbar.showSnackbar(
                announcement(update.version.toString()),
                actionLabel = "Install",
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) currentOnInstall()
        }
        onStopOrDispose { job.cancel() }
    }
}

private const val ANNOUNCE_DELAY_MS = 3_000L

/** The once-per-build message (Iris, 2026-10-30). */
internal fun announcement(version: String): String = "Version $version is out"

/** What the Updates row keeps at its end: a word to tap, the work in progress, or room only. */
internal sealed interface UpdateSlot {
    data class Action(val label: String) : UpdateSlot
    /** [percent] null: indeterminate (asking GitHub, waiting for Android). */
    data class Busy(val percent: Int?) : UpdateSlot
    /** Available: Later and Download sit under the subtitle; the slot keeps its width, empty. */
    data object Reserved : UpdateSlot
}

/**
 * The trailing slot's one width (Iris, 2026-10-30), sized to the widest single action, "Allow
 * installs", so the text column never changes width between states.
 */
private val SlotWidth = 112.dp

/**
 * Settings › About › Updates (Iris, 2026-10-30): where the updater stands, in words, with the next
 * step as a word ("Check", "Download", "Install", "Allow installs", "Try again"), never a colour
 * alone. Failures read "what: try again". The row keeps one height across states: the subtitle
 * holds two lines and the trailing slot one width, a spinner while working; only an available build
 * adds a line of buttons. Below it, the Test builds switch.
 */
@Composable
internal fun UpdateAboutRow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updater = remember { Updater.forContext(context) }
    val state by updater.state.collectAsStateWithLifecycle()
    val channel by updater.channel.collectAsStateWithLifecycle()
    val cue by updater.cue.collectAsStateWithLifecycle()
    val found by updater.found.collectAsStateWithLifecycle()
    val lastCheck by updater.lastCheck.collectAsStateWithLifecycle()

    // Coming back from "Install unknown apps" with the permission now given: install straight away.
    LifecycleResumeEffect(state) {
        (state as? UpdateState.NeedsPermission)?.let {
            if (context.packageManager.canRequestPackageInstalls()) updater.install(it.update)
        }
        onPauseOrDispose { }
    }

    if (!updater.isAvailable) {
        SettingsHairline()
        SettingsRow(
            title = "Updates",
            subtitle = "The debug build doesn't update itself",
            icon = Icons.Rounded.SystemUpdate,
            modifier = Modifier.semantics(mergeDescendants = true) {},
        )
        return
    }

    val checked = lastCheck?.let { " · checked ${relativeTime(it, System.currentTimeMillis()).replaceFirstChar(Char::lowercase)}" }.orEmpty()
    val (subtitle, slot) = when (val s = state) {
        UpdateState.Idle -> {
            val known = found?.takeIf { it > updater.current }
            when {
                known != null -> "Version $known is out$checked"
                lastCheck == null -> "Not checked yet"
                else -> "Up to date$checked"
            } to UpdateSlot.Action("Check")
        }
        UpdateState.Checking -> "Asking GitHub…" to UpdateSlot.Busy(null)
        UpdateState.UpToDate -> "Up to date$checked" to UpdateSlot.Action("Check")
        is UpdateState.Available -> "Version ${s.update.version} is ready" to UpdateSlot.Reserved
        is UpdateState.Downloading -> (s.percent?.let { "Downloading ${s.update.version} · $it%" } ?: "Downloading ${s.update.version}…") to UpdateSlot.Busy(s.percent)
        is UpdateState.NeedsPermission -> "Downloaded: allow installs to finish" to UpdateSlot.Action("Allow installs")
        is UpdateState.Installing -> "Installing…" to UpdateSlot.Busy(null)
        is UpdateState.Failed -> failureText(s.problem) to UpdateSlot.Action("Try again")
    }

    val onAction: () -> Unit = {
        when (val s = state) {
            is UpdateState.Available -> scope.launch { updater.downloadAndInstall(s.update) }
            is UpdateState.NeedsPermission -> try {
                context.startActivity(updater.permissionIntent())
            } catch (_: ActivityNotFoundException) {
            }
            is UpdateState.Failed -> scope.launch {
                val update = s.update
                if (update != null && s.problem == UpdateProblem.INSTALL) updater.install(update)
                else if (update != null) updater.downloadAndInstall(update)
                else updater.check()
            }
            else -> scope.launch { updater.check() }
        }
    }
    val available = state as? UpdateState.Available

    SettingsHairline()
    UpdateRowContent(
        subtitle = subtitle,
        slot = slot,
        onAction = onAction,
        onLater = available?.takeIf { cue == UpdateCue.NOTICE }?.let { { updater.setAside(it.update.version) } },
    )
    SettingsHairline()
    val testBuilds = channel == Channel.DEV
    SettingsRow(
        title = "Test builds",
        subtitle = "Also offer the pre-release built from every change",
        icon = Icons.Rounded.Science,
        modifier = Modifier
            .toggleable(value = testBuilds, role = Role.Switch) {
                updater.setChannel(if (it) Channel.DEV else Channel.STABLE)
            }
            .semantics { stateDescription = if (testBuilds) "On" else "Off" },
        trailing = { PastilleSwitch(checked = testBuilds) },
    )
}

/** The Updates row itself, stateless, so the renders can show every state. */
@Composable
internal fun UpdateRowContent(
    subtitle: String,
    slot: UpdateSlot,
    onAction: () -> Unit,
    onLater: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        SettingsRow(
            title = "Updates",
            subtitle = subtitle,
            subtitleMinLines = 2,
            icon = Icons.Rounded.SystemUpdate,
            // TalkBack says "Asking GitHub…", "Up to date", a failure, as they come.
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            trailing = {
                Box(Modifier.width(SlotWidth), contentAlignment = Alignment.Center) {
                    when (slot) {
                        is UpdateSlot.Action -> TextButton(onClick = onAction) { Text(slot.label, maxLines = 1) }
                        is UpdateSlot.Busy -> if (slot.percent != null) {
                            CircularProgressIndicator(progress = { slot.percent / 100f }, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                        UpdateSlot.Reserved -> Unit
                    }
                }
            },
        )
        if (slot == UpdateSlot.Reserved) {
            SettingsRowDetail(atText = true) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (onLater != null) TextButton(onClick = onLater) { Text("Later") }
                    TextButton(onClick = onAction) { Text("Download") }
                }
            }
        }
    }
}

/** "What: try again", two lines at most beside the slot at 360dp (Iris, 2026-10-30). */
internal fun failureText(problem: UpdateProblem): String = when (problem) {
    UpdateProblem.NETWORK -> "Couldn't reach GitHub: try again"
    UpdateProblem.FEED -> "GitHub's answer was garbled: try again"
    UpdateProblem.DOWNLOAD -> "Download stopped: try again"
    UpdateProblem.DIGEST -> "Checksum didn't match: try again"
    UpdateProblem.PACKAGE -> "Not a newer Pastille, deleted: try again"
    UpdateProblem.INSTALL -> "Android didn't install it: try again"
}

/**
 * The list's ⋮ item while a newer build is known and not set aside (Iris, 2026-10-30): last, under
 * a divider, so Reorder, Bin and Settings never move. No dot on ⋮. A tap opens the update sheet,
 * which installs.
 */
@Composable
internal fun UpdateMenuItem(onOpenUpdate: () -> Unit, onDismissMenu: () -> Unit) {
    val context = LocalContext.current
    val updater = remember { Updater.forContext(context) }
    val cue by updater.cue.collectAsStateWithLifecycle()
    val found by updater.found.collectAsStateWithLifecycle()
    val version = found
    if (cue != UpdateCue.NOTICE || version == null) return
    HorizontalDivider()
    DropdownMenuItem(
        text = { Text("Update to $version") },
        leadingIcon = { Icon(Icons.Rounded.SystemUpdate, contentDescription = null) },
        colors = MenuDefaults.itemColors(leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant),
        onClick = {
            onDismissMenu()
            onOpenUpdate()
        },
    )
}
