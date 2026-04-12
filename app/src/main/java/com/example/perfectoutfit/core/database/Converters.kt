package com.example.perfectoutfit.core.database

import androidx.room.TypeConverter
import com.example.perfectoutfit.core.model.BodyPart
import com.example.perfectoutfit.core.model.Sport

class Converters {
    @TypeConverter
    fun fromSport(sport: Sport): String = sport.name

    @TypeConverter
    fun toSport(value: String): Sport = Sport.valueOf(value)

    @TypeConverter
    fun fromBodyPart(bodyPart: BodyPart): String = bodyPart.name

    @TypeConverter
    fun toBodyPart(value: String): BodyPart = BodyPart.valueOf(value)
}
