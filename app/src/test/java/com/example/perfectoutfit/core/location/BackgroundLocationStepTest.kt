package com.example.perfectoutfit.core.location

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundLocationStepTest {

    @Test
    fun `a background grant needs nothing more on any API level`() {
        for (sdk in listOf(26, 29, 36)) {
            assertEquals(
                BackgroundLocationStep.AlreadyGranted,
                nextBackgroundLocationStep(sdk, foregroundGranted = true, backgroundGranted = true)
            )
        }
    }

    @Test
    fun `without foreground location that is asked for first`() {
        for (sdk in listOf(26, 29, 36)) {
            assertEquals(
                BackgroundLocationStep.RequestForegroundFirst,
                nextBackgroundLocationStep(sdk, foregroundGranted = false, backgroundGranted = false)
            )
        }
    }

    @Test
    fun `before API 29 the foreground grant covers the background`() {
        assertEquals(
            BackgroundLocationStep.AlreadyGranted,
            nextBackgroundLocationStep(28, foregroundGranted = true, backgroundGranted = false)
        )
    }

    @Test
    fun `API 29 asks inline`() {
        assertEquals(
            BackgroundLocationStep.RequestBackgroundInline,
            nextBackgroundLocationStep(29, foregroundGranted = true, backgroundGranted = false)
        )
    }

    @Test
    fun `API 30 and later go to system settings`() {
        for (sdk in listOf(30, 36)) {
            assertEquals(
                BackgroundLocationStep.DirectToSystemSettings,
                nextBackgroundLocationStep(sdk, foregroundGranted = true, backgroundGranted = false)
            )
        }
    }
}
