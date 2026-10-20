package com.example.tvim

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.util.UUID

data class TimerSnapshot(val id: String, val status: String, val duration: Long, val remaining: Long)

/** The system alarm, not the UI countdown, owns timer delivery. */
class CountdownTimer(private val context: Context) {
    private val prefs = context.getSharedPreferences("countdown_timer", Context.MODE_PRIVATE)
    private val manager = context.getSystemService(AlarmManager::class.java)

    var sound: String
        get() = context.getSharedPreferences("timer_settings", Context.MODE_PRIVATE).getString("sound", "Morning Light") ?: "Morning Light"
        set(value) { context.getSharedPreferences("timer_settings", Context.MODE_PRIVATE).edit().putString("sound", value).apply() }

    fun snapshot(): TimerSnapshot {
        val status = prefs.getString("status", "idle") ?: "idle"
        return TimerSnapshot(prefs.getString("id", "") ?: "", status, prefs.getLong("duration", 300000),
            if (status == "running") (prefs.getLong("deadline", 0) - SystemClock.elapsedRealtime()).coerceAtLeast(0)
            else prefs.getLong("remaining", 300000))
    }

    private fun pending(id: String) = PendingIntent.getBroadcast(context, 0,
        Intent(context, TimerReceiver::class.java).setData(Uri.parse("tvim://timer/$id"))
            .putExtra("TIMER_ID", id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun schedule(id: String, deadline: Long): Boolean = try {
        if (!AlarmScheduler(context).canSchedule()) false else {
            manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, pending(id))
            true
        }
    } catch (_: SecurityException) { false }

    fun start(duration: Long): Boolean {
        if (duration !in 1000..86399000 || snapshot().status != "idle") return false
        val id = "timer-" + UUID.randomUUID()
        val deadline = SystemClock.elapsedRealtime() + duration
        if (!schedule(id, deadline)) return false
        prefs.edit().putString("id", id).putString("status", "running")
            .putString("run_sound", sound).putLong("duration", duration).putLong("remaining", duration).putLong("deadline", deadline)
            .putLong("wallDeadline", System.currentTimeMillis() + duration).commit()
        return true
    }

    fun pause() {
        val state = snapshot()
        if (state.status != "running" || state.remaining == 0L) return
        manager.cancel(pending(state.id))
        prefs.edit().putString("status", "paused").putLong("remaining", state.remaining).commit()
    }

    fun resume(): Boolean {
        val state = snapshot()
        if (state.status != "paused") return false
        val deadline = SystemClock.elapsedRealtime() + state.remaining
        if (!schedule(state.id, deadline)) return false
        prefs.edit().putString("status", "running").putLong("deadline", deadline)
            .putLong("wallDeadline", System.currentTimeMillis() + state.remaining).commit()
        return true
    }

    fun reset() {
        val state = snapshot()
        if (state.status == "ringing") return
        if (state.id.isNotEmpty()) manager.cancel(pending(state.id))
        prefs.edit().putString("status", "idle").putLong("remaining", state.duration).commit()
    }

    /** Ignore stale deliveries from cancelled/replaced or paused timers. */
    fun expire(id: String): Boolean {
        val state = snapshot()
        if (state.id != id || state.status != "running") return false
        if (state.remaining > 0) { schedule(id, prefs.getLong("deadline", 0)); return false }
        prefs.edit().putString("status", "ringing").putLong("remaining", 0).commit()
        return true
    }

    fun alarm(id: String?): Alarm? {
        val state = snapshot()
        return if (state.status == "ringing" && id == state.id) Alarm(id = state.id, hour = 0, minute = 0,
            ringtoneName = prefs.getString("run_sound", "Morning Light") ?: "Morning Light",
            label = if (AppSettings(context).language == "ru") "Таймер завершён" else "Timer finished") else null
    }

    fun finish(id: String) {
        if (snapshot().id == id) prefs.edit().putString("status", "idle")
            .putLong("remaining", prefs.getLong("duration", 300000)).commit()
    }

    fun restore(reboot: Boolean = false) {
        val state = snapshot()
        if (reboot && state.status == "ringing") { finish(state.id); return }
        if (state.status != "running") return
        if (reboot) {
            val remaining = (prefs.getLong("wallDeadline", 0) - System.currentTimeMillis()).coerceAtLeast(0)
            prefs.edit().putLong("deadline", SystemClock.elapsedRealtime() + remaining).commit()
        }
        schedule(state.id, prefs.getLong("deadline", 0).coerceAtLeast(SystemClock.elapsedRealtime() + 100))
    }
}

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("TIMER_ID") ?: return
        if (CountdownTimer(context).expire(id)) {
            ContextCompat.startForegroundService(context, Intent(context, AlarmService::class.java).putExtra("ALARM_ID", id))
        }
    }
}
