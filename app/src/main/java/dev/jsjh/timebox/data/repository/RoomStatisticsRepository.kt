package dev.jsjh.timebox.data.repository

import androidx.room.withTransaction
import dev.jsjh.timebox.data.local.database.TaskDatabase
import dev.jsjh.timebox.domain.model.DailyTask
import dev.jsjh.timebox.domain.repository.StatisticsRepository
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RoomStatisticsRepository(private val database: TaskDatabase) : StatisticsRepository {
    override suspend fun getTasksForDates(dates: Collection<LocalDate>): Map<LocalDate, List<DailyTask>> =
        withContext(Dispatchers.IO) {
            val distinctDates = dates.distinct()
            if (distinctDates.isEmpty()) return@withContext emptyMap()
            database.withTransaction {
                val existing = database.dailyTaskDao().getByDates(distinctDates.map { it.toString() })
                    .map { it.toDomain() }.groupBy { it.date }
                val templates = database.taskTemplateDao().getAll().map { it.toDomain() }
                distinctDates.associateWith { statisticsTasksForDate(it, existing[it].orEmpty(), templates) }
            }
        }
}
