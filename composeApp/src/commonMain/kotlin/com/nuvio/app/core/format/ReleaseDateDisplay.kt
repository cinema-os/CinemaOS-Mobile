package com.nuvio.app.core.format

import androidx.compose.ui.text.intl.Locale
import com.nuvio.app.core.time.parseEpisodeReleaseLocalDate

fun formatReleaseDateForDisplay(raw: String): String = formatReleaseDate(raw, includeYear = true)

fun formatReleaseDateWithoutYear(raw: String): String = formatReleaseDate(raw, includeYear = false)

internal fun formatReleaseDate(
    raw: String,
    includeYear: Boolean,
    localeTag: String = Locale.current.toLanguageTag(),
): String {
    val date = parseEpisodeReleaseLocalDate(raw) ?: return raw
    return formatCalendarDate(date, localeTag, includeYear)
}

internal expect fun formatCalendarDate(isoDate: String, localeTag: String, includeYear: Boolean): String

/** Locale-aware date with an abbreviated month and the year, e.g. "Sep 25, 2026". */
internal expect fun formatCalendarDateShort(isoDate: String, localeTag: String): String

/** Short-month full date for meta lines; returns [raw] unchanged when it is not a full date. */
fun formatReleaseDateShortForDisplay(raw: String): String {
    val date = parseEpisodeReleaseLocalDate(raw) ?: return raw
    return formatCalendarDateShort(date, Locale.current.toLanguageTag())
}

/**
 * Parses a release/air string (ISO date, year-only, or timestamp prefix) for compact UI (e.g. year chips).
 */
fun extractReleaseYearForDisplay(raw: String): Int? {
    val t = raw.trim()
    if (t.isEmpty()) return null
    if (t.length == 4 && t.all { it.isDigit() }) {
        return t.toIntOrNull()?.takeIf { it in 1000..9999 }
    }
    val datePart = parseEpisodeReleaseLocalDate(t) ?: return null
    val yearStr = datePart.split('-').firstOrNull() ?: return null
    return yearStr.toIntOrNull()?.takeIf { it in 1000..9999 }
}
