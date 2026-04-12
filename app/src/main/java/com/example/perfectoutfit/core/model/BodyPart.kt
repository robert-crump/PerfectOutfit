package com.example.perfectoutfit.core.model

enum class BodyPart(val displayName: String) {
    HEAD_THROAT("Head and Throat"),
    UPPER_BODY("Upper Body"),
    ARMS("Arms"),
    LEGS("Legs"),
    FEET("Feet"),
    HANDS("Hands")
}

/** Single source of truth for the order clothing categories are displayed throughout the app. */
val BODY_PART_DISPLAY_ORDER = listOf(
    BodyPart.LEGS,
    BodyPart.UPPER_BODY,
    BodyPart.ARMS,
    BodyPart.FEET,
    BodyPart.HEAD_THROAT,
    BodyPart.HANDS
)
