package com.example.roomlab2.data

import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Update

/** Способ 1: общий DAO с готовыми операциями для любой сущности. */
interface BaseDao<T> {

    @Insert
    suspend fun insert(item: T): Long

    @Insert
    suspend fun insertAll(items: List<T>): List<Long>

    @Update
    suspend fun update(item: T): Int

    @Delete
    suspend fun delete(item: T): Int
}
