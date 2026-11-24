package com.example.roomlab2.console

import android.util.Log
import androidx.room.withTransaction
import com.example.roomlab2.data.AppDatabase
import com.example.roomlab2.data.Student
import com.example.roomlab2.data.StudyGroup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.measureTimeMillis

/**
 * «Консоль» поверх Room. Вывод идёт в Logcat (тег ROOM_LAB)
 * и, если передан sink, дублируется на экран приложения.
 */
class Console(
    private val db: AppDatabase,
    private val sink: (String) -> Unit = {}
) {
    private val tag = "ROOM_LAB"

    private fun newDateFormat() = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).apply {
        isLenient = false
    }

    private fun say(msg: String) {
        Log.d(tag, msg)
        sink(msg)
    }

    private fun fail(msg: String) {
        Log.e(tag, "Ошибка: $msg")
        sink("ОШИБКА: $msg")
    }

    // ------------------------------------------------------------------
    // Запуск сценария
    // ------------------------------------------------------------------
    suspend fun run(script: List<String>) = coroutineScope {
        resetDb()

        // Подписка на Flow: печатает число студентов при каждом изменении таблицы.
        val flowJob = launch {
            db.studentDao().observeCount().collect { say("[Flow] студентов в таблице: $it") }
        }
        delay(200)

        for (line in script) {
            say("> $line")
            try {
                execute(line)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail("${e.javaClass.simpleName}: ${e.message}")
            }
            delay(60) // чтобы сообщение Flow успело напечататься до следующей команды
        }

        delay(200)
        flowJob.cancel()
        say("=== Сценарий завершён, команд: ${script.size} ===")
    }

    /** Очищает таблицы и сбрасывает счётчики id, чтобы сценарий был воспроизводимым. */
    private fun resetDb() {
        db.clearAllTables()
        try {
            db.openHelper.writableDatabase.execSQL("DELETE FROM sqlite_sequence")
        } catch (e: Exception) {
            Log.w(tag, "Не удалось сбросить sqlite_sequence: ${e.message}")
        }
    }

    // ------------------------------------------------------------------
    // Разбор команд
    // ------------------------------------------------------------------
    private suspend fun execute(line: String) {
        val text = line.trim()
        val cmd = text.substringBefore(' ').lowercase()
        val rest = text.substringAfter(' ', "").trim()
        when (cmd) {
            "add" -> add(rest)
            "addmany" -> addMany(rest)
            "update" -> update(rest)
            "delete" -> delete(rest)
            "list" -> list(rest)
            "find" -> find(rest)
            "stat" -> stat()
            "join" -> join()
            "bench" -> bench()
            else -> say("Неизвестная команда: '$cmd'")
        }
    }

    // ---------- add ----------
    private suspend fun add(rest: String) {
        val what = rest.substringBefore(' ').lowercase()
        val p = split(rest.substringAfter(' ', ""))
        when (what) {
            "group" -> {
                need(p, 2, "add group <название>|<курс>")
                require(p[0].isNotBlank()) { "Название группы не может быть пустым" }
                val course = toInt(p[1], "Курс")
                require(course in 1..6) { "Курс должен быть от 1 до 6, получено $course" }
                val id = db.groupDao().insert(StudyGroup(title = p[0], course = course))
                say("Добавлена группа id=$id")
            }
            "student" -> {
                need(p, 4, "add student <groupId>|<имя>|<дд.мм.гггг>|<оценка>")
                val groupId = toLong(p[0], "groupId")
                val student = makeStudent(0, groupId, p[1], p[2], p[3])
                val id = db.studentDao().insert(student)
                say("Добавлен студент id=$id")
            }
            else -> say("Используйте: add group ... | add student ...")
        }
    }

    // ---------- addmany (транзакция) ----------
    private suspend fun addMany(rest: String) {
        val p = split(rest)
        need(p, 3, "addmany <группа>|<курс>|<имя:дата:оценка>,...")
        require(p[0].isNotBlank()) { "Название группы не может быть пустым" }
        val course = toInt(p[1], "Курс")
        require(course in 1..6) { "Курс должен быть от 1 до 6, получено $course" }
        val items = p[2].split(",").map { it.trim() }.filter { it.isNotEmpty() }

        // Либо сохраняется группа со всеми студентами, либо (при любой ошибке) ничего.
        db.withTransaction {
            val groupId = db.groupDao().insert(StudyGroup(title = p[0], course = course))
            val students = items.map { s ->
                val f = s.split(":").map { it.trim() }
                need(f, 3, "имя:дата:оценка")
                makeStudent(0, groupId, f[0], f[1], f[2])
            }
            db.studentDao().insertAll(students)
            say("В транзакции добавлена группа id=$groupId и студентов: ${students.size}")
        }
    }

    // ---------- update ----------
    private suspend fun update(rest: String) {
        val what = rest.substringBefore(' ').lowercase()
        val p = split(rest.substringAfter(' ', ""))
        when (what) {
            "group" -> {
                need(p, 3, "update group <id>|<название>|<курс>")
                val id = toLong(p[0], "id")
                val old = db.groupDao().getById(id)
                    ?: throw NoSuchElementException("Группа с id=$id не найдена")
                require(p[1].isNotBlank()) { "Название группы не может быть пустым" }
                val course = toInt(p[2], "Курс")
                require(course in 1..6) { "Курс должен быть от 1 до 6, получено $course" }
                db.groupDao().update(old.copy(title = p[1], course = course))
                say("Группа id=$id изменена")
            }
            "student" -> {
                need(p, 4, "update student <id>|<имя>|<дд.мм.гггг>|<оценка>")
                val id = toLong(p[0], "id")
                val old = db.studentDao().getById(id)
                    ?: throw NoSuchElementException("Студент с id=$id не найден")
                db.studentDao().update(makeStudent(id, old.groupId, p[1], p[2], p[3]))
                say("Студент id=$id изменён")
            }
            else -> say("Используйте: update group ... | update student ...")
        }
    }

    // ---------- delete ----------
    private suspend fun delete(rest: String) {
        val what = rest.substringBefore(' ').lowercase()
        val id = toLong(rest.substringAfter(' ', "").trim(), "id")
        when (what) {
            "group" -> {
                val old = db.groupDao().getById(id)
                    ?: throw NoSuchElementException("Группа с id=$id не найдена")
                val count = db.studentDao().countByGroup(id)
                db.groupDao().delete(old)
                say("Группа id=$id удалена (каскадно удалено студентов: $count)")
            }
            "student" -> {
                val old = db.studentDao().getById(id)
                    ?: throw NoSuchElementException("Студент с id=$id не найден")
                db.studentDao().delete(old)
                say("Студент id=$id удалён")
            }
            else -> say("Используйте: delete group <id> | delete student <id>")
        }
    }

    // ---------- list (способ 1: getAll + filter/map/sortedBy) ----------
    private suspend fun list(rest: String) {
        val what = rest.substringBefore(' ').lowercase()
        val arg = rest.substringAfter(' ', "").trim()
        when (what) {
            "groups" -> {
                val lines = db.groupDao().getAll()
                    .sortedBy { it.title }
                    .map { "#${it.id} ${it.title}, ${it.course} курс" }
                say("Групп: ${lines.size}")
                lines.forEach { say("  $it") }
            }
            "students" -> {
                val minGrade = if (arg.isEmpty()) 0.0 else toDouble(arg, "Оценка")
                val fmt = newDateFormat()
                val lines = db.studentDao().getAll()
                    .filter { it.grade >= minGrade }
                    .sortedBy { it.name }
                    .map { describe(it, fmt) }
                say("Студентов: ${lines.size}")
                lines.forEach { say("  $it") }
            }
            else -> say("Используйте: list groups | list students [минимальнаяОценка]")
        }
    }

    // ---------- find (способ 2: @Query; namekt — версия через filter) ----------
    private suspend fun find(rest: String) {
        val what = rest.substringBefore(' ').lowercase()
        val arg = rest.substringAfter(' ', "").trim()
        val fmt = newDateFormat()
        when (what) {
            "name" -> {
                require(arg.isNotBlank()) { "Пустой поисковый запрос" }
                val r = db.studentDao().findByName(arg)
                say("Найдено через @Query: ${r.size}")
                r.forEach { say("  ${describe(it, fmt)}") }
            }
            "namekt" -> {
                require(arg.isNotBlank()) { "Пустой поисковый запрос" }
                val r = db.studentDao().getAll().filter { it.name.contains(arg, ignoreCase = true) }
                say("Найдено через filter: ${r.size}")
                r.forEach { say("  ${describe(it, fmt)}") }
            }
            "grade" -> {
                val min = toDouble(arg, "Оценка")
                val r = db.studentDao().findByMinGrade(min)
                say("Студенты с оценкой не ниже $min (по убыванию): ${r.size}")
                r.forEach { say("  ${describe(it, fmt)}") }
            }
            else -> say("Используйте: find name <текст> | find namekt <текст> | find grade <минимальнаяОценка>")
        }
    }

    // ---------- stat ----------
    private suspend fun stat() {
        val count = db.studentDao().count()
        val avg = db.studentDao().avgGrade()
        say("Студентов всего: $count, общий средний балл: ${"%.2f".format(avg ?: 0.0)}")
        db.studentDao().avgGradePerGroup().forEach {
            say("  ${it.groupTitle}: ${"%.2f".format(it.avgGrade)}")
        }
    }

    // ---------- join ----------
    private suspend fun join() {
        val rows = db.studentDao().getStudentsWithGroups()
        say("JOIN students + student_groups, строк: ${rows.size}")
        rows.forEach {
            say("  ${it.groupTitle} (${it.course} курс) — ${it.name}, балл ${it.grade}")
        }
    }

    // ---------- bench (этап 7: filter vs @Query на 1500 записях) ----------
    private suspend fun bench() {
        val n = 1500
        val repeats = 20
        val groupId = db.groupDao().insert(StudyGroup(title = "BenchGroup", course = 1))
        try {
            val now = Date()
            val students = (1..n).map {
                Student(
                    groupId = groupId,
                    name = "Bench student $it",
                    birthDate = now,
                    grade = 2.0 + (it % 31) / 10.0
                )
            }
            val tInsert = measureTimeMillis { db.studentDao().insertAll(students) }
            say("Вставлено $n записей за $tInsert мс")

            val keyword = "student 77"
            // прогрев
            db.studentDao().getAll().filter { it.name.contains(keyword, ignoreCase = true) }
            db.studentDao().findByName(keyword)

            var sizeKt = 0
            var sizeSql = 0
            val tKt = measureTimeMillis {
                repeat(repeats) {
                    sizeKt = db.studentDao().getAll().filter { it.name.contains(keyword, ignoreCase = true) }.size
                }
            }
            val tSql = measureTimeMillis {
                repeat(repeats) { sizeSql = db.studentDao().findByName(keyword).size }
            }
            say("getAll().filter { }: $tKt мс на $repeats запусков (≈${tKt / repeats.toDouble()} мс/запуск), найдено $sizeKt")
            say("@Query LIKE:         $tSql мс на $repeats запусков (≈${tSql / repeats.toDouble()} мс/запуск), найдено $sizeSql")
        } finally {
            // каскадное удаление всех тестовых студентов
            db.groupDao().delete(StudyGroup(id = groupId, title = "BenchGroup", course = 1))
            say("Тестовые данные удалены")
        }
    }

    // ------------------------------------------------------------------
    // Вспомогательные функции
    // ------------------------------------------------------------------
    private fun split(s: String): List<String> = s.split("|").map { it.trim() }

    private fun need(parts: List<String>, min: Int, usage: String) {
        require(parts.size >= min) { "Недостаточно аргументов. Формат: $usage" }
    }

    private fun toLong(s: String, field: String): Long =
        s.toLongOrNull() ?: throw IllegalArgumentException("$field должно быть числом, получено '$s'")

    private fun toInt(s: String, field: String): Int =
        s.toIntOrNull() ?: throw IllegalArgumentException("$field должно быть числом, получено '$s'")

    private fun toDouble(s: String, field: String): Double =
        s.replace(',', '.').toDoubleOrNull()
            ?: throw IllegalArgumentException("$field должно быть числом, получено '$s'")

    private fun toDate(s: String): Date =
        try {
            newDateFormat().parse(s) ?: throw IllegalArgumentException("Неверная дата '$s'")
        } catch (e: ParseException) {
            throw IllegalArgumentException("Неверная дата '$s', ожидается дд.мм.гггг")
        }

    private fun makeStudent(id: Long, groupId: Long, name: String, birth: String, gradeStr: String): Student {
        require(name.isNotBlank()) { "Имя студента не может быть пустым" }
        val date = toDate(birth)
        val grade = toDouble(gradeStr, "Оценка")
        require(grade in 0.0..5.0) { "Оценка должна быть от 0 до 5, получено $grade" }
        return Student(id = id, groupId = groupId, name = name, birthDate = date, grade = grade)
    }

    private fun describe(s: Student, fmt: SimpleDateFormat): String =
        "#${s.id} ${s.name}, группа=${s.groupId}, родился ${fmt.format(s.birthDate)}, балл ${s.grade}"
}
