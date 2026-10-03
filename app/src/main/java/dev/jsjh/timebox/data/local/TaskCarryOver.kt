package dev.jsjh.timebox.data.local

import dev.jsjh.timebox.data.local.entity.DailyTaskEntity
import dev.jsjh.timebox.domain.model.DailyTaskSource

data class TaskCarryOverChanges(
    val moved: List<DailyTaskEntity>,
    val deleted: List<DailyTaskEntity>
)

private val legacyCarryId = Regex("^carry-(.+)-(\\d{4}-\\d{2}-\\d{2})$")

private fun DailyTaskEntity.carryOverIdentity(): Pair<String, Int> {
    if (source != DailyTaskSource.CARRY_OVER.name) return id to 0
    var root = id
    var depth = 0
    // Older versions copied tasks into nested carry-<id>-<date> IDs.
    while (true) {
        val parent = legacyCarryId.matchEntire(root)?.groupValues?.get(1) ?: break
        root = parent
        depth++
    }
    return root to depth
}

private fun oneOffGroups(history: List<DailyTaskEntity>): Collection<List<DailyTaskEntity>> =
    history.filter { it.source != DailyTaskSource.RECURRING.name && it.templateId == null }
        .groupBy { it.carryOverIdentity().first }.values

internal fun pastIncompleteTasks(
    history: List<DailyTaskEntity>,
    beforeDateIso: String
): List<DailyTaskEntity> = oneOffGroups(history)
    .map { group ->
        group.maxWith(
            compareBy<DailyTaskEntity> { it.dateIso }
                .thenBy { it.carryOverIdentity().second }
                .thenBy { it.isCompleted }
                .thenBy { it.id }
        )
    }
    .filter { it.dateIso < beforeDateIso && !it.isCompleted && it.title.isNotBlank() }
    .sortedWith(compareBy<DailyTaskEntity> { it.dateIso }.thenBy { it.title }.thenBy { it.id })

internal fun planTaskCarryOver(
    history: List<DailyTaskEntity>,
    toDateIso: String,
    confirmedTaskIds: Set<String>
): TaskCarryOverChanges {
    val candidates = pastIncompleteTasks(history, toDateIso).filter { it.id in confirmedTaskIds }
    val identities = candidates.mapTo(hashSetOf()) { it.carryOverIdentity().first }
    val movedIds = candidates.mapTo(hashSetOf()) { it.id }
    val deleted = oneOffGroups(history).flatten().filter {
        it.dateIso < toDateIso && !it.isCompleted && it.id !in movedIds &&
            it.carryOverIdentity().first in identities
    }
    return TaskCarryOverChanges(
        moved = candidates.map {
            it.copy(
                dateIso = toDateIso,
                isBig3 = false,
                startMinute = null,
                endMinute = null,
                reminderEnabled = false,
                source = DailyTaskSource.CARRY_OVER.name
            )
        },
        deleted = deleted
    )
}

internal fun carryOverPredecessors(
    task: DailyTaskEntity,
    history: List<DailyTaskEntity>
): List<DailyTaskEntity> {
    if (task.source != DailyTaskSource.CARRY_OVER.name) return emptyList()
    val identity = task.carryOverIdentity().first
    return oneOffGroups(history).flatten().filter {
        it.dateIso < task.dateIso && !it.isCompleted && it.carryOverIdentity().first == identity
    }
}
