package dev.jsjh.timebox.feature.tutorial

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

enum class TutorialTarget {
    BRAIN_DUMP_INPUT,
    MARK_BIG3,
    OPEN_EDITOR_TASK,
    RECURRING_HABIT,
    TIME_BLOCK,
    TIMETABLE_TAB,
    TIMETABLE_TASK,
    TIMETABLE_PLACED_BLOCK,
    HOME_TAB,
    HOME_NOW
}

object TutorialTaskIds {
    const val MARK_BIG3 = "seed-docs"
    const val OPEN_EDITOR = "seed-reminder"
    const val SCHEDULE = "seed-timetable"
}

@Stable
class TutorialTargetRegistry {
    private val targetBounds = mutableStateMapOf<TutorialTarget, Rect>()

    operator fun get(target: TutorialTarget): Rect? = targetBounds[target]

    internal fun update(target: TutorialTarget, bounds: Rect) {
        if (targetBounds[target] != bounds) {
            targetBounds[target] = bounds
        }
    }
}

fun Modifier.tutorialTarget(
    registry: TutorialTargetRegistry?,
    target: TutorialTarget
): Modifier = if (registry == null) {
    this
} else {
    onGloballyPositioned { coordinates ->
        registry.update(target, coordinates.boundsInRoot())
    }
}
