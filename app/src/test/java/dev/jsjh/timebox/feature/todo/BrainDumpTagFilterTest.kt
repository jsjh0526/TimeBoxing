package dev.jsjh.timebox.feature.todo

import dev.jsjh.timebox.domain.model.DailyTask
import java.text.Collator
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainDumpTagFilterTest {
    private val tasks = listOf(
        task("A", listOf("Work", "Urgent", "Work")),
        task("B", listOf("Home")),
        task("C", listOf("Work")),
        task("D", listOf("work", "Urgent")),
        task("E", emptyList())
    )

    @Test fun noSelectionPreservesEveryTaskAndItsOrder() {
        assertEquals(tasks, tasks.filter { it.matchesBrainDumpTags(emptySet()) })
    }

    @Test fun multipleTagsRequireAllTagsNotAnyTag() {
        assertEquals(listOf("A", "C"), matching("Work"))
        assertEquals(listOf("A"), matching("Work", "Urgent"))
        assertTrue(matching("Work", "Home").isEmpty())
    }

    @Test fun tagsAreExactCaseSensitiveNamesAndDuplicatesDoNotMatter() {
        assertEquals(listOf("D"), matching("work"))
        assertFalse(tasks[0].matchesBrainDumpTags(setOf("WORK")))
        assertTrue(tasks[0].matchesBrainDumpTags(setOf("Work", "Urgent")))
        val options = brainDumpTagOptions(tasks, emptySet(), Locale.ENGLISH)
        assertEquals(4, options.size)
        assertEquals(setOf("Work", "work", "Urgent", "Home"), options.toSet())
    }

    @Test fun optionsUseLocaleCollationAndKeepMissingSelectedTags() {
        val options = brainDumpTagOptions(
            listOf(task("A", listOf("zèbre", "école", "avion"))), setOf("disparu"), Locale.FRENCH
        )
        assertEquals(listOf("avion", "disparu", "école", "zèbre"), options)
        val collator = Collator.getInstance(Locale.FRENCH)
        assertTrue(options.zipWithNext().all { (a, b) -> collator.compare(a, b) <= 0 })
        assertEquals(listOf("missing"), brainDumpTagOptions(emptyList(), setOf("missing"), Locale.ENGLISH))
    }

    private fun matching(vararg tags: String) = tasks.filter { it.matchesBrainDumpTags(tags.toSet()) }.map { it.id }
    private fun task(id: String, tags: List<String>) = DailyTask(id, date = LocalDate.of(2026, 10, 9), title = id, tags = tags)
}
