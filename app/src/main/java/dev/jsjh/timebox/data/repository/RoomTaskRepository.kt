package dev.jsjh.timebox.data.repository

import dev.jsjh.timebox.data.local.dao.DailyTaskDao
import dev.jsjh.timebox.data.local.dao.TaskTemplateDao
import dev.jsjh.timebox.data.local.entity.DailyTaskEntity
import dev.jsjh.timebox.data.local.entity.TaskTemplateEntity
import dev.jsjh.timebox.domain.model.DailyTask
import dev.jsjh.timebox.domain.model.DailyTaskSource
import dev.jsjh.timebox.domain.model.RecurrenceRule
import dev.jsjh.timebox.domain.model.RecurrenceType
import dev.jsjh.timebox.domain.model.ScheduleBlock
import dev.jsjh.timebox.domain.model.TaskEditInput
import dev.jsjh.timebox.domain.model.TaskTemplate
import dev.jsjh.timebox.domain.model.occursOn
import dev.jsjh.timebox.domain.repository.TaskRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

class RoomTaskRepository(
    private val templateDao: TaskTemplateDao,
    private val dailyTaskDao: DailyTaskDao,
    private val anchorDate: LocalDate = LocalDate.now(),
    private val seedInitialData: Boolean = true
) : TaskRepository, TemplateProvider {

    init {
        if (seedInitialData) seedIfNeeded()
    }

    override fun getTasks(date: LocalDate): List<DailyTask> {
        ensureDate(date)
        syncRecurringForDate(date)
        return dailyTaskDao.getByDate(date.toString()).map { it.toDomain() }
    }

    override fun getTask(date: LocalDate, taskId: String): DailyTask? {
        ensureDate(date)
        syncRecurringForDate(date)
        return dailyTaskDao.getById(date.toString(), taskId)?.toDomain()
    }

    override fun getTemplate(templateId: String): TaskTemplate? {
        return templateDao.getById(templateId)?.toDomain()
    }

    override fun getTemplates(): List<TaskTemplate> {
        return templateDao.getAll().map { it.toDomain() }
    }

    override fun toggleCompleted(date: LocalDate, taskId: String) {
        val current = getTask(date, taskId) ?: return
        dailyTaskDao.upsert(current.copy(isCompleted = !current.isCompleted).toEntity())
    }

    override fun toggleBig3(date: LocalDate, taskId: String) {
        ensureDate(date)
        val current = getTask(date, taskId) ?: return
        val enable = !current.isBig3
        val activeBig3 = getTasks(date).count { it.isBig3 }
        if (enable && activeBig3 >= 3) return
        dailyTaskDao.upsert(current.copy(isBig3 = enable).toEntity())
    }

    override fun setSchedule(date: LocalDate, taskId: String, schedule: ScheduleBlock?) {
        val current = getTask(date, taskId) ?: return
        val templateId = current.templateId
        if (current.source == DailyTaskSource.RECURRING && templateId != null) {
            val template = templateDao.getById(templateId)?.toDomain() ?: return
            templateDao.upsert(template.copy(defaultSchedule = schedule).toEntity())
            syncTemplateAcrossCachedDates(templateId)
            return
        }
        dailyTaskDao.upsert(current.copy(schedule = schedule).toEntity())
    }

    override fun addTask(date: LocalDate, title: String): DailyTask {
        ensureDate(date)
        val task = DailyTask(
            id = UUID.randomUUID().toString(),
            date = date,
            title = title,
            source = DailyTaskSource.ONE_OFF
        )
        dailyTaskDao.upsert(task.toEntity())
        return task
    }

    override fun upsertTask(input: TaskEditInput): DailyTask {
        ensureDate(input.date)
        val existing = input.taskId?.let { getTask(input.date, it) }
        val existingTemplateId = existing?.templateId ?: input.templateId
        val templateId = when {
            input.recurrenceRule != null -> existingTemplateId ?: "tpl-${UUID.randomUUID()}"
            else -> null
        }
        val dateIso = input.date.toString()

        if (templateId != null) {
            templateDao.upsert(
                TaskTemplate(
                    id = templateId,
                    title = input.title,
                    note = input.note,
                    tags = input.tags,
                    recurrenceRule = input.recurrenceRule,
                    defaultSchedule = input.schedule
                ).toEntity()
            )
        } else if (existingTemplateId != null) {
            // Recurrence turned off: remove generated recurring instances and keep this one as one-off.
            templateDao.deleteById(existingTemplateId)
            dailyTaskDao.deleteByTemplateId(existingTemplateId)
        }

        val targetId = if (templateId != null) {
            "$templateId-${input.date}"
        } else {
            input.taskId ?: UUID.randomUUID().toString()
        }

        if (templateId != null && existing != null && existing.id != targetId) {
            // Normalize legacy/non-template ids to deterministic recurring ids.
            dailyTaskDao.deleteById(dateIso, existing.id)
        }

        val updated = DailyTask(
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
        dailyTaskDao.upsert(updated.toEntity())

        if (templateId != null) {
            syncTemplateAcrossCachedDates(templateId)
        }

        return updated
    }

    override fun deleteTask(date: LocalDate, taskId: String) {
        ensureDate(date)
        val existing = getTask(date, taskId) ?: return
        dailyTaskDao.deleteById(date.toString(), taskId)
        if (existing.templateId != null) {
            templateDao.deleteById(existing.templateId)
            dailyTaskDao.deleteByTemplateId(existing.templateId)
        }
    }

    override fun carryOverIncompleteTasks(fromDate: LocalDate, toDate: LocalDate): Int {
        ensureDate(fromDate)
        ensureDate(toDate)
        syncRecurringForDate(fromDate)
        syncRecurringForDate(toDate)

        val carried = dailyTaskDao.getByDate(fromDate.toString())
            .map { it.toDomain() }
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

        if (carried.isNotEmpty()) {
            dailyTaskDao.upsertAll(carried.map { it.toEntity() })
        }
        return carried.size
    }

    private fun seedIfNeeded() {
        if (templateDao.count() > 0 || dailyTaskDao.count() > 0) return
        templateDao.upsertAll(seedTemplates().map { it.toEntity() })
        dailyTaskDao.upsertAll(seedAnchorTasks().map { it.toEntity() })
    }

    private fun ensureDate(date: LocalDate) {
        if (dailyTaskDao.getByDate(date.toString()).isNotEmpty()) return
        val tasks = when {
            seedInitialData && date == anchorDate -> seedAnchorTasks()
            else -> buildRecurringTasks(date)
        }
        if (tasks.isNotEmpty()) {
            dailyTaskDao.upsertAll(tasks.map { it.toEntity() })
        }
    }

    private fun syncRecurringForDate(date: LocalDate) {
        val dateIso = date.toString()
        val existing = dailyTaskDao.getByDate(dateIso).map { it.toDomain() }
        val existingByTemplateId = existing.mapNotNull { task ->
            task.templateId?.let { templateId -> templateId to task }
        }.toMap()
        val templates = templateDao.getAll().map { it.toDomain() }
        val activeTemplateIds = templates
            .filter { it.occursOn(date.dayOfWeek) }
            .map { it.id }
            .toSet()

        existing
            .filter { it.source == DailyTaskSource.RECURRING && it.templateId !in activeTemplateIds }
            .forEach { dailyTaskDao.deleteById(dateIso, it.id) }

        templates
            .filter { it.id in activeTemplateIds }
            .forEach { template ->
                val previous = existingByTemplateId[template.id]
                dailyTaskDao.upsert(
                    template.toDailyTask(date).copy(
                        isCompleted = previous?.isCompleted ?: false,
                        isBig3 = previous?.isBig3 ?: false
                    ).toEntity()
                )
            }
    }

    private fun syncTemplateAcrossCachedDates(templateId: String) {
        val template = templateDao.getById(templateId)?.toDomain() ?: return
        dailyTaskDao.getCachedDates().map(LocalDate::parse).forEach { date ->
            val dateIso = date.toString()
            val previous = dailyTaskDao.getByDate(dateIso).firstOrNull { it.templateId == templateId }?.toDomain()
            dailyTaskDao.deleteByDateAndTemplateId(dateIso, templateId)
            if (template.occursOn(date.dayOfWeek)) {
                val updated = template.toDailyTask(date)
                dailyTaskDao.upsert(
                    updated.copy(
                        isCompleted = previous?.isCompleted ?: false,
                        isBig3 = previous?.isBig3 ?: false
                    ).toEntity()
                )
            }
        }
    }

    private fun buildRecurringTasks(date: LocalDate): List<DailyTask> {
        return templateDao.getAll().map { it.toDomain() }
            .mapNotNull { template -> if (template.occursOn(date.dayOfWeek)) template.toDailyTask(date) else null }
    }

    private fun seedTemplates(): List<TaskTemplate> = listOf(
        TaskTemplate(
            id = "tpl-standup",
            title = "팀 스탠드업 미팅",
            note = "오늘 할 일과 이슈 공유",
            tags = listOf("Meeting"),
            recurrenceRule = RecurrenceRule(RecurrenceType.DAILY),
            defaultSchedule = ScheduleBlock(17 * 60, 17 * 60 + 45)
        ),
        TaskTemplate(
            id = "tpl-break",
            title = "점심 식사 및 휴식",
            tags = listOf("Break"),
            recurrenceRule = RecurrenceRule(RecurrenceType.DAILY),
            defaultSchedule = ScheduleBlock(18 * 60 + 30, 19 * 60 + 30)
        )
    )

    private fun seedAnchorTasks(): List<DailyTask> {
        val recurring = seedTemplates().mapNotNull { template ->
            if (template.occursOn(anchorDate.dayOfWeek)) template.toDailyTask(anchorDate) else null
        }
        val oneOffs = listOf(
            DailyTask(
                id = "seed-deep-work",
                date = anchorDate,
                title = "딥워크: 핵심 기능 개발",
                tags = listOf("Work", "Focus"),
                isBig3 = true,
                schedule = ScheduleBlock(15 * 60 + 30, 17 * 60)
            ),
            DailyTask(
                id = "seed-ui-review",
                date = anchorDate,
                title = "UI 디자인 리뷰",
                note = "디자이너와 함께 새로운 화면 검토",
                tags = listOf("Meeting", "Design"),
                isBig3 = true,
                schedule = ScheduleBlock(20 * 60, 20 * 60 + 45)
            ),
            DailyTask(
                id = "seed-email",
                date = anchorDate,
                title = "이메일 및 메시지 확인",
                tags = listOf("Admin"),
                schedule = ScheduleBlock(21 * 60 + 30, 22 * 60)
            ),
            DailyTask(
                id = "seed-plan",
                date = anchorDate,
                title = "내일 계획 세우기",
                tags = listOf("Planning")
            ),
            DailyTask(
                id = "seed-docs",
                date = anchorDate,
                title = "프로젝트 문서 업데이트",
                tags = listOf("Documentation")
            )
        )
        return recurring + oneOffs
    }
}

private fun TaskTemplateEntity.toDomain(): TaskTemplate {
    val recurrenceRule = recurrenceType?.let { type ->
        runCatching { RecurrenceType.valueOf(type) }.getOrNull()?.let { recurrenceType ->
            RecurrenceRule(
                type = recurrenceType,
                repeatDays = repeatDaysSerialized.deserializeDays()
            )
        }
    }
    return TaskTemplate(
        id = id,
        title = title,
        note = note,
        tags = tagsSerialized.deserializeTags(),
        recurrenceRule = recurrenceRule,
        defaultSchedule = safeScheduleBlock(defaultStartMinute, defaultEndMinute, reminderEnabled)
    )
}

private fun TaskTemplate.toEntity(): TaskTemplateEntity = TaskTemplateEntity(
    id = id,
    title = title,
    note = note,
    tagsSerialized = tags.serialize(),
    recurrenceType = recurrenceRule?.type?.name,
    repeatDaysSerialized = recurrenceRule?.repeatDays?.serializeDays().orEmpty(),
    defaultStartMinute = defaultSchedule?.startMinute,
    defaultEndMinute = defaultSchedule?.endMinute,
    reminderEnabled = defaultSchedule?.reminderEnabled == true
)

private fun DailyTaskEntity.toDomain(): DailyTask {
    return DailyTask(
        id = id,
        templateId = templateId,
        date = runCatching { LocalDate.parse(dateIso) }.getOrDefault(LocalDate.now()),
        title = title,
        note = note,
        tags = tagsSerialized.deserializeTags(),
        isBig3 = isBig3,
        isCompleted = isCompleted,
        schedule = safeScheduleBlock(startMinute, endMinute, reminderEnabled),
        source = runCatching { DailyTaskSource.valueOf(source) }.getOrDefault(DailyTaskSource.ONE_OFF)
    )
}

private fun DailyTask.toEntity(): DailyTaskEntity = DailyTaskEntity(
    id = id,
    templateId = templateId,
    dateIso = date.toString(),
    title = title,
    note = note,
    tagsSerialized = tags.serialize(),
    isBig3 = isBig3,
    isCompleted = isCompleted,
    startMinute = schedule?.startMinute,
    endMinute = schedule?.endMinute,
    reminderEnabled = schedule?.reminderEnabled == true,
    source = source.name
)

private fun TaskTemplate.toDailyTask(date: LocalDate): DailyTask = DailyTask(
    id = "$id-$date",
    templateId = id,
    date = date,
    title = title,
    note = note,
    tags = tags,
    schedule = defaultSchedule,
    source = DailyTaskSource.RECURRING
)

private fun List<String>.serialize(): String = joinToString("|")
private fun String.deserializeTags(): List<String> = if (isBlank()) emptyList() else split("|").filter { it.isNotBlank() }
private fun Set<DayOfWeek>.serializeDays(): String = map { it.name }.joinToString(",")
private fun String.deserializeDays(): Set<DayOfWeek> =
    if (isBlank()) emptySet()
    else split(",").mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }.toSet()

private fun safeScheduleBlock(startMinute: Int?, endMinute: Int?, reminderEnabled: Boolean): ScheduleBlock? {
    if (startMinute == null || endMinute == null) return null
    return runCatching {
        ScheduleBlock(startMinute = startMinute, endMinute = endMinute, reminderEnabled = reminderEnabled)
    }.getOrNull()
}
