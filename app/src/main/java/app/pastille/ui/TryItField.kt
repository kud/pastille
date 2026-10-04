package app.pastille.ui

import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun TryItField(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text("Try it") },
        placeholder = { Text("Tap here to open a keyboard") },
        supportingText = { Text("Switch to Pastille with the keyboard button, then tap a snippet.") },
        trailingIcon = {
            IconButton(
                onClick = {
                    context.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
                },
            ) {
                Icon(Icons.Outlined.Keyboard, contentDescription = "Choose keyboard")
            }
        },
        minLines = 2,
        modifier = modifier.fillMaxWidth(),
    )
}
