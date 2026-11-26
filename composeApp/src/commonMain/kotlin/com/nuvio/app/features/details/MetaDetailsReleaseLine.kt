package com.nuvio.app.features.details

import com.nuvio.app.core.format.extractReleaseYearForDisplay
import com.nuvio.app.core.format.formatReleaseDateShortForDisplay

private val FullIsoDateRegex = Regex("""\d{4}-\d{2}-\d{2}""")

/** Full localized date (e.g. Sep 25, 2026) when [raw] carries a complete date, else null. */
internal fun fullReleaseDateOrNull(raw: String?): String? =
    raw?.trim()?.takeIf { FullIsoDateRegex.containsMatchIn(it) }?.let(::formatReleaseDateShortForDisplay)

private fun isTvSeriesType(type: String): Boolean =
    when (type.trim().lowercase()) {
        "series", "tv", "show", "tvshow" -> true
        else -> false
    }

private fun isEndedSeriesStatus(status: String?): Boolean {
    if (status.isNullOrBlank()) return false
    val s = status.trim().lowercase()
    if ("returning" in s || "in production" in s) return false
    return "ended" in s || "canceled" in s || "cancelled" in s
}

/**
 * Compact release line under the details hero: movies → full date (year when only a year is known); TV → "2025 -" or "2021 - 2028".
 */
fun formatMetaReleaseLineForDetails(meta: MetaDetails): String? {
    val raw = meta.releaseInfo?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (!isTvSeriesType(meta.type)) {
        return fullReleaseDateOrNull(raw) ?: extractReleaseYearForDisplay(raw)?.toString()
    }
    val startYear = extractReleaseYearForDisplay(raw) ?: return raw
    val endYear = meta.lastAirDate?.let { extractReleaseYearForDisplay(it) }
    return when {
        isEndedSeriesStatus(meta.status) && endYear != null ->
            if (endYear == startYear) startYear.toString()
            else "$startYear - $endYear"
        isEndedSeriesStatus(meta.status) -> startYear.toString()
        else -> fullReleaseDateOrNull(raw) ?: "$startYear -"
    }
}
