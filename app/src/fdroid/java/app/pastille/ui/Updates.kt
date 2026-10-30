package app.pastille.ui

import androidx.compose.runtime.Composable

// The fdroid build has no updater and no network (docs/adr/0001-in-app-updates.md): F-Droid updates
// it. These stand in for the github build's versions in src/github, and draw nothing.

@Composable
internal fun UpdatesOnOpen() = Unit

@Composable
internal fun UpdateAboutRow() = Unit

@Composable
internal fun UpdateMenuItem(onOpenSettings: () -> Unit, onDismissMenu: () -> Unit) = Unit
