package com.example.tvim

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowAlarmManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33])
class AlarmIntegrationTest {
    private lateinit var context: Context
    private lateinit var storage: AlarmStorage

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("alarms_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        storage = AlarmStorage(context)
    }

    @Test fun legacyAlarmsReceiveMissingSoundDefaults() {
        context.getSharedPreferences("alarms_prefs", Context.MODE_PRIVATE).edit()
            .putString("alarms_list", """[{"id":"old","hour":7,"minute":30}]""").commit()
        val alarm = storage.getAlarms().single()
        assertEquals(0.7f, alarm.volume)
        assertTrue(alarm.isEnabled)
        assertTrue(alarm.isVibrationEnabled)
        assertEquals("Morning Light", alarm.ringtoneName)
        assertEquals(MissionType.NONE, alarm.missionType)
    }

    @Test fun corruptStorageDoesNotCrashAndOriginalIsKept() {
        context.getSharedPreferences("alarms_prefs", Context.MODE_PRIVATE).edit()
            .putString("alarms_list", "broken").commit()
        assertTrue(storage.getAlarms().isEmpty())
        assertEquals("broken", context.getSharedPreferences("alarms_prefs", Context.MODE_PRIVATE)
            .getString("alarms_recovery", null))
    }

    @Test fun schedulerKeepsHashCollisionsSeparateAndCancelRemovesOnlyOne() {
        val first = Alarm(id = "Aa", hour = 7, minute = 0)
        val second = Alarm(id = "BB", hour = 8, minute = 0)
        assertEquals(first.id.hashCode(), second.id.hashCode())
        val manager = context.getSystemService(AlarmManager::class.java)
        val shadow = shadowOf(manager)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val scheduler = AlarmScheduler(context)
        assertTrue(scheduler.schedule(first))
        assertTrue(scheduler.schedule(second))
        assertEquals(2, shadow.scheduledAlarms.size)
        scheduler.cancel(first.id)
        assertEquals(1, shadow.scheduledAlarms.size)
    }

    @Test fun disabledAlarmNeverStartsPlayback() {
        val alarm = Alarm(hour = 7, minute = 0, isEnabled = false)
        storage.addAlarm(alarm)
        AlarmTriggerReceiver().onReceive(context, Intent().putExtra("ALARM_ID", alarm.id))
        assertNull(shadowOf(context as android.app.Application).nextStartedService)
    }

    @Test fun oneTimeTriggerStartsServiceAndDisablesAlarm() {
        val alarm = Alarm(hour = 7, minute = 0)
        storage.addAlarm(alarm)
        AlarmTriggerReceiver().onReceive(context, Intent().putExtra("ALARM_ID", alarm.id))
        assertFalse(storage.getAlarms().single().isEnabled)
        val started = shadowOf(context as android.app.Application).nextStartedService
        assertEquals(AlarmService::class.java.name, started.component?.className)
        assertEquals(alarm.id, started.getStringExtra("ALARM_ID"))
    }

    @Test fun repeatingTriggerSchedulesTheNextOccurrence() {
        val manager = context.getSystemService(AlarmManager::class.java)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val alarm = Alarm(hour = 7, minute = 0, days = listOf(1, 3, 5))
        storage.addAlarm(alarm)
        AlarmTriggerReceiver().onReceive(context, Intent().putExtra("ALARM_ID", alarm.id))
        assertTrue(storage.getAlarms().single().isEnabled)
        assertEquals(1, shadowOf(manager).scheduledAlarms.size)
        assertTrue(shadowOf(manager).scheduledAlarms.single().triggerAtTime > System.currentTimeMillis())
    }
}
