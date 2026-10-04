package app.pastille.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostFieldTest {

    @Test
    fun plainTextFieldIsNotSensitive() {
        assertFalse(isSensitiveField(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_NULL))
    }

    @Test
    fun textPasswordVariationsAreSensitive() {
        assertTrue(
            isSensitiveField(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
                EditorInfo.IME_NULL,
            ),
        )
        assertTrue(
            isSensitiveField(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                EditorInfo.IME_NULL,
            ),
        )
        assertTrue(
            isSensitiveField(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                EditorInfo.IME_NULL,
            ),
        )
    }

    @Test
    fun numericPasswordIsSensitive() {
        assertTrue(
            isSensitiveField(
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
                EditorInfo.IME_NULL,
            ),
        )
    }

    @Test
    fun plainNumberFieldIsNotSensitive() {
        assertFalse(isSensitiveField(InputType.TYPE_CLASS_NUMBER, EditorInfo.IME_NULL))
    }

    @Test
    fun emailVariationIsNotSensitive() {
        assertFalse(
            isSensitiveField(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                EditorInfo.IME_NULL,
            ),
        )
    }

    @Test
    fun noPersonalizedLearningIsSensitive() {
        assertTrue(
            isSensitiveField(
                InputType.TYPE_CLASS_TEXT,
                EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING,
            ),
        )
    }

    @Test
    fun previewSkipsBlankLinesAndTrims() {
        assertEquals("Hello\nWorld", previewOf("\n  \n  Hello  \n\nWorld\n"))
    }

    @Test
    fun previewCapsLines() {
        assertEquals("one\ntwo", previewOf("one\ntwo\nthree"))
        assertEquals("one", previewOf("one\ntwo", maxLines = 1))
        assertEquals("one\ntwo\nthree", previewOf("one\ntwo\nthree", maxLines = 3))
    }

    @Test
    fun previewCapsLength() {
        assertEquals("a".repeat(200) + "…", previewOf("a".repeat(250)))
        assertEquals("a".repeat(200), previewOf("a".repeat(200)))
        assertEquals("", previewOf("   \n  "))
    }

    @Test
    fun displayTitlePrefersTitle() {
        assertEquals("My title", displayTitle("My title", "some body text"))
    }

    @Test
    fun displayTitleFallsBackToFirstLine() {
        assertEquals("Hello", displayTitle("", "\n  Hello  \nmore"))
        assertEquals("Hello", displayTitle("   ", "Hello"))
        assertEquals("", displayTitle("", "   \n  "))
        assertEquals("", displayTitle("", ""))
    }

    @Test
    fun displayTitleCutsLongFirstLine() {
        assertEquals("a".repeat(40) + "…", displayTitle("", "a".repeat(60) + "\nshort"))
        assertEquals("a".repeat(40), displayTitle("", "a".repeat(40)))
    }
}
