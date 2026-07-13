package com.example.tvim

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class AlarmStorage(context: Context) {
    private val prefs = context.getSharedPreferences("alarms_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveAlarms(alarms: List<Alarm>) {
        val json = gson.toJson(alarms)
        prefs.edit().putString("alarms_list", json).apply()
    }

    fun getAlarms(): List<Alarm> {
        val json = prefs.getString("alarms_list", null) ?: return emptyList()
        val type = object : TypeToken<List<Alarm>>() {}.type
        val alarms = gson.fromJson<List<Alarm>>(json, type)
        // Ensure new fields are not null (Gson bypasses Kotlin default values if fields are missing)
        return alarms.map { alarm ->
            alarm.copy(
                vibrationPattern = alarm.vibrationPattern ?: "Стандарт",
                ringtoneName = alarm.ringtoneName ?: "Morning Light"
            )
        }
    }

    fun addAlarm(alarm: Alarm) {
        val alarms = getAlarms().toMutableList()
        alarms.add(alarm)
        saveAlarms(alarms)
    }

    fun updateAlarm(updatedAlarm: Alarm) {
        val alarms = getAlarms().map {
            if (it.id == updatedAlarm.id) updatedAlarm else it
        }
        saveAlarms(alarms)
    }

    fun deleteAlarm(alarmId: String) {
        val alarms = getAlarms().filter { it.id != alarmId }
        saveAlarms(alarms)
    }

    fun saveBedtimeData(data: BedtimeData) {
        val json = gson.toJson(data)
        prefs.edit().putString("bedtime_data", json).apply()
    }

    fun getBedtimeData(): BedtimeData {
        val json = prefs.getString("bedtime_data", null) ?: return BedtimeData()
        return gson.fromJson(json, BedtimeData::class.java)
    }
}
