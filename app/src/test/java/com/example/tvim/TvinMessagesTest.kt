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
        store.save(TvinMessage(0, 8, 0, "Morning custom"))
        store.save(TvinMessage(1, 22, 0, "Night custom"))
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
        assertEquals(1, shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.size)
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
        store.save(TvinMessage(0, 8, 0, "Hello!"))
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
        assertEquals(1, shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.size)
        store.enabled = false
        assertEquals(0, notifications.size())
    }
    @Test fun randomChoicesCoverPoolWithoutConsecutiveRepeats() {
        val message = TvinMessage(0, 8, 0, listOf("First", "Second", "Third"))
        val random = kotlin.random.Random(42)
        val choices = (1..60).map { store.chooseText(message, random) }
        assertEquals(message.texts.toSet(), choices.toSet())
        assertTrue(choices.zipWithNext().all { (a, b) -> a != b })
        val last = choices.last()
        assertNotEquals(last, TvinMessages(context).chooseText(message, random))
    }
    @Test fun variantsPersistTrimAndDeduplicate() {
        store.save(TvinMessage(1, 21, 30, listOf(" Good night ", "Rest well", "Good night")))
        assertEquals(listOf("Good night", "Rest well"), TvinMessages(context).messages()[1].texts)
        store.save(TvinMessage(1, 21, 30, listOf("Only one")))
        repeat(3) { assertEquals("Only one", store.chooseText(store.messages()[1])) }
    }
    @Test fun legacyCustomMessageSurvivesWithoutDefaults() {
        context.getSharedPreferences("tvin_messages", Context.MODE_PRIVATE).edit().putString("text_0", "My greeting").commit()
        assertEquals(listOf("My greeting"), store.messages()[0].texts)
        assertTrue(store.messages()[1].texts.isEmpty())
    }
    @Test fun freshInstallHasNoMessagesOrSchedules() {
        assertTrue(store.messages().all { it.texts.isEmpty() })
        store.enabled = true
        assertEquals(0, shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.size)
        assertNull(store.chooseText(store.messages()[0]))
    }
    @Test fun shippedMessagesAreRemovedOnceButCustomEditsSurvive() {
        val defaults = TvinMessages.legacyDefaultVariants(0, "ru") + TvinMessages.legacyDefaultVariants(0, "en")
        context.getSharedPreferences("tvin_messages", Context.MODE_PRIVATE).edit()
            .putString("variants_0", org.json.JSONArray(defaults + "My edited greeting").toString()).commit()
        assertEquals(listOf("My edited greeting"), store.messages()[0].texts)
        store.save(TvinMessage(0, 8, 0, defaults.first()))
        assertEquals(listOf(defaults.first()), TvinMessages(context).messages()[0].texts)
    }
    @Test fun deletingLastMessageCancelsAndRemainsEmptyAfterRestore() {
        store.enabled = true
        store.save(TvinMessage(0, 8, 0, "Hello"))
        store.save(TvinMessage(0, 8, 0, emptyList()))
        val recreated = TvinMessages(context)
        recreated.restore()
        assertTrue(recreated.messages()[0].texts.isEmpty())
        assertEquals(0, shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.size)
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
