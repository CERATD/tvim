package com.example.tvim

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Calendar

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            scheduleAlarms(context)
            return
        }

        val type = intent.getStringExtra("type") ?: return
        showNotification(context, type)
        // Reschedule for tomorrow
        scheduleAlarms(context)
    }

    private fun showNotification(context: Context, type: String) {
        val channelId = "morning_night_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Daily Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val text: String = if (type == "morning") {
            "Доброе утро!"
        } else {
            listOf("Спокойной ночи!", "Сладких снов булочка", "Пушистых снов)").random()
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(if (type == "morning") 1 else 2, notification)
    }

    companion object {
        fun scheduleAlarms(context: Context) {
            scheduleAlarm(context, 11, 0, "morning", 1)
            scheduleAlarm(context, 23, 30, "night", 2)
        }

        private fun scheduleAlarm(context: Context, hour: Int, minute: Int, type: String, requestCode: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                putExtra("type", type)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                if (before(Calendar.getInstance())) {
                    add(Calendar.DATE, 1)
                }
            }

            val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }

            if (canScheduleExact) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } else {
                // Fallback to inexact
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        }
    }
}
