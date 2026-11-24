package com.example.roomlab2.data

import androidx.room.TypeConverter
import java.util.Date

/** Date хранится в БД как Long (метка времени в миллисекундах). */
class Converters {

    @TypeConverter
    fun fromDate(date: Date): Long = date.time

    @TypeConverter
    fun toDate(value: Long): Date = Date(value)
}
