package com.example.perfectoutfit

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.perfectoutfit.core.datastore.PreferencesManager
import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.core.model.ThemeMode
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
    @Inject lateinit var preferencesManager: PreferencesManager

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
            // Null until DataStore has answered, so a forced mode doesn't flash the device's one first.
            val themeMode by preferencesManager.themeMode.collectAsStateWithLifecycle(initialValue = null)
            val darkTheme = when (themeMode ?: return@setContent) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // The default bar styles follow the device, so a forced mode would get unreadable bar icons.
            LaunchedEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LightScrim, DarkScrim) { darkTheme }
                )
            }
            PerfectOutfitTheme(darkTheme = darkTheme, dynamicColor = themeOptions.dynamicColor) {
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
        // The scrims androidx.activity uses by default.
        private val LightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        private val DarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

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
