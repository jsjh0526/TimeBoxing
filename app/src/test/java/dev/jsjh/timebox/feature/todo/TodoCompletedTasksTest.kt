package dev.jsjh.timebox.feature.todo

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.jsjh.timebox.R
import dev.jsjh.timebox.domain.model.DailyTask
import dev.jsjh.timebox.domain.model.DailyTaskSource
import dev.jsjh.timebox.domain.model.ScheduleBlock
import dev.jsjh.timebox.feature.tutorial.TutorialTarget
import dev.jsjh.timebox.feature.tutorial.TutorialTargetRegistry
import dev.jsjh.timebox.feature.tutorial.TutorialTaskIds
import java.io.File
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodoCompletedTasksTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val date = mutableStateOf(LocalDate.of(2026, 10, 7))
    private val tasks = mutableStateOf(listOf(
        DailyTask("A", date = date.value, title = "Active A"),
        DailyTask("B", date = date.value, title = "Completed B", isCompleted = true, schedule = ScheduleBlock(600, 630)),
        DailyTask("C", date = date.value, title = "Active C")
    ))
    private val language = mutableStateOf("en")
    private val opened = mutableListOf<String>()
    private val stars = mutableListOf<String>()
    private val reordered = mutableListOf<Pair<String, Int>>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun completedTasksStartCollapsedAndHaveNoDragHandle() {
        setScreen()
        compose.onNodeWithTag("completed_tasks_header").performScrollTo().assertIsDisplayed().assertTextContains("1")
        compose.onNodeWithTag("todo_task_B").assertDoesNotExist()
        expand()
        compose.onNodeWithTag("todo_task_B").assertIsDisplayed()
        compose.onNodeWithTag("todo_drag_B", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("completed_tasks_header").performClick()
        compose.onNodeWithTag("todo_task_B").assertDoesNotExist()
    }

    @Test fun sectionIsHiddenWhenThereAreNoCompletedBrainDumpTasks() {
        tasks.value = tasks.value.filterNot { it.isCompleted }
        setScreen()
        compose.onNodeWithTag("completed_tasks_header").assertDoesNotExist()
    }

    @Test fun big3AndHabitsRemainInTheirOriginalSections() {
        tasks.value = listOf(
            DailyTask("big3", date = date.value, title = "Finished priority", isBig3 = true, isCompleted = true),
            DailyTask("habit", date = date.value, title = "Finished habit", source = DailyTaskSource.RECURRING, isCompleted = true)
        )
        setScreen()
        compose.onNodeWithTag("completed_tasks_header").assertDoesNotExist()
        compose.onNodeWithTag("todo_task_big3").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("todo_task_habit").performScrollTo().assertIsDisplayed()
    }

    @Test fun completingAndUndoingMovesOnlyTheDisplayedCard() {
        setScreen()
        compose.onNodeWithTag("todo_complete_A").performScrollTo().performClick()
        compose.onNodeWithTag("todo_task_A").assertDoesNotExist()
        compose.onNodeWithTag("completed_tasks_header").assertTextContains("2")
        expand()
        compose.onNodeWithTag("todo_complete_A").performClick()
        compose.onNodeWithTag("todo_task_A").performScrollTo().assertIsDisplayed()
        assertEquals(listOf("A", "B", "C"), tasks.value.map { it.id })
        assertFalse(tasks.value.first().isCompleted)
        assertEquals(ScheduleBlock(600, 630), tasks.value[1].schedule)
    }

    @Test fun completedCardSupportsDetailsAndBig3Toggle() {
        setScreen()
        expand()
        compose.onNodeWithTag("todo_task_B").performClick()
        assertEquals(listOf("B"), opened)
        compose.onNodeWithTag("todo_big3_B").performClick()
        assertEquals(listOf("B"), stars)
        compose.onNodeWithTag("completed_tasks_header").assertDoesNotExist()
        assertTrue(tasks.value.first { it.id == "B" }.isCompleted)
    }

    @Test fun lastUndoHidesTheSectionAndAChangedDateStartsCollapsed() {
        setScreen()
        expand()
        compose.onNodeWithTag("todo_complete_B").performClick()
        compose.onNodeWithTag("completed_tasks_header").assertDoesNotExist()
        compose.runOnIdle { tasks.value = tasks.value.map { if (it.id == "B") it.copy(isCompleted = true) else it } }
        compose.onNodeWithTag("todo_task_B").assertDoesNotExist()
        expand()
        compose.runOnIdle { date.value = date.value.plusDays(1) }
        compose.onNodeWithTag("completed_tasks_header").assertIsDisplayed()
        compose.onNodeWithTag("todo_task_B").assertDoesNotExist()
    }

    @Test fun completedCardsFollowTheSavedOrderNotTitleOrCompletionTime() {
        tasks.value = listOf(
            DailyTask("Z", date = date.value, title = "Zulu", isCompleted = true),
            DailyTask("A", date = date.value, title = "Alpha", isCompleted = true)
        )
        setScreen()
        expand()
        val z = compose.onNodeWithTag("todo_task_Z").fetchSemanticsNode().boundsInRoot
        val a = compose.onNodeWithTag("todo_task_A").fetchSemanticsNode().boundsInRoot
        assertTrue(z.top < a.top)
    }

    @Test fun draggingReordersOnlyVisibleIncompleteTasks() {
        setScreen()
        startDrag()
        compose.onNodeWithTag("todo_insertion_indicator").assertIsDisplayed()
        compose.onNodeWithTag("todo_drag_A", useUnmergedTree = true).performTouchInput { up() }
        assertEquals(listOf("A" to 1), reordered)
    }

    @Test fun changedListCancelsAnActiveDragWithoutApplyingAStaleIndex() {
        setScreen()
        startDrag()
        compose.onNodeWithTag("todo_insertion_indicator").assertIsDisplayed()
        compose.runOnIdle { tasks.value = tasks.value.filterNot { it.id == "C" } }
        compose.onNodeWithTag("todo_insertion_indicator").assertDoesNotExist()
        compose.onNodeWithTag("todo_drag_A", useUnmergedTree = true).performTouchInput { up() }
        assertTrue(reordered.isEmpty())
        compose.onNodeWithTag("completed_tasks_header").performScrollTo().performClick()
        compose.onNodeWithTag("todo_task_B").assertIsDisplayed()
    }

    @Test fun tutorialFocusStillRevealsTheOriginalBrainDumpTargets() {
        tasks.value = listOf(
            DailyTask(TutorialTaskIds.MARK_BIG3, date = date.value, title = "Mark priority"),
            DailyTask(TutorialTaskIds.OPEN_EDITOR, date = date.value, title = "Open editor"),
            tasks.value[1]
        )
        val registry = TutorialTargetRegistry()
        setScreen(
            registry = registry, focus = TutorialTarget.OPEN_EDITOR_TASK,
            past = listOf(DailyTask("past", date = date.value.minusDays(2), title = "Past task"))
        )
        compose.onNodeWithTag("todo_task_${TutorialTaskIds.OPEN_EDITOR}").assertIsDisplayed()
        compose.onNodeWithTag("todo_big3_${TutorialTaskIds.MARK_BIG3}").assertIsDisplayed()
        compose.runOnIdle {
            val viewport = compose.onNodeWithTag("todo_list").fetchSemanticsNode().boundsInRoot
            val target = requireNotNull(registry[TutorialTarget.OPEN_EDITOR_TASK])
            assertTrue(target.top >= viewport.top && target.bottom <= viewport.bottom)
            assertTrue(registry[TutorialTarget.MARK_BIG3] != null)
        }
    }

    @Test fun removingTheWholeDraggedSectionCancelsWithoutSavingAndUnlocksScrolling() {
        setScreen()
        startDrag()
        compose.onNodeWithTag("todo_insertion_indicator").assertIsDisplayed()
        compose.runOnIdle { tasks.value = tasks.value.filter { it.isCompleted } }
        compose.onNodeWithTag("todo_list").performTouchInput { up() }
        compose.onNodeWithTag("todo_insertion_indicator").assertDoesNotExist()
        assertTrue(reordered.isEmpty())
        expand()
        compose.onNodeWithTag("todo_task_B").assertIsDisplayed()
    }

    @Test fun everyLocaleFitsOnNarrowScreensWithLargeTextAndRtl() {
        setScreen(fontScale = 1.4f)
        expand()
        listOf("en", "ko", "es", "hi", "fil", "zu", "fa", "sn", "fr", "de").forEach { locale ->
            compose.runOnIdle { language.value = locale }
            compose.onNodeWithTag("completed_tasks_header").performScrollTo()
            val layouts = mutableListOf<TextLayoutResult>()
            val label = localizedContext().getString(R.string.todo_completed)
            compose.onNodeWithText(label).assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("No header layout for $locale", layouts.isNotEmpty())
            assertFalse("Clipped completed header for $locale", layouts.first().hasVisualOverflow)
            if (locale in listOf("en", "ko", "fa")) {
                compose.runOnIdle {
                    val view = compose.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
                    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    assertTrue("Blank preview for $locale", pixels.distinct().size > 10)
                    val file = File("build/reports/todo-completed-$locale.png")
                    file.parentFile?.mkdirs()
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
        }
    }

    private fun startDrag() {
        compose.onNodeWithTag("todo_drag_A", useUnmergedTree = true).performScrollTo().performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("todo_drag_A", useUnmergedTree = true).performTouchInput {
            advanceEventTime(700)
            moveBy(Offset(0f, 150f))
        }
    }

    private fun expand() {
        compose.onNodeWithTag("completed_tasks_header").performScrollTo().performClick()
        val firstCompleted = tasks.value.first { it.isCompleted && !it.isBig3 && it.source != DailyTaskSource.RECURRING }
        compose.onNodeWithTag("todo_list").performScrollToNode(hasTestTag("todo_task_${firstCompleted.id}"))
    }

    private fun setScreen(
        fontScale: Float = 1f, registry: TutorialTargetRegistry? = null,
        focus: TutorialTarget? = null, past: List<DailyTask> = emptyList()
    ) {
        compose.setContent {
            val localized = localizedContext()
            val configuration = localized.resources.configuration
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(density.density, fontScale),
                LocalLayoutDirection provides if (language.value == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                Box(Modifier.size(320.dp, 800.dp).testTag("todo_preview")) {
                    TodoScreen(
                        tasks = tasks.value,
                        date = date.value,
                        pastIncompleteTasks = past,
                        onQuickAddTask = {},
                        onOpenAddTaskEditor = {},
                        onCarryOverPastTasks = {},
                        onToggleBig3 = { id ->
                            stars += id
                            tasks.value = tasks.value.map { if (it.id == id) it.copy(isBig3 = !it.isBig3) else it }
                        },
                        onToggleComplete = { id ->
                            tasks.value = tasks.value.map { if (it.id == id) it.copy(isCompleted = !it.isCompleted) else it }
                        },
                        onOpenTask = opened::add,
                        onReorderTask = { id, index -> reordered += id to index },
                        tutorialTargetRegistry = registry,
                        tutorialFocusTarget = focus
                    )
                }
            }
        }
    }

    private fun localizedContext() = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language.value))
        }
    )
}
