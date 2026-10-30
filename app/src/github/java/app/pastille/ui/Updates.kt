package app.pastille.ui

import android.content.ActivityNotFoundException
import androidx.compose.foundation.clickable
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

/** The automatic check, when the list comes up: at most hourly on a test build, daily otherwise. */
@Composable
internal fun UpdatesOnOpen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updater = remember { Updater.forContext(context) }
    LifecycleResumeEffect(Unit) {
        scope.launch { updater.checkIfDue() }
        onPauseOrDispose { }
    }
}

/**
 * Settings › About › Updates (Iris, 2026-10-30): where the updater stands, in words, with the next
 * step as a word ("Check", "Download", "Install", "Allow installs", "Try again"), never a colour
 * alone. Failures read "what: do this". Below it, the Test builds switch.
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
    val (subtitle, action) = when (val s = state) {
        UpdateState.Idle -> {
            val known = found?.takeIf { it > updater.current }
            when {
                known != null -> "Version $known is out$checked"
                lastCheck == null -> "Not checked yet"
                else -> "Up to date$checked"
            } to "Check"
        }
        UpdateState.Checking -> "Asking GitHub…" to null
        UpdateState.UpToDate -> "Up to date$checked" to "Check"
        is UpdateState.Available -> "Version ${s.update.version} is ready" to "Download"
        is UpdateState.Downloading -> (s.percent?.let { "Downloading ${s.update.version} · $it%" } ?: "Downloading ${s.update.version}…") to null
        is UpdateState.NeedsPermission -> "Downloaded. Allow Pastille to install apps to finish" to "Allow installs"
        is UpdateState.Installing -> "Waiting for Android to confirm" to null
        is UpdateState.Failed -> failureText(s.problem) to "Try again"
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

    SettingsHairline()
    SettingsRow(
        title = "Updates",
        subtitle = subtitle,
        icon = Icons.Rounded.SystemUpdate,
        modifier = Modifier.semantics(mergeDescendants = true) {},
        trailing = {
            Row {
                val available = state as? UpdateState.Available
                if (available != null && cue == UpdateCue.NOTICE) {
                    TextButton(onClick = { updater.setAside(available.update.version) }) { Text("Later") }
                }
                if (action != null) TextButton(onClick = onAction) { Text(action) }
            }
        },
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

/** "What: do this", at most 14 words (Iris, 2026-10-30). */
internal fun failureText(problem: UpdateProblem): String = when (problem) {
    UpdateProblem.NETWORK -> "Couldn't reach GitHub: check the connection and try again"
    UpdateProblem.FEED -> "GitHub's answer made no sense: try again later"
    UpdateProblem.DOWNLOAD -> "Download stopped: try again on a steadier connection"
    UpdateProblem.DIGEST -> "Download didn't match GitHub's checksum, so it was deleted: try again"
    UpdateProblem.PACKAGE -> "That file isn't a newer Pastille, so it was deleted: try again"
    UpdateProblem.INSTALL -> "Android didn't install it: try again"
}

/**
 * The list's ⋮ item while a newer build is known and not set aside (Iris, 2026-10-30): last, under
 * a divider, so Reorder, Bin and Settings never move. No dot on ⋮.
 */
@Composable
internal fun UpdateMenuItem(onOpenSettings: () -> Unit, onDismissMenu: () -> Unit) {
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
            onOpenSettings()
        },
    )
}
