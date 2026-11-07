package com.example.perfectoutfit.core.time

import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** The current clock hour (minutes and below truncated), in the Clock's zone. */
fun Clock.currentHour(): LocalDateTime = LocalDateTime.now(this).truncatedTo(ChronoUnit.HOURS)

/** Epoch millis of a local [hour] (as forecast hours are expressed), in the Clock's zone. */
fun Clock.epochMillisOf(hour: LocalDateTime): Long = hour.atZone(zone).toInstant().toEpochMilli()
