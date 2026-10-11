package dev.jsjh.timebox.domain.repository

import dev.jsjh.timebox.domain.model.DailyTask
import java.time.LocalDate

interface StatisticsRepository {
    suspend fun getTasksForDates(dates: Collection<LocalDate>): Map<LocalDate, List<DailyTask>>
}
