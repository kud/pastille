package app.pastille.ui

import java.time.format.FormatStyle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun relativeTime(
    then: Long,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    val elapsed = now - then
    if (elapsed < 60_000L) {
        return "Just now"
    }
    val minutes = elapsed / 60_000L
    if (minutes < 60) {
        return "$minutes min ago"
    }
    val thenDate = Instant.ofEpochMilli(then).atZone(zone).toLocalDate()
    val nowDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    if (thenDate.isEqual(nowDate)) {
        return "${elapsed / 3_600_000L} h ago"
    }
    if (thenDate.isEqual(nowDate.minusDays(1))) {
        return "Yesterday"
    }
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(locale)
        .format(Instant.ofEpochMilli(then).atZone(zone))
}
