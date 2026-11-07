package com.example.perfectoutfit.ui.components

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * An outfit entry's date as History writes it ("12 Oct"), with the year only outside [today]'s
 * year ("12 Oct 2025") and, with [withWeekday], the weekday first ("Sun, 12 Oct"). With
 * [relative], [today] and the day before read "Today" and "Yesterday".
 */
fun entryDateLabel(
    date: LocalDate,
    today: LocalDate,
    withWeekday: Boolean = false,
    relative: Boolean = false
): String {
    if (relative) {
        if (date == today) return "Today"
        if (date == today.minusDays(1)) return "Yesterday"
    }
    val day = if (withWeekday) "EEE, d MMM" else "d MMM"
    val pattern = if (date.year == today.year) day else "$day yyyy"
    return date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}
