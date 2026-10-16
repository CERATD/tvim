package com.example.tvim

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri

object AlarmSounds {
    val builtIns = linkedMapOf(
        "Morning Light" to R.raw.morning_light, "Deep Moon" to R.raw.deep_moon,
        "Solar Rise" to R.raw.solar_rise, "Star Night" to R.raw.star_night,
        "Soft Piano" to R.raw.soft_piano, "Minimal" to R.raw.minimal,
        "Classic Bell" to R.raw.classic_bell, "Forest Birds" to R.raw.forest_birds,
        "Rain Drops" to R.raw.rain_drops, "Retro Arcade" to R.raw.retro_arcade
    )
    fun description(sound: String, lang: String): String {
        val labels = when (sound) {
            "Morning Light" -> "Светлые колокольчики" to "Bright chimes"
            "Deep Moon" -> "Низкие протяжные аккорды" to "Deep sustained chords"
            "Solar Rise" -> "Ритмичные басовые импульсы" to "Rhythmic bass pulses"
            "Star Night" -> "Высокий перезвон" to "High shimmering bells"
            "Soft Piano" -> "Мягкие синтезированные клавиши" to "Soft synthesized keys"
            "Minimal" -> "Короткий электронный сигнал" to "Short electronic beeps"
            "Classic Bell" -> "Настойчивый звонок" to "Insistent ringing bell"
            "Forest Birds" -> "Синтезированные птичьи трели" to "Synthesized bird calls"
            "Rain Drops" -> "Тихий синтезированный дождь" to "Quiet synthesized rain"
            "Retro Arcade" -> "Быстрая ретромелодия" to "Fast retro melody"
            else -> "Моя мелодия" to "My sound"
        }
        return if (lang == "ru") labels.first else labels.second
    }
    fun uri(context: Context, sound: String): Uri {
        builtIns[sound]?.let { return Uri.parse("android.resource://${context.packageName}/$it") }
        SoundLibrary(context).file(sound)?.let { return Uri.fromFile(it) }
        if (sound.startsWith("content://")) return Uri.parse(sound)
        return Uri.parse("android.resource://${context.packageName}/${R.raw.morning_light}")
    }
    fun title(context: Context, sound: String): String =
        SoundLibrary(context).title(sound) ?: if (sound.startsWith("content://")) runCatching { getFileName(context, Uri.parse(sound)) }.getOrNull() ?: "Audio" else sound
}

class AlarmAudio(private val context: Context) {
    private var player: MediaPlayer? = null
    fun start(sound: String, volume: Float, loop: Boolean = true, fallback: Boolean = true): Boolean {
        stop()
        val choices = listOf(AlarmSounds.uri(context, sound)) + if (fallback) listOf(
            Uri.parse("android.resource://${context.packageName}/${R.raw.morning_light}"),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)) else emptyList()
        for (uri in choices.distinct()) {
            val candidate = MediaPlayer()
            try {
                candidate.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                candidate.setDataSource(context, uri)
                candidate.isLooping = loop
                candidate.setVolume(volume.coerceIn(0f, 1f), volume.coerceIn(0f, 1f))
                candidate.prepare()
                candidate.start()
                player = candidate
                return true
            } catch (_: Exception) { candidate.release() }
        }
        return false
    }
    fun volume(value: Float) { player?.setVolume(value.coerceIn(0f, 1f), value.coerceIn(0f, 1f)) }
    fun stop() { player?.release(); player = null }
}
