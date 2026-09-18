package com.example.perfectoutfit.core.notification

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.perfectoutfit.feature.home.OutfitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RatingActionHandlerTest {

    private lateinit var context: Context
    private lateinit var fakeDao: FakeOutfitEntryDao
    private lateinit var fakeRatingReminder: FakeRatingReminder
    private lateinit var handler: RatingActionHandler

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        fakeDao = FakeOutfitEntryDao()
        fakeRatingReminder = FakeRatingReminder()
        handler = RatingActionHandler(
            OutfitRepository(fakeDao, FakeOutfitItemDao()),
            fakeRatingReminder
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `handle saves the rating`() = runTest {
        fakeDao.seed(entryId = 42L)

        handler.handle(context, entryId = 42L, rating = -1)

        assertEquals(-1, fakeDao.lastUpdated?.comfortRating)
    }

    @Test
    fun `handle for a deleted entry is a no-op`() = runTest {
        handler.handle(context, entryId = 999L, rating = 0)

        assertNull(fakeDao.lastUpdated)
    }

    @Test
    fun `handle overwrites a previous rating`() = runTest {
        fakeDao.seed(entryId = 5L, rating = 0)

        handler.handle(context, entryId = 5L, rating = 1)

        assertEquals(1, fakeDao.lastUpdated?.comfortRating)
    }

    @Test
    fun `handle cancels the reminder for that entry`() = runTest {
        fakeDao.seed(entryId = 42L)

        handler.handle(context, entryId = 42L, rating = -1)

        assertEquals(listOf(42L), fakeRatingReminder.cancelledEntryIds)
    }

    @Test
    fun `handle cancels the reminder even for a deleted entry`() = runTest {
        handler.handle(context, entryId = 999L, rating = 0)

        assertTrue(fakeRatingReminder.cancelledEntryIds.contains(999L))
    }
}
