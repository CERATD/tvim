package com.example.tvim

import org.junit.Assert.assertEquals
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

    @Test fun listOrdersActiveAlarmsByOccurrenceAndDisabledLast() {
        val alarms = listOf(
            Alarm(id = "nine", hour = 9, minute = 0),
            Alarm(id = "ten", hour = 10, minute = 0),
            Alarm(id = "eight", hour = 8, minute = 30),
            Alarm(id = "off", hour = 7, minute = 0, isEnabled = false)
        )
        assertEquals(listOf("eight", "nine", "ten", "off"),
            AlarmTime.ordered(alarms, instant(2026, 10, 21, 23, 12), utc).map { it.id })
        assertEquals(listOf("ten", "eight", "nine", "off"),
            AlarmTime.ordered(alarms, instant(2026, 10, 22, 9, 30), utc).map { it.id })
    }

    @Test fun orderingUsesWeekdaysAndUpdatesAfterToggle() {
        val later = Alarm(id = "friday", hour = 6, minute = 0, days = listOf(5))
        val today = Alarm(id = "today", hour = 10, minute = 0)
        val now = instant(2026, 10, 21, 9, 0)
        assertEquals(listOf("today", "friday"), AlarmTime.ordered(listOf(later, today), now, utc).map { it.id })
        assertEquals(listOf("friday", "today"), AlarmTime.ordered(listOf(later, today.copy(isEnabled = false)), now, utc).map { it.id })
        assertEquals(emptyList<Alarm>(), AlarmTime.ordered(emptyList(), now, utc))
    }

    @Test fun tiesAndDisabledAlarmsHaveStableOrder() {
        val now = instant(2026, 10, 21, 9, 0)
        val alarms = listOf(Alarm(id = "b", hour = 10, minute = 0), Alarm(id = "a", hour = 10, minute = 0),
            Alarm(id = "late-off", hour = 12, minute = 0, isEnabled = false), Alarm(id = "early-off", hour = 8, minute = 0, isEnabled = false))
        assertEquals(listOf("a", "b", "early-off", "late-off"), AlarmTime.ordered(alarms, now, utc).map { it.id })
    }

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
