package com.example.tvim

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

class AlarmScheduler(private val context: Context) {
    private val manager = context.getSystemService(AlarmManager::class.java)
    fun canSchedule(): Boolean = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()

    private fun operation(id: String) = PendingIntent.getBroadcast(context, 0,
        Intent(context, AlarmTriggerReceiver::class.java).apply {
            data = Uri.parse("tvim://alarm/$id")
            putExtra("ALARM_ID", id)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(alarm: Alarm): Boolean {
        cancel(alarm.id)
        if (!alarm.isEnabled) return true
        if (!canSchedule()) return false
        val show = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return try {
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(AlarmTime.next(alarm), show), operation(alarm.id))
            true
        } catch (_: SecurityException) { false }
    }

    fun cancel(id: String) {
        manager.cancel(operation(id))
        val old = PendingIntent.getBroadcast(context, id.hashCode(), Intent(context, AlarmTriggerReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        old?.let { manager.cancel(it); it.cancel() }
    }

    fun scheduleAll() = AlarmStorage(context).getAlarms().forEach { schedule(it) }
}
