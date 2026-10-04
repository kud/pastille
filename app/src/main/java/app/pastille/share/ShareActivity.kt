package app.pastille.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pastille.MainActivity
import app.pastille.ui.theme.PastilleTheme

class ShareActivity : ComponentActivity() {

    private val shareViewModel: ShareViewModel by lazy {
        ViewModelProvider(this)[ShareViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            shareViewModel.start(extractInput())
        }
        setContent {
            PastilleTheme {
                val state = shareViewModel.uiState
                val categories by shareViewModel.categories.collectAsStateWithLifecycle()
                LaunchedEffect(state.shouldFinish) {
                    if (state.shouldFinish) finish()
                }
                ShareSheet(
                    state = state,
                    categories = categories,
                    onSelectCategory = shareViewModel::selectCategory,
                    onUndo = shareViewModel::undo,
                    onEdit = {
                        state.firstId?.let { openInPastille(it) }
                    },
                    onDismiss = { finish() },
                    onRequestFinish = shareViewModel::requestFinish,
                )
            }
        }
    }

    private fun openInPastille(id: Long) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_EDIT_SNIPPET_ID, id)
                .addFlags(MainActivity.LAUNCH_FLAGS),
        )
        finish()
    }

    private fun extractInput(): ShareInput {
        val intent = intent
        val uris = linkedSetOf<Uri>()
        // Text shares can carry a stream too (a .txt attachment); only image streams become snippets.
        val sharesImages = intent.type?.startsWith("image/") == true
        if (sharesImages && intent.action == Intent.ACTION_SEND) {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let {
                uris.add(it)
            }
        } else if (sharesImages && intent.action == Intent.ACTION_SEND_MULTIPLE) {
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?.let { uris.addAll(it) }
            intent.clipData?.let { clip ->
                for (index in 0 until clip.itemCount) {
                    clip.getItemAt(index).uri?.let { uris.add(it) }
                }
            }
        }
        val streams = uris.toList()
        val displayNames = streams.map { uri ->
            runCatching {
                contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()
        }
        return ShareInput(
            text = intent.getStringExtra(Intent.EXTRA_TEXT),
            subject = intent.getStringExtra(Intent.EXTRA_SUBJECT),
            streams = streams,
            displayNames = displayNames,
        )
    }
}
