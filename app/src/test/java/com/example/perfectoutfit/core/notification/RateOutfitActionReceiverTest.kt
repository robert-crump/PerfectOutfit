package com.example.perfectoutfit.core.notification

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.perfectoutfit.feature.home.OutfitRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RateOutfitActionReceiverTest {

    private lateinit var context: Context
    private lateinit var receiver: TestableReceiver

    private class TestableReceiver : RateOutfitActionReceiver() {
        var beginAsyncWorkCallCount = 0
            private set

        override fun beginAsyncWork(): () -> Unit {
            beginAsyncWorkCallCount++
            return {}
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        receiver = TestableReceiver().apply {
            ratingActionHandler = RatingActionHandler(OutfitRepository(FakeOutfitEntryDao(), FakeOutfitItemDao()))
        }
    }

    @Test
    fun `valid rating action starts async work`() {
        deliverRatingBroadcast(entryId = 42L, rating = -1)

        assertEquals(1, receiver.beginAsyncWorkCallCount)
    }

    @Test
    fun `invalid rating extra does not start async work`() {
        deliverRatingBroadcast(entryId = 42L, rating = 99)

        assertEquals(0, receiver.beginAsyncWorkCallCount)
    }

    @Test
    fun `non-positive entry id does not start async work`() {
        deliverRatingBroadcast(entryId = 0L, rating = 0)

        assertEquals(0, receiver.beginAsyncWorkCallCount)
    }

    @Test
    fun `unrelated action is ignored`() {
        receiver.onReceive(context, Intent("some.other.action"))

        assertEquals(0, receiver.beginAsyncWorkCallCount)
    }

    private fun deliverRatingBroadcast(entryId: Long, rating: Int) {
        val intent = Intent(RateOutfitActionReceiver.ACTION_RATE).apply {
            putExtra(RateOutfitActionReceiver.EXTRA_ENTRY_ID, entryId)
            putExtra(RateOutfitActionReceiver.EXTRA_RATING, rating)
        }
        receiver.onReceive(context, intent)
    }
}
