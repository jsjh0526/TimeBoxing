package dev.jsjh.timebox.feature.tutorial

import dev.jsjh.timebox.domain.repository.TaskRepository
import java.time.LocalDate

enum class TutorialMaterializationResult {
    SUCCESS,
    FAILED,
    NOT_REQUIRED
}

suspend fun materializeTutorialResult(
    repository: TaskRepository,
    date: LocalDate,
    source: TutorialLaunchSource,
    result: TutorialCompletionResult
): TutorialMaterializationResult {
    if (source != TutorialLaunchSource.AUTO_NEW) {
        return TutorialMaterializationResult.NOT_REQUIRED
    }

    return runCatching {
        val big3Task = checkNotNull(repository.getTask(date, TutorialTaskIds.MARK_BIG3))
        val scheduleTask = checkNotNull(repository.getTask(date, TutorialTaskIds.SCHEDULE))

        if (!big3Task.isBig3) {
            repository.toggleBig3(date, TutorialTaskIds.MARK_BIG3)
        }
        if (scheduleTask.schedule != result.schedule) {
            repository.setSchedule(date, TutorialTaskIds.SCHEDULE, result.schedule)
        }

        check(repository.getTask(date, TutorialTaskIds.MARK_BIG3)?.isBig3 == true)
        check(repository.getTask(date, TutorialTaskIds.SCHEDULE)?.schedule == result.schedule)
    }.fold(
        onSuccess = { TutorialMaterializationResult.SUCCESS },
        onFailure = { TutorialMaterializationResult.FAILED }
    )
}
