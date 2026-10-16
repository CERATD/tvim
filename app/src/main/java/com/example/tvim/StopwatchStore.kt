package com.example.tvim

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import org.json.JSONArray

data class StopwatchSnapshot(val running: Boolean, val elapsed: Long, val laps: List<Long>)

class StopwatchStore(context: Context,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    private val boot: () -> Int = { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0) }) {
    private val prefs = context.getSharedPreferences("stopwatch", Context.MODE_PRIVATE)

    fun snapshot(): StopwatchSnapshot {
        var running = prefs.getBoolean("running", false)
        var accumulated = prefs.getLong("accumulated", 0)
        val now = clock()
        if (running && (prefs.getInt("boot", boot()) != boot() || now < prefs.getLong("start", 0))) {
            accumulated = prefs.getLong("checkpoint", accumulated)
            prefs.edit().putBoolean("running", false).putLong("accumulated", accumulated).commit()
            running = false
        }
        val elapsed = accumulated + if (running) (now - prefs.getLong("start", now)).coerceAtLeast(0) else 0
        if (running && now - prefs.getLong("checkpointAt", 0) >= 1000) {
            prefs.edit().putLong("checkpoint", elapsed).putLong("checkpointAt", now).apply()
        }
        val laps = runCatching {
            val array = JSONArray(prefs.getString("laps", "[]"))
            (0 until array.length()).map { array.getLong(it) }
        }.getOrDefault(emptyList())
        return StopwatchSnapshot(running, elapsed, laps)
    }

    fun start() {
        if (snapshot().running) return
        prefs.edit().putBoolean("running", true).putLong("start", clock()).putInt("boot", boot())
            .putLong("checkpoint", prefs.getLong("accumulated", 0)).putLong("checkpointAt", clock()).commit()
    }

    fun pause() {
        val state = snapshot()
        prefs.edit().putBoolean("running", false).putLong("accumulated", state.elapsed).commit()
    }

    fun lap() {
        val state = snapshot()
        if (!state.running || state.laps.size >= 500 || state.elapsed <= (state.laps.lastOrNull() ?: 0)) return
        prefs.edit().putString("laps", JSONArray(state.laps + state.elapsed).toString())
            .putLong("checkpoint", state.elapsed).putLong("checkpointAt", clock()).commit()
    }

    fun reset() { if (!snapshot().running) prefs.edit().clear().commit() }
}
