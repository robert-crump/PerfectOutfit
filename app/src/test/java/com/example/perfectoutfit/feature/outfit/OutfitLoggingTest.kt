package com.example.perfectoutfit.feature.outfit

import com.example.perfectoutfit.core.model.OutfitEntry
import com.example.perfectoutfit.core.model.Sport
import com.example.perfectoutfit.core.notification.FakeRatingReminder
import com.example.perfectoutfit.feature.home.HourlyWeather
import com.example.perfectoutfit.testutil.FakeDatabaseTransactionRunner
import com.example.perfectoutfit.testutil.FakeOutfitEntryDao
import com.example.perfectoutfit.testutil.FakeOutfitItemDao
import com.example.perfectoutfit.testutil.FakeWeatherSnapshotDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class OutfitLoggingTest {

    private val entryDao = FakeOutfitEntryDao()
    private val itemDao = FakeOutfitItemDao()
    private val snapshotDao = FakeWeatherSnapshotDao()
    private val reminder = FakeRatingReminder()
    private val logging = OutfitLogging(entryDao, itemDao, snapshotDao, FakeDatabaseTransactionRunner(), reminder)

    private val hour = HourlyWeather(
        time = LocalDateTime.of(2026, 9, 1, 14, 0),
        temperatureCelsius = 12.5,
        apparentTemperatureCelsius = 10.0,
        windSpeedKmh = 18.0,
        windDirectionDegrees = 90,
        uvIndex = 3,
        cloudCoverPercent = 40,
        precipitationProbabilityPercent = 20
    )
    private val hourMs = hour.time.atZone(ZoneId.systemDefault()).toEpochSecond() * 1000
    private val location = LogLocation("Berlin", 52.5, 13.4)

    private suspend fun log(rating: Int? = null, mode: LogMode) = logging.log(
        hour = hour, location = location, sport = Sport.CYCLING, clothingItemIds = listOf(1L, 2L),
        rating = rating, notes = "n", workoutDurationHours = 2, mode = mode
    )

    @Test
    fun `live log schedules a reminder at the workout hour`() = runTest {
        val id = log(mode = LogMode.LIVE)

        assertEquals(listOf(FakeRatingReminder.ShowCall(id, Sport.CYCLING, hourMs, 2)), reminder.showCalls)
    }

    @Test
    fun `live log with a rating still schedules a reminder`() = runTest {
        log(rating = 0, mode = LogMode.LIVE)

        assertEquals(1, reminder.showCalls.size)
    }

    @Test
    fun `past log with rating does not schedule a reminder`() = runTest {
        log(rating = 0, mode = LogMode.PAST)

        assertTrue(reminder.showCalls.isEmpty())
    }

    @Test
    fun `past log without rating schedules a reminder`() = runTest {
        log(mode = LogMode.PAST)

        assertEquals(1, reminder.showCalls.size)
    }

    @Test
    fun `entry is stamped with the workout hour and stores items`() = runTest {
        val id = log(rating = -1, mode = LogMode.PAST)

        val entry = entryDao.getById(id)!!
        assertEquals(hourMs, entry.createdAt)
        assertEquals(-1, entry.comfortRating)
        assertNotNull(entry.ratedAt)
        assertEquals("n", entry.notes)
        assertEquals(listOf(1L, 2L), itemDao.getByEntryId(id).map { it.clothingItemId })
    }

    @Test
    fun `unrated log has no ratedAt`() = runTest {
        val id = log(mode = LogMode.LIVE)

        assertNull(entryDao.getById(id)!!.ratedAt)
    }

    @Test
    fun `snapshot fields equal the hour's fields`() = runTest {
        val id = log(mode = LogMode.LIVE)

        val snapshot = snapshotDao.getById(entryDao.getById(id)!!.weatherSnapshotId)!!
        assertEquals(hourMs, snapshot.timestamp)
        assertEquals("Berlin", snapshot.locationName)
        assertEquals(52.5, snapshot.latitude, 0.0)
        assertEquals(13.4, snapshot.longitude, 0.0)
        assertEquals(hour.temperatureCelsius, snapshot.temperatureCelsius, 0.0)
        assertEquals(hour.apparentTemperatureCelsius, snapshot.apparentTemperatureCelsius, 0.0)
        assertEquals(hour.windSpeedKmh, snapshot.windSpeedKmh, 0.0)
        assertEquals(hour.windDirectionDegrees, snapshot.windDirectionDegrees)
        assertEquals(hour.uvIndex, snapshot.uvIndex)
        assertEquals(hour.cloudCoverPercent, snapshot.cloudCoverPercent)
        assertEquals(hour.precipitationProbabilityPercent, snapshot.precipitationProbabilityPercent)
    }

    @Test
    fun `blank location name falls back to Current Location`() = runTest {
        val id = logging.log(
            hour = hour, location = LogLocation("", 1.0, 2.0), sport = Sport.CYCLING,
            clothingItemIds = listOf(1L), mode = LogMode.LIVE
        )

        assertEquals("Current Location", snapshotDao.getById(entryDao.getById(id)!!.weatherSnapshotId)!!.locationName)
    }

    @Test
    fun `rate sets ratedAt and cancels the reminder`() = runTest {
        val id = log(mode = LogMode.LIVE)

        logging.rate(id, 1)

        val entry = entryDao.getById(id)!!
        assertEquals(1, entry.comfortRating)
        assertNotNull(entry.ratedAt)
        assertEquals(listOf(id), reminder.cancelledEntryIds)
    }

    @Test
    fun `update replaces items and notes and rates in one call`() = runTest {
        val id = log(mode = LogMode.PAST)

        logging.update(id, clothingItemIds = listOf(3L), notes = "new", rating = 0)

        val entry = entryDao.getById(id)!!
        assertEquals("new", entry.notes)
        assertEquals(0, entry.comfortRating)
        assertEquals(listOf(3L), itemDao.getByEntryId(id).map { it.clothingItemId })
    }

    @Test
    fun `update without rating leaves the rating alone`() = runTest {
        val id = log(rating = 1, mode = LogMode.PAST)

        logging.update(id, clothingItemIds = listOf(3L), notes = "x", rating = null)

        assertEquals(1, entryDao.getById(id)!!.comfortRating)
    }

    @Test
    fun `restore re-inserts entry and items with the original id`() = runTest {
        val entry = OutfitEntry(id = 77L, weatherSnapshotId = 1L, sport = Sport.CYCLING, createdAt = 5L)

        logging.restore(entry, listOf(4L, 5L))

        assertEquals(entry, entryDao.getById(77L))
        assertEquals(listOf(4L, 5L), itemDao.getByEntryId(77L).map { it.clothingItemId })
    }
}
