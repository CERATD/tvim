package com.example.tvim

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AlarmTimeTest {
    private val utc = TimeZone.getTimeZone("UTC")

    private fun instant(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(utc).apply {
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun at(timestamp: Long) = Calendar.getInstance(utc).apply { timeInMillis = timestamp }

    @Test fun oneTimeAlarmMovesToTomorrowAfterItsTime() {
        val now = instant(2026, 10, 14, 8, 31)
        val result = at(AlarmTime.next(Alarm(hour = 8, minute = 30), now, utc))
        assertTrue(result.timeInMillis > now)
        assertTrue(result.get(Calendar.DAY_OF_MONTH) == 15)
        assertTrue(result.get(Calendar.HOUR_OF_DAY) == 8)
        assertTrue(result.get(Calendar.MINUTE) == 30)
    }

    @Test fun repeatingAlarmSkipsUnselectedDaysAndFindsSelectedWeekday() {
        // Wednesday, with Friday and Sunday selected.
        val now = instant(2026, 10, 14, 9, 0)
        val result = at(AlarmTime.next(Alarm(hour = 7, minute = 0, days = listOf(5, 7)), now, utc))
        assertTrue(result.get(Calendar.DAY_OF_MONTH) == 16)
        assertTrue((result.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1 == 5)
    }

    @Test fun selectedTodayIsUsedOnlyWhileTheAlarmTimeIsStillAhead() {
        val now = instant(2026, 10, 14, 6, 59)
        val result = at(AlarmTime.next(Alarm(hour = 7, minute = 0, days = listOf(3)), now, utc))
        assertTrue(result.get(Calendar.DAY_OF_MONTH) == 14)
        assertTrue(result.get(Calendar.HOUR_OF_DAY) == 7)
    }

    @Test fun versionComparisonUsesNumericPartsAndRejectsInvalidTags() {
        assertTrue(VersionOrder.isNewer("v1.10.0", "1.9"))
        assertFalse(VersionOrder.isNewer("v1.4.0", "1.4"))
        assertFalse(VersionOrder.isNewer("latest", "1.4"))
    }
}
