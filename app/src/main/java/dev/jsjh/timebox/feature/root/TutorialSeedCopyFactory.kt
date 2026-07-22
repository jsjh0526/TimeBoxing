package dev.jsjh.timebox.feature.root

import android.content.Context
import dev.jsjh.timebox.R
import dev.jsjh.timebox.data.repository.TutorialSeedCopy
import dev.jsjh.timebox.feature.settings.AppLanguage

internal fun tutorialSeedCopy(context: Context): TutorialSeedCopy = TutorialSeedCopy(
    language = AppLanguage.appliedLanguageCode(context),
    habitTitle = context.getString(R.string.tutorial_habit_title),
    habitNote = context.getString(R.string.tutorial_habit_note),
    leftoverTitle = context.getString(R.string.tutorial_leftover_title),
    leftoverNote = context.getString(R.string.tutorial_leftover_note),
    searchTimeboxingTitle = context.getString(R.string.tutorial_search_timeboxing),
    chooseBig3Title = context.getString(R.string.tutorial_choose_big3),
    brainDumpTitle = context.getString(R.string.tutorial_brain_dump),
    completeTaskTitle = context.getString(R.string.tutorial_complete_task),
    markBig3Title = context.getString(R.string.tutorial_mark_big3),
    enableAlertTitle = context.getString(R.string.tutorial_enable_alert),
    timetableTitle = context.getString(R.string.tutorial_open_timetable)
)
