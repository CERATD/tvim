package com.example.tvim

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    var volume: Float
        get() = prefs.getFloat("alarm_volume", 0.7f)
        set(value) = prefs.edit().putFloat("alarm_volume", value).apply()

    var isVibrationEnabled: Boolean
        get() = prefs.getBoolean("alarm_vibration", true)
        set(value) = prefs.edit().putBoolean("alarm_vibration", value).apply()

    var primaryColor: Color
        get() = Color(prefs.getInt("primary_color", Color(0xFFFF0000).toArgb()))
        set(value) = prefs.edit().putInt("primary_color", value.toArgb()).apply()

    var language: String
        get() = prefs.getString("app_language", "ru") ?: "ru"
        set(value) = prefs.edit().putString("app_language", value).apply()
}
