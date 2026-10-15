package com.example.tvim

import android.app.*
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import java.util.LinkedHashSet
import org.json.JSONArray

/** Owns playback independently of the activity; concurrent alarms are queued, never lost. */
class AlarmService : Service() {
    private val queue = LinkedHashSet<String>()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var audio: AlarmAudio
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var active: String? = null
    private var started = 0L
    private val prefs by lazy { getSharedPreferences("alarm_runtime", MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        audio = AlarmAudio(this)
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Tvim · Alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            if (intent.getStringExtra("ALARM_ID") == active) finishCurrent("dismissed")
            if (active == null) stopSelf()
            return if (active == null) START_NOT_STICKY else START_STICKY
        }
        if (intent == null) {
            val savedStart = prefs.getLong("started", 0)
            if (System.currentTimeMillis() - savedStart in 0 until MAX_RING_MS) {
                runCatching {
                    val pending = JSONArray(prefs.getString("queue", "[]"))
                    for (index in 0 until pending.length()) queue.add(pending.getString(index))
                }
            } else {
                prefs.getString("active", null)?.let {
                    AlarmHistory(this).record(it, savedStart, System.currentTimeMillis(), "missed")
                }
                prefs.edit().clear().apply()
            }
        }
        val id = intent?.getStringExtra("ALARM_ID")
        if (id != null && AlarmStorage(this).getAlarms().any { it.id == id }) queue.add(id)
        if (active == null) playNext() else persistQueue()
        return if (active == null) START_NOT_STICKY else START_STICKY
    }

    private fun playNext() {
        handler.removeCallbacksAndMessages(null)
        audio.stop()
        vibrator?.cancel()
        active = queue.firstOrNull()
        val alarm = AlarmStorage(this).getAlarms().find { it.id == active }
        if (alarm == null) {
            active?.let { queue.remove(it) }
            if (queue.isNotEmpty()) { playNext(); return }
            active = null
            prefs.edit().clear().apply()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        val savedStart = prefs.getLong("started", 0)
        started = if (prefs.getString("active", null) == alarm.id &&
            System.currentTimeMillis() - savedStart in 0 until MAX_RING_MS) savedStart else System.currentTimeMillis()
        prefs.edit().putString("active", alarm.id).putLong("started", started).apply()
        persistQueue()
        val screen = PendingIntent.getActivity(this, 0,
            Intent(this, AlarmScreenActivity::class.java).putExtra("ALARM_ID", alarm.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(alarm.label.ifBlank { "Tvim" })
            .setContentText(if (AppSettings(this).language == "ru") "Нажмите, чтобы выключить будильник" else "Tap to dismiss alarm")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true).setContentIntent(screen).setFullScreenIntent(screen, true)
        if (alarm.missionType == MissionType.NONE) {
            val dismiss = PendingIntent.getService(this, 1,
                Intent(this, AlarmService::class.java).setAction(STOP).putExtra("ALARM_ID", alarm.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(0, if (AppSettings(this).language == "ru") "Выключить" else "Dismiss", dismiss)
        }
        startForeground(100, builder.build())
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "tvim:alarm").apply { acquire(16 * 60 * 1000L) }
        audio.start(alarm.ringtoneName, if (alarm.gradualWakeup > 0) 0.01f else alarm.volume)
        if (alarm.isVibrationEnabled && alarm.vibrationPattern != "Off") {
            val pattern = when (alarm.vibrationPattern) {
                "Pulse" -> longArrayOf(0, 200, 200)
                "Knock" -> longArrayOf(0, 100, 100, 100, 700)
                "Melody" -> longArrayOf(0, 150, 150, 300, 150, 600, 500)
                else -> longArrayOf(0, 500, 500)
            }
            if (Build.VERSION.SDK_INT >= 26) vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            else @Suppress("DEPRECATION") vibrator?.vibrate(pattern, 0)
        }
        if (alarm.gradualWakeup > 0) {
            val rampStart = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - started).coerceAtLeast(0)
            val ramp = object : Runnable {
                override fun run() {
                    val fraction = ((SystemClock.elapsedRealtime() - rampStart) / (alarm.gradualWakeup * 60000f)).coerceIn(0f, 1f)
                    audio.volume(alarm.volume * fraction)
                    if (fraction < 1f) handler.postDelayed(this, 1000)
                }
            }
            handler.post(ramp)
        }
        handler.postDelayed({ finishCurrent("missed") }, (MAX_RING_MS - (System.currentTimeMillis() - started)).coerceAtLeast(1))
    }

    private fun persistQueue() {
        prefs.edit().putString("queue", JSONArray(queue.toList()).toString()).apply()
    }

    private fun finishCurrent(result: String) {
        active?.let { id ->
            AlarmHistory(this).record(id, started, System.currentTimeMillis(), result)
            queue.remove(id)
        }
        playNext()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        audio.stop()
        vibrator?.cancel()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        if (queue.isEmpty()) prefs.edit().clear().apply()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null

    companion object {
        private const val MAX_RING_MS = 15 * 60 * 1000L
        const val CHANNEL = "tvim_alarm_v2"
        const val STOP = "com.example.tvim.DISMISS"
    }
}
