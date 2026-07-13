package com.example.tvim

import java.util.UUID

enum class MissionType {
    NONE, MATH, SHAKE, TYPING
}

data class Alarm(
    val id: String = UUID.randomUUID().toString(),
    var hour: Int,
    var minute: Int,
    var days: List<Int> = emptyList(), // 1=ПН, 2=ВТ...
    var isEnabled: Boolean = true,
    var missionType: MissionType = MissionType.NONE,
    var difficulty: Int = 1,
    var label: String = "Будильник",
    var ringtoneName: String = "Morning Light",
    var volume: Float = 0.7f,
    var vibrationPattern: String = "Стандарт",
    var isVibrationEnabled: Boolean = true,
    var gradualWakeup: Int = 0 // в минутах
)

data class BedtimeData(
    val isConfigured: Boolean = false,
    val wakeUpHour: Int = 7,
    val wakeUpMinute: Int = 30,
    val bedtimeHour: Int = 22,
    val bedtimeMinute: Int = 15,
    val wakeUpDays: Set<Int> = setOf(1, 2, 3, 4, 5),
    val bedtimeDays: Set<Int> = setOf(1, 2, 3, 4, 5),
    val sunriseEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val reminderEnabled: Boolean = true,
    val bedtimeModeEnabled: Boolean = true
)
