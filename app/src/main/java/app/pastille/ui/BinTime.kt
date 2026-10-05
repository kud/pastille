package app.pastille.ui

import app.pastille.data.SnippetRepository
import kotlin.math.ceil

private const val DAY_MS = 24L * 60 * 60 * 1000

/** Whole days before a binned snippet is purged, rounded up so "0 days left" never shows early. */
fun binDaysLeft(deletedAt: Long, now: Long): Int {
    val remaining = deletedAt + SnippetRepository.BIN_RETENTION_MS - now
    return ceil(remaining.toDouble() / DAY_MS).toInt().coerceAtLeast(0)
}

fun binDaysLeftLabel(days: Int): String = if (days == 1) "1 day left" else "$days days left"

/** At three days or fewer, the countdown turns to the error colour. */
fun binExpiresSoon(days: Int): Boolean = days <= 3
