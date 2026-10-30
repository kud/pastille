package app.pastille.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.pastille.clipboard.copyOwnText
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.pastille.BuildConfig
import app.pastille.crash.CrashLog

private const val SOURCE_URL = "https://github.com/kud/pastille"

/** "0.1.5 (16)", plus "debug" for the debug build, which installs beside the release one. */
internal fun versionLabel(versionName: String, versionCode: Int, debug: Boolean): String =
    "$versionName ($versionCode)" + if (debug) " · debug" else ""

@Composable
internal fun AboutGroup() {
    val context = LocalContext.current
    val version = versionLabel(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.DEBUG)
    // Read again on resume: a crash leaves the app, so the report appears on the next visit.
    var hasCrashReport by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        hasCrashReport = CrashLog.forContext(context).entries().isNotEmpty()
        onPauseOrDispose { }
    }
    var showCrash by remember { mutableStateOf(false) }
    var showNotices by remember { mutableStateOf(false) }

    SettingsGroup(title = "About") {
        SettingsRow(
            title = "Pastille",
            subtitle = "Version $version",
            icon = Icons.Rounded.Info,
            modifier = Modifier.clickable(onClickLabel = "Copy version") {
                copyOwnText(context, "Pastille $version")
                // Android 13 and later confirm a copy themselves.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(context, "Version copied", Toast.LENGTH_SHORT).show()
                }
            },
        )
        // The github build's updater; nothing in the fdroid build.
        UpdateAboutRow()
        SettingsHairline()
        SettingsRow(
            title = "Source code",
            subtitle = "github.com/kud/pastille",
            icon = Icons.Rounded.Code,
            modifier = Modifier.clickable(onClickLabel = "Open in browser") {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, "No app here can open links", Toast.LENGTH_SHORT).show()
                }
            },
            trailing = {
                Icon(
                    Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            },
        )
        SettingsHairline()
        SettingsRow(
            title = "Licence",
            subtitle = "GPL-3.0-or-later",
            icon = Icons.Rounded.Gavel,
            // Not tappable, so TalkBack reads title and value as one item.
            modifier = Modifier.semantics(mergeDescendants = true) {},
        )
        SettingsHairline()
        SettingsRow(
            title = "Third-party notices",
            subtitle = "Code from others that Pastille includes",
            icon = Icons.Rounded.Description,
            modifier = Modifier.clickable(onClickLabel = "Open notices") { showNotices = true },
            trailing = { SettingsChevron() },
        )
        if (hasCrashReport) {
            SettingsHairline()
            SettingsRow(
                title = "Last crash report",
                subtitle = "To copy into a bug report",
                icon = Icons.Rounded.BugReport,
                modifier = Modifier.clickable(onClickLabel = "Open report") { showCrash = true },
                trailing = { SettingsChevron() },
            )
        }
    }

    if (showCrash) CrashReportDialog(onDismiss = { showCrash = false })
    if (showNotices) ThirdPartyNoticesDialog(onDismiss = { showNotices = false })
}
