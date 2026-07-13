package com.example.tvim

import android.app.KeyguardManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.*
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tvim.ui.theme.RedAccent
import com.example.tvim.ui.theme.TvimTheme
import java.util.*
import kotlin.math.sqrt
import kotlin.random.Random

class AlarmScreenActivity : ComponentActivity(), SensorEventListener {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var sensorManager: SensorManager? = null
    private var shakeCount = mutableStateOf(0)
    private var requiredShakes = 30
    private var missionType = mutableStateOf(MissionType.MATH)
    private lateinit var appSettings: AppSettings
    private lateinit var lang: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        appSettings = AppSettings(this)
        lang = appSettings.language

        val alarmId = intent.getStringExtra("ALARM_ID")
        val alarm = AlarmStorage(this).getAlarms().find { it.id == alarmId }
        alarm?.let {
            missionType.value = it.missionType
            requiredShakes = it.difficulty * 10
        }

        setupUnlock()
        startAlarmEffects()
        
        if (missionType.value == MissionType.SHAKE) {
            sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
            sensorManager?.registerListener(this, sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_UI)
        }

        setContent {
            TvimTheme(primaryColor = appSettings.primaryColor) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = String.format(Locale.getDefault(), "%02d:%02d", Calendar.getInstance().get(Calendar.HOUR_OF_DAY), Calendar.getInstance().get(Calendar.MINUTE)),
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Light
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            AlarmRingVisualization()
                        }

                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            when (missionType.value) {
                                MissionType.MATH -> MathMission { finishMission() }
                                MissionType.SHAKE -> ShakeMission(shakeCount.value, requiredShakes)
                                MissionType.TYPING -> TypingMission { finishMission() }
                                else -> DefaultMission { finishMission() }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun AlarmRingVisualization() {
        val primaryColor = MaterialTheme.colorScheme.primary
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(160.dp)) {
                drawCircle(color = primaryColor.copy(alpha = 0.1f), style = Stroke(width = 2.dp.toPx()))
                drawCircle(color = primaryColor, style = Stroke(width = 12.dp.toPx()))
            }
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(primaryColor)
            )
        }
    }

    private fun finishMission() {
        stopAlarm()
        finish()
    }

    @Composable
    fun MathMission(onComplete: () -> Unit) {
        var num1 by remember { mutableStateOf(Random.nextInt(10, 50)) }
        var num2 by remember { mutableStateOf(Random.nextInt(10, 50)) }
        var userAnswer by remember { mutableStateOf("") }
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${num1} + ${num2} = ?", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = if (userAnswer.isEmpty()) "_" else userAnswer,
                color = if (userAnswer.isEmpty()) Color.Gray else Color.White,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(32.dp))
            
            NumberPad(
                onNumberClick = { if (userAnswer.length < 5) userAnswer += it },
                onDeleteClick = { if (userAnswer.isNotEmpty()) userAnswer = userAnswer.dropLast(1) },
                onCheckClick = { 
                    if (userAnswer.toIntOrNull() == (num1 + num2)) {
                        onComplete()
                    } else {
                        userAnswer = ""
                    }
                }
            )
        }
    }

    @Composable
    fun NumberPad(onNumberClick: (String) -> Unit, onDeleteClick: () -> Unit, onCheckClick: () -> Unit) {
        val buttons = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "DEL", "0", "OK")
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.width(280.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(buttons) { btn ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1.2f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (btn == "OK") MaterialTheme.colorScheme.primary else Color(0xFF1A1A1A))
                        .clickable {
                            when (btn) {
                                "DEL" -> onDeleteClick()
                                "OK" -> onCheckClick()
                                else -> onNumberClick(btn)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (btn == "DEL") {
                        Icon(Icons.AutoMirrored.Filled.Backspace, null, tint = Color.White)
                    } else {
                        Text(
                            text = btn,
                            color = if (btn == "OK") Color.Black else Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun ShakeMission(count: Int, required: Int) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(Strings.get("shake_to_dismiss", lang), color = Color.Gray, fontSize = 12.sp, letterSpacing = 2.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text("$count / $required", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Bold)
        }
    }

    @Composable
    fun TypingMission(onComplete: () -> Unit) {
        val target = if (lang == "ru") "Я ПРОСНУЛСЯ" else "I AM AWAKE"
        var typed by remember { mutableStateOf("") }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(Strings.get("type_to_dismiss", lang), color = Color.Gray, fontSize = 12.sp)
            Text(target, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it; if (it.equals(target, true)) onComplete() },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.DarkGray),
                textStyle = LocalTextStyle.current.copy(color = Color.White, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    @Composable
    fun DefaultMission(onComplete: () -> Unit) {
        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth(0.8f).height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text(Strings.get("dismiss", lang), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }

    private fun setupUnlock() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
    }

    private fun startAlarmEffects() {
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(this, alarmUri)
        ringtone?.audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ringtone?.play()
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0))
        } else {
            vibrator?.vibrate(longArrayOf(0, 500, 500), 0)
        }
    }

    private fun stopAlarm() {
        ringtone?.stop()
        vibrator?.cancel()
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val acceleration = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH
            if (acceleration > 5) {
                shakeCount.value++
                if (shakeCount.value >= requiredShakes) {
                    finishMission()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    @Deprecated("Deprecated in Java", ReplaceWith("onBackPressedDispatcher.onBackPressed()"))
    override fun onBackPressed() {}
    override fun onDestroy() { stopAlarm(); super.onDestroy() }
}
