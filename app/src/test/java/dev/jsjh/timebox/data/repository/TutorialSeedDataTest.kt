package dev.jsjh.timebox.data.repository

import dev.jsjh.timebox.data.local.dao.DailyTaskDao
import dev.jsjh.timebox.data.local.dao.TaskTemplateDao
import dev.jsjh.timebox.data.local.entity.DailyTaskEntity
import dev.jsjh.timebox.data.local.entity.TaskTemplateEntity
import dev.jsjh.timebox.domain.model.DailyTaskSource
import dev.jsjh.timebox.domain.model.RecurrenceType
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialSeedDataTest {
    private val anchorDate = LocalDate.of(2026, 7, 18)
    private val copy = TutorialSeedCopy(
        language = "test",
        habitTitle = "habit-title",
        habitNote = "habit-note",
        leftoverTitle = "leftover-title",
        leftoverNote = "leftover-note",
        searchTimeboxingTitle = "search-title",
        chooseBig3Title = "big3-title",
        brainDumpTitle = "dump-title",
        completeTaskTitle = "complete-title",
        markBig3Title = "mark-title",
        enableAlertTitle = "alert-title",
        timetableTitle = "timetable-title"
    )

    @Test
    fun createsLocalizedTutorialWithoutChangingBehavioralSeedData() {
        val seed = createTutorialSeedData(copy, anchorDate)

        assertEquals(1, seed.templates.size)
        with(seed.templates.single()) {
            assertEquals("tpl-standup", id)
            assertEquals("habit-title", title)
            assertEquals("habit-note", note)
            assertEquals(listOf("Habit", "Plan"), tags)
            assertEquals(RecurrenceType.DAILY, recurrenceRule?.type)
            assertNull(defaultSchedule)
        }

        assertEquals(
            setOf(
                "tpl-standup-$anchorDate",
                "seed-deep-work",
                "seed-ui-review",
                "seed-email",
                "seed-plan",
                "seed-docs",
                "seed-reminder",
                "seed-timetable",
                "seed-yesterday-leftover"
            ),
            seed.tasks.map { it.id }.toSet()
        )
        assertEquals(
            mapOf(
                "tpl-standup-$anchorDate" to "habit-title",
                "seed-deep-work" to "search-title",
                "seed-ui-review" to "big3-title",
                "seed-email" to "dump-title",
                "seed-plan" to "complete-title",
                "seed-docs" to "mark-title",
                "seed-reminder" to "alert-title",
                "seed-timetable" to "timetable-title",
                "seed-yesterday-leftover" to "leftover-title"
            ),
            seed.tasks.associate { it.id to it.title }
        )

        val recurring = seed.tasks.single { it.id == "tpl-standup-$anchorDate" }
        assertEquals(DailyTaskSource.RECURRING, recurring.source)
        assertEquals("tpl-standup", recurring.templateId)
        assertEquals("habit-title", recurring.title)

        val timeboxing = seed.tasks.single { it.id == "seed-deep-work" }
        assertEquals("search-title", timeboxing.title)
        assertEquals(listOf("Timebox", "Timeboxing"), timeboxing.tags)
        assertTrue(timeboxing.isBig3)

        val big3 = seed.tasks.single { it.id == "seed-ui-review" }
        assertEquals("big3-title", big3.title)
        assertTrue(big3.isBig3)

        val leftover = seed.tasks.single { it.id == "seed-yesterday-leftover" }
        assertEquals(anchorDate.minusDays(1), leftover.date)
        assertEquals("leftover-title", leftover.title)
        assertEquals("leftover-note", leftover.note)
        assertEquals(DailyTaskSource.ONE_OFF, leftover.source)
        assertFalse(leftover.isBig3)
    }

    @Test
    fun seedsNewStorageWithSelectedLanguageAndDoesNotRewriteExistingStorage() = runBlocking {
        val templateDao = FakeTaskTemplateDao()
        val dailyTaskDao = FakeDailyTaskDao()
        val seededLanguages = mutableListOf<String>()
        val localizedCopy = copy.copy(language = "es", habitTitle = "habito")

        RoomTaskRepository(
            templateDao = templateDao,
            dailyTaskDao = dailyTaskDao,
            anchorDate = anchorDate,
            tutorialSeedCopy = localizedCopy,
            seedInitialData = true,
            onTutorialSeeded = seededLanguages::add
        ).initialize()

        assertEquals("habito", templateDao.getById("tpl-standup")?.title)
        assertEquals(listOf("es"), seededLanguages)

        RoomTaskRepository(
            templateDao = templateDao,
            dailyTaskDao = dailyTaskDao,
            anchorDate = anchorDate,
            tutorialSeedCopy = copy.copy(language = "hi", habitTitle = "rewritten"),
            seedInitialData = false,
            onTutorialSeeded = seededLanguages::add
        ).initialize()

        assertEquals("habito", templateDao.getById("tpl-standup")?.title)
        assertEquals(listOf("es"), seededLanguages)
    }
}

private class FakeTaskTemplateDao : TaskTemplateDao {
    private val entities = linkedMapOf<String, TaskTemplateEntity>()

    override fun getAll(): List<TaskTemplateEntity> = entities.values.toList()
    override fun getById(id: String): TaskTemplateEntity? = entities[id]
    override fun upsert(entity: TaskTemplateEntity) {
        entities[entity.id] = entity
    }
    override fun upsertAll(entities: List<TaskTemplateEntity>) = entities.forEach(::upsert)
    override fun deleteById(id: String) {
        entities.remove(id)
    }
    override fun deleteByIds(ids: List<String>) = ids.forEach(entities::remove)
    override fun clearAll() = entities.clear()
    override fun count(): Int = entities.size
}

private class FakeDailyTaskDao : DailyTaskDao {
    private val entities = linkedMapOf<String, DailyTaskEntity>()

    override fun getByDate(dateIso: String): List<DailyTaskEntity> =
        entities.values.filter { it.dateIso == dateIso }

    override fun getByDates(dateIsos: List<String>): List<DailyTaskEntity> =
        entities.values.filter { it.dateIso in dateIsos }

    override fun getById(dateIso: String, taskId: String): DailyTaskEntity? =
        entities[taskId]?.takeIf { it.dateIso == dateIso }

    override fun getAll(): List<DailyTaskEntity> = entities.values.toList()
    override fun getByTemplateId(templateId: String): List<DailyTaskEntity> =
        entities.values.filter { it.templateId == templateId }

    override fun getCachedDates(): List<String> = entities.values.map { it.dateIso }.distinct().sorted()
    override fun upsert(entity: DailyTaskEntity) {
        entities[entity.id] = entity
    }
    override fun upsertAll(entities: List<DailyTaskEntity>) = entities.forEach(::upsert)
    override fun deleteById(dateIso: String, taskId: String) {
        entities[taskId]?.takeIf { it.dateIso == dateIso }?.let { entities.remove(taskId) }
    }
    override fun deleteByIds(taskIds: List<String>) = taskIds.forEach(entities::remove)
    override fun deleteByTemplateId(templateId: String) {
        entities.entries.removeAll { it.value.templateId == templateId }
    }
    override fun deleteByTemplateIds(templateIds: List<String>) {
        entities.entries.removeAll { it.value.templateId in templateIds }
    }
    override fun deleteByDateAndTemplateId(dateIso: String, templateId: String) {
        entities.entries.removeAll { it.value.dateIso == dateIso && it.value.templateId == templateId }
    }
    override fun deleteByDateAndSource(dateIso: String, source: String) {
        entities.entries.removeAll { it.value.dateIso == dateIso && it.value.source == source }
    }
    override fun clearAll() = entities.clear()
    override fun count(): Int = entities.size
}
