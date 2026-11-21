package com.nuvio.app.features.calendar

/** Small Gregorian date helpers so the calendar does not need a date-time library. */
internal data class CalendarMonth(val year: Int, val month: Int) {
    val key: String get() = "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}"

    val dayCount: Int get() = daysInMonth(year, month)

    val startIso: String get() = isoDate(year, month, 1)
    val endIso: String get() = isoDate(year, month, dayCount)

    /** 0 = Monday ... 6 = Sunday, for the first day of the month. */
    val leadingBlankDays: Int get() = dayOfWeekMondayFirst(year, month, 1)

    fun plusMonths(delta: Int): CalendarMonth {
        val total = year * 12 + (month - 1) + delta
        return CalendarMonth(year = total.floorDiv(12), month = total.mod(12) + 1)
    }

    fun dayIso(day: Int): String = isoDate(year, month, day)

    fun contains(iso: String): Boolean = iso in startIso..endIso

    companion object {
        fun fromIso(iso: String): CalendarMonth? {
            val parts = iso.take(10).split("-")
            val y = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val m = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..12 } ?: return null
            return CalendarMonth(y, m)
        }
    }
}

internal fun isLeapYear(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

internal fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    else -> if (isLeapYear(year)) 29 else 28
}

internal fun isoDate(year: Int, month: Int, day: Int): String =
    "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

/** Sakamoto's algorithm, re-based so Monday = 0 and Sunday = 6. */
internal fun dayOfWeekMondayFirst(year: Int, month: Int, day: Int): Int {
    val offsets = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
    val y = if (month < 3) year - 1 else year
    val sundayFirst = (y + y / 4 - y / 100 + y / 400 + offsets[month - 1] + day) % 7
    return (sundayFirst + 6) % 7
}

internal fun dayOfWeekMondayFirst(iso: String): Int? {
    val parts = iso.take(10).split("-")
    val y = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val m = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..12 } ?: return null
    val d = parts.getOrNull(2)?.toIntOrNull()?.takeIf { it in 1..31 } ?: return null
    return dayOfWeekMondayFirst(y, m, d)
}

/** Normalizes a TMDB date ("2026-11-21" or a timestamp) to `yyyy-MM-dd`, or null when invalid. */
internal fun normalizeIsoDate(raw: String?): String? {
    val value = raw?.trim()?.take(10) ?: return null
    if (value.length != 10 || value[4] != '-' || value[7] != '-') return null
    return value.takeIf { CalendarMonth.fromIso(it) != null && value.substring(8).toIntOrNull() != null }
}
