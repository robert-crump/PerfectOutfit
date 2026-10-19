package com.example.perfectoutfit.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.core.model.BodyPart
import com.example.perfectoutfit.core.model.ClothingItem
import com.example.perfectoutfit.core.model.ClothingItemActivityType
import com.example.perfectoutfit.core.model.OutfitEntry
import com.example.perfectoutfit.core.model.OutfitItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs Room on Robolectric's native SQLite. SDK 35 gives it a sandbox of its own: sharing the
 * SDK 34 sandbox with the notification tests made RatingActionHandlerTest's Toast fail.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test.db"
    private var database: AppDatabase? = null

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(dbName)
    }

    /** The version 4 schema exactly as Room created it, with a little data. */
    private fun createVersion4() {
        val file = context.getDatabasePath(dbName).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `clothing_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sport` TEXT NOT NULL, `bodyPart` TEXT NOT NULL, `name` TEXT NOT NULL, `isDefault` INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `weather_snapshots` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `locationName` TEXT NOT NULL, `temperatureCelsius` REAL NOT NULL, `apparentTemperatureCelsius` REAL NOT NULL, `windSpeedKmh` REAL NOT NULL, `windDirectionDegrees` INTEGER NOT NULL, `uvIndex` INTEGER NOT NULL, `cloudCoverPercent` INTEGER NOT NULL, `precipitationProbabilityPercent` INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `outfit_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `weatherSnapshotId` INTEGER NOT NULL, `sport` TEXT NOT NULL, `comfortRating` INTEGER, `createdAt` INTEGER NOT NULL, `ratedAt` INTEGER, `notes` TEXT NOT NULL, FOREIGN KEY(`weatherSnapshotId`) REFERENCES `weather_snapshots`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_outfit_entries_weatherSnapshotId` ON `outfit_entries` (`weatherSnapshotId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `outfit_items` (`outfitEntryId` INTEGER NOT NULL, `clothingItemId` INTEGER NOT NULL, PRIMARY KEY(`outfitEntryId`, `clothingItemId`), FOREIGN KEY(`outfitEntryId`) REFERENCES `outfit_entries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`clothingItemId`) REFERENCES `clothing_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_outfit_items_clothingItemId` ON `outfit_items` (`clothingItemId`)")

            db.execSQL("INSERT INTO clothing_items VALUES (1, 'CYCLING', 'UPPER_BODY', 'Jersey', 1)")
            db.execSQL("INSERT INTO clothing_items VALUES (2, 'RUNNING', 'LEGS', 'Shorts', 0)")
            db.execSQL("INSERT INTO weather_snapshots VALUES (1, 1000, 52.5, 13.4, 'Berlin', 10.0, 8.0, 15.0, 180, 3, 40, 20)")
            db.execSQL("INSERT INTO weather_snapshots VALUES (2, 2000, 52.5, 13.4, 'Berlin', 20.0, 21.0, 5.0, 90, 6, 10, 0)")
            db.execSQL("INSERT INTO outfit_entries VALUES (1, 1, 'CYCLING', 0, 1000, 1500, 'Good ride')")
            db.execSQL("INSERT INTO outfit_entries VALUES (2, 2, 'RUNNING', NULL, 2000, NULL, '')")
            db.execSQL("INSERT INTO outfit_items VALUES (1, 1)")
            db.execSQL("INSERT INTO outfit_items VALUES (2, 2)")
            db.version = 4
        }
    }

    private fun openMigrated(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(
                AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5
            )
            .allowMainThreadQueries()
            .build()
            .also { database = it }

    @Test
    fun `migration 4 to 5 keeps items, entries, ratings and snapshots`() = runBlocking {
        createVersion4()

        val db = openMigrated()

        assertEquals(
            listOf(
                ClothingItem(id = 2, bodyPart = BodyPart.LEGS, name = "Shorts"),
                ClothingItem(id = 1, bodyPart = BodyPart.UPPER_BODY, name = "Jersey", isDefault = true)
            ),
            db.clothingItemDao().getAll()
        )
        assertEquals(
            listOf(
                ClothingItemActivityType(1, ActivityType.CYCLING),
                ClothingItemActivityType(2, ActivityType.RUNNING)
            ),
            db.clothingItemDao().getAllActivityTypes()
        )
        assertEquals(
            listOf(
                OutfitEntry(id = 2, weatherSnapshotId = 2, activityType = ActivityType.RUNNING, createdAt = 2000),
                OutfitEntry(
                    id = 1, weatherSnapshotId = 1, activityType = ActivityType.CYCLING, comfortRating = 0,
                    createdAt = 1000, ratedAt = 1500, notes = "Good ride"
                )
            ),
            db.outfitEntryDao().getAll()
        )
        assertEquals(listOf(OutfitItem(1, 1), OutfitItem(2, 2)), db.outfitItemDao().getAll())
        assertEquals(listOf(1L, 2L), db.weatherSnapshotDao().getAll().map { it.id }.sorted())
        assertEquals(
            listOf("Shorts"),
            db.clothingItemDao().getByActivityType(ActivityType.RUNNING).first().map { it.name }
        )
        db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { cursor ->
            assertEquals(0, cursor.count)
        }
    }

    @Test
    fun `after the migration deleting an item still cascades to its rows and entries`() = runBlocking {
        createVersion4()
        val db = openMigrated()

        db.clothingItemDao().deleteById(1)

        assertEquals(
            listOf(ClothingItemActivityType(2, ActivityType.RUNNING)),
            db.clothingItemDao().getAllActivityTypes()
        )
        assertEquals(listOf(OutfitItem(2, 2)), db.outfitItemDao().getAll())
    }

    @Test
    fun `a fresh install seeds every default item with one activity type`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.prepopulateCallback)
            .allowMainThreadQueries()
            .build()
            .also { database = it }

        val items = db.clothingItemDao().getAll()
        val rows = db.clothingItemDao().getAllActivityTypes()

        assertTrue(items.isNotEmpty())
        assertEquals(items.map { it.id }.sorted(), rows.map { it.clothingItemId })
        assertEquals(
            listOf("Bib Shorts", "Boxers", "Thermal Bib Tights"),
            db.clothingItemDao().getByActivityType(ActivityType.CYCLING).first()
                .filter { it.bodyPart == BodyPart.LEGS }.map { it.name }
        )
    }
}
