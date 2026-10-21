package com.example.perfectoutfit.navigation

import com.example.perfectoutfit.core.model.ActivityType

/** Where an intent from outside the app (a notification, the widget) asks to go. */
sealed interface LaunchTarget {
    /** The rating reminder's deep link: rate this entry, rating bar highlighted. */
    data class RateEntry(val outfitEntryId: Long) : LaunchTarget

    /** The backup failure notification. */
    data object Settings : LaunchTarget

    /** The widget: [activityType]'s recommendation, or Home's first step when null. */
    data class Home(val activityType: ActivityType?) : LaunchTarget
}

/**
 * One arrival of a [LaunchTarget]. Deliberately not a data class: tapping the same widget
 * icon twice is two requests, and each must navigate.
 */
class LaunchRequest(val target: LaunchTarget)
