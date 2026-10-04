package app.pastille.ui

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {

    private val zone = ZoneId.of("UTC")
    private val locale = Locale.UK

    @Test
    fun justNow() {
        val now = Instant.parse("2026-06-15T12:00:00Z").toEpochMilli()
        assertEquals("Just now", relativeTime(now - 30_000L, now, zone, locale))
    }

    @Test
    fun minutesAgo() {
        val now = Instant.parse("2026-06-15T12:00:00Z").toEpochMilli()
        assertEquals("5 min ago", relativeTime(now - 5 * 60_000L, now, zone, locale))
    }

    @Test
    fun sameDayHoursAgo() {
        val now = Instant.parse("2026-06-15T12:00:00Z").toEpochMilli()
        val then = Instant.parse("2026-06-15T09:30:00Z").toEpochMilli()
        assertEquals("2 h ago", relativeTime(then, now, zone, locale))
    }

    @Test
    fun yesterday() {
        val now = Instant.parse("2026-06-15T12:00:00Z").toEpochMilli()
        val then = Instant.parse("2026-06-14T20:00:00Z").toEpochMilli()
        assertEquals("Yesterday", relativeTime(then, now, zone, locale))
    }

    @Test
    fun oldDateUsesMediumFormat() {
        val now = Instant.parse("2026-06-15T12:00:00Z").toEpochMilli()
        val then = Instant.parse("2026-01-05T10:00:00Z").toEpochMilli()
        assertEquals("5 Jan 2026", relativeTime(then, now, zone, locale))
    }
}
