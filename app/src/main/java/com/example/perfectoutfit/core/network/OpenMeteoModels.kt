package com.example.perfectoutfit.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ForecastResponse(
    val latitude: Double,
    val longitude: Double,
    val timezone: String = "",
    val hourly: HourlyData
)

@Serializable
data class HourlyData(
    val time: List<String>,
    @SerialName("temperature_2m") val temperature2m: List<Double>,
    @SerialName("apparent_temperature") val apparentTemperature: List<Double>,
    @SerialName("wind_speed_10m") val windSpeed10m: List<Double>,
    @SerialName("wind_direction_10m") val windDirection10m: List<Int>,
    @SerialName("uv_index") val uvIndex: List<Double>,
    @SerialName("cloud_cover") val cloudCover: List<Int>,
    @SerialName("precipitation_probability") val precipitationProbability: List<Int>
)
