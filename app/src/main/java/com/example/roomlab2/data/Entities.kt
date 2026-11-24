package com.example.roomlab2.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Таблица 1 (родитель): Group (id, title, course).
 * Класс назван StudyGroup, а таблица student_groups: слова Group/GROUPS
 * зарезервированы в SQL и Kotlin-коде путают с функциями стандартной библиотеки.
 */
@Entity(tableName = "student_groups")
data class StudyGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val course: Int
)

/**
 * Таблица 2 (потомок): Student (id, groupId, name, birthDate, grade).
 * Связь один-ко-многим: одна группа — много студентов.
 * birthDate хранится через TypeConverter (Date <-> Long).
 */
@Entity(
    tableName = "students",
    foreignKeys = [
        ForeignKey(
            entity = StudyGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("groupId")]
)
data class Student(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val name: String,
    val birthDate: Date,
    val grade: Double
)

/** Результат JOIN-запроса: студент + данные его группы. */
data class StudentWithGroup(
    val studentId: Long,
    val name: String,
    val grade: Double,
    val groupTitle: String,
    val course: Int
)

/** Результат агрегата с GROUP BY: средний балл по группе. */
data class GroupAvgGrade(
    val groupTitle: String,
    val avgGrade: Double
)
