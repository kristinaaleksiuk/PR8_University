# Room Lab — практическая работа (вариант 2: Университет)

Kotlin + Room 2.6.1 + KSP. «Консоль» — Logcat (тег `ROOM_LAB`), вывод дублируется на экране.

## Как открыть
1. Распаковать архив.
2. Android Studio → File → Open → папка `RoomLab2`.
3. Дождаться Gradle Sync (нужен интернет; Gradle 8.9 скачается по `gradle-wrapper.properties`).
4. Выбрать эмулятор (API 24+) и нажать Run.
5. В приложении нажать **«Запустить сценарий»**.

## Где смотреть результат
- **Logcat**: фильтр `tag:ROOM_LAB`.
- **Database Inspector**: View → Tool Windows → App Inspection → Database Inspector → `university.db`.
  Скриншот «до» — сразу после установки (таблицы пусты), «после» — после сценария (нажмите Refresh).
  В конце сценария `bench` вставляет 1500 тестовых студентов и каскадно удаляет их вместе с группой BenchGroup.

## Структура
| Файл | Назначение |
| --- | --- |
| `data/Entities.kt` | `StudyGroup` (таблица `student_groups`), `Student` (@ForeignKey CASCADE + @Index), POJO для JOIN/агрегата |
| `data/Converters.kt` | TypeConverter `Date`↔`Long` (дата рождения) |
| `data/BaseDao.kt` | Способ 1: `insert`, `insertAll`, `update`, `delete` |
| `data/GroupDao.kt`, `data/StudentDao.kt` | DAO-наследники + `@Query` (способ 2), в т.ч. `Flow` |
| `data/AppDatabase.kt` | База `university.db`, экспорт схемы, singleton |
| `console/Console.kt` | Команды `add addmany update delete list find stat join` (+ `bench`) |
| `console/Script.kt` | Сценарий проверки, включая ошибочные команды |

Таблица названа `student_groups`, а класс `StudyGroup`: слово `GROUPS` зарезервировано в SQLite.

## Соответствие требованиям
- Две таблицы, связь 1:N, `@ForeignKey` — `student_groups` / `students`.
- TypeConverter — `Date` (birthDate).
- `BaseDao<T>` + `GroupDao`, `StudentDao` — способ 1.
- `@Query` с параметрами: `findByName`, `findByMinGrade` (особый запрос варианта: оценка не ниже заданной, по убыванию), `avgGrade` и `avgGradePerGroup` (средний балл по группе), `getStudentsWithGroups` (JOIN), `observeCount` (Flow).
- Транзакция `db.withTransaction` — команда `addmany` (при ошибке откатывается и группа, и студенты).
- Singleton — `AppDatabase.get(context)`.

## Формат команд
```
add group <название>|<курс>
add student <groupId>|<имя>|<дд.мм.гггг>|<оценка>
addmany <группа>|<курс>|<имя:дата:оценка>,<имя:дата:оценка>
update group <id>|<название>|<курс>
update student <id>|<имя>|<дд.мм.гггг>|<оценка>
delete group|student <id>
list groups | list students [минимальнаяОценка]
find name <текст> | find namekt <текст> | find grade <минимальнаяОценка>
stat | join | bench
```

## Этап 7: сравнение способов
Команда `bench` вставляет 1500 студентов и 20 раз ищет по имени двумя способами:
- `getAll().filter { ... }` — все записи выгружаются в память и фильтруются в Kotlin;
- `@Query ... LIKE` — фильтрация внутри SQLite.

Подставьте свои числа:

> filter = ___ мс / 20 запусков, @Query = ___ мс / 20 запусков.
> Вывод: на больших таблицах @Query быстрее и экономнее по памяти, так как отбор делает СУБД,
> а `filter` создаёт в памяти все 1500+ объектов. На малых объёмах разница незаметна.

Примечание: `LIKE` в SQLite нечувствителен к регистру только для латиницы, поэтому в сценарии `find name Иван` написано
с заглавной буквы; в `bench` ключевое слово латинское. Версия `namekt` регистр кириллицы игнорирует.
