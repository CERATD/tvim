package com.example.tvim

import android.app.KeyguardManager
import android.content.Intent
import androidx.activity.compose.BackHandler
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import com.example.tvim.ui.theme.NothingDisplayFont
import java.util.*
import kotlin.math.sqrt
import kotlin.random.Random

class AlarmScreenActivity : ComponentActivity(), SensorEventListener {
    private var sensorManager: SensorManager? = null
    private var shakeCount = mutableStateOf(0)
    private var requiredShakes = 30
    private var missionType = mutableStateOf(MissionType.NONE)
    private var alarmId: String? = null
    private var lastShake = 0L
    private lateinit var appSettings: AppSettings
    private lateinit var lang: String
    private lateinit var typingPhrase: BookPhrase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        appSettings = AppSettings(this)
        lang = appSettings.language

        alarmId = getSharedPreferences("alarm_runtime", MODE_PRIVATE).getString("active", null)
        if (alarmId == null) { finish(); return }
        val alarm = CountdownTimer(this).alarm(alarmId) ?: AlarmStorage(this).getAlarms().find { it.id == alarmId }
        alarm?.let {
            missionType.value = it.missionType
            requiredShakes = it.difficulty.coerceIn(1, 5) * 10
        }

        setupUnlock()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        if (missionType.value == MissionType.SHAKE) {
            sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
            val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (sensor == null) missionType.value = MissionType.TYPING
            else sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }

        if (missionType.value == MissionType.TYPING) typingPhrase = TypingChallengeStore(this).phraseFor(
            alarmId ?: return,
            getSharedPreferences("alarm_runtime", MODE_PRIVATE).getLong("started", 0),
            lang
        )

        setContent {
            BackHandler { }
            LaunchedEffect(Unit) {
                while (true) {
                    val current = getSharedPreferences("alarm_runtime", MODE_PRIVATE).getString("active", null)
                    if (current == null) { finish(); break }
                    if (current != alarmId) { recreate(); break }
                    kotlinx.coroutines.delay(500)
                }
            }
            TvimTheme(primaryColor = appSettings.primaryColor, nothingDesign = appSettings.nothingDesign) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    if (missionType.value == MissionType.TYPING) {
                        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 24.dp, vertical = 32.dp)) {
                            TypingMission { finishMission() }
                        }
                    } else Column(
                        modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            TvimDigitalTime(
                                text = String.format(Locale.getDefault(), "%02d:%02d", Calendar.getInstance().get(Calendar.HOUR_OF_DAY), Calendar.getInstance().get(Calendar.MINUTE)),
                                fontSize = if (missionType.value == MissionType.NONE) 64.sp else 32.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Light,
                                fontFamily = if (appSettings.nothingDesign) NothingDisplayFont else androidx.compose.ui.text.font.FontFamily.Default,
                                letterSpacing = if (appSettings.nothingDesign) 1.5.sp else 0.sp,
                                nothingDesign = appSettings.nothingDesign
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            Text(alarm?.label.orEmpty(), color = Color.Gray)
                            if (missionType.value == MissionType.NONE) {
                                Spacer(Modifier.height(48.dp))
                                AlarmRingVisualization()
                            }
                        }

                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
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
            Canvas(Modifier.size(124.dp)) {
                drawCircle(color = primaryColor.copy(alpha = 0.35f), style = Stroke(width = 2.dp.toPx()))
            }
            TvimIcon(Icons.Default.NotificationsActive, null, Modifier.size(56.dp), tint = primaryColor)
        }
    }

    private fun finishMission() {
        startService(Intent(this, AlarmService::class.java).setAction(AlarmService.STOP).putExtra("ALARM_ID", alarmId))
        sensorManager?.unregisterListener(this)
        finish()
    }

    @Composable
    fun MathMission(onComplete: () -> Unit) {
        var num1 by remember { mutableStateOf(Random.nextInt(10, 50)) }
        var num2 by remember { mutableStateOf(Random.nextInt(10, 50)) }
        var userAnswer by remember { mutableStateOf("") }
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${num1} + ${num2} = ?", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold,
                fontFamily = if (appSettings.nothingDesign) NothingDisplayFont else androidx.compose.ui.text.font.FontFamily.Default)
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
            modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth().height(320.dp),
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
                        TvimIcon(Icons.AutoMirrored.Filled.Backspace, null, tint = Color.White)
                    } else {
                        Text(
                            text = btn,
                            color = if (btn == "OK") Color.Black else Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = if (appSettings.nothingDesign) NothingDisplayFont else androidx.compose.ui.text.font.FontFamily.Default,
                            letterSpacing = if (appSettings.nothingDesign) 0.8.sp else 0.sp
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun ShakeMission(count: Int, required: Int) {
        val nothingDesign = com.example.tvim.ui.theme.LocalNothingDesign.current
        val progressColor = MaterialTheme.colorScheme.primary
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(Strings.get("shake_to_dismiss", lang), color = Color.Gray, fontSize = 12.sp, letterSpacing = 2.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                val progress = (count.toFloat() / required).coerceIn(0f, 1f)
                if (nothingDesign) androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    drawNothingProgressDots(progressColor, progress, 8.dp)
                } else CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize(), strokeWidth = 8.dp, trackColor = Color(0xFF242424))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TvimIcon(Icons.Default.Vibration, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text("$count / $required", color = Color.White, fontSize = 36.sp,
                        fontFamily = if (appSettings.nothingDesign) NothingDisplayFont else androidx.compose.ui.text.font.FontFamily.Default,
                        letterSpacing = if (appSettings.nothingDesign) 1.sp else 0.sp)
                }
            }
        }
    }

    @Composable
    fun TypingMission(onComplete: () -> Unit) {
        val target = typingPhrase.text(lang)
        var typed by rememberSaveable(typingPhrase.id) { mutableStateOf("") }
        var completed by remember { mutableStateOf(false) }
        // The reference stays outside the input's scroll container, so IME focus
        // cannot scroll it behind the status bar. Long references scroll on their own.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val referenceMaxHeight = maxHeight * 0.42f
            val topSpace = (maxHeight * 0.08f).coerceIn(12.dp, 48.dp)
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(topSpace))
                Text(Strings.get("type_to_dismiss", lang), color = Color.Gray, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().heightIn(max = referenceMaxHeight),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B1B)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        target,
                        modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                        color = Color.White, fontSize = 24.sp, lineHeight = 32.sp,
                        fontWeight = FontWeight.Medium, textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = {
                            typed = it.take(maxOf(500, target.length * 2))
                            if (!completed && TypingPhrases.matches(typed, target)) {
                                completed = true
                                onComplete()
                            }
                        },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = false),
                        minLines = 2,
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.DarkGray),
                        textStyle = LocalTextStyle.current.copy(color = Color.White, textAlign = TextAlign.Center),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(if (lang == "ru") "Регистр, пунктуация и лишние пробелы не учитываются."
                        else "Capitalization, punctuation and extra spaces do not matter.",
                        color = Color.Gray, fontSize = 12.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(if (lang == "ru") "Джордж Р. Р. Мартин · Игра престолов" else "George R. R. Martin · A Game of Thrones",
                        color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }

    @Composable
    fun DefaultMission(onComplete: () -> Unit) {
        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(16.dp)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val acceleration = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH
            if (acceleration > 8 && SystemClock.elapsedRealtime() - lastShake > 350) {
                lastShake = SystemClock.elapsedRealtime()
                shakeCount.value++
                if (shakeCount.value >= requiredShakes) {
                    finishMission()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onDestroy() { sensorManager?.unregisterListener(this); super.onDestroy() }
}
