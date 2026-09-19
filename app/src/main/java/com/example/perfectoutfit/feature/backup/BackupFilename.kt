package com.example.perfectoutfit.feature.backup

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

/** Generates and parses the timestamped backup filenames ("yyMMdd-HHmm PerfectOutfit.json"). */
object BackupFilename {
    private const val SUFFIX = " PerfectOutfit.json"
    private val formatter = DateTimeFormatter.ofPattern("uuMMdd-HHmm").withResolverStyle(ResolverStyle.STRICT)

    fun forTimestamp(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        formatter.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone)) + SUFFIX

    /** The backup's timestamp in millis, or null if [filename] isn't one of ours. */
    fun parseTimestamp(filename: String, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (!filename.endsWith(SUFFIX)) return null
        return try {
            LocalDateTime.parse(filename.removeSuffix(SUFFIX), formatter)
                .atZone(zone).toInstant().toEpochMilli()
        } catch (e: DateTimeParseException) {
            null
        }
    }
}
