package com.example.perfectoutfit.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.perfectoutfit.core.database.dao.ClothingItemDao
import com.example.perfectoutfit.core.database.dao.OutfitEntryDao
import com.example.perfectoutfit.core.database.dao.OutfitItemDao
import com.example.perfectoutfit.core.database.dao.WeatherSnapshotDao
import com.example.perfectoutfit.core.model.BodyPart
import com.example.perfectoutfit.core.model.ClothingItem
import com.example.perfectoutfit.core.model.ClothingItemActivityType
import com.example.perfectoutfit.core.model.OutfitEntry
import com.example.perfectoutfit.core.model.OutfitItem
import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.core.model.WeatherSnapshot

@Database(
    entities = [
        ClothingItem::class,
        ClothingItemActivityType::class,
        WeatherSnapshot::class,
        OutfitEntry::class,
        OutfitItem::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clothingItemDao(): ClothingItemDao
    abstract fun weatherSnapshotDao(): WeatherSnapshotDao
    abstract fun outfitEntryDao(): OutfitEntryDao
    abstract fun outfitItemDao(): OutfitItemDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE outfit_entries ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS favorite_locations")
            }
        }

        /** Fixes the typo in the German default glove name seeded by installs before version 4. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "UPDATE clothing_items SET name = 'Handschuhe (dünn)' " +
                        "WHERE isDefault = 1 AND name = 'Handschühe (dünn)'"
                )
            }
        }

        /**
         * Renames sport to activity type and lets an item belong to several activity types:
         * each item's single sport becomes one join row. minSdk 26 ships SQLite < 3.25 (no
         * RENAME COLUMN), so both tables are rebuilt. Foreign keys are off during migrations
         * (Room enables them on open), so dropping the old tables cascades nowhere.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `clothing_item_activity_types` (" +
                        "`clothingItemId` INTEGER NOT NULL, `activityType` TEXT NOT NULL, " +
                        "PRIMARY KEY(`clothingItemId`, `activityType`), " +
                        "FOREIGN KEY(`clothingItemId`) REFERENCES `clothing_items`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_clothing_item_activity_types_activityType` " +
                        "ON `clothing_item_activity_types` (`activityType`)"
                )
                database.execSQL(
                    "INSERT INTO clothing_item_activity_types (clothingItemId, activityType) " +
                        "SELECT id, sport FROM clothing_items"
                )

                database.execSQL(
                    "CREATE TABLE `clothing_items_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`bodyPart` TEXT NOT NULL, `name` TEXT NOT NULL, `isDefault` INTEGER NOT NULL)"
                )
                database.execSQL(
                    "INSERT INTO clothing_items_new (id, bodyPart, name, isDefault) " +
                        "SELECT id, bodyPart, name, isDefault FROM clothing_items"
                )
                database.execSQL("DROP TABLE clothing_items")
                database.execSQL("ALTER TABLE clothing_items_new RENAME TO clothing_items")

                database.execSQL(
                    "CREATE TABLE `outfit_entries_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`weatherSnapshotId` INTEGER NOT NULL, `activityType` TEXT NOT NULL, " +
                        "`comfortRating` INTEGER, `createdAt` INTEGER NOT NULL, `ratedAt` INTEGER, " +
                        "`notes` TEXT NOT NULL, FOREIGN KEY(`weatherSnapshotId`) REFERENCES " +
                        "`weather_snapshots`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                database.execSQL(
                    "INSERT INTO outfit_entries_new " +
                        "(id, weatherSnapshotId, activityType, comfortRating, createdAt, ratedAt, notes) " +
                        "SELECT id, weatherSnapshotId, sport, comfortRating, createdAt, ratedAt, notes " +
                        "FROM outfit_entries"
                )
                database.execSQL("DROP TABLE outfit_entries")
                database.execSQL("ALTER TABLE outfit_entries_new RENAME TO outfit_entries")
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_outfit_entries_weatherSnapshotId` " +
                        "ON `outfit_entries` (`weatherSnapshotId`)"
                )
            }
        }

        val prepopulateCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                buildDefaultCatalog().forEach { item ->
                    db.execSQL(
                        "INSERT INTO clothing_items (bodyPart, name, isDefault) VALUES (?, ?, 1)",
                        arrayOf(item.bodyPart.name, item.name)
                    )
                    // Not last_insert_rowid(): after the first tag row it is that row's, not the item's.
                    item.activityTypes.forEach { activityType ->
                        db.execSQL(
                            "INSERT INTO clothing_item_activity_types (clothingItemId, activityType) " +
                                "SELECT MAX(id), ? FROM clothing_items",
                            arrayOf(activityType.name)
                        )
                    }
                }
            }
        }

        private class DefaultItem(val activityTypes: Set<ActivityType>, val bodyPart: BodyPart, val name: String) {
            constructor(activityType: ActivityType, bodyPart: BodyPart, name: String) :
                this(setOf(activityType), bodyPart, name)
        }

        private fun buildDefaultCatalog(): List<DefaultItem> {
            val items = mutableListOf<DefaultItem>()

            // Shared with Bike commute and Walking: one row each, not copies.
            val everyday = setOf(ActivityType.BIKE_COMMUTE, ActivityType.WALKING)

            // Cycling items
            val c = ActivityType.CYCLING
            items += listOf(
                DefaultItem(c, BodyPart.HEAD_THROAT, "Cap"),
                DefaultItem(c, BodyPart.HEAD_THROAT, "Balaclava"),
                DefaultItem(c, BodyPart.UPPER_BODY, "Jersey"),
                DefaultItem(c, BodyPart.UPPER_BODY, "Fleece"),
                DefaultItem(c, BodyPart.UPPER_BODY, "Vest"),
                DefaultItem(c, BodyPart.UPPER_BODY, "Rain Jacket"),
                DefaultItem(c, BodyPart.ARMS, "Arm Warmers"),
                DefaultItem(c, BodyPart.ARMS, "UV Sleeves"),
                DefaultItem(c, BodyPart.LEGS, "Bib Shorts"),
                DefaultItem(c, BodyPart.LEGS, "Thermal Bib Tights"),
                DefaultItem(c, BodyPart.LEGS, "Boxers"),
                DefaultItem(c, BodyPart.FEET, "Socks"),
                DefaultItem(c, BodyPart.FEET, "Thermal Socks"),
                DefaultItem(c, BodyPart.FEET, "Overshoes"),
                DefaultItem(c, BodyPart.FEET, "Thermal Overshoes"),
                DefaultItem(everyday + c, BodyPart.HANDS, "Thin Gloves"),
                DefaultItem(everyday + c, BodyPart.HANDS, "Thick Gloves"),
            )

            // Running items
            val r = ActivityType.RUNNING
            items += listOf(
                DefaultItem(r, BodyPart.HEAD_THROAT, "Tuque"),
                DefaultItem(r, BodyPart.HEAD_THROAT, "Headband"),
                DefaultItem(r, BodyPart.HEAD_THROAT, "Buff"),
                DefaultItem(everyday + r, BodyPart.HEAD_THROAT, "Ski Mask"),
                DefaultItem(r, BodyPart.UPPER_BODY, "Shirt"),
                DefaultItem(r, BodyPart.UPPER_BODY, "Longsleeve Shirt"),
                DefaultItem(r, BodyPart.UPPER_BODY, "Pullover"),
                DefaultItem(r, BodyPart.UPPER_BODY, "Fleece"),
                DefaultItem(r, BodyPart.UPPER_BODY, "Rain Jacket"),
                DefaultItem(r, BodyPart.LEGS, "Shorts"),
                DefaultItem(r, BodyPart.LEGS, "Jogging Pants"),
                DefaultItem(r, BodyPart.HANDS, "Thin Winter Gloves"),
            )

            // Bike commute and Walking items
            items += listOf(
                DefaultItem(everyday, BodyPart.UPPER_BODY, "Rain Jacket"),
                DefaultItem(everyday, BodyPart.UPPER_BODY, "Fleece Jacket"),
                DefaultItem(everyday, BodyPart.UPPER_BODY, "Winter Jacket"),
                DefaultItem(everyday, BodyPart.UPPER_BODY, "Teddy Jacket"),
            )

            return items
        }
    }
}
