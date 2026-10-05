package app.pastille.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ClipboardClearDelayTest {

    @Test
    fun `the default is off`() {
        assertEquals(ClipboardClearDelay.Off, ClipboardClearDelay.fromKey(null))
        assertEquals(ClipboardClearDelay.Off, ClipboardClearDelay.fromKey("unknown"))
    }

    @Test
    fun `offers off, 5, 10, 30 and 60 seconds`() {
        assertEquals(listOf(null, 5, 10, 30, 60), ClipboardClearDelay.entries.map { it.seconds })
    }

    @Test
    fun `stored keys round-trip`() {
        ClipboardClearDelay.entries.forEach { assertEquals(it, ClipboardClearDelay.fromKey(it.key)) }
    }

    @Test
    fun `snackbar durations read as seconds, then minutes`() {
        assertEquals("10s", shortDuration(10))
        assertEquals("1 min", shortDuration(60))
    }
}
