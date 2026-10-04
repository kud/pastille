package app.pastille.tile

import android.app.Activity
import android.view.inputmethod.InputMethodManager

class ImePickerActivity : Activity() {

    private var pickerShown = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !pickerShown) {
            getSystemService(InputMethodManager::class.java).showInputMethodPicker()
            pickerShown = true
        } else if (hasFocus && pickerShown) {
            finish()
        }
    }
}
