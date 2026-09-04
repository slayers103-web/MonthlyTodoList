package com.example.monthlytodolist

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.YearMonth
import java.util.UUID

/**
 * Stores an independent snapshot for each month.
 *
 * Rules:
 * - Past months are immutable snapshots.
 * - The current month can be edited.
 * - When a new month begins, its initial list is copied from the previous
 *   month's final list. The previous month is never modified afterwards.
 * - A task created in the current month can therefore appear next month,
 *   but can never be retroactively inserted into an older month.
 */
class TodoRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()

    /** Advances monthly snapshots only when the real calendar month changes.
     *  Future months are never synchronized in real time. A future month is
     *  finalized only when it becomes the current month, at which point the
     *  previous month's final list is merged into it. Existing pre-added items
     *  in that future month are preserved.
     */
    fun prepareMonths(today: YearMonth = YearMonth.now()) {
        val records = getMonthRecords().toMutableMap()
        val lastActive = prefs.getString(KEY_LAST_ACTIVE_MONTH, null)
            ?.let { runCatching { YearMonth.parse(it) }.getOrNull() }

        if (records.isEmpty()) {
            migrateLegacy(records, today)
        }

        if (records.isEmpty()) {
            records[today.toString()] = MonthRecord()
        }

        if (lastActive != null && lastActive < today) {
            // The app was last opened in an earlier month. Advance one month
            // at a time so each month's final snapshot becomes the source for
            // the next month. Existing future/pre-added items are merged, not
            // overwritten.
            var cursor = lastActive.plusMonths(1)
            var previous = records[lastActive.toString()] ?: MonthRecord()
            while (cursor <= today) {
                val key = cursor.toString()
                val target = records[key] ?: MonthRecord()
                val merged = mergeInheritedItems(previous, target)
                records[key] = merged.copy(completedIds = target.completedIds)
                previous = records[key]!!
                cursor = cursor.plusMonths(1)
            }
        } else if (lastActive == null) {
            // First run after migration/upgrade: initialize the current month
            // from the latest past snapshot, while preserving any pre-added
            // current-month items.
            val previousMonth = records.keys
                .mapNotNull { runCatching { YearMonth.parse(it) }.getOrNull() }
                .filter { it < today }
                .maxOrNull()
            if (!records.containsKey(today.toString())) {
                records[today.toString()] = MonthRecord(
                    items = previousMonth?.let { records[it.toString()]?.items }.orEmpty(),
                    completedIds = emptySet()
                )
            } else if (previousMonth != null) {
                val target = records[today.toString()]!!
                records[today.toString()] = mergeInheritedItems(
                    records[previousMonth.toString()] ?: MonthRecord(), target
                )
            }
        }

        saveMonthRecords(records)
        prefs.edit().putString(KEY_LAST_ACTIVE_MONTH, today.toString()).apply()
    }

    private fun mergeInheritedItems(previous: MonthRecord, target: MonthRecord): MonthRecord {
        val existingIds = target.items.map { it.id }.toSet()
        val inherited = previous.items.filter {
            it.id !in existingIds && it.id !in target.suppressedIds
        }
        return if (inherited.isEmpty()) target
        else target.copy(items = target.items + inherited)
    }

    fun getTodos(): List<TodoItem> = getMonthItems(YearMonth.now())

    fun getMonthItems(month: YearMonth): List<TodoItem> {
        // Reading a month must never alter future data. Inheritance happens
        // only in prepareMonths() when a month actually becomes current.
        return getMonthRecords()[month.toString()]?.items.orEmpty()
    }

    fun addTodo(month: YearMonth, text: String): TodoItem {
        require(isEditableMonth(month)) { "지난 달의 데이터는 수정할 수 없습니다." }
        val clean = text.trim()
        require(clean.isNotBlank()) { "체크 항목을 입력해 주세요." }
        val item = TodoItem(UUID.randomUUID().toString(), clean, month.toString())
        updateMonth(month) { it.copy(items = it.items + item) }
        return item
    }

    fun updateTodo(month: YearMonth, id: String, text: String, allowHistoricalEdit: Boolean = false) {
        require(isEditableMonth(month) || allowHistoricalEdit) { "지난 달의 데이터는 수정할 수 없습니다." }
        val clean = text.trim()
        require(clean.isNotBlank()) { "체크 항목을 입력해 주세요." }
        updateMonth(month) { record ->
            record.copy(items = record.items.map { if (it.id == id) it.copy(text = clean) else it })
        }
    }

    fun deleteTodo(month: YearMonth, id: String, allowHistoricalEdit: Boolean = false) {
        require(isEditableMonth(month) || allowHistoricalEdit) { "지난 달의 데이터는 수정할 수 없습니다." }
        updateMonth(month) { record ->
            record.copy(
                items = record.items.filterNot { it.id == id },
                completedIds = record.completedIds - id,
                suppressedIds = record.suppressedIds + id
            )
        }
    }

    fun isDone(month: YearMonth, todoId: String): Boolean =
        getMonthRecords()[month.toString()]?.completedIds?.contains(todoId) == true

    fun setDone(month: YearMonth, todoId: String, done: Boolean, allowHistoricalEdit: Boolean = false) {
        require(isEditableMonth(month) || allowHistoricalEdit) { "지난 달의 데이터는 수정할 수 없습니다." }
        updateMonth(month) { record ->
            record.copy(
                completedIds = if (done) record.completedIds + todoId
                else record.completedIds - todoId
            )
        }
    }

    fun isEditableMonth(month: YearMonth, today: YearMonth = YearMonth.now()): Boolean = month >= today

    fun buildBackup(): String = gson.toJson(BackupDataV2(months = getMonthRecords()))

    fun restoreBackup(json: String): Result<Unit> = runCatching {
        val backup = gson.fromJson(json, BackupDataV2::class.java)
            ?: error("백업 데이터가 비어 있습니다.")
        require(backup.version == 2) { "지원하지 않는 백업 버전입니다." }
        backup.months.forEach { (monthKey, record) ->
            require(runCatching { YearMonth.parse(monthKey) }.isSuccess) {
                "백업 데이터의 월 정보가 올바르지 않습니다."
            }
            require(record.items.all { it.id.isNotBlank() && it.text.isNotBlank() }) {
                "백업 데이터의 체크 항목이 올바르지 않습니다."
            }
        }
        saveMonthRecords(backup.months)
        prefs.edit().putString(KEY_LAST_ACTIVE_MONTH, YearMonth.now().toString()).apply()
        prepareMonths()
    }

    /** Converts the old v2 global todo/completion storage into monthly snapshots. */
    private fun migrateLegacy(records: MutableMap<String, MonthRecord>, today: YearMonth) {
        val legacyTodos = load<List<LegacyTodo>>(KEY_TODOS).orEmpty()
        if (legacyTodos.isEmpty()) return

        val legacyItems = legacyTodos.map {
            TodoItem(
                id = it.id.ifBlank { UUID.randomUUID().toString() },
                text = it.text.trim(),
                createdMonth = today.toString()
            )
        }.filter { it.text.isNotBlank() }

        val legacyCompletions = load<Map<String, Boolean>>("completions").orEmpty()
        val legacyMonths = legacyCompletions.keys.mapNotNull { key ->
            key.substringBefore('|').let { runCatching { YearMonth.parse(it) }.getOrNull() }
        }.distinct().sorted()

        // Old storage had no creation-month information. For months that have
        // historical completion data, preserve the old list as a snapshot.
        legacyMonths.forEach { month ->
            val completed = legacyCompletions
                .filterKeys { it.startsWith("$month|") }
                .filterValues { it }
                .keys
                .map { it.substringAfter('|') }
                .toSet()
            records[month.toString()] = MonthRecord(
                items = legacyItems.map { it.copy(createdMonth = month.toString()) },
                completedIds = completed
            )
        }

        if (!records.containsKey(today.toString())) {
            val previous = records.keys
                .mapNotNull { runCatching { YearMonth.parse(it) }.getOrNull() }
                .filter { it < today }
                .maxOrNull()
            records[today.toString()] = MonthRecord(
                items = previous?.let { records[it.toString()]?.items } ?: legacyItems,
                completedIds = emptySet()
            )
        }
    }


    private fun updateMonth(month: YearMonth, transform: (MonthRecord) -> MonthRecord) {
        val records = getMonthRecords().toMutableMap()
        val key = month.toString()
        val current = records[key] ?: MonthRecord()
        records[key] = transform(current)
        saveMonthRecords(records)
    }

    private fun getMonthRecords(): Map<String, MonthRecord> =
        load<Map<String, MonthRecord>>(KEY_MONTHS).orEmpty()

    private fun saveMonthRecords(values: Map<String, MonthRecord>) {
        prefs.edit().putString(KEY_MONTHS, gson.toJson(values)).apply()
    }

    private inline fun <reified T> load(key: String): T? {
        val json = prefs.getString(key, null) ?: return null
        return runCatching {
            gson.fromJson<T>(json, object : TypeToken<T>() {}.type)
        }.getOrNull()
    }

    companion object {
        const val PREFS = "monthly_todo_store"
        const val KEY_TODOS = "todos"
        const val KEY_COMPLETIONS = "completions"
        private const val KEY_MONTHS = "month_records"
        private const val KEY_LAST_ACTIVE_MONTH = "last_active_month"
    }
}

data class TodoItem(
    val id: String,
    val text: String,
    val createdMonth: String = YearMonth.now().toString()
)

data class MonthRecord(
    val items: List<TodoItem> = emptyList(),
    val completedIds: Set<String> = emptySet(),
    /** IDs intentionally removed from this month's inherited list. */
    val suppressedIds: Set<String> = emptySet()
)

data class BackupDataV2(
    val version: Int = 2,
    val months: Map<String, MonthRecord> = emptyMap()
)

data class LegacyTodo(
    val id: String = "",
    val text: String = ""
)
