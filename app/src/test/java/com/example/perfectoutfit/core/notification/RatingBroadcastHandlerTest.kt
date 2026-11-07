package com.example.perfectoutfit.core.notification

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.perfectoutfit.core.model.ComfortRating
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RatingBroadcastHandlerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val calls = mutableListOf<Pair<Long, ComfortRating>>()
    private var begun = 0
    private var finished = 0

    private fun TestScope.deliver(intent: Intent) {
        RatingBroadcastHandler({ _, id, rating -> calls += id to rating }, this)
            .onReceive(context, intent) {
                begun++
                return@onReceive { finished++ }
            }
        advanceUntilIdle()
    }

    private fun rateIntent(entryId: Long, rating: Int) = Intent(RateOutfitActionReceiver.ACTION_RATE).apply {
        putExtra(RateOutfitActionReceiver.EXTRA_ENTRY_ID, entryId)
        putExtra(RateOutfitActionReceiver.EXTRA_RATING, rating)
    }

    @Test
    fun `valid rating action runs the handler and finishes`() = runTest {
        deliver(rateIntent(42L, -1))

        assertEquals(1, calls.size)
        assertEquals(42L, calls.single().first)
        assertEquals(1, begun)
        assertEquals(1, finished)
    }

    @Test
    fun `invalid rating extra is ignored`() = runTest {
        deliver(rateIntent(42L, 99))

        assertEquals(0, begun)
        assertEquals(0, calls.size)
    }

    @Test
    fun `non-positive entry id is ignored`() = runTest {
        deliver(rateIntent(0L, 0))

        assertEquals(0, begun)
        assertEquals(0, calls.size)
    }

    @Test
    fun `unrelated action is ignored`() = runTest {
        deliver(Intent("some.other.action"))

        assertEquals(0, begun)
    }

    @Test
    fun `finish is called even when the handler throws`() = runTest {
        val scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.test.StandardTestDispatcher(testScheduler) +
                kotlinx.coroutines.CoroutineExceptionHandler { _, _ -> }
        )
        RatingBroadcastHandler({ _, _, _ -> error("boom") }, scope)
            .onReceive(context, rateIntent(42L, -1)) { { finished++ } }
        advanceUntilIdle()

        assertEquals(1, finished)
    }
}
