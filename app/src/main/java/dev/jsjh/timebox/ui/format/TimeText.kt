package dev.jsjh.timebox.ui.format

import android.content.Context
import android.text.format.DateFormat
import androidx.core.os.ConfigurationCompat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatClock(context: Context, totalMinutes: Int): String {
    val safeMinutes = totalMinutes.coerceIn(0, 24 * 60)
    val isEndOfDay = safeMinutes == 24 * 60
    if (DateFormat.is24HourFormat(context)) {
        val hour = if (isEndOfDay) 24 else safeMinutes / 60
        val minute = if (isEndOfDay) 0 else safeMinutes % 60
        return String.format(Locale.US, "%02d:%02d", hour, minute)
    }

    val minuteOfDay = if (isEndOfDay) 0 else safeMinutes
    val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
    return time.format(DateTimeFormatter.ofPattern("h:mm a", contextLocale(context)))
}

fun formatClock(context: Context, time: LocalTime): String =
    formatClock(context, time.hour * 60 + time.minute)

fun formatClockRange(context: Context, startMinute: Int, endMinute: Int): String =
    "${formatClock(context, startMinute)} - ${formatClock(context, endMinute)}"

private fun contextLocale(context: Context): Locale =
    ConfigurationCompat.getLocales(context.resources.configuration).get(0) ?: Locale.getDefault()
