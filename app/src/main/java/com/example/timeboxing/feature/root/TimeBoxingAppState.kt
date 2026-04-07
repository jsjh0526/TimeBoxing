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
    private val templateProvider = repository as? TemplateProvider

    // 섹션별 순서: date → sectionKey → ordered taskId list
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

    // [Fix 11] getTemplates()를 매 recompose마다 호출하지 않도록 AppState에 캐시
    var recurrenceByTemplateId by mutableStateOf<Map<String, RecurrenceRule?>>(emptyMap())
        private set

    // [Fix 11] otherHabits도 AppState에 캐시 (TimeBoxingApp에서 매 recompose마다 getTemplates() 호출 방지)
    var otherHabits by mutableStateOf<List<DailyTask>>(emptyList())
        private set

    val currentTime: LocalTime get() = LocalTime.now()

    init {
        refreshRecurrenceMap()
        refreshOtherHabits(today)
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

    /**
     * 드래그 완료 시 순서 변경.
     * [taskId]를 해당 섹션 내 [toIndex] 위치로 이동.
     * TodoScreen의 DraggableSection이 drag end 시 한 번만 호출.
     */
    fun reorderTodayTodoTask(taskId: String, toIndex: Int) {
        val sectionKey = inferSectionKey(taskId, todayTodoTasks) ?: return
        val sectionOrders = sectionOrderByDate.getOrPut(today) { mutableMapOf() }
        val order = sectionOrders.getOrPut(sectionKey) {
            sectionTaskIds(sectionKey, todayTodoTasks).toMutableList()
        }

        // [Fix 9] sectionTaskIds() 1번만 계산해서 재사용
        val currentIds = sectionTaskIds(sectionKey, todayTodoTasks)
        currentIds.forEach { id -> if (id !in order) order.add(id) }
        order.removeAll { it !in currentIds }

        if (taskId !in order) return
        order.remove(taskId)
        order.add(toIndex.coerceIn(0, order.size), taskId)

        todayTodoTasks = applyAllSectionOrders(today, todayTasks)
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

    // ── private ─────────────────────────────────────────────────────────────

    private fun refreshAll() { refreshToday(); refreshSelectedDate() }

    private fun refreshToday() {
        val fresh = repository.getTasks(today)
        todayTasks = fresh
        syncSectionOrders(today, fresh)
        todayTodoTasks = applyAllSectionOrders(today, fresh)
        refreshRecurrenceMap()
        refreshOtherHabits(today)
    }

    private fun refreshSelectedDate() {
        selectedDateTasks = repository.getTasks(selectedDate)
    }

    private fun refreshRecurrenceMap() {
        recurrenceByTemplateId =
            templateProvider?.getTemplates()?.associate { it.id to it.recurrenceRule }.orEmpty()
    }

    private fun refreshOtherHabits(date: LocalDate) {
        val templates = templateProvider?.getTemplates() ?: return
        otherHabits = templates
            .filter { template ->
                val rule = template.recurrenceRule
                rule != null && !rule.occursOn(date.dayOfWeek)
            }
            .sortedBy { it.title }
            .map { template -> template.toOtherHabitTask(date) }
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

        // carry_over 등 어느 섹션에도 속하지 않는 태스크
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

// ── 확장 함수 (AppState 내부 전용) ──────────────────────────────────────────

private fun TaskTemplate.toOtherHabitTask(date: LocalDate): DailyTask = DailyTask(
    id = "template-$id", templateId = id, date = date,
    title = title, note = note, tags = tags, schedule = defaultSchedule,
    source = DailyTaskSource.RECURRING
)

private fun RecurrenceRule.occursOn(dayOfWeek: java.time.DayOfWeek): Boolean = when (type) {
    RecurrenceType.DAILY    -> true
    RecurrenceType.WEEKDAYS -> {
        if (repeatDays.isNotEmpty()) dayOfWeek in repeatDays
        else dayOfWeek !in setOf(java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY)
    }
    RecurrenceType.CUSTOM -> dayOfWeek in repeatDays
}

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
