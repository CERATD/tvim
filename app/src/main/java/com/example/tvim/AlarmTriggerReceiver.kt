package com.example.tvim

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("ALARM_ID") ?: return
        val storage = AlarmStorage(context)
        val alarm = storage.getAlarms().find { it.id == id && it.isEnabled } ?: return
        ContextCompat.startForegroundService(context,
            Intent(context, AlarmService::class.java).putExtra("ALARM_ID", id))
        if (alarm.days.isEmpty()) storage.updateAlarm(alarm.copy(isEnabled = false))
        else AlarmScheduler(context).schedule(alarm)
    }
}
