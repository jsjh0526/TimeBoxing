package dev.jsjh.timebox.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class AppSettingsTest {
    @Test
    fun effectiveToday_beforeConfiguredStartUsesPreviousDate() {
        val now = LocalDateTime.of(2026, 5, 31, 2, 30)

        val today = effectiveToday(dayStartHour = 3, now = now)

        assertEquals(LocalDate.of(2026, 5, 30), today)
    }

    @Test
    fun effectiveToday_atConfiguredStartUsesCurrentDate() {
        val now = LocalDateTime.of(2026, 5, 31, 3, 0)

        val today = effectiveToday(dayStartHour = 3, now = now)

        assertEquals(LocalDate.of(2026, 5, 31), today)
    }

    @Test
    fun effectiveToday_coercesOutOfRangeStartHour() {
        val now = LocalDateTime.of(2026, 5, 31, 5, 30)

        val today = effectiveToday(dayStartHour = 99, now = now)

        assertEquals(LocalDate.of(2026, 5, 30), today)
    }
}
