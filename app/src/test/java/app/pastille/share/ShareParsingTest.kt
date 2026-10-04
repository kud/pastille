package app.pastille.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareParsingTest {

    @Test
    fun autoTitleKeepsShortSingleLineAsItsOwnTitle() {
        assertEquals("hello", autoTitle("hello"))
        assertEquals("a".repeat(40), autoTitle("a".repeat(40)))
        assertEquals("", autoTitle(""))
        assertEquals("", autoTitle("   "))
    }

    @Test
    fun autoTitleCutsOnWordBoundary() {
        assertEquals(
            "The quick brown fox jumps over the lazy…",
            autoTitle("The quick brown fox jumps over the lazy dog again"),
        )
    }

    @Test
    fun autoTitleCutsLongSingleLine() {
        assertEquals("a".repeat(40) + "…", autoTitle("a".repeat(41)))
        assertEquals("a".repeat(40) + "…", autoTitle("a".repeat(500)))
    }

    @Test
    fun autoTitleUsesFirstNonBlankLine() {
        assertEquals("Hello", autoTitle("Hello\nsecond line here"))
        assertEquals("Hello", autoTitle("\n  \n  Hello  \nmore"))
        assertEquals("short", autoTitle("short\n" + "b".repeat(100)))
    }

    @Test
    fun autoTitleCutsLongFirstLineOfMultiline() {
        assertEquals("a".repeat(40) + "…", autoTitle("a".repeat(60) + "\nshort"))
    }

    @Test
    fun findSingleUrlReturnsLoneUrl() {
        assertEquals("https://example.com/a", findSingleUrl("look https://example.com/a here"))
        assertEquals("http://example.com", findSingleUrl("http://example.com"))
    }

    @Test
    fun findSingleUrlStripsTrailingPunctuation() {
        assertEquals("https://example.com/a", findSingleUrl("see https://example.com/a."))
        assertEquals("https://example.com/a", findSingleUrl("(https://example.com/a)"))
        assertEquals("https://example.com/a", findSingleUrl("https://example.com/a, next"))
    }

    @Test
    fun findSingleUrlNullWithoutExactlyOne() {
        assertNull(findSingleUrl("no url here"))
        assertNull(findSingleUrl("https://one.com and https://two.com"))
        assertNull(findSingleUrl(""))
    }

    @Test
    fun linkTitlePrefersSubject() {
        assertEquals(
            "Dinner idea",
            linkTitle("Dinner idea", "https://example.com/recipes/42", "https://example.com/recipes/42"),
        )
        assertEquals(
            "Dinner idea",
            linkTitle("  Dinner idea  ", "https://example.com/x", "https://example.com/x"),
        )
    }

    @Test
    fun linkTitleUsesRemainingText() {
        assertEquals(
            "tasty pasta",
            linkTitle(null, "tasty pasta https://example.com/r", "https://example.com/r"),
        )
        assertEquals(
            "a".repeat(40) + "…",
            linkTitle(null, "a".repeat(60) + " https://example.com/r", "https://example.com/r"),
        )
    }

    @Test
    fun linkTitleFallsBackToHostAndPath() {
        assertEquals(
            "example.com/recipes",
            linkTitle(null, "https://www.example.com/recipes/42", "https://www.example.com/recipes/42"),
        )
        assertEquals(
            "example.com/recipes",
            linkTitle("  ", "https://www.example.com/recipes/42", "https://www.example.com/recipes/42"),
        )
    }

    @Test
    fun linkTitleFallsBackToBareHost() {
        assertEquals("example.com", linkTitle(null, "https://www.example.com", "https://www.example.com"))
        assertEquals("example.com", linkTitle(null, "https://example.com/", "https://example.com/"))
    }

    @Test
    fun textTitlePrefersSubject() {
        assertEquals("My note", textTitle("My note", "body text that is longer than forty characters yes"))
        assertEquals(
            "a".repeat(40) + "…",
            textTitle(null, "a".repeat(60)),
        )
        assertEquals("short", textTitle(null, "short"))
    }

    @Test
    fun capTextTruncatesAtLimit() {
        val (kept, truncated) = capText("a".repeat(50_001))
        assertEquals(50_000, kept.length)
        assertTrue(truncated)
        val (same, notTruncated) = capText("a".repeat(50_000))
        assertEquals(50_000, same.length)
        assertFalse(notTruncated)
    }

    @Test
    fun cameraNamesAreNotMeaningful() {
        assertFalse(isMeaningfulImageName(null))
        assertFalse(isMeaningfulImageName(""))
        assertFalse(isMeaningfulImageName("   "))
        assertFalse(isMeaningfulImageName("IMG_20240101_123456.jpg"))
        assertFalse(isMeaningfulImageName("img_123.png"))
        assertFalse(isMeaningfulImageName("PXL_20240101_123456.jpg"))
        assertFalse(isMeaningfulImageName("Screenshot_20240101_123456.png"))
        assertFalse(isMeaningfulImageName("screenshot_1.jpg"))
    }

    @Test
    fun uuidNamesAreNotMeaningful() {
        assertFalse(isMeaningfulImageName("550e8400-e29b-41d4-a716-446655440000.jpg"))
        assertFalse(isMeaningfulImageName("550E8400-E29B-41D4-A716-446655440000.png"))
    }

    @Test
    fun humanNamesAreMeaningful() {
        assertTrue(isMeaningfulImageName("holiday.jpg"))
        assertTrue(isMeaningfulImageName("holiday"))
        assertTrue(isMeaningfulImageName("IMG_awesome.png"))
        assertTrue(isMeaningfulImageName("screenshot final.jpg"))
    }

    @Test
    fun imageTitlePrefersCaption() {
        assertEquals("sunset", imageTitle("sunset", "holiday.jpg", "1 Jan 2026"))
        assertEquals("sunset", imageTitle("\n  sunset  \nmore", "IMG_20240101_123456.jpg", "1 Jan 2026"))
    }

    @Test
    fun imageTitleFallsBackToNameThenDate() {
        assertEquals("holiday", imageTitle(null, "holiday.jpg", "1 Jan 2026"))
        assertEquals("holiday", imageTitle("  ", "holiday.jpg", "1 Jan 2026"))
        assertEquals("Image · 1 Jan 2026", imageTitle(null, "IMG_20240101_123456.jpg", "1 Jan 2026"))
        assertEquals("Image · 1 Jan 2026", imageTitle(null, null, "1 Jan 2026"))
    }

    @Test
    fun linkHostStripsWww() {
        assertEquals("example.com", linkHost("https://www.example.com/recipes/42"))
        assertEquals("example.com", linkHost("https://example.com/"))
    }
}
