package com.example.roomlab2.data

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao : BaseDao<Student> {

    // ---------- Способ 1: простые выборки (дальше фильтрация в Kotlin) ----------
    @Query("SELECT * FROM students ORDER BY id")
    suspend fun getAll(): List<Student>

    @Query("SELECT * FROM students WHERE id = :id")
    suspend fun getById(id: Long): Student?

    // ---------- Способ 2: @Query с параметрами ----------

    /** Выборка по условию: поиск по части имени. */
    @Query("SELECT * FROM students WHERE name LIKE '%' || :text || '%' ORDER BY name")
    suspend fun findByName(text: String): List<Student>

    /** Особый запрос варианта 2: студенты с оценкой не ниже заданной, по убыванию. */
    @Query("SELECT * FROM students WHERE grade >= :minGrade ORDER BY grade DESC, name")
    suspend fun findByMinGrade(minGrade: Double): List<Student>

    /** Агрегат: общий средний балл. */
    @Query("SELECT AVG(grade) FROM students")
    suspend fun avgGrade(): Double?

    @Query("SELECT COUNT(*) FROM students")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM students WHERE groupId = :groupId")
    suspend fun countByGroup(groupId: Long): Int

    /** Агрегат варианта 2: средний балл по каждой группе. */
    @Query(
        """
        SELECT g.title AS groupTitle, AVG(s.grade) AS avgGrade
        FROM student_groups g INNER JOIN students s ON s.groupId = g.id
        GROUP BY g.id
        ORDER BY g.title
        """
    )
    suspend fun avgGradePerGroup(): List<GroupAvgGrade>

    /** JOIN двух связанных таблиц. */
    @Query(
        """
        SELECT s.id AS studentId, s.name AS name, s.grade AS grade,
               g.title AS groupTitle, g.course AS course
        FROM students s INNER JOIN student_groups g ON g.id = s.groupId
        ORDER BY g.title, s.name
        """
    )
    suspend fun getStudentsWithGroups(): List<StudentWithGroup>

    /** Flow: эмитит новое значение при каждом изменении таблицы students. */
    @Query("SELECT COUNT(*) FROM students")
    fun observeCount(): Flow<Int>
}
