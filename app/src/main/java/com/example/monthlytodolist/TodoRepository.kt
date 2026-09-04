package com.example.monthlytodolist

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.time.YearMonth
import java.util.UUID

/** Monthly snapshot repository. Future months are never synchronized in real time. */
class TodoRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun prepareMonths(today: YearMonth = YearMonth.now()) {
        val records = getMonthRecords().toMutableMap()
        val lastActive = prefs.getString(KEY_LAST_ACTIVE_MONTH, null)
            ?.let { runCatching { YearMonth.parse(it) }.getOrNull() }

        if (records.isEmpty()) migrateLegacy(records, today)
        if (records.isEmpty()) records[today.toString()] = MonthRecord()

        if (lastActive != null && lastActive < today) {
            var cursor = lastActive.plusMonths(1)
            var previous = records[lastActive.toString()] ?: MonthRecord()
            while (cursor <= today) {
                val key = cursor.toString()
                val target = records[key] ?: MonthRecord()
                records[key] = mergeForNewMonth(previous, target)
                previous = records[key]!!
                cursor = cursor.plusMonths(1)
            }
        } else if (lastActive == null) {
            val previousMonth = records.keys.mapNotNull { runCatching { YearMonth.parse(it) }.getOrNull() }
                .filter { it < today }.maxOrNull()
            val target = records[today.toString()]
            records[today.toString()] = if (previousMonth != null) {
                mergeForNewMonth(records[previousMonth.toString()] ?: MonthRecord(), target ?: MonthRecord())
            } else target ?: MonthRecord()
        }

        saveMonthRecords(records)
        prefs.edit().putString(KEY_LAST_ACTIVE_MONTH, today.toString()).apply()
    }

    /**
     * When a month actually begins, inherit the previous month's final items.
     * Order: previous checked items, previous unchecked items, then items that
     * were pre-created in the target month. Target items are never overwritten.
     * In the new month all inherited items start unchecked.
     */
    private fun mergeForNewMonth(previous: MonthRecord, target: MonthRecord): MonthRecord {
        val existingIds = target.items.map { it.id }.toSet()
        val checkedPrevious = previous.items.filter { it.id in previous.completedIds && it.id !in existingIds && it.id !in target.suppressedIds }
        val uncheckedPrevious = previous.items.filter { it.id !in previous.completedIds && it.id !in existingIds && it.id !in target.suppressedIds }
        val inherited = checkedPrevious + uncheckedPrevious
        val inheritedIds = inherited.map { it.id }.toSet()
        val targetNew = target.items.filter { it.id !in inheritedIds }
        return target.copy(
            items = inherited + targetNew,
            completedIds = target.completedIds.intersect(targetNew.map { it.id }.toSet())
        )
    }

    fun getMonthItems(month: YearMonth): List<TodoItem> = getMonthRecords()[month.toString()]?.items.orEmpty()

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
        updateMonth(month) { record -> record.copy(items = record.items.map { if (it.id == id) it.copy(text = clean) else it }) }
    }

    fun deleteTodo(month: YearMonth, id: String, allowHistoricalEdit: Boolean = false) {
        require(isEditableMonth(month) || allowHistoricalEdit) { "지난 달의 데이터는 수정할 수 없습니다." }
        updateMonth(month) { record ->
            record.copy(items = record.items.filterNot { it.id == id }, completedIds = record.completedIds - id, suppressedIds = record.suppressedIds + id)
        }
    }

    fun setDone(month: YearMonth, todoId: String, done: Boolean, allowHistoricalEdit: Boolean = false) {
        require(isEditableMonth(month) || allowHistoricalEdit) { "지난 달의 데이터는 수정할 수 없습니다." }
        updateMonth(month) { record ->
            val item = record.items.firstOrNull { it.id == todoId } ?: return@updateMonth record
            val newCompletedIds = if (done) {
                record.completedIds + todoId
            } else {
                record.completedIds - todoId
            }

            // Checking an item puts it at the bottom of the completed section
            // (completion order). Unchecking puts it at the top of the pending section.
            val remaining = record.items.filterNot { it.id == todoId }
            val pending = remaining.filter { it.id !in newCompletedIds }.toMutableList()
            val completed = remaining.filter { it.id in newCompletedIds }.toMutableList()

            if (done) {
                completed.add(item)
            } else {
                pending.add(0, item)
            }

            record.copy(
                items = pending + completed,
                completedIds = newCompletedIds
            )
        }
    }

    fun reorderTodo(month: YearMonth, todoId: String, targetIndexInSection: Int, done: Boolean, allowHistoricalEdit: Boolean = false) {
        require(isEditableMonth(month) || allowHistoricalEdit) { "지난 달의 데이터는 수정할 수 없습니다." }
        updateMonth(month) { record ->
            val section = record.items.filter { (it.id in record.completedIds) == done }.toMutableList()
            val moving = section.firstOrNull { it.id == todoId } ?: return@updateMonth record
            val oldIndex = section.indexOfFirst { it.id == todoId }
            section.removeAt(oldIndex)
            val newIndex = targetIndexInSection.coerceIn(0, section.size)
            section.add(newIndex, moving)
            val other = record.items.filter { (it.id in record.completedIds) != done }
            val merged = if (!done) section + other else other + section
            record.copy(items = merged)
        }
    }

    fun isDone(month: YearMonth, todoId: String): Boolean = getMonthRecords()[month.toString()]?.completedIds?.contains(todoId) == true
    fun isEditableMonth(month: YearMonth, today: YearMonth = YearMonth.now()): Boolean = month >= today

    /** Stable backup schema. Keep version 2 for forward compatibility. */
    fun buildBackup(): String = gson.toJson(BackupDataV2(months = getMonthRecords()))

    /** Accepts the current v2 schema and legacy backups, preserving compatibility across app updates. */
    fun restoreBackup(json: String): Result<Unit> = runCatching {
        val root = JsonParser.parseString(json)
        val obj = root.asJsonObject
        val version = obj.get("version")?.asInt ?: 1
        val monthsElement = obj.get("months")
        val months: Map<String, MonthRecord> = if (monthsElement != null && monthsElement.isJsonObject) {
            gson.fromJson(monthsElement, object : TypeToken<Map<String, MonthRecord>>() {}.type) ?: emptyMap()
        } else {
            migrateBackupV1(obj)
        }
        require(version in 1..2) { "지원하지 않는 백업 버전입니다." }
        months.forEach { (monthKey, record) ->
            require(runCatching { YearMonth.parse(monthKey) }.isSuccess) { "백업 데이터의 월 정보가 올바르지 않습니다." }
            require(record.items.all { it.id.isNotBlank() && it.text.isNotBlank() }) { "백업 데이터의 체크 항목이 올바르지 않습니다." }
        }
        saveMonthRecords(months)
        prefs.edit().putString(KEY_LAST_ACTIVE_MONTH, YearMonth.now().toString()).apply()
        prepareMonths()
    }

    private fun migrateBackupV1(obj: JsonObject): Map<String, MonthRecord> {
        val todosElement = obj.get("todos") ?: return emptyMap()
        val legacyTodos: List<LegacyTodo> = gson.fromJson(todosElement, object : TypeToken<List<LegacyTodo>>() {}.type) ?: emptyList()
        val completions = obj.get("completions")?.let { gson.fromJson<Map<String, Boolean>>(it, object : TypeToken<Map<String, Boolean>>() {}.type) }.orEmpty()
        val result = mutableMapOf<String, MonthRecord>()
        val months = completions.keys.mapNotNull { runCatching { YearMonth.parse(it.substringBefore('|')) }.getOrNull() }.distinct()
        val items = legacyTodos.map { TodoItem(it.id.ifBlank { UUID.randomUUID().toString() }, it.text.trim(), YearMonth.now().toString()) }.filter { it.text.isNotBlank() }
        months.forEach { month ->
            val done = completions.filterKeys { it.startsWith("$month|") && completions[it] == true }.keys.map { it.substringAfter('|') }.toSet()
            result[month.toString()] = MonthRecord(items = items.map { it.copy(createdMonth = month.toString()) }, completedIds = done)
        }
        if (result.isEmpty() && items.isNotEmpty()) result[YearMonth.now().toString()] = MonthRecord(items)
        return result
    }

    private fun migrateLegacy(records: MutableMap<String, MonthRecord>, today: YearMonth) {
        val legacyTodos = load<List<LegacyTodo>>(KEY_TODOS).orEmpty()
        if (legacyTodos.isEmpty()) return
        val items = legacyTodos.map { TodoItem(it.id.ifBlank { UUID.randomUUID().toString() }, it.text.trim(), today.toString()) }.filter { it.text.isNotBlank() }
        val completions = load<Map<String, Boolean>>(KEY_COMPLETIONS).orEmpty()
        val months = completions.keys.mapNotNull { runCatching { YearMonth.parse(it.substringBefore('|')) }.getOrNull() }.distinct().sorted()
        months.forEach { month ->
            val done = completions.filterKeys { it.startsWith("$month|") && completions[it] == true }.keys.map { it.substringAfter('|') }.toSet()
            records[month.toString()] = MonthRecord(items.map { it.copy(createdMonth = month.toString()) }, done)
        }
        if (!records.containsKey(today.toString())) {
            val previous = records.keys.mapNotNull { runCatching { YearMonth.parse(it) }.getOrNull() }.filter { it < today }.maxOrNull()
            records[today.toString()] = if (previous != null) mergeForNewMonth(records[previous.toString()] ?: MonthRecord(), MonthRecord()) else MonthRecord(items)
        }
    }

    private fun updateMonth(month: YearMonth, transform: (MonthRecord) -> MonthRecord) {
        val records = getMonthRecords().toMutableMap()
        records[month.toString()] = transform(records[month.toString()] ?: MonthRecord())
        saveMonthRecords(records)
    }

    private fun getMonthRecords(): Map<String, MonthRecord> = load<Map<String, MonthRecord>>(KEY_MONTHS).orEmpty()
    private fun saveMonthRecords(values: Map<String, MonthRecord>) { prefs.edit().putString(KEY_MONTHS, gson.toJson(values)).apply() }
    private inline fun <reified T> load(key: String): T? {
        val json = prefs.getString(key, null) ?: return null
        return runCatching { gson.fromJson<T>(json, object : TypeToken<T>() {}.type) }.getOrNull()
    }

    companion object {
        const val PREFS = "monthly_todo_store"
        const val KEY_TODOS = "todos"
        const val KEY_COMPLETIONS = "completions"
        private const val KEY_MONTHS = "month_records"
        private const val KEY_LAST_ACTIVE_MONTH = "last_active_month"
    }
}

data class TodoItem(val id: String, val text: String, val createdMonth: String = YearMonth.now().toString())
data class MonthRecord(val items: List<TodoItem> = emptyList(), val completedIds: Set<String> = emptySet(), val suppressedIds: Set<String> = emptySet())
data class BackupDataV2(val version: Int = 2, val months: Map<String, MonthRecord> = emptyMap())
data class LegacyTodo(val id: String = "", val text: String = "")
