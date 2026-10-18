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
import androidx.core.app.NotificationManagerCompat
import java.util.Calendar

data class TvinMessage(val id: Int, val hour: Int, val minute: Int, val text: String)

class TvinMessages(private val context: Context) {
    private val prefs = context.getSharedPreferences("tvin_messages", Context.MODE_PRIVATE)
    private val alarms = context.getSystemService(AlarmManager::class.java)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) {
            prefs.edit().putBoolean("enabled", value).apply()
            restore()
            if (!value) (0..1).forEach { NotificationManagerCompat.from(context).cancel("tvin", it) }
        }

    fun messages(): List<TvinMessage> = (0..1).map { id ->
        TvinMessage(id, prefs.getInt("hour_$id", if (id == 0) 8 else 22).coerceIn(0, 23),
            prefs.getInt("minute_$id", 0).coerceIn(0, 59),
            prefs.getString("text_$id", null) ?: Strings.get(
                if (id == 0) "notif_morning" else "notif_night", AppSettings(context).language))
    }

    fun save(message: TvinMessage) {
        require(message.id in 0..1 && message.hour in 0..23 && message.minute in 0..59)
        require(message.text.isNotBlank() && message.text.length <= 300)
        prefs.edit().putInt("hour_${message.id}", message.hour)
            .putInt("minute_${message.id}", message.minute)
            .putString("text_${message.id}", message.text.trim()).apply()
        schedule(message)
    }

    fun restore() = messages().forEach { schedule(it) }

    private fun intent(id: Int, at: Long = 0L) = Intent(context, TvinMessageReceiver::class.java)
        .setAction("com.example.tvim.TVIN_$id").putExtra("id", id).putExtra("at", at)

    private fun schedule(message: TvinMessage) {
        val pending = PendingIntent.getBroadcast(context, message.id, intent(message.id),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pending != null) { alarms.cancel(pending); pending.cancel() }
        prefs.edit().remove("scheduled_${message.id}").apply()
        if (!enabled) return
        val at = nextTime(message.hour, message.minute, System.currentTimeMillis())
        val next = PendingIntent.getBroadcast(context, message.id, intent(message.id, at),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        prefs.edit().putLong("scheduled_${message.id}", at).apply()
        // Greetings are ordinary notifications; they do not need exact-alarm privileges.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, next)
    }

    fun deliver(id: Int, at: Long) {
        if (!enabled || id !in 0..1 || at <= 0L || prefs.getLong("scheduled_$id", 0L) != at) return
        val message = messages().first { it.id == id }
        prefs.edit().remove("scheduled_$id").apply()
        schedule(message)
        // Do not show yesterday's greeting after a long device sleep.
        if (System.currentTimeMillis() - at !in 0L..(2 * 60 * 60 * 1000L)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Tvin", NotificationManager.IMPORTANCE_DEFAULT))
        }
        if (!notificationsAllowed()) return
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Tvin")
            .setContentText(message.text).setStyle(NotificationCompat.BigTextStyle().bigText(message.text))
            .setContentIntent(open).setAutoCancel(true).build()
        try { manager.notify("tvin", id, notification) } catch (_: SecurityException) { /* Permission revoked. */ }
    }

    fun notificationsAllowed(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 26 || context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    companion object {
        const val CHANNEL = "tvin_messages"
        fun nextTime(hour: Int, minute: Int, now: Long): Long = Calendar.getInstance().run {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
            timeInMillis
        }
    }
}

class TvinMessageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TvinMessages(context).deliver(intent.getIntExtra("id", -1), intent.getLongExtra("at", 0))
    }
}
