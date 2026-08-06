package dev.jsjh.timebox.feature.tutorial

import dev.jsjh.timebox.data.repository.TutorialSeedData
import dev.jsjh.timebox.domain.model.DailyTask
import dev.jsjh.timebox.domain.model.ScheduleBlock
import dev.jsjh.timebox.feature.root.AppTab
import java.time.LocalDate

enum class TutorialStep(
    val displayIndex: Int,
    val eventName: String,
    val advanceOnBackgroundTap: Boolean = false
) {
    BRAIN_DUMP(1, "brain_dump", advanceOnBackgroundTap = true),
    MARK_BIG3(2, "big3"),
    OPEN_EDITOR(3, "open_task"),
    RECURRING_HABIT(4, "recurring_habit", advanceOnBackgroundTap = true),
    TIME_BLOCK(5, "time_block", advanceOnBackgroundTap = true),
    SCHEDULE_TASK(6, "schedule"),
    OPEN_HOME(7, "open_home"),
    HOME_NOW(8, "now", advanceOnBackgroundTap = true)
}

enum class TutorialSchedulePhase(val eventName: String) {
    OPEN_TIMETABLE("open_timetable"),
    CHOOSE_TASK("choose_task"),
    SHOW_PLACED("show_placed")
}

enum class TutorialEffect {
    NONE,
    SHOW_PLACED_BLOCK,
    SHOW_COMPLETION
}

data class TutorialPreviewSnapshot(
    val step: TutorialStep,
    val tab: AppTab,
    val tasks: List<DailyTask>,
    val schedulePhase: TutorialSchedulePhase = TutorialSchedulePhase.OPEN_TIMETABLE,
    val editorTaskId: String? = null,
    val completionVisible: Boolean = false
) {
    val target: TutorialTarget
        get() = when (step) {
            TutorialStep.BRAIN_DUMP -> TutorialTarget.BRAIN_DUMP_INPUT
            TutorialStep.MARK_BIG3 -> TutorialTarget.MARK_BIG3
            TutorialStep.OPEN_EDITOR -> TutorialTarget.OPEN_EDITOR_TASK
            TutorialStep.RECURRING_HABIT -> TutorialTarget.RECURRING_HABIT
            TutorialStep.TIME_BLOCK -> TutorialTarget.TIME_BLOCK
            TutorialStep.SCHEDULE_TASK -> when (schedulePhase) {
                TutorialSchedulePhase.OPEN_TIMETABLE -> TutorialTarget.TIMETABLE_TAB
                TutorialSchedulePhase.CHOOSE_TASK -> TutorialTarget.TIMETABLE_TASK
                TutorialSchedulePhase.SHOW_PLACED -> TutorialTarget.TIMETABLE_PLACED_BLOCK
            }
            TutorialStep.OPEN_HOME -> TutorialTarget.HOME_TAB
            TutorialStep.HOME_NOW -> TutorialTarget.HOME_NOW
        }

    val analyticsPhase: String
        get() = if (step == TutorialStep.SCHEDULE_TASK) schedulePhase.eventName else step.eventName

    val advanceOnBackgroundTap: Boolean
        get() = step.advanceOnBackgroundTap ||
            (step == TutorialStep.SCHEDULE_TASK && schedulePhase == TutorialSchedulePhase.SHOW_PLACED)
}

data class TutorialCompletionResult(
    val schedule: ScheduleBlock
)

class TutorialPreviewController(
    seedData: TutorialSeedData,
    private val anchorDate: LocalDate
) {
    var snapshot: TutorialPreviewSnapshot = TutorialPreviewSnapshot(
        step = TutorialStep.BRAIN_DUMP,
        tab = AppTab.TODO,
        tasks = seedData.tasks.sortedWith(compareBy { tutorialTaskOrder[it.id] ?: Int.MAX_VALUE })
    )
        private set

    fun activateCurrentTarget(
        currentMinute: Int = TUTORIAL_DEMO_MINUTE
    ): TutorialEffect = when (snapshot.step) {
        TutorialStep.BRAIN_DUMP -> {
            advanceTo(TutorialStep.MARK_BIG3)
            TutorialEffect.NONE
        }

        TutorialStep.MARK_BIG3 -> {
            updateTask(TutorialTaskIds.MARK_BIG3) { it.copy(isBig3 = true) }
            advanceTo(TutorialStep.OPEN_EDITOR)
            TutorialEffect.NONE
        }

        TutorialStep.OPEN_EDITOR -> {
            snapshot = snapshot.copy(
                step = TutorialStep.RECURRING_HABIT,
                editorTaskId = TutorialTaskIds.OPEN_EDITOR
            )
            TutorialEffect.NONE
        }

        TutorialStep.RECURRING_HABIT -> {
            snapshot = snapshot.copy(
                step = TutorialStep.TIME_BLOCK
            )
            TutorialEffect.NONE
        }

        TutorialStep.TIME_BLOCK -> {
            snapshot = snapshot.copy(
                step = TutorialStep.SCHEDULE_TASK,
                schedulePhase = TutorialSchedulePhase.OPEN_TIMETABLE,
                editorTaskId = null
            )
            TutorialEffect.NONE
        }

        TutorialStep.SCHEDULE_TASK -> when (snapshot.schedulePhase) {
            TutorialSchedulePhase.OPEN_TIMETABLE -> {
                snapshot = snapshot.copy(
                    tab = AppTab.TIMETABLE,
                    schedulePhase = TutorialSchedulePhase.CHOOSE_TASK
                )
                TutorialEffect.NONE
            }

            TutorialSchedulePhase.CHOOSE_TASK -> {
                updateTask(TutorialTaskIds.SCHEDULE) {
                    it.copy(
                        date = anchorDate,
                        schedule = tutorialScheduleFor(currentMinute)
                    )
                }
                snapshot = snapshot.copy(schedulePhase = TutorialSchedulePhase.SHOW_PLACED)
                TutorialEffect.SHOW_PLACED_BLOCK
            }

            TutorialSchedulePhase.SHOW_PLACED -> {
                advanceTo(TutorialStep.OPEN_HOME)
                TutorialEffect.NONE
            }
        }

        TutorialStep.OPEN_HOME -> {
            snapshot = snapshot.copy(
                step = TutorialStep.HOME_NOW,
                tab = AppTab.HOME
            )
            TutorialEffect.NONE
        }

        TutorialStep.HOME_NOW -> {
            snapshot = snapshot.copy(completionVisible = true)
            TutorialEffect.SHOW_COMPLETION
        }
    }

    fun completionResult(): TutorialCompletionResult? {
        val schedule = snapshot.tasks
            .singleOrNull { it.id == TutorialTaskIds.SCHEDULE }
            ?.schedule
            ?: return null
        return TutorialCompletionResult(schedule)
    }

    private fun advanceTo(step: TutorialStep) {
        snapshot = snapshot.copy(step = step)
    }

    private fun updateTask(id: String, transform: (DailyTask) -> DailyTask) {
        snapshot = snapshot.copy(
            tasks = snapshot.tasks.map { task -> if (task.id == id) transform(task) else task }
        )
    }

    private companion object {
        val tutorialTaskOrder = mapOf(
            TutorialTaskIds.MARK_BIG3 to 0,
            TutorialTaskIds.OPEN_EDITOR to 1,
            "seed-email" to 2,
            "seed-plan" to 3,
            TutorialTaskIds.SCHEDULE to 4,
            "seed-deep-work" to 5,
            "seed-ui-review" to 6
        )
    }
}

internal const val TUTORIAL_DEMO_MINUTE = 14 * 60

internal fun tutorialScheduleFor(currentMinute: Int): ScheduleBlock {
    val roundedStart = currentMinute
        .coerceIn(0, MINUTES_PER_DAY - 1)
        .let { it - it.mod(SCHEDULE_INTERVAL_MINUTES) }
        .coerceAtMost(MINUTES_PER_DAY - TUTORIAL_BLOCK_MINUTES)
    return ScheduleBlock(
        startMinute = roundedStart,
        endMinute = roundedStart + TUTORIAL_BLOCK_MINUTES,
        reminderEnabled = true
    )
}

private const val SCHEDULE_INTERVAL_MINUTES = 15
private const val TUTORIAL_BLOCK_MINUTES = 60
private const val MINUTES_PER_DAY = 24 * 60
