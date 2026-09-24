package com.abe.bud_jet.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DateRangesTest {

    private fun millis(year: Int, month: Int, day: Int, hour: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, 0, 0)
        }.timeInMillis

    @Test
    fun monthCoversWholeCalendarMonth() {
        val range = DateRanges.month(millis(2026, Calendar.FEBRUARY, 14, 15))
        assertEquals(millis(2026, Calendar.FEBRUARY, 1), range.from)
        assertEquals(millis(2026, Calendar.MARCH, 1) - 1, range.to)
    }

    @Test
    fun startOfDayDropsTime() {
        assertEquals(millis(2026, Calendar.MAY, 3), DateRanges.startOfDay(millis(2026, Calendar.MAY, 3, 22)))
    }

    @Test
    fun monthContainsNow() {
        val now = System.currentTimeMillis()
        val range = DateRanges.month(now)
        assertTrue(now in range.from..range.to)
    }

    @Test
    fun pickerDatesKeepTheCalendarDayInAnyTimeZone() {
        val original = TimeZone.getDefault()
        try {
            for (zone in listOf("America/New_York", "Europe/Moscow", "Asia/Almaty", "UTC")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                val utcMidnight = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    clear()
                    set(2026, Calendar.OCTOBER, 10)
                }.timeInMillis
                val local = DateRanges.pickerUtcToLocalMidnight(utcMidnight)
                assertEquals(zone, millis(2026, Calendar.OCTOBER, 10), local)
                assertEquals(zone, utcMidnight, DateRanges.localToPickerUtc(local))
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
