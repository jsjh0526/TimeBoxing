package dev.jsjh.timebox.data.repository

import dev.jsjh.timebox.domain.model.DailyTask
import dev.jsjh.timebox.domain.model.DailyTaskSource
import dev.jsjh.timebox.domain.model.TaskTemplate
import dev.jsjh.timebox.domain.model.occursOn
import java.time.LocalDate

internal fun statisticsTasksForDate(
    date: LocalDate,
    existing: List<DailyTask>,
    templates: List<TaskTemplate>
): List<DailyTask> {
    val activeTemplates = templates.filter { it.occursOn(date) }
    val activeIds = activeTemplates.mapTo(hashSetOf()) { it.id }
    val existingTemplateIds = existing.mapNotNullTo(hashSetOf()) { it.templateId }
    val visible = existing.filter {
        it.title.isNotBlank() && (it.source != DailyTaskSource.RECURRING || it.templateId in activeIds)
    }
    // Missing habit instances contribute to statistics without materializing DB rows.
    val missing = activeTemplates.filter { it.title.isNotBlank() && it.id !in existingTemplateIds }
    return visible + missing.map { it.toRecurringDailyTask(date) }
}
