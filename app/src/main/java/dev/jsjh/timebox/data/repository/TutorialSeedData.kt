package dev.jsjh.timebox.data.repository

import dev.jsjh.timebox.domain.model.DailyTask
import dev.jsjh.timebox.domain.model.DailyTaskSource
import dev.jsjh.timebox.domain.model.RecurrenceRule
import dev.jsjh.timebox.domain.model.RecurrenceType
import dev.jsjh.timebox.domain.model.TaskTemplate
import java.time.LocalDate

data class TutorialSeedCopy(
    val language: String,
    val habitTitle: String,
    val habitNote: String,
    val leftoverTitle: String,
    val leftoverNote: String,
    val searchTimeboxingTitle: String,
    val chooseBig3Title: String,
    val brainDumpTitle: String,
    val completeTaskTitle: String,
    val markBig3Title: String,
    val enableAlertTitle: String,
    val timetableTitle: String
)

data class TutorialSeedData(
    val templates: List<TaskTemplate>,
    val tasks: List<DailyTask>
)

internal fun createTutorialSeedData(
    copy: TutorialSeedCopy,
    anchorDate: LocalDate
): TutorialSeedData {
    val templates = listOf(
        TaskTemplate(
            id = "tpl-standup",
            title = copy.habitTitle,
            note = copy.habitNote,
            tags = listOf("Habit", "Plan"),
            recurrenceRule = RecurrenceRule(RecurrenceType.DAILY),
            defaultSchedule = null
        )
    )
    val recurringTasks = templates.map { template ->
        template.toDailyTask(anchorDate)
    }
    val anchorTasks = listOf(
        DailyTask(
            id = "seed-deep-work",
            date = anchorDate,
            title = copy.searchTimeboxingTitle,
            tags = listOf("Timebox", "Timeboxing"),
            isBig3 = true
        ),
        DailyTask(
            id = "seed-ui-review",
            date = anchorDate,
            title = copy.chooseBig3Title,
            tags = listOf("Big3", "Focus"),
            isBig3 = true
        ),
        DailyTask(
            id = "seed-email",
            date = anchorDate,
            title = copy.brainDumpTitle,
            tags = listOf("BrainDump")
        ),
        DailyTask(
            id = "seed-plan",
            date = anchorDate,
            title = copy.completeTaskTitle,
            tags = listOf("Done")
        ),
        DailyTask(
            id = "seed-docs",
            date = anchorDate,
            title = copy.markBig3Title,
            tags = listOf("Big3")
        ),
        DailyTask(
            id = "seed-reminder",
            date = anchorDate,
            title = copy.enableAlertTitle,
            tags = listOf("Reminder")
        ),
        DailyTask(
            id = "seed-timetable",
            date = anchorDate,
            title = copy.timetableTitle,
            tags = listOf("TimeTable")
        )
    )
    val yesterdayTasks = listOf(
        DailyTask(
            id = "seed-yesterday-leftover",
            date = anchorDate.minusDays(1),
            title = copy.leftoverTitle,
            note = copy.leftoverNote,
            tags = listOf("Leftover"),
            source = DailyTaskSource.ONE_OFF
        )
    )
    return TutorialSeedData(
        templates = templates,
        tasks = recurringTasks + anchorTasks + yesterdayTasks
    )
}

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
