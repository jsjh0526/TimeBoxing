package dev.jsjh.timebox.feature.root

import androidx.annotation.StringRes
import dev.jsjh.timebox.R

enum class AppTab(
    @StringRes val labelRes: Int,
    val glyph: String
) {
    HOME(R.string.tab_home, "H"),
    TODO(R.string.tab_todo, "T"),
    TIMETABLE(R.string.tab_timetable, "B"),
    SETTINGS(R.string.tab_settings, "S")
}
