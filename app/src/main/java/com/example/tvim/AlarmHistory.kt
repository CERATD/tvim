package com.example.tvim

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class AlarmEvent(val label: String, val started: Long, val ended: Long, val result: String)
class AlarmHistory(private val context: Context) {
    private val prefs = context.getSharedPreferences("alarm_history", Context.MODE_PRIVATE)
    fun events(): List<AlarmEvent> = runCatching {
        Gson().fromJson<List<AlarmEvent>>(prefs.getString("events", "[]"), object : TypeToken<List<AlarmEvent>>() {}.type)
            ?: emptyList()
    }.getOrDefault(emptyList())
    fun record(id: String, started: Long, ended: Long, result: String) {
        val label = AlarmStorage(context).getAlarms().find { it.id == id }?.label ?: "Tvim"
        prefs.edit().putString("events", Gson().toJson((listOf(AlarmEvent(label, started, ended, result)) + events()).take(100))).apply()
    }
}
