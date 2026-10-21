package com.example.perfectoutfit

import android.content.Intent
import android.net.Uri
import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.navigation.LaunchTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LaunchTargetTest {

    @Test
    fun `a plain launch has no target`() {
        assertNull(MainActivity.launchTargetOf(Intent(Intent.ACTION_MAIN)))
        assertNull(MainActivity.launchTargetOf(null))
    }

    @Test
    fun `the rating reminder deep link rates its entry`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("perfectoutfit://rate?outfitEntryId=42"))

        assertEquals(LaunchTarget.RateEntry(42), MainActivity.launchTargetOf(intent))
    }

    @Test
    fun `the backup notification opens Settings`() {
        assertEquals(LaunchTarget.Settings, MainActivity.launchTargetOf(Intent(MainActivity.ACTION_OPEN_SETTINGS)))
    }

    @Test
    fun `a widget icon opens its activity type's recommendation`() {
        val intent = Intent(MainActivity.ACTION_OPEN_HOME)
            .putExtra(MainActivity.EXTRA_ACTIVITY_TYPE, ActivityType.BIKE_COMMUTE.name)

        assertEquals(LaunchTarget.Home(ActivityType.BIKE_COMMUTE), MainActivity.launchTargetOf(intent))
    }

    @Test
    fun `the widget's temperature, or an unknown activity type, opens Home's first step`() {
        assertEquals(LaunchTarget.Home(null), MainActivity.launchTargetOf(Intent(MainActivity.ACTION_OPEN_HOME)))
        assertEquals(
            LaunchTarget.Home(null),
            MainActivity.launchTargetOf(Intent(MainActivity.ACTION_OPEN_HOME).putExtra(MainActivity.EXTRA_ACTIVITY_TYPE, "SKIING"))
        )
    }
}
