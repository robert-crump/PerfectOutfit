package com.example.perfectoutfit.ui.components

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class EntryDateLabelTest {

    private val today = LocalDate.of(2026, 11, 4)
    private lateinit var defaultLocale: Locale

    @Before
    fun pinLocale() {
        defaultLocale = Locale.getDefault()
        Locale.setDefault(Locale.UK)
    }

    @After
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    @Test
    fun `this year's entry has no year`() {
        assertEquals("12 Oct", entryDateLabel(LocalDate.of(2026, 10, 12), today))
    }

    @Test
    fun `an older year's entry carries its year`() {
        assertEquals("12 Oct 2025", entryDateLabel(LocalDate.of(2025, 10, 12), today))
    }

    @Test
    fun `with weekday the day name comes first`() {
        assertEquals("Mon, 12 Oct", entryDateLabel(LocalDate.of(2026, 10, 12), today, withWeekday = true))
        assertEquals("Sun, 12 Oct 2025", entryDateLabel(LocalDate.of(2025, 10, 12), today, withWeekday = true))
    }
}
