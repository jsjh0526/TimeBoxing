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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RoomTaskRepository(
    private val templateDao: TaskTemplateDao,
    private val dailyTaskDao: DailyTaskDao,
    private val anchorDate: LocalDate = LocalDate.now(),
    private val tutorialSeedCopy: TutorialSeedCopy? = null,
    private val seedInitialData: Boolean = false,
    private val onTutorialSeeded: (String) -> Unit = {}
) : TaskRepository, TemplateProvider {
    @Volatile
    private var seedChecked = !seedInitialData
    private val seedLock = Any()

    suspend fun initialize() = io { Unit }

    override suspend fun getTasks(date: LocalDate): List<DailyTask> = io {
        getTasksBlocking(date)
    }

    override suspend fun getTasks(dates: Collection<LocalDate>): Map<LocalDate, List<DailyTask>> = io {
        getTasksBlocking(dates)
    }

    override suspend fun getTaskCompletionCounts(dates: Collection<LocalDate>): Map<LocalDate, Pair<Int, Int>> = io {
        getTaskCompletionCountsBlocking(dates)
    }

    override suspend fun getTask(date: LocalDate, taskId: String): DailyTask? = io {
        getTaskBlocking(date, taskId)
    }

    override suspend fun getTemplate(templateId: String): TaskTemplate? = io {
        getTemplateBlocking(templateId)
    }

    override suspend fun getTemplates(): List<TaskTemplate> = io {
        getTemplatesBlocking()
    }

    override suspend fun toggleCompleted(date: LocalDate, taskId: String) = io {
        toggleCompletedBlocking(date, taskId)
    }

    override suspend fun markCompleted(date: LocalDate, taskId: String) = io {
        markCompletedBlocking(date, taskId)
    }

    override suspend fun toggleBig3(date: LocalDate, taskId: String) = io {
        toggleBig3Blocking(date, taskId)
    }

    override suspend fun setSchedule(date: LocalDate, taskId: String, schedule: ScheduleBlock?) = io {
        setScheduleBlocking(date, taskId, schedule)
    }

    override suspend fun addTask(date: LocalDate, title: String): DailyTask = io {
        addTaskBlocking(date, title)
    }

    override suspend fun upsertTask(input: TaskEditInput): DailyTask = io {
        upsertTaskBlocking(input)
    }

    override suspend fun deleteTask(date: LocalDate, taskId: String) = io {
        deleteTaskBlocking(date, taskId)
    }

    override suspend fun carryOverIncompleteTasks(fromDate: LocalDate, toDate: LocalDate): Int = io {
        carryOverIncompleteTasksBlocking(fromDate, toDate)
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) {
        ensureSeeded()
        block()
    }

    private fun ensureSeeded() {
        if (seedChecked) return
        synchronized(seedLock) {
            if (!seedChecked) {
                seedIfNeeded()
                seedChecked = true
            }
        }
    }

    internal fun getTasksBlocking(date: LocalDate): List<DailyTask> {
        ensureDate(date)
        syncRecurringForDate(date)
        return dailyTaskDao.getByDate(date.toString()).map { it.toDomain() }
    }

    internal fun getTasksBlocking(dates: Collection<LocalDate>): Map<LocalDate, List<DailyTask>> {
        val distinctDates = dates.distinct()
        if (distinctDates.isEmpty()) return emptyMap()
        syncRecurringForDates(distinctDates)
        val grouped = dailyTaskDao
            .getByDates(distinctDates.map { it.toString() })
            .map { it.toDomain() }
            .groupBy { it.date }
        return distinctDates.associateWith { grouped[it].orEmpty() }
    }

    internal fun getTaskCompletionCountsBlocking(dates: Collection<LocalDate>): Map<LocalDate, Pair<Int, Int>> {
        val distinctDates = dates.distinct()
        if (distinctDates.isEmpty()) return emptyMap()
        val existingByDate = dailyTaskDao
            .getByDates(distinctDates.map { it.toString() })
            .map { it.toDomain() }
            .groupBy { it.date }
        val templates = templateDao.getAll().map { it.toDomain() }
        return distinctDates.associateWith { date ->
            completionCountForDate(
                date = date,
                existing = existingByDate[date].orEmpty(),
                templates = templates
            )
        }
    }

    internal fun getTaskBlocking(date: LocalDate, taskId: String): DailyTask? {
        ensureDate(date)
        syncRecurringForDate(date)
        return dailyTaskDao.getById(date.toString(), taskId)?.toDomain()
    }

    internal fun getTemplateBlocking(templateId: String): TaskTemplate? {
        return templateDao.getById(templateId)?.toDomain()
    }

    internal fun getTemplatesBlocking(): List<TaskTemplate> {
        return templateDao.getAll().map { it.toDomain() }
    }

    internal fun toggleCompletedBlocking(date: LocalDate, taskId: String) {
        val current = getTaskBlocking(date, taskId) ?: return
        dailyTaskDao.upsert(current.copy(isCompleted = !current.isCompleted).toEntity())
    }

    internal fun markCompletedBlocking(date: LocalDate, taskId: String) {
        val current = getTaskBlocking(date, taskId) ?: return
        if (current.isCompleted) return
        dailyTaskDao.upsert(current.copy(isCompleted = true).toEntity())
    }

    internal fun toggleBig3Blocking(date: LocalDate, taskId: String) {
        ensureDate(date)
        val current = getTaskBlocking(date, taskId) ?: return
        val enable = !current.isBig3
        val activeBig3 = getTasksBlocking(date).count { it.isBig3 }
        if (enable && activeBig3 >= 3) return
        dailyTaskDao.upsert(current.copy(isBig3 = enable).toEntity())
    }

    internal fun setScheduleBlocking(date: LocalDate, taskId: String, schedule: ScheduleBlock?) {
        val current = getTaskBlocking(date, taskId) ?: return
        val templateId = current.templateId
        if (current.source == DailyTaskSource.RECURRING && templateId != null) {
            val template = templateDao.getById(templateId)?.toDomain() ?: return
            templateDao.upsert(template.copy(defaultSchedule = schedule).toEntity())
            syncTemplateAcrossCachedDates(templateId)
            return
        }
        dailyTaskDao.upsert(current.copy(schedule = schedule).toEntity())
    }

    internal fun addTaskBlocking(date: LocalDate, title: String): DailyTask {
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

    internal fun upsertTaskBlocking(input: TaskEditInput): DailyTask {
        ensureDate(input.date)
        val existing = input.taskId?.let { getTaskBlocking(input.date, it) }
        val existingTemplateId = existing?.templateId ?: input.templateId
        val templateId = when {
            input.recurrenceRule != null -> existingTemplateId ?: "tpl-${UUID.randomUUID()}"
            else -> null
        }
        val dateIso = input.date.toString()

        if (templateId != null) {
            val previousTemplate = existingTemplateId?.let { templateDao.getById(it)?.toDomain() }
            templateDao.upsert(
                TaskTemplate(
                    id = templateId,
                    title = input.title,
                    note = input.note,
                    tags = input.tags,
                    recurrenceRule = input.recurrenceRule,
                    startDate = previousTemplate?.startDate ?: input.date,
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

        val isCarryOverTask = existing?.source == DailyTaskSource.CARRY_OVER ||
            existing?.id?.startsWith("carry-") == true

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
                isCarryOverTask -> DailyTaskSource.CARRY_OVER
                else -> DailyTaskSource.ONE_OFF
            }
        )
        dailyTaskDao.upsert(updated.toEntity())

        if (templateId != null) {
            syncTemplateAcrossCachedDates(templateId)
        }

        return updated
    }

    internal fun deleteTaskBlocking(date: LocalDate, taskId: String) {
        if (taskId.startsWith("template-")) {
            val templateId = taskId.removePrefix("template-")
            templateDao.deleteById(templateId)
            dailyTaskDao.deleteByTemplateId(templateId)
            return
        }
        ensureDate(date)
        val existing = getTaskBlocking(date, taskId) ?: return
        dailyTaskDao.deleteById(date.toString(), taskId)
        carryOverSourceId(existing, date)?.let { sourceId ->
            dailyTaskDao.deleteById(date.minusDays(1).toString(), sourceId)
        }
        if (existing.templateId != null) {
            templateDao.deleteById(existing.templateId)
            dailyTaskDao.deleteByTemplateId(existing.templateId)
        }
    }

    internal fun carryOverIncompleteTasksBlocking(fromDate: LocalDate, toDate: LocalDate): Int {
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
        val copy = requireNotNull(tutorialSeedCopy) {
            "Tutorial seed copy is required when initial data seeding is enabled."
        }
        val seedData = createTutorialSeedData(copy, anchorDate)
        val templates = templateDao.getAll()
        val tasks = dailyTaskDao.getAll()
        if (templates.isNotEmpty() || tasks.isNotEmpty()) {
            if (containsOnlyDefaultSeedData(templates, tasks)) {
                templateDao.upsertAll(seedData.templates.map { it.toEntity() })
                dailyTaskDao.upsertAll(seedData.tasks.map { it.toEntity() })
                onTutorialSeeded(copy.language)
            }
            return
        }
        templateDao.upsertAll(seedData.templates.map { it.toEntity() })
        dailyTaskDao.upsertAll(seedData.tasks.map { it.toEntity() })
        onTutorialSeeded(copy.language)
    }

    private fun containsOnlyDefaultSeedData(
        templates: List<TaskTemplateEntity>,
        tasks: List<DailyTaskEntity>
    ): Boolean {
        val seedTemplateIds = setOf("tpl-standup", "tpl-break")
        return templates.all { it.id in seedTemplateIds } &&
            tasks.all { task ->
                task.id.startsWith("seed-") ||
                    task.templateId in seedTemplateIds ||
                    seedTemplateIds.any { templateId -> task.id.startsWith("$templateId-") }
            }
    }

    private fun carryOverSourceId(task: DailyTask, toDate: LocalDate): String? {
        if (task.source != DailyTaskSource.CARRY_OVER && !task.id.startsWith("carry-")) return null
        val suffix = "-$toDate"
        return task.id
            .takeIf { it.startsWith("carry-") && it.endsWith(suffix) }
            ?.removePrefix("carry-")
            ?.removeSuffix(suffix)
            ?.takeIf { it.isNotBlank() }
    }

    private fun ensureDate(date: LocalDate) {
        if (dailyTaskDao.getByDate(date.toString()).isNotEmpty()) return
        val tasks = buildRecurringTasks(date)
        if (tasks.isNotEmpty()) {
            dailyTaskDao.upsertAll(tasks.map { it.toEntity() })
        }
    }

    private fun syncRecurringForDate(date: LocalDate) {
        syncRecurringForDates(listOf(date))
    }

    private fun syncRecurringForDates(dates: Collection<LocalDate>) {
        val distinctDates = dates.distinct()
        if (distinctDates.isEmpty()) return
        val existingByDate = dailyTaskDao
            .getByDates(distinctDates.map { it.toString() })
            .map { it.toDomain() }
            .groupBy { it.date }
        val templates = templateDao.getAll().map { it.toDomain() }
        val recurringUpserts = mutableListOf<DailyTaskEntity>()

        distinctDates.forEach { date ->
            val dateIso = date.toString()
            val existing = existingByDate[date].orEmpty()
            val existingByTemplateId = existing.mapNotNull { task ->
                task.templateId?.let { templateId -> templateId to task }
            }.toMap()
            val activeTemplates = templates.filter { it.occursOn(date) }
            val activeTemplateIds = activeTemplates.map { it.id }.toSet()

            existing
                .filter { it.source == DailyTaskSource.RECURRING && it.templateId !in activeTemplateIds }
                .forEach { dailyTaskDao.deleteById(dateIso, it.id) }

            activeTemplates.forEach { template ->
                val previous = existingByTemplateId[template.id]
                recurringUpserts +=
                    template.toDailyTask(date).copy(
                        isCompleted = previous?.isCompleted ?: false,
                        isBig3 = previous?.isBig3 ?: false
                    ).toEntity()
            }
        }

        if (recurringUpserts.isNotEmpty()) {
            dailyTaskDao.upsertAll(recurringUpserts)
        }
    }

    private fun syncTemplateAcrossCachedDates(templateId: String) {
        val template = templateDao.getById(templateId)?.toDomain() ?: return
        dailyTaskDao.getCachedDates().map(LocalDate::parse).forEach { date ->
            val dateIso = date.toString()
            val previous = dailyTaskDao.getByDate(dateIso).firstOrNull { it.templateId == templateId }?.toDomain()
            dailyTaskDao.deleteByDateAndTemplateId(dateIso, templateId)
            if (template.occursOn(date)) {
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
            .mapNotNull { template -> if (template.occursOn(date)) template.toDailyTask(date) else null }
    }

    private fun completionCountForDate(
        date: LocalDate,
        existing: List<DailyTask>,
        templates: List<TaskTemplate>
    ): Pair<Int, Int> {
        val activeTemplates = templates.filter { it.occursOn(date) }
        val activeTemplateIds = activeTemplates.map { it.id }.toSet()
        val existingByTemplateId = existing.mapNotNull { task ->
            task.templateId?.let { templateId -> templateId to task }
        }.toMap()
        val visibleExisting = existing.filter { task ->
            task.title.isNotBlank() &&
                (task.source != DailyTaskSource.RECURRING || task.templateId in activeTemplateIds)
        }
        val missingActiveTemplates = activeTemplates.filter { template ->
            template.title.isNotBlank() && template.id !in existingByTemplateId
        }
        return visibleExisting.count { it.isCompleted } to visibleExisting.size + missingActiveTemplates.size
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
        startDate = startDateIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
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
    startDateIso = startDate?.toString(),
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
