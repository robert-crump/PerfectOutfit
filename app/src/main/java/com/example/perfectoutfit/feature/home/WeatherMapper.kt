package com.example.perfectoutfit.feature.home

import com.example.perfectoutfit.core.model.WeatherReading
import com.example.perfectoutfit.core.model.WeatherSnapshot
import com.example.perfectoutfit.core.network.ForecastResponse
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

data class HourlyWeather(
    val time: LocalDateTime,
    override val temperatureCelsius: Double,
    override val apparentTemperatureCelsius: Double,
    override val windSpeedKmh: Double,
    override val windDirectionDegrees: Int,
    override val uvIndex: Int,
    override val cloudCoverPercent: Int,
    override val precipitationProbabilityPercent: Int
) : WeatherReading {
    val windDirectionLabel: String
        get() {
            val directions = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
            val index = ((windDirectionDegrees + 22.5) / 45.0).toInt() % 8
            return directions[index]
        }
}

fun HourlyWeather.toWeatherSnapshot(
    lat: Double,
    lon: Double,
    locationName: String
): WeatherSnapshot = WeatherSnapshot(
    timestamp = time.atZone(ZoneId.systemDefault()).toEpochSecond() * 1000,
    latitude = lat,
    longitude = lon,
    locationName = locationName,
    temperatureCelsius = temperatureCelsius,
    apparentTemperatureCelsius = apparentTemperatureCelsius,
    windSpeedKmh = windSpeedKmh,
    windDirectionDegrees = windDirectionDegrees,
    uvIndex = uvIndex,
    cloudCoverPercent = cloudCoverPercent,
    precipitationProbabilityPercent = precipitationProbabilityPercent
)

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
}
