package app.pastille.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** One piece of code from others that ships inside the app, with the licence text it asks for. */
internal data class ThirdPartyNotice(val name: String, val usedFor: String, val licence: String)

/** Mirrors docs/third-party-notices.md: the MIT licence asks for its notice in every copy, the APK included. */
internal val THIRD_PARTY_NOTICES = listOf(
    ThirdPartyNotice(
        name = "QR Code generator library, Project Nayuki",
        usedFor = "Show QR code. MIT License.",
        licence = """
            Copyright (c) Project Nayuki. (MIT License)
            https://www.nayuki.io/page/qr-code-generator-library

            Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
            - The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
            - The Software is provided "as is", without warranty of any kind, express or implied, including but not limited to the warranties of merchantability, fitness for a particular purpose and noninfringement. In no event shall the authors or copyright holders be liable for any claim, damages or other liability, whether in an action of contract, tort or otherwise, arising from, out of or in connection with the Software or the use or other dealings in the Software.
        """.trimIndent(),
    ),
)

@Composable
internal fun ThirdPartyNoticesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Third-party notices") },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                THIRD_PARTY_NOTICES.forEachIndexed { index, notice ->
                    if (index > 0) Spacer(Modifier.height(16.dp))
                    Text(notice.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        notice.usedFor,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer {
                        Text(notice.licence, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        },
    )
}
