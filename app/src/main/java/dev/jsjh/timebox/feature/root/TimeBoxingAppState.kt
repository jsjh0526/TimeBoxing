package dev.jsjh.timebox.feature.root

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.jsjh.timebox.analytics.TimeBoxAnalytics
import dev.jsjh.timebox.data.repository.TemplateProvider
import dev.jsjh.timebox.domain.model.DailyTask
import dev.jsjh.timebox.domain.model.DailyTaskSource
import dev.jsjh.timebox.domain.model.RecurrenceRule
import dev.jsjh.timebox.domain.model.RecurrenceType
import dev.jsjh.timebox.domain.model.ScheduleBlock
import dev.jsjh.timebox.domain.model.TaskEditInput
import dev.jsjh.timebox.domain.model.TaskTemplate
import dev.jsjh.timebox.domain.model.occursOn
import dev.jsjh.timebox.domain.repository.TaskRepository
import dev.jsjh.timebox.feature.editor.TaskEditorDraft
import dev.jsjh.timebox.feature.editor.newTaskDraft
import dev.jsjh.timebox.feature.editor.parseTime
import dev.jsjh.timebox.feature.editor.toEditorDraft
import dev.jsjh.timebox.feature.todo.TodoTaskOrderStore
import dev.jsjh.timebox.feature.todo.matchesBrainDumpTags
import dev.jsjh.timebox.feature.todo.reconcileTodoOrder
import dev.jsjh.timebox.feature.todo.reorderVisibleTodoTasks
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal const val MaxConcurrentTimeBlocks = 5
internal const val ScheduleLimitMessageToken = "schedule_limit"

@Stable
class TimeBoxingAppState(
    private val repository: TaskRepository,
    initialTodayDate: LocalDate,
    private val scope: CoroutineScope,
    private val orderStore: TodoTaskOrderStore? = null
) {
    private val sectionOrderByDate = mutableMapOf<LocalDate, MutableMap<String, MutableList<String>>>()
    private var todayRefreshGeneration = 0
    private var todayDate by mutableStateOf(initialTodayDate)
    val today: LocalDate get() = todayDate

    var currentTab by mutableStateOf(AppTab.HOME)
        private set
    var selectedDate by mutableStateOf(today)
        private set
    var todayTasks by mutableStateOf<List<DailyTask>>(emptyList())
        private set
    var todayTodoTasks by mutableStateOf<List<DailyTask>>(emptyList())
        private set
    var selectedDateTasks by mutableStateOf<List<DailyTask>>(emptyList())
        private set
    var calendarStatsRevision by mutableStateOf(0)
        private set
    var editorDraft by mutableStateOf<TaskEditorDraft?>(null)
        private set
    var brainDumpSelectedTags by mutableStateOf<Set<String>>(emptySet())
        private set
    var brainDumpTagsExpanded by mutableStateOf(false)
        private set
    private var newTaskEditorFromTodo = false

    var recurrenceByTemplateId by mutableStateOf<Map<String, RecurrenceRule?>>(emptyMap())
        private set
    var otherHabits by mutableStateOf<List<DailyTask>>(emptyList())
        private set
    var pastIncompleteTasks by mutableStateOf<List<DailyTask>>(emptyList())
        private set
    var carryOverInProgress by mutableStateOf(false)
        private set
    var carryOverFailed by mutableStateOf(false)
        private set
    var reminderScheduleRevision by mutableStateOf(0)
        private set
    var scheduleLimitMessage by mutableStateOf<String?>(null)
        private set
    var big3LimitNoticeCount by mutableStateOf(0)
        private set

    init {
        refreshAll()
    }

    fun updateTodayDate(nextToday: LocalDate) {
        if (todayDate == nextToday) return
        val previousToday = todayDate
        todayDate = nextToday
        resetBrainDumpTagFilter()
        if (selectedDate == previousToday) {
            selectedDate = nextToday
        }
        refreshAll()
    }

    fun selectTab(tab: AppTab) {
        currentTab = tab
        scope.launch {
            if (tab == AppTab.TIMETABLE) refreshSelectedDate() else refreshToday()
        }
    }

    fun updateBrainDumpTagsExpanded(expanded: Boolean) {
        brainDumpTagsExpanded = expanded
    }

    fun toggleBrainDumpTag(tag: String) {
        brainDumpSelectedTags = if (tag in brainDumpSelectedTags) {
            brainDumpSelectedTags - tag
        } else {
            brainDumpSelectedTags + tag
        }
    }

    fun clearBrainDumpTags() {
        brainDumpSelectedTags = emptySet()
    }

    private fun resetBrainDumpTagFilter() {
        clearBrainDumpTags()
        brainDumpTagsExpanded = false
    }

    fun openTimetable(date: LocalDate = today) {
        currentTab = AppTab.TIMETABLE
        selectedDate = date
        scope.launch { refreshSelectedDate() }
    }

    fun openTodo() {
        currentTab = AppTab.TODO
        scope.launch { refreshToday() }
    }

    fun moveSelectedDateBy(days: Long) {
        selectedDate = selectedDate.plusDays(days)
        scope.launch { refreshSelectedDate() }
    }

    fun goToToday() {
        selectedDate = today
        scope.launch { refreshSelectedDate() }
    }

    fun selectDate(date: LocalDate) {
        selectedDate = date
        scope.launch { refreshSelectedDate() }
    }

    suspend fun completionCounts(date: LocalDate): Pair<Int, Int> {
        return repository.getTaskCompletionCounts(listOf(date))[date] ?: (0 to 0)
    }

    suspend fun completionCounts(dates: Collection<LocalDate>): Map<LocalDate, Pair<Int, Int>> {
        return repository.getTaskCompletionCounts(dates)
    }

    fun toggleCompleted(taskId: String, date: LocalDate = today) {
        scope.launch {
            val currentTask = repository.getTask(date, taskId)
            repository.toggleCompleted(date, taskId)
            if (currentTask != null) {
                TimeBoxAnalytics.taskCompleted(
                    completed = !currentTask.isCompleted,
                    source = currentTask.source.name.lowercase(Locale.ROOT),
                    isBig3 = currentTask.isBig3,
                    isTutorial = currentTask.isTutorialSeed()
                )
            }
            refreshAllNow()
        }
    }

    fun toggleBig3(taskId: String) {
        val current = todayTasks.firstOrNull { it.id == taskId } ?: return
        val enable = !current.isBig3
        if (enable && todayTasks.count { it.isBig3 } >= 3) {
            big3LimitNoticeCount++
            return
        }
        scope.launch {
            repository.toggleBig3(today, taskId)
            TimeBoxAnalytics.big3Changed(
                enabled = enable,
                source = "todo",
                isTutorial = current.isTutorialSeed()
            )
            refreshToday()
        }
    }

    fun moveToUnscheduled(taskId: String, date: LocalDate = selectedDate) {
        scope.launch {
            repository.setSchedule(date, taskId, null)
            TimeBoxAnalytics.timeboxRemoved()
            refreshAllNow()
        }
    }

    fun updateSchedule(taskId: String, date: LocalDate = selectedDate, schedule: ScheduleBlock): Boolean {
        when (canPlaceSchedule(date = date, replacingTaskId = taskId, schedule = schedule)) {
            false -> {
                notifyScheduleLimit()
                return false
            }
            null -> {
                // Never accept a time block using an empty fallback list. The normal
                // timetable path is already cached; an uncached date is refreshed
                // before the user tries again.
                scope.launch { refreshSelectedDate() }
                return false
            }
            true -> Unit
        }
        scope.launch {
            repository.setSchedule(date, taskId, schedule)
            TimeBoxAnalytics.timeboxScheduled(
                source = "timetable_drag",
                durationMinutes = schedule.endMinute - schedule.startMinute
            )
            refreshAllNow()
        }
        return true
    }

    fun quickAddTask(title: String, date: LocalDate = today) {
        if (title.isBlank()) return
        val createdFromTodo = currentTab == AppTab.TODO
        scope.launch {
            val task = repository.addTask(date = date, title = title.trim())
            placeNewTaskFirst(task)
            if (createdFromTodo && date == today) resetBrainDumpTagFilter()
            TimeBoxAnalytics.taskCreated(
                source = "quick_add",
                hasSchedule = false,
                isRecurring = false
            )
            refreshAllNow()
        }
    }

    fun reorderTodayTodoTask(taskId: String, toIndex: Int) {
        val sectionKey = inferSectionKey(taskId, todayTodoTasks) ?: return
        val currentIds = sectionTaskIds(sectionKey, todayTodoTasks)
        val order = reconcileTodoOrder(sectionOrder(today, sectionKey), currentIds)
        val currentIdSet = currentIds.toSet()
        val visibleIds = todayTodoTasks.filter {
            it.id in currentIdSet && (sectionKey != "brainDump" ||
                (!it.isCompleted && it.matchesBrainDumpTags(brainDumpSelectedTags)))
        }.map { it.id }
        saveSectionOrder(today, sectionKey, reorderVisibleTodoTasks(order, visibleIds, taskId, toIndex))
        todayTodoTasks = applyAllSectionOrders(today, todayTasks)
    }

    fun carryOverPastIncompleteTasks(confirmedTaskIds: List<String>) {
        if (carryOverInProgress || confirmedTaskIds.isEmpty()) return
        val targetDate = today
        carryOverInProgress = true
        carryOverFailed = false
        scope.launch {
            try {
                val count = repository.carryOverPastIncompleteTasks(targetDate, confirmedTaskIds)
                if (count > 0) {
                    reminderScheduleRevision++
                    TimeBoxAnalytics.tasksCarriedOver(count)
                }
                refreshAllNow()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("TimeBoxingAppState", "Could not carry over unfinished tasks", error)
                carryOverFailed = true
            } finally {
                carryOverInProgress = false
            }
        }
    }

    fun dismissPastTask(taskId: String) {
        if (carryOverInProgress) return
        val task = pastIncompleteTasks.firstOrNull { it.id == taskId } ?: return
        scope.launch {
            repository.deleteTask(task.date, taskId)
            reminderScheduleRevision++
            refreshAllNow()
        }
    }

    fun openNewTaskEditor(date: LocalDate = today, initialTitle: String = "") {
        newTaskEditorFromTodo = currentTab == AppTab.TODO
        editorDraft = newTaskDraft(date = date, initialTitle = initialTitle)
    }

    fun openTaskEditor(taskId: String, date: LocalDate = today) {
        newTaskEditorFromTodo = false
        scope.launch {
            val task = repository.getTask(date, taskId)
            if (task != null) {
                val recurrenceRule = task.templateId?.let { repository.getTemplate(it)?.recurrenceRule }
                editorDraft = task.toEditorDraft(existingRule = recurrenceRule)
                return@launch
            }

            val templateId = taskId.removePrefix("template-")
            val template = repository.getTemplate(templateId) ?: return@launch
            editorDraft = template.toTemplateEditorDraft(date)
        }
    }

    fun updateEditor(transform: (TaskEditorDraft) -> TaskEditorDraft) {
        editorDraft = editorDraft?.let(transform)
    }

    fun dismissEditor() {
        editorDraft = null
        newTaskEditorFromTodo = false
    }

    fun saveEditor() {
        val draft = editorDraft ?: return
        if (draft.title.isBlank()) return
        val isNewTask = draft.taskId == null && draft.templateId == null
        val createdFromTodo = newTaskEditorFromTodo

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
        scope.launch {
            val previousTask = draft.taskId?.let { repository.getTask(draft.date, it) }
            if (schedule != null) {
                val replacingTaskId = draft.taskId ?: draft.templateId?.let { "$it-${draft.date}" }
                val tasks = repository.getTasks(draft.date)
                if (exceedsMaxConcurrentTimeBlocks(tasks, replacingTaskId, schedule)) {
                    notifyScheduleLimit()
                    return@launch
                }
            }

            val savedTask = repository.upsertTask(
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
            if (isNewTask) {
                placeNewTaskFirst(savedTask)
                if (createdFromTodo && draft.date == today) resetBrainDumpTagFilter()
                TimeBoxAnalytics.taskCreated(
                    source = "editor",
                    hasSchedule = schedule != null,
                    isRecurring = recurrence != null
                )
            }
            val previousBig3 = previousTask?.isBig3 ?: false
            if ((previousTask != null || draft.isBig3) && previousBig3 != draft.isBig3) {
                TimeBoxAnalytics.big3Changed(
                    enabled = draft.isBig3,
                    source = "editor",
                    isTutorial = previousTask?.isTutorialSeed() == true
                )
            }
            val previousReminder = previousTask?.schedule?.reminderEnabled == true
            val nextReminder = schedule?.reminderEnabled == true
            if ((previousTask != null || nextReminder) && previousReminder != nextReminder) {
                TimeBoxAnalytics.reminderChanged(
                    enabled = nextReminder,
                    source = "editor"
                )
            }
            if (schedule != null) {
                TimeBoxAnalytics.timeboxScheduled(
                    source = "editor",
                    durationMinutes = schedule.endMinute - schedule.startMinute
                )
            }
            dismissEditor()
            refreshAllNow()
        }
    }

    fun clearScheduleLimitMessage() {
        scheduleLimitMessage = null
    }

    fun deleteEditingTask() {
        val draft = editorDraft ?: return
        val taskId = draft.taskId ?: draft.templateId?.let { "template-$it" } ?: return
        dismissEditor()
        scope.launch {
            repository.deleteTask(draft.date, taskId)
            refreshAllNow()
        }
    }

    fun refreshAll() {
        scope.launch { refreshAllNow() }
    }

    private suspend fun refreshAllNow() {
        refreshToday()
        refreshSelectedDate()
    }

    private suspend fun refreshToday() {
        val date = today
        val generation = ++todayRefreshGeneration
        val fresh = repository.getTasks(date)
        if (date != today || generation != todayRefreshGeneration) return
        todayTasks = fresh
        syncSectionOrders(date, fresh)
        todayTodoTasks = applyAllSectionOrders(date, fresh)
        refreshTemplateCache(date)
        refreshPastIncomplete()
    }

    private suspend fun refreshSelectedDate() {
        selectedDateTasks = repository.getTasks(selectedDate)
        calendarStatsRevision++
    }

    private suspend fun refreshTemplateCache(date: LocalDate) {
        val templateProvider = repository as? TemplateProvider ?: return
        val templates = templateProvider.getTemplates()
        recurrenceByTemplateId = templates.associate { it.id to it.recurrenceRule }
        otherHabits = templates
            .filter { template -> template.recurrenceRule?.occursOn(date.dayOfWeek) == false }
            .sortedBy { it.title }
            .map { it.toOtherHabitTask(date) }
    }

    private suspend fun refreshPastIncomplete() {
        pastIncompleteTasks = repository.getPastIncompleteTasks(today)
    }

    private fun canPlaceSchedule(
        date: LocalDate,
        replacingTaskId: String?,
        schedule: ScheduleBlock
    ): Boolean? {
        val tasks = when (date) {
            today -> todayTasks
            selectedDate -> selectedDateTasks
            else -> return null
        }
        return !exceedsMaxConcurrentTimeBlocks(tasks, replacingTaskId, schedule)
    }

    private fun notifyScheduleLimit() {
        scheduleLimitMessage = ScheduleLimitMessageToken
    }

    private fun syncSectionOrders(date: LocalDate, tasks: List<DailyTask>) {
        listOf("big3", "brainDump", "recurring").forEach { key ->
            val ids = sectionTaskIds(key, tasks)
            saveSectionOrder(date, key, reconcileTodoOrder(sectionOrder(date, key), ids))
        }
    }

    private fun sectionOrder(date: LocalDate, section: String): MutableList<String> =
        sectionOrderByDate.getOrPut(date) { mutableMapOf() }.getOrPut(section) {
            orderStore?.read(date, section).orEmpty().toMutableList()
        }

    private fun saveSectionOrder(date: LocalDate, section: String, ids: List<String>) {
        sectionOrderByDate.getOrPut(date) { mutableMapOf() }[section] = ids.toMutableList()
        orderStore?.write(date, section, ids)
    }

    private fun placeNewTaskFirst(task: DailyTask) {
        val section = inferSectionKey(task.id, listOf(task)) ?: return
        val order = sectionOrder(task.date, section)
        saveSectionOrder(task.date, section, listOf(task.id) + order.filter { it != task.id })
    }

    private fun applyAllSectionOrders(date: LocalDate, tasks: List<DailyTask>): List<DailyTask> {
        val sectionOrders = sectionOrderByDate[date]
        val taskById = tasks.associateBy { it.id }
        val result = mutableListOf<DailyTask>()
        listOf("big3", "brainDump", "recurring").forEach { key ->
            val ids = sectionTaskIds(key, tasks)
            val order = sectionOrders?.get(key) ?: ids
            val idSet = ids.toSet()
            val ordered = order.filter { it in idSet } + ids.filter { it !in order.toSet() }
            result += ordered.mapNotNull { taskById[it] }
        }
        val included = result.map { it.id }.toSet()
        result += tasks.filter { it.id !in included }
        return result
    }

    private fun sectionTaskIds(sectionKey: String, tasks: List<DailyTask>): List<String> = when (sectionKey) {
        "big3" -> tasks.filter { it.isBig3 }.map { it.id }
        "brainDump" -> tasks.filter { !it.isBig3 && it.source != DailyTaskSource.RECURRING }.map { it.id }
        "recurring" -> tasks.filter { it.source == DailyTaskSource.RECURRING && !it.isBig3 }.map { it.id }
        else -> emptyList()
    }

    private fun inferSectionKey(taskId: String, tasks: List<DailyTask>): String? {
        val task = tasks.firstOrNull { it.id == taskId } ?: return null
        return when {
            task.isBig3 -> "big3"
            task.source != DailyTaskSource.RECURRING -> "brainDump"
            else -> "recurring"
        }
    }
}

private fun exceedsMaxConcurrentTimeBlocks(
    tasks: List<DailyTask>,
    replacingTaskId: String?,
    proposedSchedule: ScheduleBlock
): Boolean {
    val events = mutableListOf<Pair<Int, Int>>()
    fun add(schedule: ScheduleBlock) {
        events += schedule.startMinute to 1
        events += schedule.endMinute to -1
    }

    tasks.forEach { task ->
        if (task.id == replacingTaskId) return@forEach
        task.schedule?.let(::add)
    }
    add(proposedSchedule)

    var active = 0
    events
        .sortedWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
        .forEach { (_, delta) ->
            active += delta
            if (active > MaxConcurrentTimeBlocks) return true
        }
    return false
}

private fun TaskTemplate.toOtherHabitTask(date: LocalDate): DailyTask = DailyTask(
    id = "template-$id",
    templateId = id,
    date = date,
    title = title,
    note = note,
    tags = tags,
    schedule = defaultSchedule,
    source = DailyTaskSource.RECURRING
)

private fun DailyTask.isTutorialSeed(): Boolean =
    id.startsWith("seed-") || templateId == "tpl-standup"

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
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
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
    val h = (totalMinutes / 60).coerceIn(0, 23)
    val m = (totalMinutes % 60).coerceIn(0, 59)
    return String.format(Locale.ENGLISH, "%02d:%02d", h, m)
}

@Composable
fun rememberTimeBoxingAppState(
    repository: TaskRepository, today: LocalDate, orderStore: TodoTaskOrderStore? = null
): TimeBoxingAppState {
    val scope = rememberCoroutineScope()
    return remember(repository, orderStore) {
        TimeBoxingAppState(repository = repository, initialTodayDate = today, scope = scope, orderStore = orderStore)
    }
}
