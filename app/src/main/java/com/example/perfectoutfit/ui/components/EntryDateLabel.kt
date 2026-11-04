package com.example.perfectoutfit.ui.components

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * An outfit entry's date as History writes it ("12 Oct"), with the year only outside [today]'s
 * year ("12 Oct 2025") and, with [withWeekday], the weekday first ("Sun, 12 Oct").
 */
fun entryDateLabel(date: LocalDate, today: LocalDate, withWeekday: Boolean = false): String {
    val day = if (withWeekday) "EEE, d MMM" else "d MMM"
    val pattern = if (date.year == today.year) day else "$day yyyy"
    return date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}
