package com.example.monthlytodolist

import android.content.Context
import com.google.gson.Gson
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

    private fun mergeForNewMonth(previous: MonthRecord, target: MonthRecord): MonthRecord {
        val existingIds = target.items.map { it.id }.toSet()
        val inherited = previous.items
            .filter { it.id !in existingIds && it.id !in target.suppressedIds }
            .map { it.copy(number1 = it.number1, number2 = null) }
        val inheritedIds = inherited.map { it.id }.toSet()
        val targetNew = target.items.filter { it.id !in inheritedIds }
        return target.copy(
            items = inherited + targetNew,
            completedIds = target.completedIds.intersect(targetNew.map { it.id }.toSet())
        )
    }

    fun getMonthItems(month: YearMonth): List<TodoItem> = getMonthRecords()[month.toString()]?.items.orEmpty()

    fun addTodo(month: YearMonth, text: String, priority: Int? = null, number1: Int? = null): TodoItem {
        require(isEditableMonth(month)) { "지난 달의 데이터는 수정할 수 없습니다." }
        val clean = text.trim()
        require(clean.isNotBlank()) { "체크 항목을 입력해 주세요." }
        val item = TodoItem(UUID.randomUUID().toString(), clean, month.toString(), priority = priority, number1 = number1)
        updateMonth(month) { it.copy(items = orderItemsForSection(it.items + item, it.completedIds, item.id, uncheckNewToTop = false)) }
        return item
    }

    fun updateTodo(month: YearMonth, id: String, text: String, priority: Int?, allowHistoricalEdit: Boolean = false) {
        require(isEditableMonth(month) || allowHistoricalEdit) { "지난 달의 데이터는 수정할 수 없습니다." }
        val clean = text.trim()
        require(clean.isNotBlank()) { "체크 항목을 입력해 주세요." }
        updateMonth(month) { record ->
            val updated = record.items.map { if (it.id == id) it.copy(text = clean, priority = priority) else it }
            record.copy(items = orderItemsForSection(updated, record.completedIds, id, uncheckNewToTop = false))
        }
    }

    fun updateNumber(month: YearMonth, id: String, numberSlot: Int, number: Int?, allowHistoricalEdit: Boolean = false) {
        require(isEditableMonth(month) || allowHistoricalEdit) { "지난 달의 데이터는 수정할 수 없습니다." }
        updateMonth(month) { record -> record.copy(items = record.items.map { if (it.id == id) if (numberSlot == 1) it.copy(number1 = number) else it.copy(number2 = number) else it }) }
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
            val newCompletedIds = if (done) record.completedIds + todoId else record.completedIds - todoId
            val remaining = record.items.filterNot { it.id == todoId }
            val pending = remaining.filter { it.id !in newCompletedIds }.toMutableList()
            val completed = remaining.filter { it.id in newCompletedIds }.toMutableList()
            if (done) completed.add(item) else pending.add(0, item)
            record.copy(
                items = orderItemsForSections(pending, completed, recentlyMovedId = if (done) todoId else null, uncheckNewToTop = !done),
                completedIds = newCompletedIds
            )
        }
    }

    fun reorderTodo(month: YearMonth, todoId: String, targetIndexInSection: Int, done: Boolean, allowHistoricalEdit: Boolean = false) {
        require(isEditableMonth(month) || allowHistoricalEdit) { "지난 달의 데이터는 수정할 수 없습니다." }
        updateMonth(month) { record ->
            val section = record.items.filter { (it.id in record.completedIds) == done }.toMutableList()
            val moving = section.firstOrNull { it.id == todoId } ?: return@updateMonth record
            section.removeAt(section.indexOfFirst { it.id == todoId })
            val newIndex = targetIndexInSection.coerceIn(0, section.size)
            section.add(newIndex, moving)
            val ordered = orderItemsForSection(section, record.completedIds, moving.id, uncheckNewToTop = false)
            val other = record.items.filter { (it.id in record.completedIds) != done }
            record.copy(items = if (!done) ordered + other else other + ordered)
        }
    }

    fun isDone(month: YearMonth, todoId: String): Boolean = getMonthRecords()[month.toString()]?.completedIds?.contains(todoId) == true
    fun isEditableMonth(month: YearMonth, today: YearMonth = YearMonth.now()): Boolean = month >= today

    fun getFontSize(): Float = prefs.getFloat(KEY_FONT_SIZE, 16f)
    fun setFontSize(size: Float) { prefs.edit().putFloat(KEY_FONT_SIZE, size.coerceIn(10f, 30f)).apply() }

    private fun priorityKey(item: TodoItem): Int = item.priority ?: Int.MAX_VALUE

    private fun orderItemsForSection(
        items: List<TodoItem>,
        completedIds: Set<String>,
        preferredId: String? = null,
        uncheckNewToTop: Boolean = false
    ): List<TodoItem> {
        return items.withIndex()
            .sortedWith(compareBy<IndexedValue<TodoItem>> { priorityKey(it.value) }.thenBy { if (it.value.priority == null && uncheckNewToTop && it.value.id == preferredId) -1 else it.index })
            .map { it.value }
    }

    private fun orderItemsForSections(pending: List<TodoItem>, completed: List<TodoItem>, recentlyMovedId: String?, uncheckNewToTop: Boolean): List<TodoItem> {
        val pendingOrdered = orderItemsForSection(pending, emptySet(), recentlyMovedId, uncheckNewToTop)
        val completedOrdered = orderItemsForSection(completed, emptySet(), recentlyMovedId, false)
        return pendingOrdered + completedOrdered
    }

    fun buildBackup(): String = gson.toJson(BackupDataV3(months = getMonthRecords()))

    fun restoreBackup(json: String): Result<Unit> = runCatching {
        val root = JsonParser.parseString(json).asJsonObject
        val version = root.get("version")?.asInt ?: 0
        require(version == 3) { "지원하지 않는 백업 버전입니다. 최신 MTDL 백업 파일만 사용할 수 있습니다." }
        val monthsElement = root.get("months")
        require(monthsElement != null && monthsElement.isJsonObject) { "백업 데이터 형식이 올바르지 않습니다." }
        val months: Map<String, MonthRecord> = monthsElement.asJsonObject.entrySet().associate { (monthKey, element) ->
            val recordJson = element.asJsonObject
            val items = recordJson.getAsJsonArray("items").map { itemElement ->
                val itemJson = itemElement.asJsonObject
                val item = gson.fromJson(itemJson, TodoItem::class.java)
                if (itemJson.has("number") && !itemJson.has("number2")) {
                    item.copy(number2 = itemJson.get("number")?.takeUnless { it.isJsonNull }?.asInt)
                } else item
            }
            val completed = recordJson.getAsJsonArray("completedIds")?.map { it.asString }?.toSet().orEmpty()
            val suppressed = recordJson.getAsJsonArray("suppressedIds")?.map { it.asString }?.toSet().orEmpty()
            monthKey to MonthRecord(items, completed, suppressed)
        }
        months.forEach { (monthKey, record) ->
            require(runCatching { YearMonth.parse(monthKey) }.isSuccess) { "백업 데이터의 월 정보가 올바르지 않습니다." }
            require(record.items.all { it.id.isNotBlank() && it.text.isNotBlank() }) { "백업 데이터의 체크 항목이 올바르지 않습니다." }
            require(record.items.all { it.priority == null || it.priority >= 1 }) { "백업 데이터의 우선순위가 올바르지 않습니다." }
            require(record.items.all { (it.number1 == null || it.number1 >= 0) && (it.number2 == null || it.number2 >= 0) }) { "백업 데이터의 숫자 기록이 올바르지 않습니다." }
        }
        saveMonthRecords(months)
        prefs.edit().putString(KEY_LAST_ACTIVE_MONTH, YearMonth.now().toString()).apply()
        prepareMonths()
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

    private fun getMonthRecords(): Map<String, MonthRecord> {
        val json = prefs.getString(KEY_MONTHS, null) ?: return emptyMap()
        return runCatching {
            val root = JsonParser.parseString(json).asJsonObject
            root.entrySet().associate { (monthKey, element) ->
                val recordJson = element.asJsonObject
                val itemsJson = recordJson.getAsJsonArray("items")
                val items = itemsJson.map { itemElement ->
                    val itemJson = itemElement.asJsonObject
                    val item = gson.fromJson(itemJson, TodoItem::class.java)
                    // v4.2's single number field becomes number2 in v4.3.
                    if (itemJson.has("number") && !itemJson.has("number2")) {
                        item.copy(number2 = itemJson.get("number")?.takeUnless { it.isJsonNull }?.asInt)
                    } else item
                }
                val completed = recordJson.getAsJsonArray("completedIds")?.map { it.asString }?.toSet().orEmpty()
                val suppressed = recordJson.getAsJsonArray("suppressedIds")?.map { it.asString }?.toSet().orEmpty()
                monthKey to MonthRecord(items, completed, suppressed)
            }
        }.getOrElse { emptyMap() }
    }
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
        private const val KEY_FONT_SIZE = "list_font_size"
    }
}

data class TodoItem(
    val id: String,
    val text: String,
    val createdMonth: String = YearMonth.now().toString(),
    val priority: Int? = null,
    val number1: Int? = null,
    val number2: Int? = null
)
data class MonthRecord(val items: List<TodoItem> = emptyList(), val completedIds: Set<String> = emptySet(), val suppressedIds: Set<String> = emptySet())
data class BackupDataV3(val version: Int = 3, val months: Map<String, MonthRecord> = emptyMap())
data class LegacyTodo(val id: String = "", val text: String = "")
