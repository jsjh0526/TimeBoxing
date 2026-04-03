package com.example.timeboxing.feature.root

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.timeboxing.domain.model.DailyTask
import com.example.timeboxing.domain.model.RecurrenceRule
import com.example.timeboxing.domain.model.RecurrenceType
import com.example.timeboxing.domain.model.ScheduleBlock
import com.example.timeboxing.domain.model.TaskEditInput
import com.example.timeboxing.domain.model.TaskTemplate
import com.example.timeboxing.domain.repository.TaskRepository
import com.example.timeboxing.feature.editor.TaskEditorDraft
import com.example.timeboxing.feature.editor.newTaskDraft
import com.example.timeboxing.feature.editor.parseTime
import com.example.timeboxing.feature.editor.toEditorDraft
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

@Stable
class TimeBoxingAppState(
    private val repository: TaskRepository
) {
    private val todoOrderByDate = mutableMapOf<LocalDate, MutableList<String>>()

    private val today: LocalDate
        get() = LocalDate.now()

    var currentTab by mutableStateOf(AppTab.HOME)
        private set

    var selectedDate by mutableStateOf(today)
        private set

    var todayTasks by mutableStateOf(repository.getTasks(today))
        private set

    var todayTodoTasks by mutableStateOf(repository.getTasks(today))
        private set

    var selectedDateTasks by mutableStateOf(repository.getTasks(selectedDate))
        private set

    var editorDraft by mutableStateOf<TaskEditorDraft?>(null)
        private set

    val currentTime: LocalTime
        get() = LocalTime.now()

    fun selectTab(tab: AppTab) {
        currentTab = tab
        if (tab == AppTab.TIMETABLE) refreshSelectedDate() else refreshToday()
    }

    fun openTimetable() {
        currentTab = AppTab.TIMETABLE
        selectedDate = today
        refreshSelectedDate()
    }

    fun openTodo() {
        currentTab = AppTab.TODO
        refreshToday()
    }

    fun moveSelectedDateBy(days: Long) {
        selectedDate = selectedDate.plusDays(days)
        refreshSelectedDate()
    }

    fun toggleCompleted(taskId: String, date: LocalDate = today) {
        repository.toggleCompleted(date, taskId)
        refreshAll()
    }

    fun toggleBig3(taskId: String) {
        repository.toggleBig3(today, taskId)
        refreshToday()
    }

    fun moveToUnscheduled(taskId: String, date: LocalDate = selectedDate) {
        repository.setSchedule(date, taskId, null)
        refreshAll()
    }

    fun updateSchedule(taskId: String, date: LocalDate = selectedDate, schedule: ScheduleBlock) {
        repository.setSchedule(date, taskId, schedule)
        refreshAll()
    }

    fun quickAddTask(title: String, date: LocalDate = today) {
        if (title.isBlank()) return
        repository.addTask(date = date, title = title.trim())
        refreshAll()
    }

    fun reorderTodayTodoTask(draggedId: String, targetId: String) {
        val order = todoOrderByDate.getOrPut(today) { todayTasks.map { it.id }.toMutableList() }
        if (draggedId == targetId) return
        if (!order.contains(draggedId) || !order.contains(targetId)) return
        order.remove(draggedId)
        val targetIndex = order.indexOf(targetId)
        if (targetIndex < 0) return
        order.add(targetIndex, draggedId)
        todayTodoTasks = applyTodoOrder(today, todayTasks)
    }

    fun openNewTaskEditor(date: LocalDate = today, initialTitle: String = "") {
        editorDraft = newTaskDraft(date = date, initialTitle = initialTitle)
    }

    fun openTaskEditor(taskId: String, date: LocalDate = today) {
        val task = repository.getTask(date, taskId)
        if (task != null) {
            val recurrenceRule = task.templateId?.let { repository.getTemplate(it)?.recurrenceRule }
            editorDraft = task.toEditorDraft(existingRule = recurrenceRule)
            return
        }

        val templateId = taskId.removePrefix("template-")
        val template = repository.getTemplate(templateId) ?: return
        editorDraft = template.toTemplateEditorDraft(date)
    }

    fun updateEditor(transform: (TaskEditorDraft) -> TaskEditorDraft) {
        editorDraft = editorDraft?.let(transform)
    }

    fun dismissEditor() {
        editorDraft = null
    }

    fun saveEditor() {
        val draft = editorDraft ?: return
        if (draft.title.isBlank()) return
        val startMinute = parseTime(draft.startText)
        val endMinute = parseTime(draft.endText)
        val schedule = if (draft.timeBlockEnabled && endMinute > startMinute) {
            ScheduleBlock(
                startMinute = startMinute,
                endMinute = endMinute,
                reminderEnabled = draft.alertEnabled
            )
        } else {
            null
        }
        val recurrence = if (draft.recurringEnabled) {
            RecurrenceRule(type = draft.recurrenceType, repeatDays = draft.repeatDays)
        } else {
            null
        }
        repository.upsertTask(
            TaskEditInput(
                taskId = draft.taskId,
                templateId = draft.templateId,
                date = draft.date,
                title = draft.title.trim(),
                note = draft.note.trim().ifEmpty { null },
                tags = draft.tags,
                isBig3 = draft.isBig3,
                recurrenceRule = recurrence,
                schedule = schedule
            )
        )
        dismissEditor()
        refreshAll()
    }

    fun deleteEditingTask() {
        val draft = editorDraft ?: return
        val taskId = draft.taskId ?: return
        repository.deleteTask(draft.date, taskId)
        dismissEditor()
        refreshAll()
    }

    private fun refreshAll() {
        refreshToday()
        refreshSelectedDate()
    }

    private fun refreshToday() {
        todayTasks = repository.getTasks(today)
        syncTodoOrder(today, todayTasks)
        todayTodoTasks = applyTodoOrder(today, todayTasks)
    }

    private fun refreshSelectedDate() {
        selectedDateTasks = repository.getTasks(selectedDate)
    }

    private fun syncTodoOrder(date: LocalDate, tasks: List<DailyTask>) {
        val ids = tasks.map { it.id }
        val existing = todoOrderByDate.getOrPut(date) { mutableListOf() }
        existing.removeAll { it !in ids }
        ids.forEach { id ->
            if (id !in existing) existing.add(id)
        }
    }

    private fun applyTodoOrder(date: LocalDate, tasks: List<DailyTask>): List<DailyTask> {
        val order = todoOrderByDate[date] ?: return tasks
        val orderIndex = order.withIndex().associate { it.value to it.index }
        val fallbackIndex = tasks.withIndex().associate { it.value.id to it.index }
        return tasks.sortedWith(
            compareBy<DailyTask> { orderIndex[it.id] ?: Int.MAX_VALUE }
                .thenBy { fallbackIndex[it.id] ?: Int.MAX_VALUE }
        )
    }
}

private fun TaskTemplate.toTemplateEditorDraft(date: LocalDate): TaskEditorDraft {
    val rule = recurrenceRule ?: RecurrenceRule(RecurrenceType.DAILY)
    return TaskEditorDraft(
        taskId = null,
        templateId = id,
        date = date,
        title = title,
        note = note.orEmpty(),
        tags = tags,
        isBig3 = false,
        recurringEnabled = true,
        recurrenceType = rule.type,
        repeatDays = when {
            rule.repeatDays.isNotEmpty() -> rule.repeatDays
            rule.type == RecurrenceType.WEEKDAYS -> setOf(
                java.time.DayOfWeek.MONDAY,
                java.time.DayOfWeek.TUESDAY,
                java.time.DayOfWeek.WEDNESDAY,
                java.time.DayOfWeek.THURSDAY,
                java.time.DayOfWeek.FRIDAY
            )
            rule.type == RecurrenceType.CUSTOM -> setOf(date.dayOfWeek)
            else -> emptySet()
        },
        timeBlockEnabled = defaultSchedule != null,
        startText = defaultSchedule?.let { formatEditorTime(it.startMinute) } ?: "09:00",
        endText = defaultSchedule?.let { formatEditorTime(it.endMinute) } ?: "09:30",
        alertEnabled = defaultSchedule?.reminderEnabled == true
    )
}

private fun formatEditorTime(totalMinutes: Int): String {
    val hour = (totalMinutes / 60).coerceIn(0, 23)
    val minute = (totalMinutes % 60).coerceIn(0, 59)
    return String.format(Locale.ENGLISH, "%02d:%02d", hour, minute)
}

@Composable
fun rememberTimeBoxingAppState(repository: TaskRepository): TimeBoxingAppState {
    return remember(repository) {
        TimeBoxingAppState(repository = repository)
    }
}


