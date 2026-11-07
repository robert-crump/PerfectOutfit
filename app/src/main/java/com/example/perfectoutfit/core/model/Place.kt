package com.example.perfectoutfit.core.model

import kotlinx.serialization.Serializable

/**
 * A place: coordinates plus a name. The one place type used by Forecast, Outfit logging, the
 * location source and the widget forecast. A blank [name] displays as [DEFAULT_NAME].
 */
@Serializable
data class Place(val lat: Double, val lon: Double, val name: String) {
    val displayName: String get() = name.ifBlank { DEFAULT_NAME }

    companion object {
        /** The only spelling of the default place name. */
        const val DEFAULT_NAME = "Current location"
    }
}
