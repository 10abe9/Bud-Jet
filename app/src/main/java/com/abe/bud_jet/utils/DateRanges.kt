package com.abe.bud_jet.utils

import java.util.Calendar
import java.util.TimeZone

/** Inclusive [from, to] millisecond range. */
data class MillisRange(val from: Long, val to: Long)

object DateRanges {

    fun startOfDay(nowMillis: Long = System.currentTimeMillis()): Long {
        return Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun month(nowMillis: Long = System.currentTimeMillis()): MillisRange {
        val start = Calendar.getInstance().apply {
            timeInMillis = startOfDay(nowMillis)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val end = (start.clone() as Calendar).apply {
            add(Calendar.MONTH, 1)
            add(Calendar.MILLISECOND, -1)
        }
        return MillisRange(start.timeInMillis, end.timeInMillis)
    }

    /**
     * MaterialDatePicker works with UTC midnights. Converts such a value to local midnight
     * of the same calendar date, so the picked day does not shift by the device time zone.
     */
    fun pickerUtcToLocalMidnight(utcMillis: Long): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
        return Calendar.getInstance().apply {
            clear()
            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    /** Inverse of [pickerUtcToLocalMidnight]: local date to the UTC midnight the picker expects. */
    fun localToPickerUtc(localMillis: Long): Long {
        val local = Calendar.getInstance().apply { timeInMillis = localMillis }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }
}
