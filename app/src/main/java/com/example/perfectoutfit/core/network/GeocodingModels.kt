package com.example.perfectoutfit.core.network

import kotlinx.serialization.Serializable

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
) {
    val displayName: String
        get() = buildString {
            append(name)
            if (admin1.isNotBlank()) append(", $admin1")
            if (country.isNotBlank()) append(", $country")
        }
}
