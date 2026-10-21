package com.example.perfectoutfit

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.navigation.LaunchRequest
import com.example.perfectoutfit.navigation.LaunchTarget
import com.example.perfectoutfit.navigation.PerfectOutfitNavHost
import com.example.perfectoutfit.ui.theme.PerfectOutfitTheme
import com.example.perfectoutfit.ui.theme.ThemeOptions
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var themeOptions: ThemeOptions

    /** The latest intent's destination; singleTop delivers later ones to [onNewIntent]. */
    private var launchRequest by mutableStateOf<LaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissions(permissionsToRequest.toTypedArray(), 0)
        }
        // A recreated activity gets its back stack restored; acting on the intent again would
        // push the same screen a second time.
        if (savedInstanceState == null) launchRequest = launchTargetOf(intent)?.let(::LaunchRequest)
        setContent {
            PerfectOutfitTheme(dynamicColor = themeOptions.dynamicColor) {
                PerfectOutfitNavHost(launchRequest = launchRequest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchTargetOf(intent)?.let { launchRequest = LaunchRequest(it) }
    }

    companion object {
        const val ACTION_OPEN_SETTINGS = "com.example.perfectoutfit.OPEN_SETTINGS"

        /** Opens Home; with [EXTRA_ACTIVITY_TYPE] on that activity type's recommendation. */
        const val ACTION_OPEN_HOME = "com.example.perfectoutfit.OPEN_HOME"

        /** An [ActivityType] name. */
        const val EXTRA_ACTIVITY_TYPE = "com.example.perfectoutfit.ACTIVITY_TYPE"

        /** Where [intent] asks to go, or null for a plain launch. */
        fun launchTargetOf(intent: Intent?): LaunchTarget? {
            if (intent == null) return null
            intent.data?.getQueryParameter("outfitEntryId")?.toLongOrNull()?.let { return LaunchTarget.RateEntry(it) }
            return when (intent.action) {
                ACTION_OPEN_SETTINGS -> LaunchTarget.Settings
                ACTION_OPEN_HOME -> LaunchTarget.Home(
                    ActivityType.entries.find { it.name == intent.getStringExtra(EXTRA_ACTIVITY_TYPE) }
                )
                else -> null
            }
        }
    }
}
