package app.pastille.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageSourcesTest {

    private val rows = listOf(
        SourceRow(bucketId = 1, name = "Camera", dateAdded = 300, path = "DCIM/Camera/"),
        SourceRow(bucketId = 2, name = "Screenshots", dateAdded = 200, path = "Pictures/Screenshots/"),
        SourceRow(bucketId = 1, name = "Camera", dateAdded = 100, path = "DCIM/Camera/"),
        SourceRow(bucketId = 3, name = null, dateAdded = 400, path = "Download/"),
    )

    @Test
    fun `groups rows by bucket, most recent first`() {
        val sources = groupSources(rows)
        assertEquals(listOf(3L, 1L, 2L), sources.map { it.bucketId })
        assertEquals(2, sources.first { it.bucketId == 1L }.count)
        assertEquals("Unnamed", sources.first().name)
        assertEquals(true, sources.first { it.bucketId == 2L }.isScreenshots)
    }

    @Test
    fun `resolves the saved source, then Screenshots, then the newest`() {
        val sources = groupSources(rows)
        assertEquals(1L, resolveSource(sources, savedBucketId = 1)?.bucketId)
        assertEquals(2L, resolveSource(sources, savedBucketId = 99)?.bucketId)
        assertEquals(3L, resolveSource(sources.filterNot { it.isScreenshots }, savedBucketId = null)?.bucketId)
        assertNull(resolveSource(emptyList(), savedBucketId = 1))
    }

    @Test
    fun `chips lead with Screenshots then Camera`() {
        val ordered = orderForChips(groupSources(rows), maxOthers = 0)
        assertEquals(listOf(2L, 1L), ordered.map { it.bucketId })
    }

    private val folders = groupSources(
        rows + listOf(
            SourceRow(bucketId = 4, name = "WhatsApp Images", dateAdded = 500, path = "WhatsApp/Media/"),
            SourceRow(bucketId = 5, name = "Download", dateAdded = 50, path = "Download/"),
        ),
    )

    @Test
    fun `before any choice, only Screenshots, Camera and Download show`() {
        assertEquals(listOf(2L, 1L, 5L), visibleSources(folders, enabledIds = null).map { it.bucketId })
    }

    @Test
    fun `after a choice, only the ticked folders show, so new ones stay hidden`() {
        assertEquals(listOf(4L), visibleSources(folders, enabledIds = setOf(4L, 99L)).map { it.bucketId })
        assertEquals(emptyList<Long>(), visibleSources(folders, enabledIds = emptySet()).map { it.bucketId })
    }
}
