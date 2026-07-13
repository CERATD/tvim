package com.example.tvim

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra("ALARM_ID")
        val alarmIntent = Intent(context, AlarmScreenActivity::class.java).apply {
            putExtra("ALARM_ID", alarmId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        context.startActivity(alarmIntent)
    }
}
