package com.example.roomlab2.data

import androidx.room.Dao
import androidx.room.Query

@Dao
interface GroupDao : BaseDao<StudyGroup> {

    @Query("SELECT * FROM student_groups ORDER BY id")
    suspend fun getAll(): List<StudyGroup>

    @Query("SELECT * FROM student_groups WHERE id = :id")
    suspend fun getById(id: Long): StudyGroup?
}
