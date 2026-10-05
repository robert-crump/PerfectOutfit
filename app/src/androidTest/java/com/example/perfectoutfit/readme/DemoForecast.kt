package com.example.perfectoutfit.readme

import com.example.perfectoutfit.core.network.ForecastResponse
import com.example.perfectoutfit.core.network.HourlyData
import com.example.perfectoutfit.core.network.OpenMeteoApi
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A synthetic autumn forecast for [DemoForecast.PLACE]: a cool, still morning warming to a breezy,
 * sunny early afternoon with a showery evening, so Home shows UV and wind warnings and the
 * hours of a run differ. Every day has the same shape; only the date changes.
 */
object DemoForecast {
    const val PLACE = "Aachen"
    const val LAT = 50.7753
    const val LON = 6.0839

    /** The pinned clock's hour, so Home's 24-hour window starts on a morning ride. */
    const val START_HOUR = 8

    fun hour(time: LocalDateTime): Hour {
        val h = time.hour
        // Coldest at 02:00, warmest at 14:00.
        val dayPhase = cos((h - 14) / 24.0 * 2 * PI)
        val temp = 8.5 + 6.5 * dayPhase
        val wind = when (h) {
            in 11..16 -> 21.0 + (h - 11) % 3
            in 9..10, in 17..19 -> 15.0
            else -> 8.0
        }
        val uv = if (h in 7..19) max(0.0, 5.4 * cos((h - 13) / 12.0 * PI)) else 0.0
        val rain = when (h) {
            in 17..19 -> 65
            in 15..16, 20 -> 35
            else -> 10
        }
        val cloud = when (h) {
            in 10..15 -> 20
            in 16..20 -> 80
            else -> 45
        }
        // Wind chill, roughly: what the hour feels like on the bike.
        val apparent = temp - wind * 0.12 - 1.5
        return Hour(time, temp.round1(), apparent.round1(), wind, 240, uv.round1(), cloud, rain)
    }

    data class Hour(
        val time: LocalDateTime,
        val temperature: Double,
        val apparent: Double,
        val windKmh: Double,
        val windDirection: Int,
        val uv: Double,
        val cloud: Int,
        val rain: Int
    )

    private fun Double.round1() = (this * 10).roundToInt() / 10.0

    /** [OpenMeteoApi] answering every request from [hour], over the dates the real API would return. */
    class Api(private val today: () -> LocalDate) : OpenMeteoApi {
        override suspend fun getForecast(
            latitude: Double, longitude: Double, hourly: String, timezone: String,
            pastDays: Int, forecastDays: Int
        ): ForecastResponse {
            val first = today().minusDays(pastDays.toLong())
            return response(first, today().plusDays(forecastDays - 1L))
        }

        override suspend fun getWeatherForDateRange(
            latitude: Double, longitude: Double, startDate: String, endDate: String,
            hourly: String, timezone: String
        ): ForecastResponse = response(LocalDate.parse(startDate), LocalDate.parse(endDate))

        private fun response(first: LocalDate, last: LocalDate): ForecastResponse {
            val hours = generateSequence(first.atStartOfDay()) { it.plusHours(1) }
                .takeWhile { !it.toLocalDate().isAfter(last) }
                .map(::hour)
                .toList()
            return ForecastResponse(
                latitude = LAT,
                longitude = LON,
                hourly = HourlyData(
                    time = hours.map { it.time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) },
                    temperature2m = hours.map { it.temperature },
                    apparentTemperature = hours.map { it.apparent },
                    windSpeed10m = hours.map { it.windKmh },
                    windDirection10m = hours.map { it.windDirection },
                    uvIndex = hours.map { it.uv },
                    cloudCover = hours.map { it.cloud },
                    precipitationProbability = hours.map { it.rain }
                )
            )
        }
    }
}
