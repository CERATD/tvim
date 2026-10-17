package com.example.tvim

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import com.example.tvim.ui.theme.NothingDisplayFont

@Composable
fun TimerScreen(lang: String) {
    val context = LocalContext.current
    val timer = remember { CountdownTimer(context) }
    var state by remember { mutableStateOf(timer.snapshot()) }
    val initialSeconds = state.duration / 1000
    var hours by rememberSaveable { mutableStateOf((initialSeconds / 3600).toString()) }
    var minutes by rememberSaveable { mutableStateOf((initialSeconds % 3600 / 60).toString()) }
    var seconds by rememberSaveable { mutableStateOf((initialSeconds % 60).toString()) }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) { state = timer.snapshot(); kotlinx.coroutines.delay(250) }
    }
    val h = hours.toIntOrNull() ?: 0
    val m = minutes.toIntOrNull() ?: 0
    val s = seconds.toIntOrNull() ?: 0
    val duration = (h * 3600L + m * 60L + s) * 1000
    val valid = h in 0..23 && m in 0..59 && s in 0..59 && duration > 0
    val idle = state.status == "idle"
    val nothingDesign = com.example.tvim.ui.theme.LocalNothingDesign.current
    val progressColor = MaterialTheme.colorScheme.primary
    val remaining = if (idle) duration else state.remaining
    val totalSeconds = (remaining + 999) / 1000
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(Strings.get("tab_timer", lang), Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineSmall)
        AlarmReadiness(lang)
        Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
            val progress = if (idle) 1f else (state.remaining.toFloat() / state.duration.coerceAtLeast(1)).coerceIn(0f, 1f)
            if (nothingDesign) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    drawNothingDots(Color(0xFF303030), 0f, 360f, 8.dp)
                    drawNothingDots(progressColor, -90f, progress * 360f, 8.dp)
                }
            } else CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize(), strokeWidth = 8.dp, trackColor = Color(0xFF242424))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(String.format(Locale.ROOT, "%02d:%02d:%02d", totalSeconds / 3600,
                    totalSeconds % 3600 / 60, totalSeconds % 60), fontSize = 32.sp,
                    fontFamily = if (nothingDesign) NothingDisplayFont else androidx.compose.ui.text.font.FontFamily.Default,
                    letterSpacing = if (nothingDesign) 1.2.sp else 0.sp)
                Spacer(Modifier.height(8.dp))
                Text(when (state.status) {
                    "running" -> if (lang == "ru") "Обратный отсчёт" else "Counting down"
                    "paused" -> if (lang == "ru") "Пауза" else "Paused"
                    "ringing" -> if (lang == "ru") "Время вышло" else "Time is up"
                    else -> if (lang == "ru") "Готов к запуску" else "Ready"
                }, color = Color.Gray)
            }
        }
        if (idle) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimerNumber(hours, if (lang == "ru") "Часы" else "Hours", h > 23, Modifier.weight(1f)) { hours = it }
                TimerNumber(minutes, if (lang == "ru") "Минуты" else "Minutes", m > 59, Modifier.weight(1f)) { minutes = it }
                TimerNumber(seconds, if (lang == "ru") "Секунды" else "Seconds", s > 59, Modifier.weight(1f)) { seconds = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 5, 10, 25).forEach { value ->
                    FilterChip(h == 0 && m == value && s == 0, { hours = "0"; minutes = value.toString(); seconds = "0" },
                        label = { Text("$value " + Strings.get("min", lang)) })
                }
            }
        }
        if (error) Text(if (lang == "ru") "Разрешите точные будильники в настройках и повторите запуск."
            else "Allow exact alarms in settings, then try again.", color = MaterialTheme.colorScheme.primary)
        if (state.status == "ringing") {
            val isActive = context.getSharedPreferences("alarm_runtime", Context.MODE_PRIVATE).getString("active", null) == state.id
            Button(onClick = { context.startActivity(Intent(context, AlarmScreenActivity::class.java)) },
                enabled = isActive, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(if (isActive) (if (lang == "ru") "Открыть сигнал" else "Open alarm")
                    else if (lang == "ru") "Ожидает сигнала" else "Waiting for alarm")
            }
        } else {
            Button(onClick = {
                error = when (state.status) {
                    "running" -> { timer.pause(); false }
                    "paused" -> !timer.resume()
                    else -> !timer.start(duration)
                }
                state = timer.snapshot()
            }, enabled = if (idle) valid else state.remaining > 0,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
                Text(when (state.status) {
                    "running" -> if (lang == "ru") "Пауза" else "Pause"
                    "paused" -> if (lang == "ru") "Продолжить" else "Resume"
                    else -> if (lang == "ru") "Начать" else "Start"
                })
            }
            if (!idle) OutlinedButton(onClick = { timer.reset(); state = timer.snapshot(); error = false },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (lang == "ru") "Сбросить" else "Reset")
            }
        }
    }
}

@Composable
private fun TimerNumber(value: String, label: String, invalid: Boolean, modifier: Modifier, onChange: (String) -> Unit) {
    val nothingDesign = com.example.tvim.ui.theme.LocalNothingDesign.current
    OutlinedTextField(value, { if (it.length <= 2 && it.all { c -> c in '0'..'9' }) onChange(it) },
        modifier = modifier, label = { Text(label) }, singleLine = true, isError = invalid,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = if (nothingDesign) NothingDisplayFont else androidx.compose.ui.text.font.FontFamily.Default,
            letterSpacing = if (nothingDesign) 0.8.sp else 0.sp
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(16.dp))
}
