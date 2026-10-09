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
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToIndex
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
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h900dp")
class BrainDumpTagFilterUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val date = LocalDate.of(2026, 10, 9)
    private val tasks = mutableStateOf(listOf(
        task("A", "Work", "Urgent", "Work"), task("B", "Home"), task("C", "Work", "Urgent"),
        task("D", "work"), task("finished", "FinishedOnly").copy(isCompleted = true),
        task("priority", "PriorityOnly").copy(isBig3 = true),
        task("habit", "HabitOnly").copy(source = DailyTaskSource.RECURRING)
    ))
    private val selected = mutableStateOf<Set<String>>(emptySet())
    private val expanded = mutableStateOf(false)
    private val language = mutableStateOf("en")
    private val reordered = mutableListOf<Pair<String, Int>>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun controlsStartCollapsedAndMultiSelectionUsesAndWithoutReordering() {
        setScreen()
        compose.onNodeWithTag("brain_dump_tag_options").assertDoesNotExist()
        expand()
        compose.onNodeWithTag("brain_dump_tag_Work").performClick().assertIsOn()
        compose.onNodeWithTag("brain_dump_tag_Urgent").performClick().assertIsOn()
        compose.onNodeWithTag("todo_task_A").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("todo_task_C").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("todo_task_B").assertDoesNotExist()
        compose.onNodeWithTag("todo_task_D").assertDoesNotExist()
        assertTrue(reordered.isEmpty())
        assertEquals(listOf("A", "B", "C", "D", "finished", "priority", "habit"), tasks.value.map { it.id })
    }

    @Test fun optionsExcludeOtherSectionsDeduplicateAndRemainAfterTheirTasksDisappear() {
        setScreen()
        expand()
        compose.onNodeWithTag("brain_dump_tag_FinishedOnly").assertDoesNotExist()
        compose.onNodeWithTag("brain_dump_tag_PriorityOnly").assertDoesNotExist()
        compose.onNodeWithTag("brain_dump_tag_HabitOnly").assertDoesNotExist()
        compose.onNodeWithTag("brain_dump_tag_Work").performClick()
        compose.runOnIdle { tasks.value = tasks.value.filterNot { "Work" in it.tags } }
        compose.onNodeWithTag("brain_dump_tag_Work").assertIsOn().performClick()
        compose.onNodeWithTag("brain_dump_tag_Work").assertDoesNotExist()
        assertTrue(selected.value.isEmpty())
    }

    @Test fun collapseKeepsActiveCountAndTagDeselectionKeepsPanelExpandedWithoutClearButton() {
        setScreen()
        expand()
        compose.onNodeWithTag("brain_dump_tag_Work").performClick()
        compose.onNodeWithTag("brain_dump_filter_header").performClick()
        compose.onNodeWithTag("brain_dump_tag_options").assertDoesNotExist()
        compose.onNodeWithTag("brain_dump_filter_count", true).assertTextContains("2 / 4")
        compose.onNodeWithTag("brain_dump_filter_active", true).assertIsDisplayed()
        compose.onNodeWithTag("brain_dump_filter_clear").assertDoesNotExist()
        assertFalse(expanded.value)
        assertEquals(setOf("Work"), selected.value)
        expand()
        compose.onNodeWithTag("brain_dump_tag_Work").assertIsOn().performClick()
        compose.onNodeWithTag("brain_dump_filter_clear").assertDoesNotExist()
        compose.onNodeWithTag("brain_dump_tag_options").assertIsDisplayed()
        assertTrue(selected.value.isEmpty())
        assertTrue(expanded.value)
        compose.onNodeWithTag("todo_task_B").performScrollTo().assertIsDisplayed()
    }

    @Test fun noTagsAndNoMatchesHaveDifferentEmptyStates() {
        tasks.value = listOf(task("empty"))
        setScreen()
        expand()
        compose.onNodeWithText(context.getString(R.string.todo_tags_empty)).assertIsDisplayed()
        compose.runOnIdle { selected.value = setOf("missing") }
        compose.onNodeWithText(context.getString(R.string.todo_tags_empty)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.todo_tags_no_matches)).assertIsDisplayed()
        compose.onNodeWithTag("brain_dump_tag_missing").assertIsOn()
    }

    @Test fun filteredDragUsesVisibleIndicesAndChangingSelectionCancelsDrag() {
        tasks.value = tasks.value.filterNot { it.isBig3 || it.source == DailyTaskSource.RECURRING }
        selected.value = setOf("Work", "Urgent")
        setScreen()
        startDrag()
        compose.onNodeWithTag("todo_drag_A", true).performTouchInput { up() }
        assertEquals(listOf("A" to 1), reordered)
        startDrag()
        compose.runOnIdle { selected.value = setOf("Urgent", "Home") }
        compose.onNodeWithTag("todo_list").performTouchInput { up() }
        compose.onNodeWithTag("todo_insertion_indicator").assertDoesNotExist()
        assertEquals(listOf("A" to 1), reordered)
        compose.onNodeWithTag("completed_tasks_header").performScrollTo().performClick()
        compose.onNodeWithTag("todo_task_finished").assertIsDisplayed()
    }

    @Test fun tutorialHidesFilterAndIgnoresSelectionsWithoutMovingTargets() {
        tasks.value = listOf(task(TutorialTaskIds.MARK_BIG3), task(TutorialTaskIds.OPEN_EDITOR))
        selected.value = setOf("missing")
        expanded.value = true
        val registry = TutorialTargetRegistry()
        setScreen(registry = registry)
        compose.onNodeWithTag("brain_dump_filter_header").assertDoesNotExist()
        compose.onNodeWithTag("brain_dump_tag_options").assertDoesNotExist()
        compose.onNodeWithTag("todo_task_${TutorialTaskIds.OPEN_EDITOR}").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(registry[TutorialTarget.MARK_BIG3] != null)
            assertTrue(registry[TutorialTarget.OPEN_EDITOR_TASK] != null)
        }
    }

    @Test fun wrappingLongTagsAndLocalizedEmptyStateFitNarrowLargeTextScreensIncludingRtl() {
        val longTag = "VeryLongProjectTagWithoutAnySpacesForWrapping"
        tasks.value = listOf(task("A", "Alpha", "Home", "Urgent", "Work", "Study", longTag))
        expanded.value = true
        selected.value = setOf("Work", "Urgent")
        setScreen(fontScale = 1.5f, width = 280)
        listOf("en", "ko", "es", "hi", "fil", "zu", "fa", "sn", "fr", "de").forEach { locale ->
            compose.runOnIdle { language.value = locale }
            compose.onNodeWithTag("todo_list").performScrollToIndex(8)
            val countLayouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("brain_dump_filter_count", true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(countLayouts) }
            val countLayout = countLayouts.single()
            // Single-line Text can expose a paragraph canvas wider than its actual glyphs.
            val countTooWide = (0 until countLayout.lineCount).any { line ->
                countLayout.getLineRight(line) - countLayout.getLineLeft(line) > countLayout.size.width + 1f
            }
            assertEquals("Count badge must stay on one line for $locale", 1, countLayout.lineCount)
            assertFalse(
                "Count badge clipped for $locale: size=${countLayout.size}, lines=${countLayout.lineCount}, " +
                    "paragraphHeight=${countLayout.multiParagraph.height}",
                countTooWide || countLayout.didOverflowHeight
            )
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("brain_dump_tag_$longTag").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse("Long tag clipped for $locale", layouts.single().hasVisualOverflow)
            val bounds = listOf("Alpha", "Home", "Urgent", "Work", "Study", longTag).map {
                compose.onNodeWithTag("brain_dump_tag_$it").fetchSemanticsNode().boundsInRoot
            }
            val root = compose.onNodeWithTag("tag_filter_preview").fetchSemanticsNode().boundsInRoot
            assertTrue("Tags overflow for $locale", bounds.all { it.left >= root.left && it.right <= root.right })
            assertTrue("Tags must wrap for $locale", bounds.map { it.top }.distinct().size > 1)
            bounds.forEachIndexed { i, a ->
                bounds.drop(i + 1).forEach { b -> assertFalse("Overlapping tags for $locale", a.overlaps(b)) }
            }
            screenshot(locale)
            compose.runOnIdle { selected.value = setOf("missing") }
            val emptyLabel = localizedContext().getString(R.string.todo_tags_no_matches)
            compose.onNodeWithTag("todo_list").performScrollToNode(hasText(emptyLabel))
            compose.onNodeWithText(emptyLabel)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { result ->
                    val text = mutableListOf<TextLayoutResult>()
                    result(text)
                    assertFalse("Empty state clipped for $locale", text.single().hasVisualOverflow)
                }
            compose.runOnIdle { selected.value = setOf("Work", "Urgent") }
        }
    }

    @Test fun selectedButtonsKeepTheirSizeAndKoreanCollapsedHeaderShowsActiveFilter() {
        language.value = "ko"
        setScreen()
        expand()
        val before = compose.onNodeWithTag("brain_dump_tag_Work").fetchSemanticsNode().boundsInRoot
        val textLayouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("brain_dump_tag_Work")
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(textLayouts) }
        val density = compose.activity.resources.displayMetrics.density
        assertEquals("Chip must not reserve icon space", textLayouts.single().size.width + 24f * density, before.width, 1f)
        assertEquals("Single-line tags must have a compact height", 32f * density, before.height, 1f)
        val badge = compose.onNodeWithTag("brain_dump_filter_count", true).fetchSemanticsNode().boundsInRoot
        assertTrue("Count badge must not inherit the taller default text line", badge.height <= 16f * density + 1f)
        assertHeaderCentered()
        compose.onNodeWithTag("brain_dump_tag_Work").performClick()
        val after = compose.onNodeWithTag("brain_dump_tag_Work").fetchSemanticsNode().boundsInRoot
        assertEquals(before.width, after.width)
        assertEquals(before.height, after.height)
        assertHeaderCentered()
        compose.onNodeWithTag("brain_dump_filter_clear").assertDoesNotExist()
        compose.onNodeWithTag("todo_list").performScrollToIndex(8)
        screenshot("ko-normal-expanded")
        compose.onNodeWithTag("brain_dump_filter_header").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onNodeWithTag("todo_task_A").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("todo_task_C").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("brain_dump_filter_active", true).assertIsDisplayed()
        compose.onNodeWithTag("brain_dump_filter_count", true).assertTextContains("2 / 4")
        screenshot("ko-normal-collapsed")
    }

    private fun assertHeaderCentered() {
        val title = compose.onNodeWithTag("brain_dump_filter_title", true).fetchSemanticsNode().boundsInRoot
        val count = compose.onNodeWithTag("brain_dump_filter_count", true).fetchSemanticsNode().boundsInRoot
        assertEquals("Title and count must share their vertical center", title.center.y, count.center.y, 1f)
        if (selected.value.isNotEmpty()) {
            val active = compose.onNodeWithTag("brain_dump_filter_active", true).fetchSemanticsNode().boundsInRoot
            assertEquals("Filter indicator must align with title", title.center.y, active.center.y, 1f)
        }
    }

    private fun screenshot(locale: String) {
        compose.runOnIdle {
            val view = compose.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertTrue("Blank preview for $locale", pixels.distinct().size > 10)
            val file = File("build/reports/tag-filters/$locale.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private fun startDrag() {
        compose.onNodeWithTag("todo_drag_A", true).performScrollTo().performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("todo_drag_A", true).performTouchInput {
            advanceEventTime(700)
            moveBy(Offset(0f, 180f))
        }
        compose.onNodeWithTag("todo_insertion_indicator").assertIsDisplayed()
    }
    private fun expand() = compose.onNodeWithTag("brain_dump_filter_header").performScrollTo().performClick()
    private fun task(id: String, vararg tags: String) = DailyTask(id, date = date, title = id, tags = tags.toList())
    private fun localizedContext() = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
        setLocale(Locale.forLanguageTag(language.value))
    })

    private fun setScreen(fontScale: Float = 1f, width: Int = 320, registry: TutorialTargetRegistry? = null) {
        compose.setContent {
            val localized = localizedContext()
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides localized.resources.configuration,
                LocalDensity provides Density(density.density, fontScale),
                LocalLayoutDirection provides if (language.value == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                Box(Modifier.size(width.dp, 800.dp).testTag("tag_filter_preview")) {
                    TodoScreen(
                        tasks = tasks.value, date = date,
                        brainDumpSelectedTags = selected.value, brainDumpTagsExpanded = expanded.value,
                        onBrainDumpTagToggle = { selected.value = if (it in selected.value) selected.value - it else selected.value + it },
                        onBrainDumpTagsExpandedChange = { expanded.value = it },
                        onQuickAddTask = {}, onOpenAddTaskEditor = {}, onCarryOverPastTasks = {},
                        onToggleBig3 = {}, onToggleComplete = {}, onOpenTask = {},
                        onReorderTask = { id, index -> reordered += id to index },
                        tutorialTargetRegistry = registry,
                        tutorialFocusTarget = if (registry != null) TutorialTarget.MARK_BIG3 else null
                    )
                }
            }
        }
    }
}
