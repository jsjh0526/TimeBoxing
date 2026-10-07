package dev.jsjh.timebox.feature.todo

import android.content.Context
import java.time.LocalDate
import org.json.JSONArray

class TodoTaskOrderStore(context: Context, private val userId: String) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "todo_task_order_v1", Context.MODE_PRIVATE
    )

    fun read(date: LocalDate, section: String): List<String>? = runCatching {
        val raw = preferences.getString(key(date, section), null) ?: return null
        val array = JSONArray(raw)
        List(array.length()) { index ->
            (array.get(index) as? String)?.takeIf { it.isNotBlank() } ?: return null
        }.distinct()
    }.getOrNull()

    fun write(date: LocalDate, section: String, ids: List<String>) {
        val preferenceKey = key(date, section)
        val encoded = JSONArray(ids.filter { it.isNotBlank() }.distinct()).toString()
        val previous = runCatching { preferences.getString(preferenceKey, null) }.getOrNull()
        if (previous == encoded) return
        preferences.edit().putString(preferenceKey, encoded).apply()
    }

    private fun key(date: LocalDate, section: String): String =
        JSONArray(listOf(userId, date.toString(), section)).toString()
}

internal fun reconcileTodoOrder(saved: List<String>?, current: List<String>): List<String> {
    val currentIds = current.toSet()
    val retained = saved.orEmpty().filter { it in currentIds }.distinct()
    val retainedIds = retained.toSet()
    return retained + current.distinct().filter { it !in retainedIds }
}

internal fun reorderVisibleTodoTasks(
    order: List<String>, visible: List<String>, taskId: String, toIndex: Int
): List<String> {
    val reordered = visible.toMutableList()
    if (!reordered.remove(taskId)) return order
    reordered.add(toIndex.coerceIn(0, reordered.size), taskId)
    val visibleIds = visible.toSet()
    var index = 0
    // Hidden completed tasks keep their slots while visible tasks are rearranged.
    return order.map { if (it in visibleIds) reordered[index++] else it }
}
