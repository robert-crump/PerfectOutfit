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
import com.example.perfectoutfit.core.model.OutfitEntry
import com.example.perfectoutfit.core.model.OutfitItem
import com.example.perfectoutfit.core.model.Sport
import com.example.perfectoutfit.core.model.WeatherSnapshot

@Database(
    entities = [
        ClothingItem::class,
        WeatherSnapshot::class,
        OutfitEntry::class,
        OutfitItem::class
    ],
    version = 3,
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

        val prepopulateCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                val items = buildDefaultCatalog()
                items.forEach { item ->
                    db.execSQL(
                        "INSERT INTO clothing_items (sport, bodyPart, name, isDefault) VALUES (?, ?, ?, 1)",
                        arrayOf(item.sport.name, item.bodyPart.name, item.name)
                    )
                }
            }
        }

        private fun buildDefaultCatalog(): List<ClothingItem> {
            val items = mutableListOf<ClothingItem>()

            // Cycling items
            val c = Sport.CYCLING
            items += listOf(
                ClothingItem(sport = c, bodyPart = BodyPart.HEAD_THROAT, name = "Mütze"),
                ClothingItem(sport = c, bodyPart = BodyPart.HEAD_THROAT, name = "Skimaske"),
                ClothingItem(sport = c, bodyPart = BodyPart.UPPER_BODY, name = "Trikot"),
                ClothingItem(sport = c, bodyPart = BodyPart.UPPER_BODY, name = "Fleece"),
                ClothingItem(sport = c, bodyPart = BodyPart.UPPER_BODY, name = "Weste"),
                ClothingItem(sport = c, bodyPart = BodyPart.UPPER_BODY, name = "Regenjacke"),
                ClothingItem(sport = c, bodyPart = BodyPart.ARMS, name = "Armwärmer"),
                ClothingItem(sport = c, bodyPart = BodyPart.ARMS, name = "UV Sleeves"),
                ClothingItem(sport = c, bodyPart = BodyPart.LEGS, name = "Bib (kurz)"),
                ClothingItem(sport = c, bodyPart = BodyPart.LEGS, name = "Bib (Thermo)"),
                ClothingItem(sport = c, bodyPart = BodyPart.LEGS, name = "Boxershorts"),
                ClothingItem(sport = c, bodyPart = BodyPart.FEET, name = "Socken"),
                ClothingItem(sport = c, bodyPart = BodyPart.FEET, name = "Socken (Thermo)"),
                ClothingItem(sport = c, bodyPart = BodyPart.FEET, name = "Überschuhe"),
                ClothingItem(sport = c, bodyPart = BodyPart.FEET, name = "Überschuhe (Thermo)"),
                ClothingItem(sport = c, bodyPart = BodyPart.HANDS, name = "Handschühe (dünn)"),
                ClothingItem(sport = c, bodyPart = BodyPart.HANDS, name = "Handschuhe (dick)"),
            )

            // Running items
            val r = Sport.RUNNING
            items += listOf(
                ClothingItem(sport = r, bodyPart = BodyPart.HEAD_THROAT, name = "Tuque"),
                ClothingItem(sport = r, bodyPart = BodyPart.HEAD_THROAT, name = "Headband"),
                ClothingItem(sport = r, bodyPart = BodyPart.HEAD_THROAT, name = "Buff"),
                ClothingItem(sport = r, bodyPart = BodyPart.HEAD_THROAT, name = "Ski Mask"),
                ClothingItem(sport = r, bodyPart = BodyPart.UPPER_BODY, name = "Shirt"),
                ClothingItem(sport = r, bodyPart = BodyPart.UPPER_BODY, name = "Longsleeve Shirt"),
                ClothingItem(sport = r, bodyPart = BodyPart.UPPER_BODY, name = "Pullover"),
                ClothingItem(sport = r, bodyPart = BodyPart.UPPER_BODY, name = "Fleece"),
                ClothingItem(sport = r, bodyPart = BodyPart.UPPER_BODY, name = "Rain Jacket"),
                ClothingItem(sport = r, bodyPart = BodyPart.LEGS, name = "Shorts"),
                ClothingItem(sport = r, bodyPart = BodyPart.LEGS, name = "Jogging Pants"),
                ClothingItem(sport = r, bodyPart = BodyPart.HANDS, name = "Thin Winter Gloves"),
            )

            return items
        }
    }
}
