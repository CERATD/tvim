package com.example.tvim

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33])
class AlarmPlaybackTest {
    private lateinit var context: Context
    private lateinit var player: MediaPlayer

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        listOf("alarms_prefs", "alarm_runtime", "alarm_history").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(3000, 0) }
        ShadowMediaPlayer.setCreateListener { created, _ -> player = created }
    }

    @Test fun selectedSoundLoopsOnAlarmStreamWithSelectedVolumeAndStops() {
        val audio = AlarmAudio(context)
        assertTrue(audio.start("Deep Moon", 0.35f))
        assertEquals(AlarmSounds.uri(context, "Deep Moon"), shadowOf(player).sourceUri)
        assertTrue(player.isPlaying)
        assertTrue(player.isLooping)
        assertEquals(AudioAttributes.USAGE_ALARM, shadowOf(player).audioAttributes.usage)
        assertEquals(0.35f, shadowOf(player).leftVolume, 0.001f)
        audio.stop()
        assertEquals(ShadowMediaPlayer.State.END, shadowOf(player).state)
    }

    @Test fun missingImportedSoundFallsBackToBundledAudio() {
        val sound = "content://missing/audio"
        ShadowMediaPlayer.addException(DataSource.toDataSource(context, android.net.Uri.parse(sound)), java.io.IOException("Gone"))
        val audio = AlarmAudio(context)
        assertTrue(audio.start(sound, 0.7f))
        assertEquals(AlarmSounds.uri(context, "Morning Light"), shadowOf(player).sourceUri)
        assertTrue(player.isPlaying)
        audio.stop()
    }

    @Test fun serviceQueuesConcurrentAlarmsAndDismissalStopsPlayback() {
        val first = Alarm(id = "first", hour = 7, minute = 0, isVibrationEnabled = false)
        val second = Alarm(id = "second", hour = 7, minute = 0, isVibrationEnabled = false)
        AlarmStorage(context).saveAlarms(listOf(first, second))
        val controller = Robolectric.buildService(AlarmService::class.java).create()
        val service = controller.get()
        val prefs = context.getSharedPreferences("alarm_runtime", Context.MODE_PRIVATE)
        try {
            service.onStartCommand(Intent().putExtra("ALARM_ID", first.id), 0, 1)
            service.onStartCommand(Intent().putExtra("ALARM_ID", second.id), 0, 2)
            assertEquals(first.id, prefs.getString("active", null))
            assertTrue(player.isPlaying)
            service.onStartCommand(Intent().setAction(AlarmService.STOP).putExtra("ALARM_ID", first.id), 0, 3)
            assertEquals(second.id, prefs.getString("active", null))
            assertTrue(player.isPlaying)
            service.onStartCommand(Intent().setAction(AlarmService.STOP).putExtra("ALARM_ID", second.id), 0, 4)
            assertNull(prefs.getString("active", null))
            assertEquals(2, AlarmHistory(context).events().size)
            assertEquals(ShadowMediaPlayer.State.END, shadowOf(player).state)
        } finally { controller.destroy() }
    }
}

