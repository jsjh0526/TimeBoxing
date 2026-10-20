package dev.jsjh.timebox.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.jsjh.timebox.domain.model.DailyTask
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoWidgetLayoutTest {
    private val today = LocalDate.of(2026, 10, 20)

    @Test fun supportedSizesMatchMinimumsAndCoverNarrowAndWideLayouts() {
        assertEquals(6, TodoWidgetSizes.size)
        listOf(200, 250, 360).forEach { width ->
            listOf(180, 220).forEach { height ->
                assertTrue(DpSize(width.dp, height.dp) in TodoWidgetSizes)
            }
        }
    }

    @Test fun compactLayoutDependsOnBothWidthAndHeight() {
        assertTrue(todoWidgetLayout(DpSize(200.dp, 600.dp), 1f).compact)
        assertTrue(todoWidgetLayout(DpSize(360.dp, 180.dp), 1f).compact)
        assertFalse(todoWidgetLayout(DpSize(250.dp, 220.dp), 1f).compact)
    }

    @Test fun largeTextUsesMoreOfTheAvailableSpaceForContent() {
        TodoWidgetSizes.forEach { size ->
            val normal = todoWidgetLayout(size, 1f)
            listOf(1.5f, 2f).forEach { scale ->
                val largeText = todoWidgetLayout(size, scale)
                assertTrue(largeText.padding < normal.padding)
                assertTrue(largeText.spacing < normal.spacing)
                assertTrue(largeText.cardVerticalPadding < normal.cardVerticalPadding)
            }
        }
    }

    @Test fun idsAreStableAcrossReorderCompletionDeletionAndInsertion() {
        val tasks = (1..25).map { task("task-$it") }
        val ids = todoWidgetItemIds(tasks)
        assertEquals(25, ids.size)
        assertEquals(25, ids.values.toSet().size)
        assertTrue(ids.values.all { it >= 0 })
        assertEquals(ids, todoWidgetItemIds(tasks.reversed()))
        val updated = todoWidgetItemIds(tasks.drop(1).map { it.copy(isCompleted = true) } + task("new"))
        tasks.drop(1).forEach { task -> assertEquals(ids[task.widgetItemKey()], updated[task.widgetItemKey()]) }
    }

    @Test fun dateAndFullStringIdDetermineIdentityNotJavaHashCode() {
        assertEquals("Aa".hashCode(), "BB".hashCode())
        val tasks = listOf(task("Aa"), task("BB"), task("Aa").copy(date = today.minusDays(1)))
        val ids = todoWidgetItemIds(tasks)
        assertEquals(3, ids.values.toSet().size)
        assertEquals(emptyMap<String, Long>(), todoWidgetItemIds(emptyList()))
    }

    private fun task(id: String) = DailyTask(id = id, date = today, title = id)
}
