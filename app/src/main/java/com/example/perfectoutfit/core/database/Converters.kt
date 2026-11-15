package com.example.perfectoutfit.core.database

import androidx.room.TypeConverter
import com.example.perfectoutfit.core.model.BodyPart
import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.core.model.LogMode

class Converters {
    @TypeConverter
    fun fromActivityType(activityType: ActivityType): String = activityType.name

    @TypeConverter
    fun toActivityType(value: String): ActivityType = ActivityType.valueOf(value)

    @TypeConverter
    fun fromBodyPart(bodyPart: BodyPart): String = bodyPart.name

    @TypeConverter
    fun toBodyPart(value: String): BodyPart = BodyPart.valueOf(value)

    @TypeConverter
    fun fromLogMode(logMode: LogMode?): String? = logMode?.name

    @TypeConverter
    fun toLogMode(value: String?): LogMode? = value?.let(LogMode::valueOf)
}
