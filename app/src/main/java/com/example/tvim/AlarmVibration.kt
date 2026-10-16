package com.example.tvim

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Explicit alarm usage permits vibration while the activity is in the background. */
class AlarmVibration(context: Context) {
    @Suppress("DEPRECATION")
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31)
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    else context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    fun available(): Boolean = vibrator?.hasVibrator() == true

    @Suppress("DEPRECATION")
    fun start(pattern: String, repeat: Boolean = true): Boolean {
        stop()
        if (!available() || pattern == "Off") return false
        val timings = when (pattern) {
            "Pulse" -> longArrayOf(0, 200, 200)
            "Knock" -> longArrayOf(0, 100, 100, 100, 700)
            "Melody" -> longArrayOf(0, 150, 150, 300, 150, 600, 500)
            else -> longArrayOf(0, 500, 500)
        }
        val repeatIndex = if (repeat) 0 else -1
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                vibrator?.vibrate(VibrationEffect.createWaveform(timings, repeatIndex),
                    VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_ALARM).build())
            } else {
                val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                if (Build.VERSION.SDK_INT >= 26) vibrator?.vibrate(VibrationEffect.createWaveform(timings, repeatIndex), attributes)
                else vibrator?.vibrate(timings, repeatIndex, attributes)
            }
            true
        } catch (_: SecurityException) { false }
    }

    fun stop() { vibrator?.cancel() }
}
