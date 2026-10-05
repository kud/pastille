package app.pastille.images

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        assertEquals("webp", extensionFor("image/webp"))
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

    @Test
    fun storedNameMatchesTheStemWhateverTheExtension() {
        val present = listOf(".tmp-abc", "0123.jpg", "4567.png")
        assertEquals("0123.jpg", storedNameForStem("0123", present))
        assertEquals("4567.png", storedNameForStem("4567", present))
        assertNull(storedNameForStem("89ab", present))
    }

    @Test
    fun isAnimatedWebpReadsTheVp8xAnimationFlag() {
        assertTrue(isAnimatedWebp(webpHeader("VP8X", flags = 0x02)))
        assertTrue(isAnimatedWebp(webpHeader("VP8X", flags = 0x12)))
        assertFalse(isAnimatedWebp(webpHeader("VP8X", flags = 0x10)))
        assertFalse(isAnimatedWebp(webpHeader("VP8 ", flags = 0x02)))
        assertFalse(isAnimatedWebp(webpHeader("VP8L", flags = 0x02)))
    }

    @Test
    fun isAnimatedWebpIsFalseOnShortOrGarbageInput() {
        assertFalse(isAnimatedWebp(ByteArray(0)))
        assertFalse(isAnimatedWebp(webpHeader("VP8X", flags = 0x02).copyOf(20)))
        assertFalse(isAnimatedWebp(ByteArray(64) { (it * 37).toByte() }))
    }

    @Test
    fun storagePlanKeepsAnimationAndTransparency() {
        assertEquals(StoragePlan.Copy, storagePlan("image/webp", 3000, 3000, MB, animated = true))
        assertEquals(StoragePlan.Copy, storagePlan("image/webp", 512, 512, MB, animated = false))
        assertEquals(StoragePlan.Copy, storagePlan("image/gif", 3000, 3000, MB, animated = false))
        assertEquals(StoragePlan.Copy, storagePlan("image/png", 1000, 1000, MB, animated = false))
    }

    @Test
    fun storagePlanRefusesOversizedAnimations() {
        assertTrue(storagePlan("image/webp", 512, 512, 16 * MB, animated = true) is StoragePlan.Refuse)
        assertTrue(storagePlan("image/gif", 512, 512, 16 * MB, animated = false) is StoragePlan.Refuse)
    }

    @Test
    fun storagePlanTranscodesLargeOrOtherImages() {
        assertEquals(StoragePlan.Transcode(2048), storagePlan("image/webp", 4000, 3000, MB, animated = false))
        assertEquals(StoragePlan.Transcode(2048), storagePlan("image/png", 4000, 3000, MB, animated = false))
        assertEquals(StoragePlan.Transcode(2048), storagePlan("image/jpeg", 4000, 3000, MB, animated = false))
        assertEquals(StoragePlan.Transcode(2048), storagePlan("image/jpeg", 800, 600, MB, animated = false))
        assertEquals(StoragePlan.Transcode(2048), storagePlan(null, 800, 600, MB, animated = false))
    }

    @Test
    fun thumbnailCacheIsAnEighthOfMemoryClassBetween8And32Mb() {
        assertEquals(8 * MB.toInt(), thumbnailCacheBytes(32))
        assertEquals(24 * MB.toInt(), thumbnailCacheBytes(192))
        assertEquals(32 * MB.toInt(), thumbnailCacheBytes(512))
        assertEquals(8 * MB.toInt(), thumbnailCacheBytes(0))
    }

    private fun webpHeader(chunk: String, flags: Int): ByteArray {
        val bytes = ByteArray(30)
        "RIFF".forEachIndexed { i, c -> bytes[i] = c.code.toByte() }
        "WEBP".forEachIndexed { i, c -> bytes[8 + i] = c.code.toByte() }
        chunk.forEachIndexed { i, c -> bytes[12 + i] = c.code.toByte() }
        bytes[20] = flags.toByte()
        return bytes
    }

    private companion object {
        const val MB = 1024L * 1024
        const val NOW = 1_000_000_000L
        const val HOUR = 60 * 60 * 1000L
    }
}
