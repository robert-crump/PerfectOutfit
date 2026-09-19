package com.example.perfectoutfit.testutil

import com.example.perfectoutfit.core.network.ForecastResponse
import com.example.perfectoutfit.core.network.HourlyData
import com.example.perfectoutfit.core.network.OpenMeteoApi
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** In-memory [OpenMeteoApi]: serves the hours it was given, filtered by date like the real API. */
class FakeOpenMeteoApi(
    private val forecastHours: List<LocalDateTime> = emptyList(),
    private val archiveHours: List<LocalDateTime> = emptyList()
) : OpenMeteoApi {
    var forecastCalls = 0
    val dateRangeCalls = mutableListOf<Pair<String, String>>()
    var failNext = false

    override suspend fun getForecast(
        latitude: Double, longitude: Double, hourly: String, timezone: String,
        pastDays: Int, forecastDays: Int
    ): ForecastResponse {
        forecastCalls++
        checkFail()
        return response(forecastHours)
    }

    override suspend fun getWeatherForDateRange(
        latitude: Double, longitude: Double, startDate: String, endDate: String,
        hourly: String, timezone: String
    ): ForecastResponse {
        dateRangeCalls += startDate to endDate
        checkFail()
        return response((forecastHours + archiveHours).filter { it.toLocalDate().toString() in startDate..endDate })
    }

    private fun checkFail() {
        if (failNext) {
            failNext = false
            throw IOException("offline")
        }
    }

    private fun response(times: List<LocalDateTime>) = ForecastResponse(
        latitude = 0.0,
        longitude = 0.0,
        hourly = HourlyData(
            time = times.map { it.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) },
            temperature2m = times.map { it.dayOfMonth.toDouble() },
            apparentTemperature = times.map { 0.0 },
            windSpeed10m = times.map { 0.0 },
            windDirection10m = times.map { 0 },
            uvIndex = times.map { 0.0 },
            cloudCover = times.map { 0 },
            precipitationProbability = times.map { 0 }
        )
    )
}
