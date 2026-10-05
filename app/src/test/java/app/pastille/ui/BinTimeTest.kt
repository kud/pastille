package app.pastille.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BinTimeTest {

    private val day = 24L * 60 * 60 * 1000

    @Test
    fun `a snippet binned just now has thirty days left`() {
        assertEquals(30, binDaysLeft(deletedAt = 1_000, now = 1_000))
    }

    @Test
    fun `partial days round up`() {
        assertEquals(28, binDaysLeft(deletedAt = 0, now = 3 * day - 1))
        assertEquals(27, binDaysLeft(deletedAt = 0, now = 3 * day))
        assertEquals(1, binDaysLeft(deletedAt = 0, now = 30 * day - 1))
    }

    @Test
    fun `past the retention it never goes negative`() {
        assertEquals(0, binDaysLeft(deletedAt = 0, now = 31 * day))
    }

    @Test
    fun `labels and the warning threshold`() {
        assertEquals("1 day left", binDaysLeftLabel(1))
        assertEquals("27 days left", binDaysLeftLabel(27))
        assertTrue(binExpiresSoon(3))
        assertFalse(binExpiresSoon(4))
    }
}
