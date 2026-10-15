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
        return runCatching {
            val array = com.google.gson.JsonParser().parse(json).asJsonArray
            array.mapNotNull { element ->
                runCatching {
                    val data = element.asJsonObject
                    if (!data.has("id") || data.get("id").isJsonNull || data.get("id").asString.isBlank()) {
                        data.addProperty("id", java.util.UUID.nameUUIDFromBytes(element.toString().toByteArray(Charsets.UTF_8)).toString())
                    }
                    val defaults = gson.toJsonTree(Alarm(hour = 8, minute = 0)).asJsonObject
                    defaults.entrySet().forEach { (key, value) ->
                        if (!data.has(key) || data.get(key).isJsonNull) data.add(key, value)
                    }
                    val alarm = gson.fromJson(data, Alarm::class.java)
                    alarm.copy(
                        hour = alarm.hour.coerceIn(0, 23),
                        minute = alarm.minute.coerceIn(0, 59),
                        days = alarm.days.filter { it in 1..7 }.distinct().sorted(),
                        volume = if (alarm.volume.isFinite()) alarm.volume.coerceIn(0f, 1f) else 0.7f,
                        difficulty = alarm.difficulty.coerceIn(1, 5),
                        gradualWakeup = alarm.gradualWakeup.coerceIn(0, 15)
                    )
                }.getOrNull()
            }.distinctBy { it.id }
        }.getOrElse {
            // Preserve unreadable data for recovery instead of crashing at startup.
            prefs.edit().putString("alarms_recovery", json).apply()
            emptyList()
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
        return runCatching {
            val data = com.google.gson.JsonParser().parse(json).asJsonObject
            gson.toJsonTree(BedtimeData()).asJsonObject.entrySet().forEach { (key, value) ->
                if (!data.has(key) || data.get(key).isJsonNull) data.add(key, value)
            }
            val restored = gson.fromJson(data, BedtimeData::class.java)
            restored.copy(wakeUpHour = restored.wakeUpHour.coerceIn(0, 23),
                wakeUpMinute = restored.wakeUpMinute.coerceIn(0, 59),
                bedtimeHour = restored.bedtimeHour.coerceIn(0, 23),
                bedtimeMinute = restored.bedtimeMinute.coerceIn(0, 59),
                wakeUpDays = restored.wakeUpDays.filter { it in 1..7 }.toSet())
        }.getOrDefault(BedtimeData())
    }
}
