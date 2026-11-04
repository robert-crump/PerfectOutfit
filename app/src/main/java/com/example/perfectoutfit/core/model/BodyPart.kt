package com.example.perfectoutfit.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class BodyPart(val displayName: String) {
    HEAD_THROAT("Head and Throat"),
    UPPER_BODY("Upper Body"),
    ARMS("Arms"),
    LEGS("Legs"),
    FEET("Feet"),
    HANDS("Hands")
}

/**
 * Single source of truth for the order clothing categories are displayed throughout the app:
 * head to toe, with hands after the arms they hang from.
 */
val BODY_PART_DISPLAY_ORDER = listOf(
    BodyPart.HEAD_THROAT,
    BodyPart.UPPER_BODY,
    BodyPart.ARMS,
    BodyPart.HANDS,
    BodyPart.LEGS,
    BodyPart.FEET
)
