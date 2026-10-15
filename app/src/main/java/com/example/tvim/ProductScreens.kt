package com.example.tvim

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat

@Composable
fun AlarmReadiness(lang: String) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(1000); refresh++ } }
    val exact = remember(refresh) { AlarmScheduler(context).canSchedule() }
    val notifications = remember(refresh) {
        val manager = context.getSystemService(NotificationManager::class.java)
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 26 || manager.getNotificationChannel(AlarmService.CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE)
    }
    val fullScreen = remember(refresh) { Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent() }
    val volume = remember(refresh) { (context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager).getStreamVolume(android.media.AudioManager.STREAM_ALARM) }
    if (!exact || !notifications || !fullScreen || volume == 0) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(if (lang == "ru") "Проверьте настройки будильника" else "Check alarm settings")
                if (!exact && Build.VERSION.SDK_INT >= 31) TextButton(onClick = {
                    openSystemSettings(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                }) { Text(if (lang == "ru") "Разрешить точные будильники" else "Allow exact alarms") }
                if (!notifications) TextButton(onClick = {
                    openSystemSettings(context, if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }) { Text(if (lang == "ru") "Включить уведомления будильника" else "Enable alarm notifications") }
                if (!fullScreen && Build.VERSION.SDK_INT >= 34) TextButton(onClick = {
                    openSystemSettings(context, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")))
                }) { Text(if (lang == "ru") "Разрешить экран поверх блокировки" else "Allow full-screen alarms") }
                if (volume == 0) TextButton(onClick = { openSystemSettings(context, Intent(Settings.ACTION_SOUND_SETTINGS)) }) {
                    Text(if (lang == "ru") "Системная громкость будильника — 0" else "System alarm volume is zero")
                }
            }
        }
    }
}

fun openSystemSettings(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent) }.onFailure {
        runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }
}

@Composable
fun HistoryScreen(lang: String) {
    val context = LocalContext.current
    val events = remember { AlarmHistory(context).events() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (lang == "ru") "История пробуждений" else "Wake-up history", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("dismissed" to if (lang == "ru") "Выключено" else "Dismissed",
                "missed" to if (lang == "ru") "Пропущено" else "Missed").forEach { (result, label) ->
                Card(Modifier.weight(1f)) {
                    Column(Modifier.padding(20.dp)) {
                        Text(events.count { it.result == result }.toString(), style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.primary)
                        Text(label)
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (lang == "ru") "Последние 7 дней" else "Last 7 days")
                val today = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
                }
                val counts = (6 downTo 0).map { offset ->
                    val day = (today.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, -offset) }
                    val start = day.timeInMillis
                    day.add(java.util.Calendar.DAY_OF_YEAR, 1)
                    events.count { it.started >= start && it.started < day.timeInMillis }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                    counts.forEachIndexed { index, count ->
                        Column(Modifier.weight(1f), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                            Text(count.toString(), style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(8.dp))
                            Surface(Modifier.fillMaxWidth().height((4 + 76 * count / (counts.maxOrNull() ?: 1).coerceAtLeast(1)).dp),
                                color = if (count == 0) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)) {}
                            val day = (today.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, index - 6) }
                            Text(java.text.SimpleDateFormat("dd.MM", java.util.Locale.getDefault()).format(day.time),
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        if (events.isEmpty()) Text(if (lang == "ru") "Здесь появятся ваши срабатывания будильника." else "Your alarm events will appear here.")
        events.forEach { event ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(event.label)
                    Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(event.started)))
                    Text(if (event.result == "dismissed") {
                        if (lang == "ru") "Выключен через ${(event.ended-event.started)/1000} с" else "Dismissed after ${(event.ended-event.started)/1000}s"
                    } else if (lang == "ru") "Пропущен" else "Missed")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepScreen(storage: AlarmStorage, lang: String) {
    val context = LocalContext.current
    val initial = remember { storage.getBedtimeData() }
    val time = rememberTimePickerState(initial.wakeUpHour, initial.wakeUpMinute, true)
    var days by remember { mutableStateOf(initial.wakeUpDays.toList()) }
    var duration by remember {
        mutableFloatStateOf(if (initial.isConfigured) (((initial.wakeUpHour * 60 + initial.wakeUpMinute -
            initial.bedtimeHour * 60 - initial.bedtimeMinute + 1440) % 1440) / 60f).coerceIn(4f, 12f) else 8f)
    }
    var saved by remember { mutableStateOf(false) }
    LaunchedEffect(time.hour, time.minute) { saved = false }
    val bedMinutes = (time.hour * 60 + time.minute - (duration * 60).toInt() + 1440) % 1440
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(if (lang == "ru") "Режим сна" else "Sleep schedule", style = MaterialTheme.typography.headlineSmall)
        AlarmReadiness(lang)
        Text(if (lang == "ru") "Время пробуждения" else "Wake-up time")
        TimeInput(time)
        DayOfWeekPicker(days, lang) { days = it; saved = false }
        Text(if (lang == "ru") "Запланировано: ${"%.1f".format(duration)} ч сна" else "Planned sleep: ${"%.1f".format(duration)} h")
        Slider(duration, { duration = it; saved = false }, valueRange = 4f..12f, steps = 15)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp)) {
                Text(if (lang == "ru") "Время отхода ко сну" else "Bedtime")
                Text("%02d:%02d".format(bedMinutes / 60, bedMinutes % 60), style = MaterialTheme.typography.displayMedium)
            }
        }
        Text(if (lang == "ru") "Создаёт обычный будильник. Изменить звук, отключить или удалить его можно во вкладке «Будильник»."
            else "Creates a regular alarm. Change its sound, disable or delete it in the Alarm tab.")
        Button(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), onClick = {
            val previous = storage.getAlarms().find { it.id == "sleep_schedule" }
            val alarm = (previous ?: Alarm(id = "sleep_schedule", hour = time.hour, minute = time.minute)).copy(
                hour = time.hour, minute = time.minute, days = days, isEnabled = true,
                label = if (lang == "ru") "Пробуждение" else "Wake up")
            if (previous == null) storage.addAlarm(alarm) else storage.updateAlarm(alarm)
            AlarmScheduler(context).schedule(alarm)
            storage.saveBedtimeData(BedtimeData(isConfigured = true, wakeUpHour = time.hour, wakeUpMinute = time.minute,
                bedtimeHour = bedMinutes / 60, bedtimeMinute = bedMinutes % 60, wakeUpDays = days.toSet()))
            saved = true
        }) { Text(if (lang == "ru") "Сохранить будильник" else "Save wake-up alarm") }
        if (saved) Text(if (lang == "ru") "Сохранено во вкладке «Будильник»" else "Saved in the Alarm tab")
    }
}
