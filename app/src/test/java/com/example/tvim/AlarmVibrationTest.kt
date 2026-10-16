package com.example.tvim

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.Vibrator
import android.os.VibratorManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowVibrator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33])
class AlarmVibrationTest {
    private lateinit var vibration: AlarmVibration
    private lateinit var shadow: ShadowVibrator

    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        val vibrator = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java).defaultVibrator
            else context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        shadow = shadowOf(vibrator)
        shadow.setHasVibrator(true)
        vibration = AlarmVibration(context)
    }

    @Test fun alarmUsesAlarmAttributesAndRepeatsUntilStopped() {
        assertTrue(vibration.start("Knock"))
        assertTrue(shadow.isVibrating)
        assertEquals(0, shadow.repeat)
        if (Build.VERSION.SDK_INT >= 33) assertEquals(VibrationAttributes.USAGE_ALARM,
            (shadow.vibrationAttributesFromLastVibration as VibrationAttributes).usage)
        else assertEquals(AudioAttributes.USAGE_ALARM, shadow.audioAttributesFromLastVibration?.usage)
        vibration.stop()
        assertFalse(shadow.isVibrating)
    }

    @Test fun previewsAreFiniteAndOffStopsVibration() {
        for (pattern in listOf("Standard", "Pulse", "Knock", "Melody")) {
            assertTrue(vibration.start(pattern, repeat = false))
            assertEquals(-1, shadow.repeat)
        }
        assertFalse(vibration.start("Off"))
        assertFalse(shadow.isVibrating)
    }

    @Test fun reportsDeviceWithoutVibrator() {
        shadow.setHasVibrator(false)
        assertFalse(vibration.available())
        assertFalse(vibration.start("Standard"))
    }
}
