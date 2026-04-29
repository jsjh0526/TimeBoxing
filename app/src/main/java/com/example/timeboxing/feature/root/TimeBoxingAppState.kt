package com.example.timeboxing.feature.root

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.timeboxing.data.repository.TemplateProvider
import com.example.timeboxing.domain.model.DailyTask
import com.example.timeboxing.domain.model.DailyTaskSource
import com.example.timeboxing.domain.model.RecurrenceRule
import com.example.timeboxing.domain.model.RecurrenceType
import com.example.timeboxing.domain.model.ScheduleBlock
import com.example.timeboxing.domain.model.TaskEditInput
import com.example.timeboxing.domain.model.TaskTemplate
import com.example.timeboxing.domain.model.occursOn
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
    private val sectionOrderByDate = mutableMapOf<LocalDate, MutableMap<String, MutableList<String>>>()
    private val today: LocalDate get() = LocalDate.now()

    var currentTab        by mutableStateOf(AppTab.HOME)
        private set
    var selectedDate      by mutableStateOf(today)
        private set
    var todayTasks        by mutableStateOf(repository.getTasks(today))
        private set
    var todayTodoTasks    by mutableStateOf(repository.getTasks(today))
        private set
    var selectedDateTasks by mutableStateOf(repository.getTasks(selectedDate))
        private set
    var editorDraft       by mutableStateOf<TaskEditorDraft?>(null)
        private set

    var recurrenceByTemplateId by mutableStateOf<Map<String, RecurrenceRule?>>(emptyMap())
        private set
    var otherHabits by mutableStateOf<List<DailyTask>>(emptyList())
        private set
    var yesterdayIncompleteTasks by mutableStateOf<List<DailyTask>>(emptyList())
        private set

    val currentTime: LocalTime get() = LocalTime.now()

    init {
        refreshTemplateCache(today)
        refreshYesterdayIncomplete()
    }

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

    fun goToToday() {
        selectedDate = today
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

    fun reorderTodayTodoTask(taskId: String, toIndex: Int) {
        val sectionKey = inferSectionKey(taskId, todayTodoTasks) ?: return
        val sectionOrders = sectionOrderByDate.getOrPut(today) { mutableMapOf() }
        val currentIds = sectionTaskIds(sectionKey, todayTodoTasks)
        val order = sectionOrders.getOrPut(sectionKey) { currentIds.toMutableList() }
        currentIds.forEach { id -> if (id !in order) order.add(id) }
        order.removeAll { it !in currentIds }
        if (taskId !in order) return
        order.remove(taskId)
        order.add(toIndex.coerceIn(0, order.size), taskId)
        todayTodoTasks = applyAllSectionOrders(today, todayTasks)
    }

    fun carryOverYesterdayIncompleteTasks() {
        val yesterday = today.minusDays(1)
        repository.carryOverIncompleteTasks(fromDate = yesterday, toDate = today)
        refreshAll()
    }

    // 어제 이월 리스트에서 특정 태스크만 제거 (삭제)
    // 이월하지 않고 버리고 싶을 때 사용
    fun dismissYesterdayTask(taskId: String) {
        val yesterday = today.minusDays(1)
        repository.deleteTask(yesterday, taskId)
        refreshYesterdayIncomplete()
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

    fun dismissEditor() { editorDraft = null }

    fun saveEditor() {
        val draft = editorDraft ?: return
        if (draft.title.isBlank()) return
        val startMinute = parseTime(draft.startText)
        val endMinute   = parseTime(draft.endText)
        val schedule = if (draft.timeBlockEnabled && endMinute > startMinute) {
            ScheduleBlock(startMinute = startMinute, endMinute = endMinute, reminderEnabled = draft.alertEnabled)
        } else null
        val recurrence = if (draft.recurringEnabled) {
            RecurrenceRule(type = draft.recurrenceType, repeatDays = draft.repeatDays)
        } else null
        repository.upsertTask(
            TaskEditInput(
                taskId = draft.taskId, templateId = draft.templateId, date = draft.date,
                title = draft.title.trim(), note = draft.note.trim().ifEmpty { null },
                tags = draft.tags, isBig3 = draft.isBig3, recurrenceRule = recurrence, schedule = schedule
            )
        )
        dismissEditor()
        refreshAll()
    }

    fun deleteEditingTask() {
        val draft  = editorDraft ?: return
        val taskId = draft.taskId ?: return
        repository.deleteTask(draft.date, taskId)
        dismissEditor()
        refreshAll()
    }

    // ── Fix 3: public으로 변경 → pull 완료 후 TimeBoxingApp에서 호출 가능 ─────
    fun refreshAll() { refreshToday(); refreshSelectedDate() }

    // ── private ─────────────────────────────────────────────────────────────

    private fun refreshToday() {
        val fresh = repository.getTasks(today)
        todayTasks = fresh
        syncSectionOrders(today, fresh)
        todayTodoTasks = applyAllSectionOrders(today, fresh)
        refreshTemplateCache(today)
        refreshYesterdayIncomplete()
    }

    private fun refreshSelectedDate() {
        selectedDateTasks = repository.getTasks(selectedDate)
    }

    private fun refreshTemplateCache(date: LocalDate) {
        val templateProvider = repository as? TemplateProvider ?: return
        val templates = templateProvider.getTemplates()
        recurrenceByTemplateId = templates.associate { it.id to it.recurrenceRule }
        otherHabits = templates
            .filter { template -> template.recurrenceRule?.occursOn(date.dayOfWeek) == false }
            .sortedBy { it.title }
            .map { it.toOtherHabitTask(date) }
    }

    private fun refreshYesterdayIncomplete() {
        val yesterday = today.minusDays(1)
        val carriedTodayIds = repository.getTasks(today)
            .filter { task -> task.source == DailyTaskSource.CARRY_OVER }
            .map { task -> task.id }
            .toSet()
        yesterdayIncompleteTasks = repository.getTasks(yesterday)
            .filter { task -> !task.isCompleted && task.source != DailyTaskSource.RECURRING }
            .filter { task -> "carry-${task.id}-$today" !in carriedTodayIds }
    }

    private fun syncSectionOrders(date: LocalDate, tasks: List<DailyTask>) {
        val sectionOrders = sectionOrderByDate.getOrPut(date) { mutableMapOf() }
        listOf("big3", "brainDump", "recurring").forEach { key ->
            val ids   = sectionTaskIds(key, tasks)
            val order = sectionOrders.getOrPut(key) { ids.toMutableList() }
            order.removeAll { it !in ids }
            ids.forEach { id -> if (id !in order) order.add(id) }
        }
    }

    private fun applyAllSectionOrders(date: LocalDate, tasks: List<DailyTask>): List<DailyTask> {
        val sectionOrders = sectionOrderByDate[date]
        val taskById = tasks.associateBy { it.id }
        val result   = mutableListOf<DailyTask>()
        listOf("big3", "brainDump", "recurring").forEach { key ->
            val ids     = sectionTaskIds(key, tasks)
            val order   = sectionOrders?.get(key) ?: ids
            val idSet   = ids.toSet()
            val ordered = order.filter { it in idSet } + ids.filter { it !in order.toSet() }
            result += ordered.mapNotNull { taskById[it] }
        }
        val included = result.map { it.id }.toSet()
        result += tasks.filter { it.id !in included }
        return result
    }

    private fun sectionTaskIds(sectionKey: String, tasks: List<DailyTask>): List<String> = when (sectionKey) {
        "big3"      -> tasks.filter { it.isBig3 }.map { it.id }
        "brainDump" -> tasks.filter { !it.isBig3 && it.source != DailyTaskSource.RECURRING }.map { it.id }
        "recurring" -> tasks.filter { it.source == DailyTaskSource.RECURRING && !it.isBig3 }.map { it.id }
        else        -> emptyList()
    }

    private fun inferSectionKey(taskId: String, tasks: List<DailyTask>): String? {
        val task = tasks.firstOrNull { it.id == taskId } ?: return null
        return when {
            task.isBig3  -> "big3"
            task.source != DailyTaskSource.RECURRING -> "brainDump"
            else -> "recurring"
        }
    }
}

private fun TaskTemplate.toOtherHabitTask(date: LocalDate): DailyTask = DailyTask(
    id = "template-$id", templateId = id, date = date,
    title = title, note = note, tags = tags, schedule = defaultSchedule,
    source = DailyTaskSource.RECURRING
)

private fun TaskTemplate.toTemplateEditorDraft(date: LocalDate): TaskEditorDraft {
    val rule = recurrenceRule ?: RecurrenceRule(RecurrenceType.DAILY)
    return TaskEditorDraft(
        taskId = null, templateId = id, date = date,
        title = title, note = note.orEmpty(), tags = tags,
        isBig3 = false, recurringEnabled = true,
        recurrenceType = rule.type,
        repeatDays = when {
            rule.repeatDays.isNotEmpty() -> rule.repeatDays
            rule.type == RecurrenceType.WEEKDAYS -> setOf(
                java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.WEDNESDAY,
                java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.FRIDAY
            )
            rule.type == RecurrenceType.CUSTOM -> setOf(date.dayOfWeek)
            else -> emptySet()
        },
        timeBlockEnabled = defaultSchedule != null,
        startText    = defaultSchedule?.let { formatEditorTime(it.startMinute) } ?: "09:00",
        endText      = defaultSchedule?.let { formatEditorTime(it.endMinute) }   ?: "09:30",
        alertEnabled = defaultSchedule?.reminderEnabled == true
    )
}

private fun formatEditorTime(totalMinutes: Int): String {
    val h = (totalMinutes / 60).coerceIn(0, 23)
    val m = (totalMinutes % 60).coerceIn(0, 59)
    return String.format(Locale.ENGLISH, "%02d:%02d", h, m)
}

@Composable
fun rememberTimeBoxingAppState(repository: TaskRepository): TimeBoxingAppState =
    remember(repository) { TimeBoxingAppState(repository = repository) }
