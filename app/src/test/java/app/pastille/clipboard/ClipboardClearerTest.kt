package app.pastille.clipboard

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClipboardClearerTest {

    private class FakeClipboard(var label: CharSequence?, val readable: Boolean = true) : ClipboardAccess {
        var clears = 0

        override fun primaryClipLabel(): CharSequence? = if (readable) label else null

        override fun clear() {
            clears++
            label = null
        }
    }

    private val ours = "pastille:1b2c3d4e-0000-4000-8000-000000000001"

    @Test
    fun `labels carry the pastille prefix and are unique`() {
        val first = newClipLabel()
        assertTrue(first.startsWith(CLIP_LABEL_PREFIX))
        assertTrue(first != newClipLabel())
    }

    @Test
    fun `the label check only accepts our exact label`() {
        assertTrue(isOwnClip(ours, ours))
        assertFalse(isOwnClip("pastille:someone-else", ours))
        assertFalse(isOwnClip("Copied text", ours))
        assertFalse(isOwnClip(null, ours))
    }

    @Test
    fun `clears our clip when the timer ends, not before`() {
        val scope = TestScope()
        val clipboard = FakeClipboard(label = ours)
        val clearer = ClipboardClearer(scope, clipboard)

        clearer.schedule(ours, 10_000)
        scope.advanceTimeBy(9_999)
        scope.runCurrent()
        assertEquals(0, clipboard.clears)

        scope.advanceTimeBy(2)
        scope.runCurrent()
        assertEquals(1, clipboard.clears)
    }

    @Test
    fun `leaves the clipboard alone when something else was copied since`() {
        val scope = TestScope()
        val clipboard = FakeClipboard(label = ours)
        val clearer = ClipboardClearer(scope, clipboard)

        clearer.schedule(ours, 5_000)
        clipboard.label = "Copied from another app"
        scope.advanceTimeBy(6_000)
        scope.runCurrent()

        assertEquals(0, clipboard.clears)
        assertEquals("Copied from another app", clipboard.label)
    }

    @Test
    fun `never wipes blind when the clipboard can't be read`() {
        val scope = TestScope()
        val clipboard = FakeClipboard(label = ours, readable = false)
        val clearer = ClipboardClearer(scope, clipboard)

        clearer.schedule(ours, 5_000)
        scope.advanceTimeBy(6_000)
        scope.runCurrent()

        assertEquals(0, clipboard.clears)
    }

    @Test
    fun `a new copy replaces the pending timer`() {
        val scope = TestScope()
        val clipboard = FakeClipboard(label = ours)
        val clearer = ClipboardClearer(scope, clipboard)
        val next = "pastille:1b2c3d4e-0000-4000-8000-000000000002"

        clearer.schedule(ours, 5_000)
        scope.advanceTimeBy(4_000)
        clipboard.label = next
        clearer.schedule(next, 30_000)
        scope.advanceTimeBy(2_000)
        scope.runCurrent()
        assertEquals(0, clipboard.clears)

        scope.advanceTimeBy(29_000)
        scope.runCurrent()
        assertEquals(1, clipboard.clears)
    }

    @Test
    fun `clear now clears our clip at once and stops the timer`() {
        val scope = TestScope()
        val clipboard = FakeClipboard(label = ours)
        val clearer = ClipboardClearer(scope, clipboard)

        clearer.schedule(ours, 10_000)
        assertTrue(clearer.clearNow(ours))
        assertEquals(1, clipboard.clears)

        scope.advanceTimeBy(11_000)
        scope.runCurrent()
        assertEquals(1, clipboard.clears)
    }

    @Test
    fun `clear now leaves someone else's clip`() {
        val scope = TestScope()
        val clipboard = FakeClipboard(label = "pastille:another-copy")
        val clearer = ClipboardClearer(scope, clipboard)

        assertFalse(clearer.clearNow(ours))
        assertEquals(0, clipboard.clears)
    }

    @Test
    fun `cancel stops a pending clear`() {
        val scope = TestScope()
        val clipboard = FakeClipboard(label = ours)
        val clearer = ClipboardClearer(scope, clipboard)

        clearer.schedule(ours, 5_000)
        clearer.cancel()
        scope.advanceTimeBy(6_000)
        scope.runCurrent()

        assertEquals(0, clipboard.clears)
    }
}
