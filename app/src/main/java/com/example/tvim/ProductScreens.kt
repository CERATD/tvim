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
import com.example.tvim.ui.theme.DarkGray
import com.example.tvim.ui.theme.GrayText

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
    val historyCardColors = CardDefaults.cardColors(containerColor = DarkGray, contentColor = androidx.compose.ui.graphics.Color.White)
    val flatCardElevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (lang == "ru") "История пробуждений" else "Wake-up history", color = androidx.compose.ui.graphics.Color.White,
            style = MaterialTheme.typography.headlineSmall.copy(shadow = null))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("dismissed" to if (lang == "ru") "Выключено" else "Dismissed",
                "missed" to if (lang == "ru") "Пропущено" else "Missed").forEach { (result, label) ->
                Card(Modifier.weight(1f), colors = historyCardColors, elevation = flatCardElevation) {
                    Column(Modifier.padding(20.dp)) {
                        Text(events.count { it.result == result }.toString(),
                            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.displaySmall.copy(shadow = null))
                        Text(label, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.bodyMedium.copy(shadow = null))
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth(), colors = historyCardColors, elevation = flatCardElevation) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (lang == "ru") "Последние 7 дней" else "Last 7 days", color = androidx.compose.ui.graphics.Color.White,
                    style = MaterialTheme.typography.bodyLarge.copy(shadow = null))
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
                            Text(count.toString(), color = androidx.compose.ui.graphics.Color.White,
                                style = MaterialTheme.typography.labelSmall.copy(shadow = null))
                            Spacer(Modifier.height(8.dp))
                            Surface(Modifier.fillMaxWidth().height((4 + 76 * count / (counts.maxOrNull() ?: 1).coerceAtLeast(1)).dp),
                                color = if (count == 0) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)) {}
                            val day = (today.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, index - 6) }
                            Text(java.text.SimpleDateFormat("dd.MM", java.util.Locale.getDefault()).format(day.time),
                                color = GrayText, style = MaterialTheme.typography.labelSmall.copy(shadow = null))
                        }
                    }
                }
            }
        }
        if (events.isEmpty()) Text(if (lang == "ru") "Здесь появятся ваши срабатывания будильника." else "Your alarm events will appear here.",
            color = GrayText, style = MaterialTheme.typography.bodyMedium.copy(shadow = null))
        events.forEach { event ->
            Card(Modifier.fillMaxWidth(), colors = historyCardColors, elevation = flatCardElevation) {
                Column(Modifier.padding(16.dp)) {
                    Text(event.label, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.bodyLarge.copy(shadow = null))
                    Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(event.started)),
                        color = GrayText, style = MaterialTheme.typography.bodyMedium.copy(shadow = null))
                    Text(if (event.result == "dismissed") {
                        if (lang == "ru") "Выключен через ${(event.ended-event.started)/1000} с" else "Dismissed after ${(event.ended-event.started)/1000}s"
                    } else if (lang == "ru") "Пропущен" else "Missed", color = androidx.compose.ui.graphics.Color.White,
                        style = MaterialTheme.typography.bodyMedium.copy(shadow = null))
                }
            }
        }
    }
}
