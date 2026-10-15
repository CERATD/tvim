package com.example.tvim

import java.util.Calendar
import java.util.TimeZone

/** Shared by scheduling and the UI. Days are ISO Monday=1 through Sunday=7. */
object AlarmTime {
    fun next(alarm: Alarm, now: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()): Long {
        require(alarm.hour in 0..23 && alarm.minute in 0..59)
        val days = alarm.days.filter { it in 1..7 }
        val date = Calendar.getInstance(zone).apply { timeInMillis = now }
        repeat(8) {
            val candidate = (date.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, alarm.hour)
                set(Calendar.MINUTE, alarm.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val day = (candidate.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
            if (candidate.timeInMillis > now && (days.isEmpty() || day in days)) return candidate.timeInMillis
            date.add(Calendar.DATE, 1)
        }
        error("No next alarm date")
    }
}
