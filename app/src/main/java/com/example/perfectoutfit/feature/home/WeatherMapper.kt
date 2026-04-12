package com.example.perfectoutfit.feature.home

import com.example.perfectoutfit.core.network.ForecastResponse
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

data class HourlyWeather(
    val time: LocalDateTime,
    val temperatureCelsius: Double,
    val apparentTemperatureCelsius: Double,
    val windSpeedKmh: Double,
    val windDirectionDegrees: Int,
    val uvIndex: Int,
    val cloudCoverPercent: Int,
    val precipitationProbabilityPercent: Int
) {
    val windDirectionLabel: String
        get() {
            val directions = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
            val index = ((windDirectionDegrees + 22.5) / 45.0).toInt() % 8
            return directions[index]
        }
}

object WeatherMapper {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    /** Returns every hour available in the API response (past 2 days + today + tomorrow). */
    fun extractAllHours(response: ForecastResponse): List<HourlyWeather> {
        val times = response.hourly.time.map { LocalDateTime.parse(it, formatter) }
        return times.indices.map { i ->
            HourlyWeather(
                time = times[i],
                temperatureCelsius = response.hourly.temperature2m[i],
                apparentTemperatureCelsius = response.hourly.apparentTemperature[i],
                windSpeedKmh = response.hourly.windSpeed10m[i],
                windDirectionDegrees = response.hourly.windDirection10m[i],
                uvIndex = response.hourly.uvIndex[i].roundToInt(),
                cloudCoverPercent = response.hourly.cloudCover[i],
                precipitationProbabilityPercent = response.hourly.precipitationProbability[i]
            )
        }
    }

    /**
     * From all hours, returns a 24-hour window starting at the current hour.
     * These are shown in the home screen hour selector.
     */
    fun extractDisplayedHours(allHours: List<HourlyWeather>): List<HourlyWeather> {
        val currentHour = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0)
        val startIndex = allHours.indexOfFirst { !it.time.isBefore(currentHour) }
        if (startIndex < 0) return emptyList()
        return allHours.subList(startIndex, minOf(startIndex + 24, allHours.size))
    }

    fun hasRainWarning(hours: List<HourlyWeather>): Boolean =
        hours.any { it.precipitationProbabilityPercent > 50 }

    fun hasUvWarning(hours: List<HourlyWeather>): Boolean =
        hours.any { it.uvIndex >= 4 }

    fun hasWindWarning(hours: List<HourlyWeather>): Boolean =
        hours.any { it.windSpeedKmh >= 20.0 }
}
