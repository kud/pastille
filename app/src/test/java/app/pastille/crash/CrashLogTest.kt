package app.pastille.crash

import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashLogTest {

    @Test
    fun recordStoresVersionAndMessage() {
        val dir = createTempDirectory("crashlog").toFile()
        val log = CrashLog(dir)
        log.record(RuntimeException("boom happened"), "1.2.3", "14 (API 34)", now = 1700000000000L)
        val entries = log.entries()
        assertEquals(1, entries.size)
        val entry = entries.single()
        assertTrue(entry.contains("1.2.3"))
        assertTrue(entry.contains("14 (API 34)"))
        assertTrue(entry.contains("boom happened"))
    }

    @Test
    fun keepsOnlyNewestFive() {
        val dir = createTempDirectory("crashlog").toFile()
        val log = CrashLog(dir)
        for (i in 1..7) {
            log.record(RuntimeException("crash $i"), "v", "a", now = 1700000000000L + i)
        }
        val entries = log.entries()
        assertEquals(5, entries.size)
        assertTrue(entries.first().contains("crash 7"))
        assertTrue(entries.last().contains("crash 3"))
    }

    @Test
    fun clearEmptiesEntries() {
        val dir = createTempDirectory("crashlog").toFile()
        val log = CrashLog(dir)
        log.record(RuntimeException("boom"), "v", "a", now = 1700000000000L)
        log.clear()
        assertTrue(log.entries().isEmpty())
    }

    @Test
    fun syncFailuresAreListedOnceEachAndCleared() {
        val dir = createTempDirectory("crashlog").toFile()
        val log = CrashLog(dir)
        log.record(RuntimeException("boom"), "v", "a", now = 1700000000000L)
        log.recordSync("Sync failed: write\nProvider: p", "Write|x|p", "v", "a", now = 1700000000005L)
        // The same failure on every trigger logs once.
        log.recordSync("Sync failed: write\nProvider: p", "Write|x|p", "v", "a", now = 1700000000009L)
        val entries = log.entries()
        assertEquals(2, entries.size)
        assertTrue(entries.first().contains("Sync failed: write"))
        assertTrue(entries.last().contains("boom"))
        log.recordSync("Sync failed: read", "Read|y|p", "v", "a", now = 1700000000010L)
        assertEquals(3, log.entries().size)
        log.clear()
        assertTrue(log.entries().isEmpty())
    }

    @Test
    fun keepsOnlyNewestThreeSyncFailures() {
        val dir = createTempDirectory("crashlog").toFile()
        val log = CrashLog(dir)
        for (i in 1..5) log.recordSync("Sync failed $i", "sig$i", "v", "a", now = 1700000000000L + i)
        val entries = log.entries()
        assertEquals(3, entries.size)
        assertTrue(entries.first().contains("Sync failed 5"))
    }
}
