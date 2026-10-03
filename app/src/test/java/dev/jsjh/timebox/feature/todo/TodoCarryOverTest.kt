package dev.jsjh.timebox.feature.todo

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.jsjh.timebox.R
import dev.jsjh.timebox.domain.model.DailyTask
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodoCarryOverTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 10, 3)
    private val past = mutableStateOf(listOf(
        DailyTask(id = "two-days-ago", date = today.minusDays(2), title = "Older task"),
        DailyTask(id = "yesterday", date = today.minusDays(1), title = "Yesterday task")
    ))
    private val busy = mutableStateOf(false)
    private val failed = mutableStateOf(false)
    private val locale = mutableStateOf("en")
    private val moves = mutableListOf<List<String>>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun requiresCountConfirmationAndCancellationDoesNotMoveAnything() {
        setScreen()
        openConfirmation()
        compose.onNodeWithText(localizedContext().getString(R.string.todo_carry_over_message, 2)).assertIsDisplayed()
        assertTrue(moves.isEmpty())
        compose.onNodeWithTag("carry_over_cancel").performClick()
        assertTrue(moves.isEmpty())
        compose.onNodeWithTag("carry_over_confirm").assertDoesNotExist()
    }

    @Test
    fun confirmationKeepsItsSnapshotWhenOtherPastTasksArrive() {
        setScreen()
        openConfirmation()
        compose.runOnIdle { past.value += DailyTask("later-arrival", date = today.minusDays(5), title = "New backlog") }
        compose.onNodeWithTag("carry_over_confirm").performClick()
        assertEquals(listOf(listOf("two-days-ago", "yesterday")), moves)
        compose.onNodeWithTag("carry_over_confirm").assertDoesNotExist()
    }

    @Test
    fun processingDisablesRepeatedMoveRequests() {
        busy.value = true
        setScreen()
        compose.onNodeWithTag("past_tasks_header").performClick()
        compose.onNodeWithTag("past_tasks_move").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(localizedContext().getString(R.string.todo_carry_over_progress)).assertIsDisplayed()
        assertTrue(moves.isEmpty())
    }

    @Test
    fun failureKeepsARetryableMessageAndButton() {
        failed.value = true
        setScreen()
        compose.onNodeWithTag("past_tasks_header").performClick()
        compose.onNodeWithText(localizedContext().getString(R.string.todo_carry_over_failed))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("past_tasks_move").performScrollTo().performClick()
        compose.onNodeWithTag("carry_over_confirm").assertIsDisplayed()
    }

    @Test
    fun emptyBacklogHidesTheSectionAndNeverAutomaticallyMovesTasks() {
        past.value = emptyList()
        setScreen()
        compose.onNodeWithTag("past_tasks_header").assertDoesNotExist()
        assertTrue(moves.isEmpty())
    }

    @Test
    fun largeBacklogScrollsAndShowsOriginalDates() {
        past.value = (0..199).map {
            DailyTask("task-$it", date = today.minusDays((200 - it).toLong()), title = "Past task $it")
        }
        setScreen()
        compose.onNodeWithTag("past_tasks_header").performClick()
        compose.onNodeWithTag("past_tasks_list").performScrollTo().performScrollToIndex(199)
        compose.onNodeWithText("Past task 199").assertIsDisplayed()
        compose.onNodeWithText(today.minusDays(1).toString()).assertIsDisplayed()
        compose.onNodeWithTag("past_tasks_move").performScrollTo().performClick()
        compose.onNodeWithText(localizedContext().getString(R.string.todo_carry_over_message, 200)).assertIsDisplayed()
    }

    @Test
    fun allLocalesWrapHeadersOnNarrowScreens() {
        setScreen(fontScale = 1.4f)
        listOf("en", "ko", "es", "hi", "fil", "zu", "fa", "sn", "fr", "de").forEach { language ->
            compose.runOnIdle { locale.value = language }
            val header = localizedContext().getString(R.string.todo_past_incomplete)
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(header).performScrollTo().assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("No text layout for $language", layouts.isNotEmpty())
            val layout = layouts.first()
            assertFalse(
                "Clipped header for $language: size=${layout.size}, lines=${layout.lineCount}, " +
                    "width=${layout.didOverflowWidth}, height=${layout.didOverflowHeight}, " +
                    "constraints=${layout.layoutInput.constraints}, " +
                    "paragraph=${layout.multiParagraph.width} x ${layout.multiParagraph.height}",
                layout.hasVisualOverflow
            )
        }
        assertTrue(moves.isEmpty())
    }

    @Test fun koreanConfirmation() = checkLocalizedConfirmation("ko")
    @Test fun spanishConfirmation() = checkLocalizedConfirmation("es")
    @Test fun hindiConfirmation() = checkLocalizedConfirmation("hi")
    @Test fun filipinoConfirmation() = checkLocalizedConfirmation("fil")
    @Test fun zuluConfirmation() = checkLocalizedConfirmation("zu")
    @Test fun persianConfirmation() = checkLocalizedConfirmation("fa")
    @Test fun shonaConfirmation() = checkLocalizedConfirmation("sn")
    @Test fun frenchConfirmation() = checkLocalizedConfirmation("fr")
    @Test fun germanConfirmation() = checkLocalizedConfirmation("de")

    private fun checkLocalizedConfirmation(language: String) {
        locale.value = language
        setScreen(fontScale = 1.4f)
        openConfirmation()
        compose.onNodeWithText(localizedContext().getString(R.string.todo_carry_over_message, 2)).assertIsDisplayed()
        compose.onNodeWithTag("carry_over_confirm").assertIsDisplayed()
        compose.onNodeWithTag("carry_over_cancel").assertIsDisplayed().performClick()
        assertTrue(moves.isEmpty())
    }

    private fun openConfirmation() {
        compose.onNodeWithTag("past_tasks_header").performClick()
        compose.onNodeWithTag("past_tasks_move").performScrollTo().performClick()
    }

    private fun localizedContext() = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(this@TodoCarryOverTest.locale.value))
        }
    )

    private fun setScreen(fontScale: Float = 1f) {
        compose.setContent {
            val localized = localizedContext()
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides localized.resources.configuration,
                LocalDensity provides Density(1f, fontScale),
                LocalLayoutDirection provides if (locale.value == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                Box(Modifier.size(width = 320.dp, height = 700.dp)) {
                    TodoScreen(
                        tasks = emptyList(), date = today, pastIncompleteTasks = past.value,
                        carryOverInProgress = busy.value, carryOverFailed = failed.value,
                        onQuickAddTask = {}, onOpenAddTaskEditor = {}, onCarryOverPastTasks = { moves += it },
                        onToggleBig3 = {}, onToggleComplete = {}, onOpenTask = {}, onReorderTask = { _, _ -> }
                    )
                }
            }
        }
    }
}
