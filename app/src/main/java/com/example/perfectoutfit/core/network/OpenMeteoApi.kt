package com.example.perfectoutfit.core.network

import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String = "temperature_2m,apparent_temperature,wind_speed_10m,wind_direction_10m,uv_index,cloud_cover,precipitation_probability",
        @Query("timezone") timezone: String = "auto",
        @Query("past_days") pastDays: Int = 2,
        @Query("forecast_days") forecastDays: Int = 2
    ): ForecastResponse

    @GET("v1/forecast")
    suspend fun getWeatherForDateRange(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("hourly") hourly: String = "temperature_2m,apparent_temperature,wind_speed_10m,wind_direction_10m,uv_index,cloud_cover,precipitation_probability",
        @Query("timezone") timezone: String = "auto"
    ): ForecastResponse
}
