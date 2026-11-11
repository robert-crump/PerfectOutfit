package com.example.perfectoutfit.feature.rate

import com.example.perfectoutfit.core.model.ComfortRating
import com.example.perfectoutfit.core.model.Place
import com.example.perfectoutfit.feature.forecast.Forecast
import com.example.perfectoutfit.feature.forecast.PlacedHours
import com.example.perfectoutfit.feature.home.HourlyWeather
import com.example.perfectoutfit.feature.home.LiveOutfitPayload
import com.example.perfectoutfit.feature.outfit.LogMode
import java.time.LocalDate
import java.time.LocalDateTime

/** The log wizard's steps, in wizard order. */
enum class LogStep { WHEN_WHERE, ITEMS, SUMMARY, RATING }

/** Everything [com.example.perfectoutfit.feature.outfit.OutfitLogging.log] needs from the wizard. */
data class LogRequest(
    val hour: HourlyWeather,
    /** Where it was worn: the place [hour]'s weather was fetched for. */
    val place: Place,
    val itemIds: Set<Long>,
    val rating: ComfortRating?,
    val notes: String,
    val durationHours: Int,
    val mode: LogMode
)

/** What a Next or Back press does. */
sealed interface WizardMove {
    /** Stay in the wizard, now at [state]. */
    data class To(val state: LogWizardState) : WizardMove

    /** Next on the last step: log [request]. */
    data class Save(val request: LogRequest) : WizardMove

    /** Back on the first step: leave the wizard (through the discard guard). */
    data object Leave : WizardMove
}

/**
 * The outfit-logging wizard (live and past logs): which step comes next, when Next is enabled,
 * the dates and hours that can be picked, and what gets logged. Pure: the ViewModel fetches
 * weather and feeds it back through [hoursLoaded], which drops results for a date or place
 * picked over in the meantime.
 *
 * Past logs: when and where → items → summary → rating → save. Live logs start at the hour Home
 * handed off and are only items → save ("Use outfit"). "Today" is [now]'s date, from the injected Clock when the
 * wizard opened, so labels, the max pickable date and the forecast never disagree at midnight.
 */
data class LogWizardState(
    val mode: LogMode,
    val step: LogStep,
    /** The current clock hour when the wizard opened. */
    val now: LocalDateTime,
    val date: LocalDate,
    /** Known hours of [date] at [weatherPlace]. */
    val hours: List<HourlyWeather>,
    /**
     * Where [hours] were fetched for: the picked place, or the current location at the time.
     * Null while no hours are known. This is the place that gets logged.
     */
    val weatherPlace: Place?,
    /** Index into [hours]; -1 = none picked. */
    val hourIndex: Int,
    /** Where it was worn, as picked; null = the current location. */
    val place: Place?,
    /** The current location's name when the wizard opened; shown until its weather is known. */
    val currentPlaceName: String,
    /** Weather for a newly picked date or place is on its way. */
    val loadingHours: Boolean = false,
    /** The clock hour to re-pick once a newly picked place's hours arrive. */
    private val keepClockHour: Int? = null,
    val itemIds: Set<Long> = emptySet(),
    val rating: ComfortRating? = null,
    val notes: String = "",
    val durationHours: Int = 1
) {
    val today: LocalDate get() = now.toLocalDate()

    /** Wizard rule: only hours up to today can be picked. */
    val maxPickableDate: LocalDate get() = today

    val selectedHour: HourlyWeather? get() = hours.getOrNull(hourIndex)

    /** The name shown for where it was worn: the picked place, else where the weather came from. */
    val placeName: String get() = place?.name ?: weatherPlace?.name ?: currentPlaceName

    /** The hour the time picker opens on. */
    val pickerDefaultHour: Int get() = selectedHour?.time?.hour ?: now.hour

    private val steps: List<LogStep>
        get() = when (mode) {
            LogMode.PAST -> LogStep.entries
            LogMode.LIVE -> listOf(LogStep.ITEMS)
        }

    /** Position in wizard order (for the slide direction). */
    val stepIndex: Int get() = step.ordinal

    val isLastStep: Boolean get() = step == steps.last()

    /**
     * Items are required where the outfit is confirmed: the summary of a past log, or the items
     * step that saves a live log. A past log's items step itself can always go on.
     */
    val canGoNext: Boolean
        get() = when (step) {
            LogStep.WHEN_WHERE -> !loadingHours && selectedHour != null
            LogStep.ITEMS -> mode == LogMode.PAST || itemIds.isNotEmpty()
            LogStep.SUMMARY -> itemIds.isNotEmpty()
            LogStep.RATING -> true
        }

    fun next(): WizardMove {
        if (!canGoNext) return WizardMove.To(this)
        if (isLastStep) return toLogRequest()?.let(WizardMove::Save) ?: WizardMove.To(this)
        return WizardMove.To(copy(step = steps[steps.indexOf(step) + 1]))
    }

    fun back(): WizardMove {
        val i = steps.indexOf(step)
        return if (i == 0) WizardMove.Leave else WizardMove.To(copy(step = steps[i - 1]))
    }

    /** Picks [date] (dates after today are refused) and waits for its hours; the hour is cleared. */
    fun pickDate(date: LocalDate): LogWizardState =
        if (date > maxPickableDate) this
        else copy(
            date = date, hours = emptyList(), weatherPlace = null, hourIndex = -1,
            loadingHours = true, keepClockHour = null
        )

    /** Picks where it was worn ([place] null = the current location), keeping the clock hour once loaded. */
    fun pickPlace(place: Place?): LogWizardState = copy(
        place = place,
        hours = emptyList(),
        weatherPlace = null,
        hourIndex = -1,
        loadingHours = true,
        keepClockHour = selectedHour?.time?.hour ?: keepClockHour
    )

    /**
     * [loaded] hours of [date] for the picked [place] (null = none could be fetched); ignored
     * unless those are still the ones picked.
     */
    fun hoursLoaded(date: LocalDate, place: Place?, loaded: PlacedHours?): LogWizardState {
        if (date != this.date || place != this.place) return this
        val hours = loaded?.hours.orEmpty()
        val kept = keepClockHour?.let { Forecast.hourNearest(hours, it) }
        return copy(
            hours = hours,
            weatherPlace = loaded?.place,
            hourIndex = kept?.let(hours::indexOf) ?: -1,
            loadingHours = false,
            keepClockHour = null
        )
    }

    /** Picks the known hour nearest [clockHour]; nothing changes if no hours are known. */
    fun pickClockHour(clockHour: Int): LogWizardState {
        val match = Forecast.hourNearest(hours, clockHour) ?: return this
        return copy(hourIndex = hours.indexOf(match))
    }

    fun toggleItem(itemId: Long): LogWizardState =
        copy(itemIds = if (itemId in itemIds) itemIds - itemId else itemIds + itemId)

    fun rate(rating: ComfortRating?): LogWizardState = copy(rating = rating)

    fun setNotes(notes: String): LogWizardState = copy(notes = notes)

    private fun toLogRequest(): LogRequest? {
        val hour = selectedHour ?: return null
        val at = weatherPlace ?: return null
        if (itemIds.isEmpty()) return null
        return LogRequest(hour, at, itemIds, rating, notes, durationHours, mode)
    }

    companion object {
        /** A past log: today at the current place ([loaded] = today's hours there), no hour picked yet. */
        fun past(now: LocalDateTime, loaded: PlacedHours?, currentPlaceName: String) = LogWizardState(
            mode = LogMode.PAST,
            step = LogStep.WHEN_WHERE,
            now = now,
            date = now.toLocalDate(),
            hours = loaded?.hours.orEmpty(),
            weatherPlace = loaded?.place,
            hourIndex = -1,
            place = null,
            currentPlaceName = currentPlaceName
        )

        /** Wizard rule: a live log starts on the date of the hour Home handed off, else today. */
        fun liveStartDate(now: LocalDateTime, handoff: LiveOutfitPayload?): LocalDate =
            handoff?.selectedHourTime?.toLocalDate() ?: now.toLocalDate()

        /**
         * A live log at the handed-off hour (else the first known), with its items and duration;
         * [loaded] are the current location's hours of [liveStartDate].
         */
        fun live(
            now: LocalDateTime,
            handoff: LiveOutfitPayload?,
            loaded: PlacedHours?,
            currentPlaceName: String
        ) = LogWizardState(
            mode = LogMode.LIVE,
            step = LogStep.ITEMS,
            now = now,
            date = liveStartDate(now, handoff),
            hours = loaded?.hours.orEmpty(),
            weatherPlace = loaded?.place,
            hourIndex = loaded?.hours.orEmpty().indexOfFirst { it.time == handoff?.selectedHourTime }.coerceAtLeast(0),
            place = null,
            currentPlaceName = currentPlaceName,
            itemIds = handoff?.prefillItemIds?.toSet().orEmpty(),
            durationHours = handoff?.workoutDurationHours ?: 1
        )
    }
}
