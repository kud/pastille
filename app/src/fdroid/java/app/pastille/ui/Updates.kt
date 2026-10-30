package app.pastille.ui

import androidx.compose.runtime.Composable

// The fdroid build has no updater and no network (docs/adr/0001-in-app-updates.md): F-Droid updates
// it. These stand in for the github build's versions in src/github, and draw nothing.

@Composable
internal fun UpdatesOnOpen(
    snackbar: androidx.compose.material3.SnackbarHostState,
    canAnnounce: () -> Boolean,
    onInstall: () -> Unit,
) = Unit

@Composable
internal fun UpdateAboutRow() = Unit

@Composable
internal fun UpdateMenuItem(onOpenUpdate: () -> Unit, onDismissMenu: () -> Unit) = Unit

@Composable
internal fun UpdateNowSheet(onDismiss: () -> Unit) = Unit
