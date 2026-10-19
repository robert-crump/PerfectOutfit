package com.example.perfectoutfit.core.model

import androidx.annotation.DrawableRes
import com.example.perfectoutfit.R
import kotlinx.serialization.Serializable

/** Anything the user dresses for, sport or not. Carries its own label and icon. */
@Serializable
enum class ActivityType(val displayName: String, @get:DrawableRes val iconRes: Int) {
    CYCLING("Cycling", R.drawable.ic_bike),
    RUNNING("Running", R.drawable.ic_sprint)
}
