package com.example.perfectoutfit.feature.rate

import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.core.model.ComfortRating
import com.example.perfectoutfit.core.model.Place
import com.example.perfectoutfit.feature.home.HourlyWeather
import com.example.perfectoutfit.feature.home.LiveOutfitPayload
import com.example.perfectoutfit.feature.outfit.LogMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class LogWizardStateTest {

    private val today = LocalDate.of(2026, 3, 10)
    private val now = today.atTime(18, 0)
    private val yesterday = today.minusDays(1)
    private val cologne = Place(50.94, 6.96, "Cologne")

    private fun hour(time: LocalDateTime) = HourlyWeather(time, 5.0, 3.0, 10.0, 180, 1, 50, 10)
    private fun hoursOf(date: LocalDate) = (0..23).map { hour(date.atTime(it, 0)) }

    private fun past() = LogWizardState.past(now, hoursOf(today), "Aachen")

    private fun live(handoff: LiveOutfitPayload? = payload()) =
        LogWizardState.live(now, handoff, hoursOf(handoff?.selectedHourTime?.toLocalDate() ?: today), "Aachen")

    private fun payload(at: LocalDateTime? = today.atTime(9, 0)) = LiveOutfitPayload(
        selectedHourTime = at,
        activityType = ActivityType.RUNNING,
        workoutDurationHours = 2,
        prefillItemIds = listOf(1L, 2L)
    )

    private fun LogWizardState.moved(move: WizardMove): LogWizardState = (move as WizardMove.To).state
    private fun LogWizardState.forward() = moved(next())
    private fun LogWizardState.backward() = moved(back())

    // ─── Start ───────────────────────────────────────────────────────────────

    @Test
    fun `a past log starts on when-and-where today at the current place with no hour picked`() {
        val s = past()
        assertEquals(LogStep.WHEN_WHERE, s.step)
        assertEquals(today, s.date)
        assertNull(s.place)
        assertEquals("Aachen", s.placeName)
        assertNull(s.selectedHour)
        assertTrue(s.itemIds.isEmpty())
    }

    @Test
    fun `a live log starts on items at the handed-off hour with its items and duration`() {
        val s = live(payload(at = yesterday.atTime(20, 0)))
        assertEquals(LogStep.ITEMS, s.step)
        assertEquals(yesterday, s.date)
        assertEquals(yesterday.atTime(20, 0), s.selectedHour?.time)
        assertEquals(setOf(1L, 2L), s.itemIds)
        assertEquals(2, s.durationHours)
    }

    @Test
    fun `a live log without a handoff starts today at the first known hour`() {
        val s = live(handoff = null)
        assertEquals(today, s.date)
        assertEquals(today.atTime(0, 0), s.selectedHour?.time)
        assertTrue(s.itemIds.isEmpty())
        assertEquals(1, s.durationHours)
    }

    @Test
    fun `a live log starts on the handed-off hour's date, else today`() {
        assertEquals(yesterday, LogWizardState.liveStartDate(now, payload(at = yesterday.atTime(23, 0))))
        assertEquals(today, LogWizardState.liveStartDate(now, payload(at = null)))
        assertEquals(today, LogWizardState.liveStartDate(now, null))
    }

    // ─── Clock ───────────────────────────────────────────────────────────────

    @Test
    fun `the max pickable date is the Clock's today`() {
        val justBeforeMidnight = LogWizardState.past(today.atTime(23, 0), hoursOf(today), "Aachen")
        assertEquals(today, justBeforeMidnight.maxPickableDate)
        val justAfter = LogWizardState.past(today.plusDays(1).atTime(0, 0), emptyList(), "Aachen")
        assertEquals(today.plusDays(1), justAfter.maxPickableDate)
    }

    @Test
    fun `a date after today cannot be picked`() {
        val s = past()
        assertSame(s, s.pickDate(today.plusDays(1)))
    }

    @Test
    fun `the time picker opens on the picked hour, else the Clock's hour`() {
        assertEquals(18, past().pickerDefaultHour)
        assertEquals(9, past().pickClockHour(9).pickerDefaultHour)
    }

    // ─── Step order ──────────────────────────────────────────────────────────

    @Test
    fun `a past log goes when-and-where, items, summary, rating, then saves`() {
        var s = past().pickClockHour(9).toggleItem(1)
        assertEquals(LogStep.WHEN_WHERE, s.step)
        s = s.forward(); assertEquals(LogStep.ITEMS, s.step)
        s = s.forward(); assertEquals(LogStep.SUMMARY, s.step)
        s = s.forward(); assertEquals(LogStep.RATING, s.step)
        assertTrue(s.isLastStep)
        assertTrue(s.next() is WizardMove.Save)
    }

    @Test
    fun `a live log goes items, summary, then saves`() {
        var s = live()
        assertEquals(LogStep.ITEMS, s.step)
        s = s.forward(); assertEquals(LogStep.SUMMARY, s.step)
        assertTrue(s.isLastStep)
        assertTrue(s.next() is WizardMove.Save)
    }

    @Test
    fun `back retraces a past log's steps and leaves from the first`() {
        val rating = past().pickClockHour(9).toggleItem(1).forward().forward().forward()
        val summary = rating.backward()
        assertEquals(LogStep.SUMMARY, summary.step)
        val items = summary.backward()
        assertEquals(LogStep.ITEMS, items.step)
        val whenWhere = items.backward()
        assertEquals(LogStep.WHEN_WHERE, whenWhere.step)
        assertEquals(WizardMove.Leave, whenWhere.back())
    }

    @Test
    fun `back from a live log's items leaves`() {
        assertEquals(WizardMove.Leave, live().back())
        assertEquals(LogStep.ITEMS, live().forward().backward().step)
    }

    @Test
    fun `steps are indexed in wizard order for the slide direction`() {
        val s = past().pickClockHour(9).toggleItem(1)
        assertTrue(s.stepIndex < s.forward().stepIndex)
        assertTrue(s.forward().stepIndex < s.forward().forward().stepIndex)
    }

    // ─── Next enablement ─────────────────────────────────────────────────────

    @Test
    fun `when-and-where needs a picked hour`() {
        assertFalse(past().canGoNext)
        assertTrue(past().pickClockHour(9).canGoNext)
    }

    @Test
    fun `when-and-where waits for weather to load`() {
        val loading = past().pickClockHour(9).pickPlace(cologne)
        assertFalse(loading.canGoNext)
    }

    @Test
    fun `items can always go next, even with none picked`() {
        val items = past().pickClockHour(9).forward()
        assertTrue(items.itemIds.isEmpty())
        assertTrue(items.canGoNext)
    }

    @Test
    fun `the summary needs at least one item`() {
        val summary = past().pickClockHour(9).forward().forward()
        assertFalse(summary.canGoNext)
        assertTrue(summary.toggleItem(1).canGoNext)
    }

    @Test
    fun `next when not allowed stays put`() {
        val s = past()
        assertSame(s, s.forward())
    }

    // ─── Save ────────────────────────────────────────────────────────────────

    @Test
    fun `a past log saves what was entered, as a past log`() {
        val rating = past().pickPlace(cologne)
            .hoursLoaded(today, cologne, hoursOf(today))
            .pickClockHour(9)
            .toggleItem(3)
            .forward().forward().forward()
            .rate(ComfortRating.TOO_COLD)
            .setNotes("Windy")

        val request = (rating.next() as WizardMove.Save).request

        assertEquals(today.atTime(9, 0), request.hour.time)
        assertEquals(cologne, request.place)
        assertEquals(setOf(3L), request.itemIds)
        assertEquals(ComfortRating.TOO_COLD, request.rating)
        assertEquals("Windy", request.notes)
        assertEquals(LogMode.PAST, request.mode)
    }

    @Test
    fun `a live log saves unrated, as a live log, with its handed-off duration`() {
        val request = (live().forward().next() as WizardMove.Save).request
        assertEquals(today.atTime(9, 0), request.hour.time)
        assertNull(request.place)
        assertEquals("Aachen", request.placeName)
        assertNull(request.rating)
        assertEquals(2, request.durationHours)
        assertEquals(LogMode.LIVE, request.mode)
    }

    // ─── Date and place race guards ──────────────────────────────────────────

    @Test
    fun `picking a date clears the hour and loads that date`() {
        val s = past().pickClockHour(9).pickDate(yesterday)
        assertEquals(yesterday, s.date)
        assertNull(s.selectedHour)
        assertTrue(s.loadingHours)
        val loaded = s.hoursLoaded(yesterday, null, hoursOf(yesterday))
        assertFalse(loaded.loadingHours)
        assertNull(loaded.selectedHour)
        assertEquals(24, loaded.hours.size)
    }

    @Test
    fun `picking a place keeps the picked clock hour once its weather loads`() {
        val s = past().pickClockHour(9).pickPlace(cologne)
        assertEquals(cologne, s.place)
        assertEquals("Cologne", s.placeName)
        assertNull(s.selectedHour)
        val loaded = s.hoursLoaded(today, cologne, hoursOf(today))
        assertEquals(today.atTime(9, 0), loaded.selectedHour?.time)
    }

    @Test
    fun `picking a place before a time leaves the time unpicked`() {
        val loaded = past().pickPlace(cologne).hoursLoaded(today, cologne, hoursOf(today))
        assertNull(loaded.selectedHour)
    }

    @Test
    fun `going back to the current place takes the current place's name`() {
        val s = past().pickPlace(cologne).pickPlace(null)
        assertNull(s.place)
        assertEquals("Aachen", s.placeName)
    }

    @Test
    fun `hours for a date no longer picked are ignored`() {
        val s = past().pickDate(yesterday).pickDate(today.minusDays(2))
        assertSame(s, s.hoursLoaded(yesterday, null, hoursOf(yesterday)))
    }

    @Test
    fun `hours for a place no longer picked are ignored`() {
        val s = past().pickPlace(cologne).pickPlace(null)
        assertSame(s, s.hoursLoaded(today, cologne, hoursOf(today)))
    }

    @Test
    fun `a date picked while a place loads drops the place's late hours and the kept hour`() {
        val s = past().pickClockHour(9).pickPlace(cologne).pickDate(yesterday)
        assertSame(s, s.hoursLoaded(today, cologne, hoursOf(today)))
        assertNull(s.hoursLoaded(yesterday, cologne, hoursOf(yesterday)).selectedHour)
    }

    // ─── Edits ───────────────────────────────────────────────────────────────

    @Test
    fun `toggling an item adds then removes it`() {
        val s = past().toggleItem(4)
        assertEquals(setOf(4L), s.itemIds)
        assertTrue(s.toggleItem(4).itemIds.isEmpty())
    }

    @Test
    fun `picking a clock hour with no hours known changes nothing`() {
        val s = LogWizardState.past(now, emptyList(), "Aachen")
        assertSame(s, s.pickClockHour(9))
    }
}
