package dev.jsjh.timebox.data.repository

import dev.jsjh.timebox.domain.model.DailyTask
import dev.jsjh.timebox.domain.model.DailyTaskSource
import dev.jsjh.timebox.domain.model.RecurrenceRule
import dev.jsjh.timebox.domain.model.RecurrenceType
import dev.jsjh.timebox.domain.model.ScheduleBlock
import dev.jsjh.timebox.domain.model.TaskEditInput
import dev.jsjh.timebox.domain.model.TaskTemplate
import dev.jsjh.timebox.domain.model.occursOn
import dev.jsjh.timebox.domain.repository.TaskRepository
import java.time.LocalDate
import java.util.UUID

class InMemoryTaskRepository(
    private val anchorDate: LocalDate = LocalDate.now()
) : TaskRepository, TemplateProvider {

    private val templates = linkedMapOf<String, TaskTemplate>().apply {
        put("tpl-standup", TaskTemplate(
            id = "tpl-standup",
            title = "팀 스탠드업 미팅",
            note = "오늘 할 일과 어제 한 일 공유",
            tags = listOf("Meeting"),
            recurrenceRule = RecurrenceRule(RecurrenceType.DAILY),
            defaultSchedule = ScheduleBlock(17 * 60, 17 * 60 + 45)
        ))
        put("tpl-break", TaskTemplate(
            id = "tpl-break",
            title = "점심 식사 및 휴식",
            tags = listOf("Break"),
            recurrenceRule = RecurrenceRule(RecurrenceType.DAILY),
            defaultSchedule = ScheduleBlock(18 * 60 + 30, 19 * 60 + 30)
        ))
        put("tpl-funny", TaskTemplate(
            id = "tpl-funny",
            title = "ㅋㅋㅋ",
            tags = listOf("ㅋㅋㅋ", "ㄴㄴㄴ"),
            recurrenceRule = RecurrenceRule(RecurrenceType.DAILY),
            defaultSchedule = ScheduleBlock(9 * 60, 9 * 60 + 30)
        ))
    }

    private val seededOneOffs = listOf(
        DailyTask(id = "seed-deep-work", date = anchorDate, title = "딥워크: 핵심 기능 개발", tags = listOf("Work", "Focus"), isBig3 = true, schedule = ScheduleBlock(15 * 60 + 30, 17 * 60)),
        DailyTask(id = "seed-ui-review", date = anchorDate, title = "UI 디자인 리뷰", note = "디자이너와 함께 새로운 화면 검토", tags = listOf("Meeting", "Design"), isBig3 = true, schedule = ScheduleBlock(20 * 60, 20 * 60 + 45)),
        DailyTask(id = "seed-email", date = anchorDate, title = "이메일 및 메시지 확인", tags = listOf("Admin"), schedule = ScheduleBlock(21 * 60 + 30, 22 * 60)),
        DailyTask(id = "seed-plan", date = anchorDate, title = "내일 계획 세우기", tags = listOf("Planning")),
        DailyTask(id = "seed-docs", date = anchorDate, title = "프로젝트 문서 업데이트", tags = listOf("Documentation"))
    )

    private val tasksByDate = linkedMapOf<LocalDate, MutableList<DailyTask>>()

    override fun getTasks(date: LocalDate): List<DailyTask> {
        ensureDate(date)
        syncRecurringForDate(date)
        return tasksByDate.getValue(date).sortedWith(compareBy<DailyTask> { it.schedule?.startMinute ?: Int.MAX_VALUE }.thenBy { it.title })
    }

    override fun getTask(date: LocalDate, taskId: String): DailyTask? {
        ensureDate(date)
        syncRecurringForDate(date)
        return tasksByDate.getValue(date).firstOrNull { it.id == taskId }
    }

    override fun getTemplate(templateId: String): TaskTemplate? {
        return templates[templateId]
    }

    override fun getTemplates(): List<TaskTemplate> {
        return templates.values.toList()
    }

    override fun toggleCompleted(date: LocalDate, taskId: String) {
        val current = getTask(date, taskId) ?: return
        mutateTask(date, taskId) { it.copy(isCompleted = !it.isCompleted) }
    }

    override fun toggleBig3(date: LocalDate, taskId: String) {
        ensureDate(date)
        val current = tasksByDate.getValue(date)
        val task = current.firstOrNull { it.id == taskId } ?: return
        val enable = !task.isBig3
        val activeBig3 = current.count { it.isBig3 }
        if (enable && activeBig3 >= 3) return
        mutateTask(date, taskId) { it.copy(isBig3 = enable) }
    }

    override fun setSchedule(date: LocalDate, taskId: String, schedule: ScheduleBlock?) {
        val current = getTask(date, taskId) ?: return
        val templateId = current.templateId
        if (current.source == DailyTaskSource.RECURRING && templateId != null) {
            val template = templates[templateId] ?: return
            templates[templateId] = template.copy(defaultSchedule = schedule)
            syncTemplateAcrossCachedDates(templateId)
            return
        }
        mutateTask(date, taskId) { it.copy(schedule = schedule) }
    }

    override fun addTask(date: LocalDate, title: String): DailyTask {
        ensureDate(date)
        val newTask = DailyTask(id = UUID.randomUUID().toString(), date = date, title = title, source = DailyTaskSource.ONE_OFF)
        tasksByDate.getValue(date).add(newTask)
        return newTask
    }

    override fun upsertTask(input: TaskEditInput): DailyTask {
        ensureDate(input.date)
        val existing = input.taskId?.let { getTask(input.date, it) }
        val existingTemplateId = existing?.templateId ?: input.templateId
        val templateId = when {
            input.recurrenceRule != null -> existingTemplateId ?: "tpl-${UUID.randomUUID()}"
            else -> null
        }

        if (templateId != null) {
            templates[templateId] = TaskTemplate(
                id = templateId,
                title = input.title,
                note = input.note,
                tags = input.tags,
                recurrenceRule = input.recurrenceRule,
                defaultSchedule = input.schedule
            )
        } else if (existingTemplateId != null) {
            templates.remove(existingTemplateId)
            tasksByDate.keys.forEach { cachedDate ->
                tasksByDate[cachedDate] = tasksByDate.getValue(cachedDate)
                    .filterNot { it.templateId == existingTemplateId }
                    .toMutableList()
            }
        }

        val targetId = if (templateId != null) {
            "$templateId-${input.date}"
        } else {
            input.taskId ?: UUID.randomUUID().toString()
        }

        val updatedTask = DailyTask(
            id = targetId,
            templateId = templateId,
            date = input.date,
            title = input.title,
            note = input.note,
            tags = input.tags,
            isBig3 = input.isBig3,
            isCompleted = existing?.isCompleted ?: false,
            schedule = input.schedule,
            source = when {
                templateId != null -> DailyTaskSource.RECURRING
                else -> DailyTaskSource.ONE_OFF
            }
        )

        val current = tasksByDate.getValue(input.date).toMutableList()
        if (templateId != null && existing != null && existing.id != targetId) {
            current.removeAll { it.id == existing.id }
        }
        val index = current.indexOfFirst { it.id == targetId }
        if (index >= 0) current[index] = updatedTask else current.add(updatedTask)
        tasksByDate[input.date] = current

        if (templateId != null) {
            syncTemplateAcrossCachedDates(templateId)
        }

        return updatedTask
    }

    override fun deleteTask(date: LocalDate, taskId: String) {
        ensureDate(date)
        val existing = getTask(date, taskId) ?: return
        tasksByDate[date] = tasksByDate.getValue(date).filterNot { it.id == taskId }.toMutableList()
        if (existing.templateId != null) {
            templates.remove(existing.templateId)
            tasksByDate.keys.forEach { cachedDate ->
                tasksByDate[cachedDate] = tasksByDate.getValue(cachedDate).filterNot { it.templateId == existing.templateId }.toMutableList()
            }
        }
    }

    override fun carryOverIncompleteTasks(fromDate: LocalDate, toDate: LocalDate): Int {
        ensureDate(fromDate)
        ensureDate(toDate)
        syncRecurringForDate(fromDate)
        syncRecurringForDate(toDate)
        val carried = tasksByDate.getValue(fromDate)
            .filter { task ->
                task.source != DailyTaskSource.RECURRING &&
                    !task.isCompleted &&
                    task.title.isNotBlank()
            }
            .map { task ->
                task.copy(
                    id = "carry-${task.id}-$toDate",
                    templateId = null,
                    date = toDate,
                    isBig3 = false,
                    isCompleted = false,
                    schedule = null,
                    source = DailyTaskSource.CARRY_OVER
                )
            }
        val current = tasksByDate.getValue(toDate).toMutableList()
        carried.forEach { task ->
            val index = current.indexOfFirst { it.id == task.id }
            if (index >= 0) current[index] = task else current.add(task)
        }
        tasksByDate[toDate] = current
        return carried.size
    }

    private fun syncTemplateAcrossCachedDates(templateId: String) {
        val template = templates[templateId] ?: return
        tasksByDate.keys.toList().forEach { date ->
            if (!template.occursOn(date.dayOfWeek)) {
                tasksByDate[date] = tasksByDate.getValue(date).filterNot { it.templateId == templateId }.toMutableList()
            } else {
                val updated = template.toDailyTask(date)
                val list = tasksByDate.getValue(date).toMutableList()
                val previous = list.firstOrNull { it.templateId == templateId }
                list.removeAll { it.templateId == templateId }
                list.add(
                    updated.copy(
                        isCompleted = previous?.isCompleted ?: false,
                        isBig3 = previous?.isBig3 ?: false
                    )
                )
                tasksByDate[date] = list
            }
        }
    }

    private fun syncRecurringForDate(date: LocalDate) {
        val current = tasksByDate.getValue(date).toMutableList()
        val existingByTemplateId = current.mapNotNull { task ->
            task.templateId?.let { templateId -> templateId to task }
        }.toMap()
        val activeTemplates = templates.values.filter { it.occursOn(date.dayOfWeek) }
        val activeTemplateIds = activeTemplates.map { it.id }.toSet()

        current.removeAll { it.source == DailyTaskSource.RECURRING && it.templateId !in activeTemplateIds }
        activeTemplates.forEach { template ->
            val previous = existingByTemplateId[template.id]
            current.removeAll { it.templateId == template.id }
            current.add(
                template.toDailyTask(date).copy(
                    isCompleted = previous?.isCompleted ?: false,
                    isBig3 = previous?.isBig3 ?: false
                )
            )
        }
        tasksByDate[date] = current
    }

    private fun mutateTask(date: LocalDate, taskId: String, transform: (DailyTask) -> DailyTask) {
        ensureDate(date)
        val updated = tasksByDate.getValue(date).map { task -> if (task.id == taskId) transform(task) else task }
        tasksByDate[date] = updated.toMutableList()
    }

    private fun ensureDate(date: LocalDate) {
        if (tasksByDate.containsKey(date)) return
        val tasks = when {
            date == anchorDate -> buildAnchorTasks()
            date.isBefore(anchorDate) -> buildPastTasks(date)
            else -> buildFutureTasks(date)
        }
        tasksByDate[date] = tasks.toMutableList()
    }

    private fun buildAnchorTasks(): List<DailyTask> {
        return templates.values.mapNotNull { template -> if (template.occursOn(anchorDate.dayOfWeek)) template.toDailyTask(anchorDate) else null } + seededOneOffs
    }

    private fun buildPastTasks(date: LocalDate): List<DailyTask> {
        return templates.values.mapNotNull { template -> if (template.occursOn(date.dayOfWeek)) template.toDailyTask(date) else null }
    }

    private fun buildFutureTasks(date: LocalDate): List<DailyTask> {
        return templates.values.mapNotNull { template -> if (template.occursOn(date.dayOfWeek)) template.toDailyTask(date) else null }
    }

    private fun TaskTemplate.toDailyTask(date: LocalDate): DailyTask {
        return DailyTask(
            id = "$id-$date",
            templateId = id,
            date = date,
            title = title,
            note = note,
            tags = tags,
            schedule = defaultSchedule,
            source = DailyTaskSource.RECURRING
        )
    }

}

