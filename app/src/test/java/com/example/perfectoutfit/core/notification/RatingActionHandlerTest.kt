package com.example.perfectoutfit.core.notification

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.perfectoutfit.feature.home.OutfitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RatingActionHandlerTest {

    private lateinit var context: Context
    private lateinit var fakeDao: FakeOutfitEntryDao
    private lateinit var handler: RatingActionHandler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fakeDao = FakeOutfitEntryDao()
        handler = RatingActionHandler(OutfitRepository(fakeDao, FakeOutfitItemDao())).apply {
            mainDispatcher = Dispatchers.Unconfined
        }
    }

    @Test
    fun `handle saves the rating`() = runBlocking {
        fakeDao.seed(entryId = 42L)

        handler.handle(context, entryId = 42L, rating = -1)

        assertEquals(-1, fakeDao.lastUpdated?.comfortRating)
    }

    @Test
    fun `handle for a deleted entry is a no-op`() = runBlocking {
        handler.handle(context, entryId = 999L, rating = 0)

        assertNull(fakeDao.lastUpdated)
    }

    @Test
    fun `handle overwrites a previous rating`() = runBlocking {
        fakeDao.seed(entryId = 5L, rating = 0)

        handler.handle(context, entryId = 5L, rating = 1)

        assertEquals(1, fakeDao.lastUpdated?.comfortRating)
    }
}
