package com.example.tvim

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33])
class TvinMessagesTest {
    private lateinit var context: Context
    private lateinit var store: TvinMessages
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("tvin_messages", Context.MODE_PRIVATE).edit().clear().commit()
        store = TvinMessages(context)
    }
    @Test fun optInSchedulesTwoMessagesAndDisablingCancelsBoth() {
        assertFalse(store.enabled)
        store.restore()
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
        assertEquals(0, alarms.scheduledAlarms.size)
        store.enabled = true
        assertEquals(2, alarms.scheduledAlarms.size)
        store.restore()
        assertEquals(2, alarms.scheduledAlarms.size)
        store.enabled = false
        assertEquals(0, alarms.scheduledAlarms.size)
    }
    @Test fun editsPersistAndReplacePendingSchedule() {
        store.enabled = true
        store.save(TvinMessage(0, 9, 45, "  Hello!  "))
        assertEquals(TvinMessage(0, 9, 45, "Hello!"), TvinMessages(context).messages()[0])
        assertEquals(2, shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.size)
    }
    @Test fun editingWhileDisabledDoesNotSchedule() {
        store.save(TvinMessage(1, 23, 15, "Sleep well"))
        assertEquals(0, shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.size)
    }
    @Test(expected = IllegalArgumentException::class) fun emptyTextIsRejected() {
        store.save(TvinMessage(0, 8, 0, "  "))
    }
    @Test fun staleOrDisabledBroadcastDoesNotNotify() {
        store.enabled = true
        store.deliver(0, 1L)
        store.enabled = false
        store.deliver(0, System.currentTimeMillis())
        assertEquals(0, shadowOf(context.getSystemService(NotificationManager::class.java)).size())
    }
    @Test fun deliverySchedulesNextDayAndCannotDeliverTwice() {
        shadowOf(context as android.app.Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        store.enabled = true
        val at = System.currentTimeMillis()
        val prefs = context.getSharedPreferences("tvin_messages", Context.MODE_PRIVATE)
        prefs.edit().putLong("scheduled_0", at).commit()
        store.deliver(0, at)
        val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))
        assertEquals(1, notifications.size())
        assertEquals(store.messages()[0].text, notifications.getNotification("tvin", 0).extras.getCharSequence("android.text"))
        val next = prefs.getLong("scheduled_0", 0)
        assertTrue(next > at)
        store.deliver(0, at)
        assertEquals(next, prefs.getLong("scheduled_0", 0))
        assertEquals(2, shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.size)
        store.enabled = false
        assertEquals(0, notifications.size())
    }
    @Test fun nextTimeRollsOverAndUsesLocalHourAcrossDst() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Bucharest"))
            val now = Calendar.getInstance().apply { set(2026, Calendar.MARCH, 28, 22, 0, 0); set(Calendar.MILLISECOND, 0) }
            val next = Calendar.getInstance().apply { timeInMillis = TvinMessages.nextTime(22, 0, now.timeInMillis) }
            assertEquals(29, next.get(Calendar.DAY_OF_MONTH))
            assertEquals(22, next.get(Calendar.HOUR_OF_DAY))
            assertEquals(23 * 60 * 60 * 1000L, next.timeInMillis - now.timeInMillis)
        } finally { TimeZone.setDefault(original) }
    }
}
