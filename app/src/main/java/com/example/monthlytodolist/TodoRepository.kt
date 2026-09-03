package com.example.monthlytodolist

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.YearMonth
import java.util.UUID

/**
 * Recurring Todo definitions are stored separately from monthly completion state.
 * This prevents a checkbox in September from changing August or October.
 */
class TodoRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getTodos(): List<TodoItem> = load<List<TodoItem>>(KEY_TODOS) ?: emptyList()

    fun addTodo(text: String): TodoItem {
        val item = TodoItem(id = UUID.randomUUID().toString(), text = text.trim())
        saveTodos(getTodos() + item)
        return item
    }

    fun updateTodo(id: String, text: String) {
        saveTodos(getTodos().map { if (it.id == id) it.copy(text = text.trim()) else it })
    }

    fun deleteTodo(id: String) {
        saveTodos(getTodos().filterNot { it.id == id })
        val completions = getCompletions().toMutableMap()
        completions.keys.removeAll { it.endsWith("|$id") }
        saveCompletions(completions)
    }

    fun isDone(yearMonth: YearMonth, todoId: String): Boolean =
        getCompletions()[completionKey(yearMonth, todoId)] == true

    fun setDone(yearMonth: YearMonth, todoId: String, done: Boolean) {
        val completions = getCompletions().toMutableMap()
        val key = completionKey(yearMonth, todoId)
        if (done) completions[key] = true else completions.remove(key)
        saveCompletions(completions)
    }

    fun buildBackup(): String = gson.toJson(BackupData(getTodos(), getCompletions()))

    fun restoreBackup(json: String): Result<Unit> = runCatching {
        val backup = gson.fromJson(json, BackupData::class.java)
            ?: error("백업 데이터가 비어 있습니다.")
        require(backup.version == 1) { "지원하지 않는 백업 버전입니다." }
        require(backup.todos.all { it.id.isNotBlank() && it.text.isNotBlank() }) {
            "백업 데이터의 할 일이 올바르지 않습니다."
        }
        saveTodos(backup.todos.map { it.copy(text = it.text.trim()) })
        saveCompletions(backup.completions.filterValues { it })
    }

    private fun saveTodos(items: List<TodoItem>) {
        prefs.edit().putString(KEY_TODOS, gson.toJson(items)).apply()
    }

    private fun getCompletions(): Map<String, Boolean> =
        load<Map<String, Boolean>>(KEY_COMPLETIONS) ?: emptyMap()

    private fun saveCompletions(values: Map<String, Boolean>) {
        prefs.edit().putString(KEY_COMPLETIONS, gson.toJson(values)).apply()
    }

    private inline fun <reified T> load(key: String): T? {
        val json = prefs.getString(key, null) ?: return null
        return runCatching {
            gson.fromJson<T>(json, object : TypeToken<T>() {}.type)
        }.getOrNull()
    }

    private fun completionKey(month: YearMonth, todoId: String) = "$month|$todoId"

    companion object {
        const val PREFS = "monthly_todo_store"
        const val KEY_TODOS = "todos"
        const val KEY_COMPLETIONS = "completions"
    }
}

data class TodoItem(
    val id: String,
    val text: String
)

data class BackupData(
    val version: Int = 1,
    val todos: List<TodoItem> = emptyList(),
    val completions: Map<String, Boolean> = emptyMap()
)
