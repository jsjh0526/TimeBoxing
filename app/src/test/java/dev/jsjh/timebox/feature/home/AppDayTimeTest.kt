package dev.jsjh.timebox.feature.home

import dev.jsjh.timebox.domain.model.ScheduleBlock
import org.junit.Assert.assertEquals
import org.junit.Test

class AppDayTimeTest {
    @Test
    fun appDayMinute_ordersTimesFromConfiguredDayStart() {
        val dayStartMinute = 3 * 60
        val ordered = listOf(0, 179, 180, 23 * 60, 90)
            .sortedBy { appDayMinute(it, dayStartMinute) }

        assertEquals(listOf(180, 23 * 60, 0, 90, 179), ordered)
    }

    @Test
    fun appDayMinute_midnightStartKeepsClockOrder() {
        val ordered = listOf(23 * 60, 90, 0, 3 * 60)
            .sortedBy { appDayMinute(it, dayStartMinute = 0) }

        assertEquals(listOf(0, 90, 3 * 60, 23 * 60), ordered)
    }

    @Test
    fun appDayEndMinute_preservesDurationAcrossDayBoundary() {
        val schedule = ScheduleBlock(startMinute = 150, endMinute = 210)

        assertEquals(1650, appDayEndMinute(schedule, dayStartMinute = 180))
    }
}
