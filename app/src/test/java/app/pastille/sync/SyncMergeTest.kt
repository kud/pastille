package app.pastille.sync

import app.pastille.backup.SnippetBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {

    private val now = 1_000_000L
    private val work = SyncCategory(uid = "c-work", name = "Work", position = 0, createdAt = 1, updatedAt = 1)
    private val home = SyncCategory(uid = "c-home", name = "Home", position = 1, createdAt = 1, updatedAt = 1)
    private val hello = SyncSnippet(uid = "s-hello", title = "Hi", text = "hello", createdAt = 1, updatedAt = 1, lastUsedAt = 1, categoryUid = "c-work")
    private val bye = SyncSnippet(uid = "s-bye", title = "Bye", text = "goodbye", createdAt = 1, updatedAt = 1, lastUsedAt = 1, position = 1)
    private val base = SyncState(snippets = listOf(hello, bye), categories = listOf(work, home))

    private fun merge(b: SyncState?, l: SyncState, r: SyncState) = SyncMerge.merge(b, l, r, now)

    private fun SyncState.snippet(uid: String) = snippets.single { it.uid == uid }

    private fun SyncState.with(vararg changed: SyncSnippet) =
        copy(snippets = snippets.map { s -> changed.firstOrNull { it.uid == s.uid } ?: s } + changed.filter { c -> snippets.none { it.uid == c.uid } })

    @Test
    fun `an item new on either side is kept`() {
        val mine = SyncSnippet(uid = "s-mine", text = "mine", updatedAt = 5)
        val theirs = SyncSnippet(uid = "s-theirs", text = "theirs", updatedAt = 6)
        val merged = merge(base, base.with(mine), base.with(theirs)).state
        assertEquals(setOf("s-hello", "s-bye", "s-mine", "s-theirs"), merged.snippets.map { it.uid }.toSet())
    }

    @Test
    fun `the side that edited wins, whatever the clocks say`() {
        // The remote phone's clock is behind: its edit still wins, because only it changed the text.
        val remote = base.with(hello.copy(text = "hello there", updatedAt = 0))
        val merged = merge(base, base, remote).state
        assertEquals("hello there", merged.snippet("s-hello").text)
    }

    @Test
    fun `different edits to the text on both sides keep both`() {
        val local = base.with(hello.copy(text = "hello A", updatedAt = 10))
        val remote = base.with(hello.copy(text = "hello B", updatedAt = 20))
        val result = merge(base, local, remote)
        assertEquals(1, result.conflicts)
        assertEquals("hello B", result.state.snippet("s-hello").text)
        val copy = result.state.snippets.single { it.text == "hello A" }
        assertTrue(copy.uid != "s-hello")
        // Both phones derive the same copy, whichever side runs the merge.
        val mirrored = merge(base, remote, local).state
        assertEquals(result.state, mirrored)
    }

    @Test
    fun `the same edit on both sides is not a conflict`() {
        val local = base.with(hello.copy(text = "same", updatedAt = 10))
        val remote = base.with(hello.copy(text = "same", updatedAt = 20))
        val result = merge(base, local, remote)
        assertEquals(0, result.conflicts)
        assertEquals(2, result.state.snippets.size)
        assertEquals(20L, result.state.snippet("s-hello").updatedAt)
    }

    @Test
    fun `different fields edited on each side both survive`() {
        val local = base.with(hello.copy(pinned = true, updatedAt = 10))
        val remote = base.with(hello.copy(categoryUid = "c-home", tags = listOf("otp"), updatedAt = 20))
        val merged = merge(base, local, remote).state.snippet("s-hello")
        assertTrue(merged.pinned)
        assertEquals("c-home", merged.categoryUid)
        assertEquals(listOf("otp"), merged.tags)
        assertEquals("hello", merged.text)
    }

    @Test
    fun `the same field changed on both sides takes the later edit`() {
        val local = base.with(hello.copy(categoryUid = null, updatedAt = 30))
        val remote = base.with(hello.copy(categoryUid = "c-home", updatedAt = 20))
        assertNull(merge(base, local, remote).state.snippet("s-hello").categoryUid)
    }

    @Test
    fun `use is not an edit, and the later use is kept`() {
        val local = base.with(hello.copy(lastUsedAt = 50))
        val remote = base.with(hello.copy(text = "edited", updatedAt = 20, lastUsedAt = 40))
        val merged = merge(base, local, remote).state.snippet("s-hello")
        assertEquals("edited", merged.text)
        assertEquals(50L, merged.lastUsedAt)
    }

    @Test
    fun `a deletion reaches the other side`() {
        val remote = base.copy(snippets = listOf(bye), tombstones = listOf(SyncTombstone("s-hello", "snippet", 100)))
        val merged = merge(base, base, remote).state
        assertEquals(listOf("s-bye"), merged.snippets.map { it.uid })
        assertEquals(listOf("s-hello"), merged.tombstones.map { it.uid })
    }

    @Test
    fun `an edit made after the deletion wins over it`() {
        val local = base.with(hello.copy(text = "still needed", updatedAt = 200))
        val remote = base.copy(snippets = listOf(bye), tombstones = listOf(SyncTombstone("s-hello", "snippet", 100)))
        val merged = merge(base, local, remote).state
        assertEquals("still needed", merged.snippet("s-hello").text)
        assertTrue(merged.tombstones.isEmpty())
    }

    @Test
    fun `an edit made before the deletion goes with it`() {
        val local = base.with(hello.copy(text = "old edit", updatedAt = 50))
        val remote = base.copy(snippets = listOf(bye), tombstones = listOf(SyncTombstone("s-hello", "snippet", 100)))
        assertEquals(listOf("s-bye"), merge(base, local, remote).state.snippets.map { it.uid })
    }

    @Test
    fun `an absence without a tombstone never deletes`() {
        val remote = base.copy(snippets = listOf(bye))
        assertEquals(2, merge(base, base, remote).state.snippets.size)
    }

    @Test
    fun `the bin travels as a field`() {
        val remote = base.with(hello.copy(deletedAt = 300, updatedAt = 300))
        assertEquals(300L, merge(base, base, remote).state.snippet("s-hello").deletedAt)
    }

    @Test
    fun `a renamed folder and a snippet moved into it on the other side`() {
        val local = base.copy(categories = listOf(work.copy(name = "Job", updatedAt = 10), home))
        val remote = base.with(bye.copy(categoryUid = "c-work", updatedAt = 20))
        val merged = merge(base, local, remote).state
        assertEquals("Job", merged.categories.single { it.uid == "c-work" }.name)
        assertEquals("c-work", merged.snippet("s-bye").categoryUid)
    }

    @Test
    fun `a deleted folder leaves its snippets unfiled`() {
        val remote = base.copy(categories = listOf(home), tombstones = listOf(SyncTombstone("c-work", "category", 100)))
        val merged = merge(base, base, remote).state
        assertEquals(listOf("c-home"), merged.categories.map { it.uid })
        assertNull(merged.snippet("s-hello").categoryUid)
    }

    @Test
    fun `the same folder created on both phones becomes one`() {
        val mine = SyncCategory(uid = "c-zz", name = "Codes", createdAt = 5, updatedAt = 5)
        val theirs = SyncCategory(uid = "c-aa", name = "codes", createdAt = 6, updatedAt = 6)
        val local = base.copy(categories = base.categories + mine).with(bye.copy(categoryUid = "c-zz", updatedAt = 5))
        val remote = base.copy(categories = base.categories + theirs)
        val result = merge(base, local, remote)
        val codes = result.state.categories.filter { it.name.equals("codes", ignoreCase = true) }
        assertEquals(listOf("c-aa"), codes.map { it.uid })
        assertEquals(mapOf("c-zz" to "c-aa"), result.renamedCategories)
        assertEquals("c-aa", result.state.snippet("s-bye").categoryUid)
    }

    @Test
    fun `the same text saved on both phones becomes one snippet`() {
        val mine = SyncSnippet(uid = "s-z", text = "otp 1234", updatedAt = 5)
        val theirs = SyncSnippet(uid = "s-a", text = "otp 1234", updatedAt = 6, pinned = true)
        val result = merge(null, SyncState(listOf(mine)), SyncState(listOf(theirs)))
        assertEquals(1, result.state.snippets.size)
        assertEquals("s-a", result.state.snippets.single().uid)
        assertTrue(result.state.snippets.single().pinned)
        assertEquals(mapOf("s-z" to "s-a"), result.renamedSnippets)
    }

    @Test
    fun `images pair by their content name`() {
        val mine = SyncSnippet(uid = "s-2", imageFile = "aaaaaaaa.png", title = "Cat", updatedAt = 5)
        val theirs = SyncSnippet(uid = "s-1", imageFile = "aaaaaaaa.png", title = "Kitten", updatedAt = 6)
        val other = SyncSnippet(uid = "s-3", imageFile = "bbbbbbbb.webp", sticker = true, updatedAt = 6)
        val merged = merge(null, SyncState(listOf(mine)), SyncState(listOf(theirs, other))).state
        assertEquals(listOf("aaaaaaaa.png", "bbbbbbbb.webp"), merged.snippets.map { it.imageFile }.sortedBy { it })
        assertEquals("Kitten", merged.snippet("s-1").title)
        assertTrue(merged.snippet("s-3").sticker)
    }

    @Test
    fun `tags are names, and one uid per name on both phones`() {
        val local = base.copy(tags = listOf(SyncTag("t-z", "otp", 1, 1))).with(hello.copy(tags = listOf("otp")))
        val remote = base.copy(tags = listOf(SyncTag("t-a", "otp", 2, 2))).with(bye.copy(tags = listOf("OTP"), updatedAt = 3))
        val merged = merge(base, local, remote).state
        assertEquals(listOf(SyncTag("t-a", "otp", 1, 2)), merged.tags)
        assertEquals(listOf("otp"), merged.snippet("s-bye").tags)
    }

    @Test
    fun `very old tombstones are pruned`() {
        val old = SyncTombstone("gone", "snippet", now - SyncMerge.TOMBSTONE_KEEP_MS - 1)
        val recent = SyncTombstone("recent", "snippet", now - 10)
        val merged = merge(base, base.copy(tombstones = listOf(old, recent)), base).state
        assertEquals(listOf("recent"), merged.tombstones.map { it.uid })
    }

    @Test
    fun `merging the same file twice changes nothing`() {
        val local = base.with(hello.copy(text = "hello A", updatedAt = 10), SyncSnippet(uid = "s-new", text = "new", updatedAt = 11))
        val remote = base.with(hello.copy(text = "hello B", updatedAt = 20), bye.copy(pinned = true, updatedAt = 21))
            .copy(tombstones = listOf(SyncTombstone("s-old", "snippet", 5)))
        val first = merge(base, local, remote).state
        // After a merge, the file read is the new base.
        assertEquals(first, merge(remote, first, remote).state)
        assertEquals(first, merge(first, first, first).state)
    }

    // Two phones and one file, each syncing in turn, as AutoBackup does: read and merge (the file
    // becomes the base), then write (what was written becomes the base).
    private class Phone(var state: SyncState, var base: SyncState?) {
        fun sync(file: SyncState, now: Long): SyncState {
            state = SyncMerge.merge(base, state, file, now).state
            base = state
            return state
        }
    }

    @Test
    fun `two phones converge`() {
        val a = Phone(base, base)
        val b = Phone(base, base)
        var file = base

        a.state = a.state.with(hello.copy(text = "from A", updatedAt = 10), SyncSnippet(uid = "s-a", text = "A only", updatedAt = 10))
            .copy(categories = listOf(work.copy(name = "Job", updatedAt = 10), home))
        b.state = b.state.with(hello.copy(text = "from B", updatedAt = 20), bye.copy(pinned = true, updatedAt = 20))
            .copy(categories = listOf(work), tombstones = listOf(SyncTombstone("c-home", "category", 15)))

        file = a.sync(file, now)
        file = b.sync(file, now)
        file = a.sync(file, now)
        file = b.sync(file, now)

        assertEquals(a.state, b.state)
        assertEquals(file, a.state)
        assertEquals(setOf("from A", "from B", "A only", "goodbye"), a.state.snippets.map { it.text }.toSet())
        assertEquals(listOf("Job"), a.state.categories.map { it.name })
        assertTrue(a.state.snippet("s-bye").pinned)
        // Another round changes nothing.
        assertEquals(file, a.sync(file, now))
    }

    @Test
    fun `a backup from before sync reads the same on both phones`() {
        val v3 = """{"version":3,"categories":[{"name":"Work","position":0}],
            |"snippets":[{"text":"hello","category":"Work","tags":["OTP"]}]}""".trimMargin()
        val one = SnippetBackup.decodeState(v3)
        assertEquals(one, SnippetBackup.decodeState(v3))
        assertEquals(one.categories.single().uid, one.snippets.single().categoryUid)
        assertEquals(listOf("otp"), one.snippets.single().tags)
        assertTrue(one.snippets.single().uid.isNotEmpty())
    }

    @Test
    fun `the synced file round-trips and keeps the bin out of old readers`() {
        val state = SyncState(
            snippets = listOf(hello.copy(tags = listOf("otp")), bye.copy(deletedAt = 9, updatedAt = 9)),
            categories = listOf(work),
            tags = listOf(SyncTag("t", "otp", 1, 1)),
            tombstones = listOf(SyncTombstone("x", "snippet", 7)),
        )
        val raw = SnippetBackup.encodeState(state)
        assertEquals(state.copy(snippets = state.snippets.sortedBy { it.uid }), SnippetBackup.decodeState(raw).let { it.copy(snippets = it.snippets.sortedBy { s -> s.uid }) })
        // An import (and any build from before sync) reads `snippets` only: the bin stays out.
        assertEquals(listOf("hello"), SnippetBackup.decode(raw).snippets.map { it.record.text })
        assertTrue(raw.contains("\"version\": 4"))
    }
}
