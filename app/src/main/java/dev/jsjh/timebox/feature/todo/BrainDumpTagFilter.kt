package dev.jsjh.timebox.feature.todo

import dev.jsjh.timebox.domain.model.DailyTask
import java.text.Collator
import java.util.Locale

internal fun DailyTask.matchesBrainDumpTags(selectedTags: Set<String>): Boolean =
    selectedTags.all { it in tags }

internal fun brainDumpTagOptions(
    tasks: List<DailyTask>,
    selectedTags: Set<String>,
    locale: Locale
): List<String> {
    val collator = Collator.getInstance(locale)
    // Keep orphaned selections available to turn off without changing stored tags.
    return (tasks.flatMap { it.tags } + selectedTags).distinct().sortedWith { a, b ->
        collator.compare(a, b).takeIf { it != 0 } ?: a.compareTo(b)
    }
}
