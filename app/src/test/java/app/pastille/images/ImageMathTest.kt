package app.pastille.images

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageMathTest {

    @Test
    fun smallImageKeepsSize() {
        assertEquals(800 to 600, targetSize(800, 600))
    }

    @Test
    fun longEdgeScalesTo2048KeepingAspect() {
        assertEquals(2048 to 1024, targetSize(4000, 2000))
        assertEquals(1024 to 2048, targetSize(2000, 4000))
    }

    @Test
    fun exactLimitNeverUpscales() {
        assertEquals(2048 to 1024, targetSize(2048, 1024))
        assertEquals(100 to 100, targetSize(100, 100))
    }

    @Test
    fun roundingNeverDropsToZero() {
        val (width, height) = targetSize(5000, 1)
        assertEquals(2048 to 1, width to height)
        assertTrue(width >= 1 && height >= 1)
    }

    @Test
    fun sampleSizeIsLargestPowerOfTwoAboveTarget() {
        assertEquals(1, sampleSizeFor(800, 600, 2048))
        assertEquals(1, sampleSizeFor(4000, 2000, 2048))
        assertEquals(2, sampleSizeFor(8000, 6000, 2048))
        assertEquals(4, sampleSizeFor(8192, 8192, 2048))
    }

    @Test
    fun extensionForKnownTypes() {
        assertEquals("gif", extensionFor("image/gif"))
        assertEquals("png", extensionFor("image/png"))
        assertEquals("jpg", extensionFor("image/jpeg"))
        assertEquals("jpg", extensionFor("image/webp"))
        assertEquals("jpg", extensionFor("image/heic"))
        assertEquals("jpg", extensionFor(""))
    }

    @Test
    fun mimeTypeForFileRoundTrips() {
        assertEquals("image/png", mimeTypeForFile("abc.png"))
        assertEquals("image/gif", mimeTypeForFile("abc.GIF"))
        assertEquals("image/jpeg", mimeTypeForFile("abc.jpg"))
        assertEquals("image/jpeg", mimeTypeForFile("abc.jpeg"))
        assertEquals("image/webp", mimeTypeForFile("abc.webp"))
    }

    @Test
    fun hashNameUsesFirst32HexChars() {
        val digest = ByteArray(32) { it.toByte() }
        val hex = digest.joinToString("") { "%02x".format(it) }.take(32)
        assertEquals("$hex.png", hashName(digest, "png"))
        assertEquals(32 + 1 + 3, hashName(digest, "png").length)
    }

    @Test
    fun orphansKeepsReferencedAndDropsOldTmp() {
        val old = NOW - 2 * HOUR
        val present = listOf("a.jpg" to old, "b.png" to old, ".tmp-123" to old, "c.gif" to old)
        assertEquals(
            listOf("b.png", ".tmp-123", "c.gif"),
            orphans(present, setOf("a.jpg"), NOW),
        )
        assertEquals(emptyList<String>(), orphans(present, present.map { it.first }.toSet(), NOW))
    }

    @Test
    fun orphansSparesFilesYoungerThanAnHour() {
        val present = listOf("fresh.jpg" to NOW - HOUR / 2, ".tmp-456" to NOW - 1000, "stale.jpg" to NOW - 2 * HOUR)

        assertEquals(listOf("stale.jpg"), orphans(present, emptySet(), NOW))
    }

    @Test
    fun staleSharedDropsOnlyFilesOlderThan24h() {
        val now = 1_000_000_000L
        val hour = 60 * 60 * 1000L
        val files = listOf(
            "fresh.png" to (now - hour),
            "old.png" to (now - 25 * hour),
            "edge.png" to (now - 24 * hour),
        )
        assertEquals(listOf("old.png"), staleShared(files, now))
    }

    private companion object {
        const val NOW = 1_000_000_000L
        const val HOUR = 60 * 60 * 1000L
    }
}
