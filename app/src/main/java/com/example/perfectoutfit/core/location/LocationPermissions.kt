package com.example.perfectoutfit.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The location grants the app holds now. Two adapters exist: [AndroidLocationPermissions] and
 * fakes in `app/src/test`.
 */
interface LocationPermissions {
    /** Coarse location, which Home needs while the app is open. */
    fun hasForeground(): Boolean

    /** Location while no screen is showing, which the widget's refresh needs to follow the device. */
    fun hasBackground(): Boolean
}

/** Reads the grants from the OS. */
class AndroidLocationPermissions @Inject constructor(
    @param:ApplicationContext private val context: Context
) : LocationPermissions {

    override fun hasForeground(): Boolean = isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

    // ACCESS_BACKGROUND_LOCATION exists only from API 29; before that the foreground grant covers it.
    override fun hasBackground(): Boolean =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            hasForeground()
        } else {
            isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

/** The next action towards background location; Android makes it a multi-step grant. */
enum class BackgroundLocationStep {
    /** Already available; nothing to ask. */
    AlreadyGranted,

    /** Background location can only be asked for once foreground location is granted. */
    RequestForegroundFirst,

    /** API 29: a runtime dialog offers "Allow all the time". */
    RequestBackgroundInline,

    /** API 30+: "Allow all the time" can only be picked in the app's system settings. */
    DirectToSystemSettings
}

/** Pure decision for the next [BackgroundLocationStep], so it can be unit tested on the JVM. */
fun nextBackgroundLocationStep(
    sdkInt: Int,
    foregroundGranted: Boolean,
    backgroundGranted: Boolean
): BackgroundLocationStep = when {
    backgroundGranted -> BackgroundLocationStep.AlreadyGranted
    !foregroundGranted -> BackgroundLocationStep.RequestForegroundFirst
    sdkInt < Build.VERSION_CODES.Q -> BackgroundLocationStep.AlreadyGranted
    sdkInt == Build.VERSION_CODES.Q -> BackgroundLocationStep.RequestBackgroundInline
    else -> BackgroundLocationStep.DirectToSystemSettings
}
