package com.example.perfectoutfit.core.network

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/** Open-Meteo city search, for logging a past outfit at a place other than the current one. */
interface GeocodingApi {
    @GET("v1/search")
    suspend fun search(
        @Query("name") query: String,
        @Query("count") count: Int = 10
    ): GeocodingResponse
}

@Serializable
data class GeocodingResponse(
    val results: List<GeocodingResult> = emptyList()
)

@Serializable
data class GeocodingResult(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String = "",
    val admin1: String = ""
)
