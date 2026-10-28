package app.pastille.clipboard

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecentClipsTest {

    private var stamps = 100L
    private fun clip(text: String?, label: String? = "text", sensitive: Boolean = false, stamp: Long = stamps++) =
        ClipCandidate(text = text, label = label, sensitive = sensitive, stamp = stamp)

    private fun RecentClipsState.texts() = clips.map { it.text }

    @Test
    fun `plain text is recorded, newest first`() {
        val state = RecentClipsState()
            .record(clip("first"), now = 1_000)
            .record(clip("second"), now = 2_000)
        assertEquals(listOf("second", "first"), state.texts())
    }

    @Test
    fun `a clip marked sensitive is skipped`() {
        val state = RecentClipsState().record(clip("hunter2", sensitive = true), now = 1_000)
        assertTrue(state.clips.isEmpty())
    }

    @Test
    fun `nothing is kept while the keyboard sits in a password field`() {
        val state = RecentClipsState().record(clip("hunter2"), now = 1_000, sensitiveField = true)
        assertTrue(state.clips.isEmpty())
        assertFalse(isRecordable(clip("hunter2"), sensitiveField = true))
    }

    @Test
    fun `pastille's own copies are skipped`() {
        val state = RecentClipsState().record(clip("A snippet", label = newClipLabel()), now = 1_000)
        assertTrue(state.clips.isEmpty())
    }

    @Test
    fun `a label that only mentions pastille is not ours`() {
        val state = RecentClipsState().record(clip("hello", label = "Copied from pastille:"), now = 1_000)
        assertEquals(listOf("hello"), state.texts())
    }

    @Test
    fun `blank and non-text clips are skipped`() {
        val state = RecentClipsState()
            .record(clip(null), now = 1_000)
            .record(clip("   \n"), now = 1_000)
        assertTrue(state.clips.isEmpty())
    }

    @Test
    fun `the same text again moves to the front instead of repeating`() {
        val state = RecentClipsState()
            .record(clip("a"), now = 1_000)
            .record(clip("b"), now = 2_000)
            .record(clip("a"), now = 3_000)
        assertEquals(listOf("a", "b"), state.texts())
        assertEquals(3_000L, state.clips.first().at)
        assertEquals("keeps its id", 1L, state.clips.first().id)
    }

    @Test
    fun `at most ten are kept, the oldest go`() {
        var state = RecentClipsState()
        (1..12).forEach { state = state.record(clip("clip $it"), now = it * 1_000L) }
        assertEquals(RECENT_CLIPS_MAX, state.clips.size)
        assertEquals("clip 12", state.clips.first().text)
        assertEquals("clip 3", state.clips.last().text)
    }

    @Test
    fun `clips expire after an hour`() {
        val state = RecentClipsState()
            .record(clip("old"), now = 0)
            .record(clip("new"), now = 30 * 60_000L)
        assertEquals(listOf("new", "old"), state.pruned(RECENT_CLIP_TTL_MS - 1).texts())
        assertEquals(listOf("new"), state.pruned(RECENT_CLIP_TTL_MS).texts())
        assertTrue(state.pruned(RECENT_CLIP_TTL_MS + 30 * 60_000L).clips.isEmpty())
    }

    @Test
    fun `recording prunes expired clips first`() {
        val state = RecentClipsState()
            .record(clip("old"), now = 0)
            .record(clip("new"), now = RECENT_CLIP_TTL_MS + 1)
        assertEquals(listOf("new"), state.texts())
    }

    @Test
    fun `a clip from the future (clock moved back) is dropped`() {
        val state = RecentClipsState().record(clip("a"), now = 10_000)
        assertTrue(state.pruned(5_000).clips.isEmpty())
    }

    @Test
    fun `the same copy is looked at once, so a cleared clip doesn't come back`() {
        val copy = clip("secret-ish", stamp = 42)
        val cleared = RecentClipsState().record(copy, now = 1_000).cleared()
        val reopened = cleared.record(copy, now = 2_000)
        assertTrue(reopened.clips.isEmpty())
        val removed = RecentClipsState().record(copy, now = 1_000).let { it.remove(it.clips.single().id) }
        assertTrue(removed.record(copy, now = 2_000).clips.isEmpty())
    }

    @Test
    fun `copying the same text again is a new copy`() {
        val cleared = RecentClipsState().record(clip("again", stamp = 42), now = 1_000).cleared()
        assertEquals(listOf("again"), cleared.record(clip("again", stamp = 43), now = 2_000).texts())
    }

    @Test
    fun `an unknown stamp is always looked at`() {
        val state = RecentClipsState()
            .record(clip("a", stamp = 0), now = 1_000)
            .cleared()
            .record(clip("a", stamp = 0), now = 2_000)
        assertEquals(listOf("a"), state.texts())
    }

    @Test
    fun `a skipped clip still counts as seen`() {
        val sensitive = clip("hunter2", sensitive = true, stamp = 7)
        val state = RecentClipsState().record(sensitive, now = 1_000)
        assertEquals(7L, state.lastSeenStamp)
    }

    @Test
    fun `long clips are cut to the cap`() {
        val state = RecentClipsState().record(clip("x".repeat(100)), now = 1_000, maxChars = 10)
        assertEquals(10, state.clips.single().text.length)
    }

    @Test
    fun `next expiry follows the oldest clip`() {
        assertNull(RecentClipsState().nextExpiry())
        val state = RecentClipsState().record(clip("a"), now = 5).record(clip("b"), now = 9)
        assertEquals(5 + RECENT_CLIP_TTL_MS, state.nextExpiry())
    }

    @Test
    fun `the holder drops clips on time and clears when switched off`() {
        val scope = TestScope()
        var enabled = true
        val holder = RecentClipboard(
            scope = scope,
            enabled = { enabled },
            now = { scope.testScheduler.currentTime },
        )
        holder.record(clip("a"))
        assertEquals(1, holder.state.value.clips.size)
        scope.advanceTimeBy(RECENT_CLIP_TTL_MS - 1)
        scope.runCurrent()
        assertEquals(1, holder.state.value.clips.size)
        scope.advanceTimeBy(2)
        scope.runCurrent()
        assertTrue(holder.state.value.clips.isEmpty())

        holder.record(clip("b"))
        enabled = false
        holder.record(clip("c"))
        assertTrue(holder.state.value.clips.isEmpty())
    }
}
