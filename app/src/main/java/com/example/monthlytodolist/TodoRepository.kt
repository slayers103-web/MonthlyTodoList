package com.example.monthlytodolist

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.YearMonth
import java.util.UUID

/**
 * Monthly data model:
 * - Past months are snapshots and are read-only in the UI.
 * - Current/future months are editable.
 * - When a month is first created, it inherits the previous month's final list.
 * - A task added in the current month therefore appears in the next month,
 *   while it never appears in already-finished months.
 */
class TodoRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun prepareMonths(today: YearMonth = YearMonth.now()) {
        val records = getMonthRecords().toMutableMap()
        val legacyTodos = load<List<LegacyTodo>>(KEY_TODOS) ?: emptyList()
        val lastActive = prefs.getString(KEY_LAST_ACTIVE_MONTH, null)?.let { runCatching { YearMonth.parse(it) }.getOrNull() }

        if (records.isEmpty()) {
            val seed = legacyTodos.map {
                TodoItem(
                    id = it.id.ifBlank { UUID.randomUUID().toString() },
                    text = it.text.trim(),
                    createdMonth = today.toString()
                )
            }.filter { it.text.isNotBlank() }

            // Migrate the previous v2 format without throwing away its historical
            // completion information. The old format did not know when an item was
            // created, so legacy items are treated as existing for months that already
            // had completion data; new items created after this upgrade use createdMonth.
            val legacyCompletions = load<Map<String, Boolean>>(KEY_COMPLETIONS) ?: emptyMap()
            legacyCompletions.keys.mapNotNull { key -> key.substringBefore('|').let { runCatching { YearMonth.parse(it) }.getOrNull() } }
                .distinct()
                .forEach { legacyMonth ->
                    records[legacyMonth.toString()] = MonthRecord(
                        items = seed.map { it.copy(createdMonth = legacyMonth.toString()) },
                        completedIds = legacyCompletions
                            .filterKeys { it.startsWith("$legacyMonth|") }
                            .filterValues { it }
                            .keys
                            .map { it.substringAfter('|') }
                            .toSet()
                    )
                }
            records[today.toString()] = records[today.toString()] ?: MonthRecord(seed, emptySet())
        } else if (!records.containsKey(today.toString())) {
            val start = lastActive ?: records.keys.mapNotNull { runCatching { YearMonth.parse(it) }.getOrNull() }.maxOrNull()
            var previous = start?.let { records[it.toString()] }
            if (start != null && start < today) {
                var cursor = start.plusMonths(1)
                while (cursor <= today) {
                    val copied = MonthRecord(previous?.items.orEmpty(), emptySet())
                    records[cursor.toString()] = copied
                    previous = copied
                    cursor = cursor.plusMonths(1)
                }
            } else {
                records[today.toString()] = MonthRecord(previous?.items.orEmpty(), emptySet())
            }
        }

        // Ensure every month between the earliest known month and today has a snapshot.
        val earliest = records.keys.mapNotNull { runCatching { YearMonth.parse(it) }.getOrNull() }.minOrNull()
        if (earliest != null) {
            var cursor = earliest
            var previous = records[cursor.toString()]
            while (cursor < today) {
                val next = cursor.plusMonths(1)
                if (!records.containsKey(next.toString())) {
                    val copied = MonthRecord(previous?.items.orEmpty(), emptySet())
                    records[next.toString()] = copied
                }
                previous = records[next.toString()]
                cursor = next
            }
        }

        saveMonthRecords(records)
        prefs.edit().putString(KEY_LAST_ACTIVE_MONTH, today.toString()).apply()
    }

    fun getTodos(): List<TodoItem> = getMonthItems(YearMonth.now())

    fun getMonthItems(month: YearMonth): List<TodoItem> {
        ensureMonth(month)
        return getMonthRecords()[month.toString()]?.items.orEmpty()
    }

    fun addTodo(month: YearMonth, text: String): TodoItem {
        require(isEditableMonth(month)) { "지난 달의 데이터는 수정할 수 없습니다." }
        val item = TodoItem(UUID.randomUUID().toString(), text.trim(), month.toString())
        updateMonth(month) { it.copy(items = it.items + item) }
        propagateCurrentMonth(month)
        return item
    }

    fun updateTodo(month: YearMonth, id: String, text: String) {
        require(isEditableMonth(month)) { "지난 달의 데이터는 수정할 수 없습니다." }
        updateMonth(month) { record -> record.copy(items = record.items.map { if (it.id == id) it.copy(text = text.trim()) else it }) }
        propagateCurrentMonth(month)
    }

    fun deleteTodo(month: YearMonth, id: String) {
        require(isEditableMonth(month)) { "지난 달의 데이터는 수정할 수 없습니다." }
        updateMonth(month) { record ->
            record.copy(items = record.items.filterNot { it.id == id }, completedIds = record.completedIds - id)
        }
        propagateCurrentMonth(month)
    }

    fun isDone(month: YearMonth, todoId: String): Boolean =
        getMonthRecords()[month.toString()]?.completedIds?.contains(todoId) == true

    fun setDone(month: YearMonth, todoId: String, done: Boolean) {
        require(isEditableMonth(month)) { "지난 달의 데이터는 수정할 수 없습니다." }
        updateMonth(month) { record ->
            record.copy(completedIds = if (done) record.completedIds + todoId else record.completedIds - todoId)
        }
    }

    fun isEditableMonth(month: YearMonth, today: YearMonth = YearMonth.now()): Boolean = month >= today

    fun buildBackup(): String = gson.toJson(BackupDataV2(months = getMonthRecords()))

    fun restoreBackup(json: String): Result<Unit> = runCatching {
        val backup = gson.fromJson(json, BackupDataV2::class.java)
            ?: error("백업 데이터가 비어 있습니다.")
        require(backup.version == 2) { "지원하지 않는 백업 버전입니다." }
        backup.months.values.forEach { record ->
            require(record.items.all { it.id.isNotBlank() && it.text.isNotBlank() }) {
                "백업 데이터의 할 일이 올바르지 않습니다."
            }
        }
        saveMonthRecords(backup.months)
        prepareMonths()
    }

    private fun propagateCurrentMonth(month: YearMonth) {
        val today = YearMonth.now()
        if (month != today) return
        val current = getMonthRecords()[month.toString()] ?: return
        val records = getMonthRecords().toMutableMap()
        records.keys.mapNotNull { runCatching { YearMonth.parse(it) }.getOrNull() }
            .filter { it > month }
            .forEach { future ->
                val old = records[future.toString()] ?: return@forEach
                // Carry the current month's final task list forward while preserving
                // the future month's own completion state.
                val validCompleted = old.completedIds.intersect(current.items.map { it.id }.toSet())
                records[future.toString()] = old.copy(items = current.items, completedIds = validCompleted)
            }
        saveMonthRecords(records)
    }

    private fun ensureMonth(month: YearMonth) {
        val records = getMonthRecords().toMutableMap()
        if (records.containsKey(month.toString())) return
        val previous = records.keys.mapNotNull { runCatching { YearMonth.parse(it) }.getOrNull() }
            .filter { it < month }
            .maxOrNull()
            ?.let { records[it.toString()] }
        records[month.toString()] = MonthRecord(previous?.items.orEmpty(), emptySet())
        saveMonthRecords(records)
    }

    private fun updateMonth(month: YearMonth, transform: (MonthRecord) -> MonthRecord) {
        ensureMonth(month)
        val records = getMonthRecords().toMutableMap()
        val key = month.toString()
        records[key] = transform(records[key] ?: MonthRecord())
        saveMonthRecords(records)
    }

    private fun getMonthRecords(): Map<String, MonthRecord> =
        load<Map<String, MonthRecord>>(KEY_MONTHS) ?: emptyMap()

    private fun saveMonthRecords(values: Map<String, MonthRecord>) {
        prefs.edit().putString(KEY_MONTHS, gson.toJson(values)).apply()
    }

    private inline fun <reified T> load(key: String): T? {
        val json = prefs.getString(key, null) ?: return null
        return runCatching { gson.fromJson<T>(json, object : TypeToken<T>() {}.type) }.getOrNull()
    }

    companion object {
        const val PREFS = "monthly_todo_store"
        const val KEY_TODOS = "todos"
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
    val completedIds: Set<String> = emptySet()
)

data class BackupDataV2(
    val version: Int = 2,
    val months: Map<String, MonthRecord> = emptyMap()
)

data class LegacyTodo(
    val id: String = "",
    val text: String = ""
)
