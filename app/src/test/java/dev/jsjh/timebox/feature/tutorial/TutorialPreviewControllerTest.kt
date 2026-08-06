package dev.jsjh.timebox.feature.tutorial

import dev.jsjh.timebox.data.repository.TutorialSeedCopy
import dev.jsjh.timebox.data.repository.createTutorialSeedData
import dev.jsjh.timebox.feature.root.AppTab
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialPreviewControllerTest {
    private val date = LocalDate.of(2026, 8, 5)
    private val seedData = createTutorialSeedData(
        copy = TutorialSeedCopy(
            language = "test",
            habitTitle = "habit",
            habitNote = "habit-note",
            leftoverTitle = "leftover",
            leftoverNote = "leftover-note",
            searchTimeboxingTitle = "search",
            chooseBig3Title = "choose-big3",
            brainDumpTitle = "brain-dump",
            completeTaskTitle = "complete",
            markBig3Title = "mark-big3",
            enableAlertTitle = "reminder",
            timetableTitle = "timetable"
        ),
        anchorDate = date
    )

    @Test
    fun walksEightDisplayedStepsAndThreeSchedulePhasesInsideStepSix() {
        val controller = TutorialPreviewController(seedData, date)

        assertEquals(TutorialStep.BRAIN_DUMP, controller.snapshot.step)
        assertEquals(AppTab.TODO, controller.snapshot.tab)

        controller.activateCurrentTarget()
        assertEquals(TutorialStep.MARK_BIG3, controller.snapshot.step)

        controller.activateCurrentTarget()
        assertTrue(controller.snapshot.task(TutorialTaskIds.MARK_BIG3).isBig3)
        assertEquals(3, controller.snapshot.tasks.count { it.date == date && it.isBig3 })
        assertEquals(TutorialStep.OPEN_EDITOR, controller.snapshot.step)

        controller.activateCurrentTarget()
        assertEquals(TutorialStep.RECURRING_HABIT, controller.snapshot.step)
        assertEquals(TutorialTaskIds.OPEN_EDITOR, controller.snapshot.editorTaskId)

        controller.activateCurrentTarget()
        assertEquals(TutorialStep.TIME_BLOCK, controller.snapshot.step)

        controller.activateCurrentTarget()
        assertEquals(TutorialStep.SCHEDULE_TASK, controller.snapshot.step)
        assertEquals(TutorialSchedulePhase.OPEN_TIMETABLE, controller.snapshot.schedulePhase)
        assertNull(controller.snapshot.editorTaskId)

        controller.activateCurrentTarget()
        assertEquals(AppTab.TIMETABLE, controller.snapshot.tab)
        assertEquals(TutorialSchedulePhase.CHOOSE_TASK, controller.snapshot.schedulePhase)

        assertEquals(
            TutorialEffect.SHOW_PLACED_BLOCK,
            controller.activateCurrentTarget()
        )
        assertEquals(TutorialSchedulePhase.SHOW_PLACED, controller.snapshot.schedulePhase)
        with(controller.snapshot.task(TutorialTaskIds.SCHEDULE).schedule!!) {
            assertEquals(840, startMinute)
            assertEquals(900, endMinute)
            assertTrue(reminderEnabled)
        }
        assertEquals(6, controller.snapshot.step.displayIndex)

        assertTrue(controller.snapshot.advanceOnBackgroundTap)
        controller.activateCurrentTarget()
        assertEquals(TutorialStep.OPEN_HOME, controller.snapshot.step)

        controller.activateCurrentTarget()
        assertEquals(TutorialStep.HOME_NOW, controller.snapshot.step)
        assertEquals(AppTab.HOME, controller.snapshot.tab)

        assertEquals(TutorialEffect.SHOW_COMPLETION, controller.activateCurrentTarget())
        assertTrue(controller.snapshot.completionVisible)

        assertFalse(seedData.tasks.single { it.id == TutorialTaskIds.MARK_BIG3 }.isBig3)
        assertNull(seedData.tasks.single { it.id == TutorialTaskIds.SCHEDULE }.schedule)
    }

    @Test
    fun onlyPlacedSchedulePhaseAcceptsBackgroundTap() {
        val controller = TutorialPreviewController(seedData, date)

        repeat(5) { controller.activateCurrentTarget() }
        assertEquals(TutorialSchedulePhase.OPEN_TIMETABLE, controller.snapshot.schedulePhase)
        assertFalse(controller.snapshot.advanceOnBackgroundTap)

        controller.activateCurrentTarget()
        assertEquals(TutorialSchedulePhase.CHOOSE_TASK, controller.snapshot.schedulePhase)
        assertFalse(controller.snapshot.advanceOnBackgroundTap)

        controller.activateCurrentTarget(currentMinute = 607)
        assertEquals(TutorialSchedulePhase.SHOW_PLACED, controller.snapshot.schedulePhase)
        assertTrue(controller.snapshot.advanceOnBackgroundTap)

        controller.activateCurrentTarget()
        assertEquals(TutorialStep.OPEN_HOME, controller.snapshot.step)
        assertFalse(controller.snapshot.advanceOnBackgroundTap)
    }

    @Test
    fun onlyExplanationStepsAdvanceFromBackgroundTap() {
        assertEquals(
            setOf(
                TutorialStep.BRAIN_DUMP,
                TutorialStep.RECURRING_HABIT,
                TutorialStep.TIME_BLOCK,
                TutorialStep.HOME_NOW
            ),
            TutorialStep.entries.filter { it.advanceOnBackgroundTap }.toSet()
        )
    }

    @Test
    fun scheduleRoundsDownToFifteenMinutesAndAlwaysFitsInDay() {
        assertEquals(600, tutorialScheduleFor(607).startMinute)
        assertEquals(660, tutorialScheduleFor(607).endMinute)
        assertEquals(0, tutorialScheduleFor(-5).startMinute)
        assertEquals(1_380, tutorialScheduleFor(1_439).startMinute)
        assertEquals(1_440, tutorialScheduleFor(1_439).endMinute)
    }

    private fun TutorialPreviewSnapshot.task(id: String) = tasks.single { it.id == id }
}
