package com.example.perfectoutfit.core.location

/**
 * Where the user is now, as Home's forecast needs it: coordinates plus a display name.
 * Two adapters exist: [AndroidLocationSource] (fused location + reverse geocode) and a fixed
 * location the androidTest README screenshots install in its place.
 */
interface LocationSource {
    suspend fun current(): LocationResult
}

sealed interface LocationResult {
    /** [name] is a place name, or empty when reverse geocoding found none. */
    data class Found(val lat: Double, val lon: Double, val name: String) : LocationResult
    data object PermissionDenied : LocationResult
    data class Unavailable(val message: String) : LocationResult
}
