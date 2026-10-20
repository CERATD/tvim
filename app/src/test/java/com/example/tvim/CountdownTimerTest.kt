package com.example.tvim

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33])
class CountdownTimerTest {
    private lateinit var context: Context
    private lateinit var timer: CountdownTimer

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        listOf("countdown_timer", "alarms_prefs", "alarm_runtime", "alarm_history", "timer_settings").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        timer = CountdownTimer(context)
    }

    @Test fun selectedSoundPersistsAndRunningTimerKeepsItsSound() {
        timer.sound = "library:" + "a".repeat(64)
        assertEquals(timer.sound, CountdownTimer(context).sound)
        val selected = timer.sound
        assertTrue(timer.start(1000))
        val id = timer.snapshot().id
        timer.pause()
        timer.sound = "Morning Light"
        assertTrue(timer.resume())
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
        assertTrue(timer.expire(id))
        assertEquals(selected, CountdownTimer(context).alarm(id)?.ringtoneName)
        timer.finish(id)
        assertTrue(timer.start(1000))
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
        val next = timer.snapshot().id
        assertTrue(timer.expire(next))
        assertEquals("Morning Light", timer.alarm(next)?.ringtoneName)
    }

    @Test fun rejectsInvalidDurationsAndDoesNotReplaceRunningTimer() {
        assertFalse(timer.start(0))
        assertFalse(timer.start(86400000))
        assertTrue(timer.start(60000))
        val id = timer.snapshot().id
        assertFalse(timer.start(5000))
        assertEquals(id, timer.snapshot().id)
    }

    @Test fun schedulesElapsedRealtimeAlarmAndSurvivesNewController() {
        assertTrue(timer.start(60000))
        val scheduled = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.single()
        assertEquals(AlarmManager.ELAPSED_REALTIME_WAKEUP, scheduled.type)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        assertEquals(50000L, CountdownTimer(context).snapshot().remaining)
        assertTrue(AlarmStorage(context).getAlarms().isEmpty())
    }

    @Test fun pauseFreezesCountdownAndResumeSchedulesRemainingDuration() {
        timer.start(60000)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        timer.pause()
        assertTrue(shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty())
        ShadowSystemClock.advanceBy(Duration.ofSeconds(60))
        assertEquals(50000L, timer.snapshot().remaining)
        assertFalse(timer.expire(timer.snapshot().id))
        assertTrue(timer.resume())
        assertFalse(timer.expire(timer.snapshot().id))
        ShadowSystemClock.advanceBy(Duration.ofSeconds(50))
        assertTrue(timer.expire(timer.snapshot().id))
    }

    @Test fun resetRejectsStaleDeliveryAndNewRunHasNewId() {
        timer.start(1000)
        val oldId = timer.snapshot().id
        timer.reset()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        assertFalse(timer.expire(oldId))
        assertTrue(timer.start(5000))
        assertNotEquals(oldId, timer.snapshot().id)
        assertFalse(timer.expire(oldId))
    }

    @Test fun rebootRestoresRemainingWallDuration() {
        timer.start(60000)
        val wallDeadline = System.currentTimeMillis() + 12000
        context.getSharedPreferences("countdown_timer", Context.MODE_PRIVATE).edit()
            .putLong("deadline", 999999999L).putLong("wallDeadline", wallDeadline).commit()
        val upperBound = wallDeadline - System.currentTimeMillis()
        timer.restore(reboot = true)
        val remaining = timer.snapshot().remaining
        val lowerBound = wallDeadline - System.currentTimeMillis()
        assertTrue("Restored duration must account for time spent restoring", remaining in lowerBound..upperBound)
    }

    @Test fun rebootClearsAnAlreadyRingingTimer() {
        timer.start(1000)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
        assertTrue(timer.expire(timer.snapshot().id))
        timer.restore(reboot = true)
        assertEquals("idle", timer.snapshot().status)
        assertTrue(timer.start(5000))
    }

    @Test @Config(sdk = [33]) fun permissionDenialDoesNotCreatePhantomRunningTimer() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertFalse(timer.start(60000))
        assertEquals("idle", timer.snapshot().status)
        assertTrue(shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty())
    }

    @Test fun expiryRingsOnceAndDismissalReturnsToIdleWithoutDeletingAlarms() {
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(3000, 0) }
        val existing = Alarm(id = "keep", hour = 7, minute = 30)
        AlarmStorage(context).saveAlarms(listOf(existing))
        timer.start(1000)
        val id = timer.snapshot().id
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
        assertTrue(timer.expire(id))
        assertFalse(timer.expire(id))
        val controller = Robolectric.buildService(AlarmService::class.java).create()
        try {
            val service = controller.get()
            service.onStartCommand(Intent().putExtra("ALARM_ID", id), 0, 1)
            assertEquals(id, context.getSharedPreferences("alarm_runtime", Context.MODE_PRIVATE).getString("active", null))
            service.onStartCommand(Intent().setAction(AlarmService.STOP).putExtra("ALARM_ID", id), 0, 2)
            assertEquals("idle", timer.snapshot().status)
            assertEquals("dismissed", AlarmHistory(context).events().single().result)
            assertEquals("keep", AlarmStorage(context).getAlarms().single().id)
        } finally { controller.destroy() }
    }
}
