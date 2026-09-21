package com.example.perfectoutfit.feature.backup

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** How backup timestamps read in the UI, in the device's locale and time zone. */
object BackupTimeFormat {
    private val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

    fun format(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        formatter.withZone(zone).format(Instant.ofEpochMilli(epochMillis))
}
