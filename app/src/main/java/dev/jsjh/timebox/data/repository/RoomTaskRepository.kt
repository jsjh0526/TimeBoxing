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
        val templates = templateDao.getAll()
        val tasks = dailyTaskDao.getAll()
        if (templates.isNotEmpty() || tasks.isNotEmpty()) {
            if (containsOnlyDefaultSeedData(templates, tasks)) {
                templateDao.upsertAll(tutorialSeedTemplatesV2().map { it.toEntity() })
                dailyTaskDao.upsertAll(tutorialSeedInitialTasksV2().map { it.toEntity() })
            }
            return
        }
        templateDao.upsertAll(tutorialSeedTemplatesV2().map { it.toEntity() })
        dailyTaskDao.upsertAll(tutorialSeedInitialTasksV2().map { it.toEntity() })
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

    private fun ensureDate(date: LocalDate) {
        if (dailyTaskDao.getByDate(date.toString()).isNotEmpty()) return
        val tasks = when {
            seedInitialData && date == anchorDate -> tutorialSeedAnchorTasksV2()
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

    private fun tutorialSeedTemplatesV2(): List<TaskTemplate> = listOf(
        TaskTemplate(
            id = "tpl-standup",
            title = "습관 계획하기",
            note = "반복 습관은 매일 자동으로 생성돼요.",
            tags = listOf("Habit", "Plan"),
            recurrenceRule = RecurrenceRule(RecurrenceType.DAILY),
            defaultSchedule = null
        )
    )

    private fun tutorialSeedInitialTasksV2(): List<DailyTask> =
        tutorialSeedAnchorTasksV2() + tutorialSeedYesterdayTasksV2()

    private fun tutorialSeedYesterdayTasksV2(): List<DailyTask> = listOf(
        DailyTask(
            id = "seed-yesterday-leftover",
            date = anchorDate.minusDays(1),
            title = "어제 못 끝낸 할 일 넘겨보기",
            note = "Move all to today를 누르면 오늘 할 일로 이월돼요.",
            tags = listOf("Leftover"),
            source = DailyTaskSource.ONE_OFF
        )
    )

    private fun tutorialSeedAnchorTasksV2(): List<DailyTask> {
        val recurring = tutorialSeedTemplatesV2().mapNotNull { template ->
            if (template.occursOn(anchorDate.dayOfWeek)) template.toDailyTask(anchorDate) else null
        }
        val oneOffs = listOf(
            DailyTask(
                id = "seed-deep-work",
                date = anchorDate,
                title = "유튜브에 타임박싱 기법을 검색",
                tags = listOf("Timebox", "Timeboxing"),
                isBig3 = true
            ),
            DailyTask(
                id = "seed-ui-review",
                date = anchorDate,
                title = "오늘의 중요할일 3가지 정하기",
                tags = listOf("Big3", "Focus"),
                isBig3 = true
            ),
            DailyTask(
                id = "seed-email",
                date = anchorDate,
                title = "할 일은 여기에 쏟아두기",
                tags = listOf("BrainDump")
            ),
            DailyTask(
                id = "seed-plan",
                date = anchorDate,
                title = "<< 완료 체크하기",
                tags = listOf("Done")
            ),
            DailyTask(
                id = "seed-docs",
                date = anchorDate,
                title = "BIG3를 지정하기 >>",
                tags = listOf("Big3")
            ),
            DailyTask(
                id = "seed-reminder",
                date = anchorDate,
                title = "클릭해서 알림 켜고 시간설정",
                tags = listOf("Reminder")
            ),
            DailyTask(
                id = "seed-timetable",
                date = anchorDate,
                title = "시간표에서 드래그로 할일을 배치",
                tags = listOf("TimeTable")
            )
        )
        return recurring + oneOffs
    }

    private fun tutorialSeedTemplates(): List<TaskTemplate> = listOf(
        TaskTemplate(
            id = "tpl-standup",
            title = "매일 아침 5분 계획하기",
            note = "반복 습관은 매일 자동으로 나타나요.",
            tags = listOf("Habit", "Plan"),
            recurrenceRule = RecurrenceRule(RecurrenceType.DAILY),
            defaultSchedule = ScheduleBlock(9 * 60, 9 * 60 + 15)
        ),
        TaskTemplate(
            id = "tpl-break",
            title = "하루 마무리 리뷰",
            note = "오늘 끝낸 일과 내일 넘길 일을 정리해보세요.",
            tags = listOf("Review"),
            recurrenceRule = RecurrenceRule(RecurrenceType.DAILY),
            defaultSchedule = ScheduleBlock(21 * 60, 21 * 60 + 30)
        )
    )

    private fun tutorialSeedAnchorTasks(): List<DailyTask> {
        val recurring = tutorialSeedTemplates().mapNotNull { template ->
            if (template.occursOn(anchorDate.dayOfWeek)) template.toDailyTask(anchorDate) else null
        }
        val oneOffs = listOf(
            DailyTask(
                id = "seed-deep-work",
                date = anchorDate,
                title = "오늘의 Big 3 정하기",
                note = "별표를 눌러 오늘 가장 중요한 3가지를 고정해보세요.",
                tags = listOf("Big3", "Focus"),
                isBig3 = true,
                schedule = ScheduleBlock(10 * 60, 10 * 60 + 45)
            ),
            DailyTask(
                id = "seed-ui-review",
                date = anchorDate,
                title = "시간표에 할 일 배치하기",
                note = "시간이 정해진 할 일은 Time Table에 블록으로 보여요.",
                tags = listOf("Timebox", "Guide"),
                isBig3 = true,
                schedule = ScheduleBlock(14 * 60, 15 * 60)
            ),
            DailyTask(
                id = "seed-email",
                date = anchorDate,
                title = "알림 켜고 시작 시간 맞추기",
                note = "시간을 설정하면 알림을 켜서 시작을 놓치지 않을 수 있어요.",
                tags = listOf("Reminder"),
                isBig3 = true,
                schedule = ScheduleBlock(16 * 60, 16 * 60 + 30)
            ),
            DailyTask(
                id = "seed-plan",
                date = anchorDate,
                title = "나중에 할 일은 Brain Dump에 두기",
                note = "시간이 정해지지 않은 일은 아래 목록에 잠시 모아둘 수 있어요.",
                tags = listOf("BrainDump")
            ),
            DailyTask(
                id = "seed-docs",
                date = anchorDate,
                title = "완료 체크하고 하루 흐름 보기",
                note = "끝낸 일은 체크해서 오늘의 진행감을 확인해보세요.",
                tags = listOf("Done")
            )
        )
        return recurring + oneOffs
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
