package com.example.tvim

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tvim.ui.theme.DarkGray
import com.example.tvim.ui.theme.GrayText
import com.example.tvim.ui.theme.RedAccent
import com.example.tvim.ui.theme.NothingDisplayFont
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.lerp
import kotlinx.coroutines.launch
import java.util.*
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.vector.ImageVector
import android.content.Context

@Composable
fun TvimDashboard(
    alarms: List<Alarm>,
    storage: AlarmStorage,
    appSettings: AppSettings,
    lang: String,
    nothingDesign: Boolean,
    onPrimaryColorChange: (Color) -> Unit,
    onLanguageChange: (String) -> Unit,
    onNothingDesignChange: (Boolean) -> Unit,
    onAlarmToggle: (Alarm, Boolean) -> Unit,
    onAlarmDelete: (Alarm) -> Unit,
    onAlarmEdit: (Alarm) -> Unit,
    onAddAlarm: () -> Unit
) {
    var selectedTab by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            if (selectedTab == 0) {
                TvimTopBar(onAddAlarm, lang)
            }
        },
        bottomBar = {
            TvimBottomNavigation(selectedTab, lang) { selectedTab = it }
        },
        containerColor = Color.Black
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                0 -> MainAlarmScreen(alarms, onAlarmToggle, onAlarmEdit, lang)
                1 -> StopwatchScreen(lang)
                2 -> TimerScreen(lang)
                3 -> SettingsScreen(appSettings, onPrimaryColorChange, onLanguageChange, lang, nothingDesign, onNothingDesignChange)
            }
        }
    }
}

@Composable
fun MainAlarmScreen(
    alarms: List<Alarm>,
    onAlarmToggle: (Alarm, Boolean) -> Unit,
    onAlarmEdit: (Alarm) -> Unit,
    lang: String
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) } }
    val nextAlarm = alarms.filter { it.isEnabled }.minByOrNull { AlarmTime.next(it, now) }
    val remainingAlarms = if (nextAlarm != null) alarms.filter { it.id != nextAlarm.id } else alarms

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { AlarmReadiness(lang) }
        item {
            SectionHeader(Strings.get("next_alarm", lang))
            if (nextAlarm != null) {
                NextAlarmCard(nextAlarm, onAlarmToggle, onAlarmEdit, lang)
            } else {
                Text(Strings.get("no_active", lang), color = Color.Gray, modifier = Modifier.padding(16.dp))
            }
        }

        item {
            SectionHeader(Strings.get("all_alarms", lang))
        }

        items(remainingAlarms, key = { it.id }) { alarm ->
            AlarmCard(
                alarm = alarm,
                onToggle = { onAlarmToggle(alarm, it) },
                onEdit = { onAlarmEdit(alarm) },
                lang = lang
            )
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun AlarmTimeDisplay(
    hour: Int,
    minute: Int,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight,
    nothingDesign: Boolean,
    letterSpacing: androidx.compose.ui.unit.TextUnit
) {
    val hours = String.format(Locale.getDefault(), "%02d", hour)
    val minutes = String.format(Locale.getDefault(), "%02d", minute)
    TvimDigitalTime(
        text = "$hours:$minutes",
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontFamily = if (nothingDesign) NothingDisplayFont else androidx.compose.ui.text.font.FontFamily.Default,
        letterSpacing = letterSpacing,
        nothingDesign = nothingDesign
    )
}

@Composable
fun NextAlarmCard(alarm: Alarm, onToggle: (Alarm, Boolean) -> Unit, onEdit: (Alarm) -> Unit, lang: String) {
    var currentTime by remember { mutableStateOf(Calendar.getInstance()) }
    
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Calendar.getInstance()
            kotlinx.coroutines.delay(1000)
        }
    }

    val alarmTime = Calendar.getInstance().apply { timeInMillis = AlarmTime.next(alarm, currentTime.timeInMillis) }

    val totalDayMillis = 24 * 60 * 60 * 1000f
    val remainingMillis = alarmTime.timeInMillis - currentTime.timeInMillis
    val progress = (1f - (remainingMillis.toFloat() / totalDayMillis)).coerceIn(0f, 1f)
    val sweepAngle = 360f * progress
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val nothingDesign = com.example.tvim.ui.theme.LocalNothingDesign.current
    val dynamicCircleColor = lerp(primaryColor.copy(alpha = 0.3f), primaryColor, progress)

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkGray),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().clickable { onEdit(alarm) }
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AlarmTimeDisplay(
                    hour = alarm.hour,
                    minute = alarm.minute,
                    color = Color.White,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Medium,
                    nothingDesign = nothingDesign,
                    letterSpacing = if (nothingDesign) 1.4.sp else 0.sp
                )
                
                Box(contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(80.dp)) {
                        if (nothingDesign) {
                            drawNothingProgressDots(dynamicCircleColor, sweepAngle / 360f, 8.dp)
                        } else {
                            drawCircle(color = Color.Black.copy(alpha = 0.3f), style = Stroke(width = 8.dp.toPx()))
                            drawArc(color = dynamicCircleColor, startAngle = -90f, sweepAngle = sweepAngle,
                                useCenter = false, style = Stroke(width = 8.dp.toPx()))
                        }
                    }
                    TvimSwitch(checked = alarm.isEnabled, onCheckedChange = { onToggle(alarm, it) })
                }
            }
            Text(
                text = formatDays(alarm.days, lang) + " · " + java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT).format(alarmTime.time),
                color = GrayText,
                fontSize = 12.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                TvimIcon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (alarm.label == "Будильник" || alarm.label == "Alarm") Strings.get("tab_alarm", lang) else alarm.label,
                    color = GrayText,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun AlarmCard(alarm: Alarm, onToggle: (Boolean) -> Unit, onEdit: () -> Unit, lang: String) {
    val nothingDesign = com.example.tvim.ui.theme.LocalNothingDesign.current
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkGray),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().clickable { onEdit() }
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AlarmTimeDisplay(
                    hour = alarm.hour,
                    minute = alarm.minute,
                    color = if (alarm.isEnabled) Color.White else GrayText,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Normal,
                    nothingDesign = nothingDesign,
                    letterSpacing = if (nothingDesign) 1.2.sp else 0.sp
                )
                TvimSwitch(checked = alarm.isEnabled, onCheckedChange = onToggle)
            }
            Text(text = formatDays(alarm.days, lang), color = GrayText, fontSize = 11.sp)
            Text(text = if (alarm.label == "Будильник" || alarm.label == "Alarm") Strings.get("tab_alarm", lang) else alarm.label, color = GrayText, fontSize = 11.sp)
        }
    }
}

@Composable
fun SettingsScreen(appSettings: AppSettings, onColorChange: (Color) -> Unit, onLanguageChange: (String) -> Unit, lang: String, nothingDesign: Boolean, onNothingDesignChange: (Boolean) -> Unit) {
    var showColorPicker by remember { mutableStateOf(false) }
    var showLangPicker by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showTvin by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var showUpdates by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(Strings.get("settings_title", lang), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        
        SettingsItem(if (lang == "ru") "История будильников" else "Alarm history",
            if (lang == "ru") "Срабатывания и время отключения" else "Alarm events and dismissal times", { showHistory = true })
        SectionHeader(Strings.get("appearance", lang))
        NothingDesignSetting(nothingDesign, onNothingDesignChange, lang)
        Spacer(modifier = Modifier.height(12.dp))
        SettingsItem(Strings.get("theme_color", lang), Strings.get("theme_custom", lang), onClick = { showColorPicker = true })
        SettingsItem(Strings.get("language", lang), if (appSettings.language == "ru") "Русский" else "English", onClick = { showLangPicker = true })
        
        Spacer(modifier = Modifier.height(16.dp))
        SectionHeader(Strings.get("notifications", lang))
        SettingsItem(if (lang == "ru") "Режим TVIM" else "TVIM mode",
            if (lang == "ru") "Пожелания, текст и время сообщений" else "Greetings, message text and schedule", { showTvin = true })
        SettingsItem(
            title = Strings.get("check_updates", lang),
            subtitle = if (lang == "ru") "Загрузка и установка · v" + BuildConfig.VERSION_NAME else "Download and install · v" + BuildConfig.VERSION_NAME,
            onClick = { showUpdates = true }
        )

        Spacer(modifier = Modifier.height(16.dp))
        SectionHeader(Strings.get("support", lang))
        SettingsItem(
            title = Strings.get("report_error", lang),
            subtitle = "procopciucdanu@gmail.com",
            onClick = {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:procopciucdanu@gmail.com")
                    putExtra(Intent.EXTRA_SUBJECT, Strings.get("feedback_subject", lang))
                    putExtra(Intent.EXTRA_TEXT, Strings.get("feedback_body", lang))
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, if (lang == "ru") "Установите почтовое приложение или напишите на указанный адрес" else "Install an email app or use the address shown", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    if (showUpdates) UpdateDialog(lang = lang, onDismiss = { showUpdates = false })
    if (showTvin) TvinSettings(lang, onDismiss = { showTvin = false })

    if (showHistory) androidx.compose.ui.window.Dialog(onDismissRequest = { showHistory = false },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                TextButton(onClick = { showHistory = false }) { Text(if (lang == "ru") "Назад" else "Back") }
                Box(Modifier.weight(1f)) { HistoryScreen(lang) }
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            currentColor = appSettings.primaryColor,
            onDismiss = { showColorPicker = false },
            onSelect = { 
                appSettings.primaryColor = it
                onColorChange(it)
                showColorPicker = false
            },
            lang = lang
        )
    }

    if (showLangPicker) {
        LanguagePickerDialog(
            currentLang = appSettings.language,
            onDismiss = { showLangPicker = false },
            onSelect = {
                appSettings.language = it
                onLanguageChange(it)
                showLangPicker = false
            },
            lang = lang
        )
    }
}

@Composable
fun ColorPickerDialog(currentColor: Color, onDismiss: () -> Unit, onSelect: (Color) -> Unit, lang: String) {
    val presetColors = listOf(
        Color(0xFFFF2433), // Red
        Color(0xFF40E0D0), // Turquoise
        Color(0xFFFFFFFF), // White
        Color(0xFFFF4F9A), // Pink
        Color(0xFF35C759)  // Green
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = { Text(Strings.get("theme_color", lang), color = Color.White) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                presetColors.forEach { color ->
                    val selected = currentColor.toArgb() == color.toArgb()
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) Color.White else Color(0xFF555555),
                                shape = CircleShape
                            )
                            .clickable { onSelect(color) }
                    ) {
                        if (selected) {
                            TvimIcon(Icons.Default.Check, null, tint = if (color == Color.White || color == Color(0xFF40E0D0) || color == Color(0xFFFF4F9A) || color == Color(0xFF35C759)) Color.Black else Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(Strings.get("ok", lang), color = MaterialTheme.colorScheme.primary) }
        }
    )
}

@Composable
fun LanguagePickerDialog(currentLang: String, onDismiss: () -> Unit, onSelect: (String) -> Unit, lang: String) {
    val languages = listOf("ru" to "Русский", "en" to "English")
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = { Text(Strings.get("language", lang), color = Color.White) },
        text = {
            Column {
                languages.forEach { (code, name) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(code) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = currentLang == code, onClick = { onSelect(code) }, colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary))
                        Text(name, color = Color.White, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.get("cancel", lang), color = GrayText) } }
    )
}

@Composable
fun TvimTopBar(onAddClick: () -> Unit, lang: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = Strings.get("app_name", lang), color = MaterialTheme.colorScheme.primary, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
        IconButton(onClick = onAddClick) {
            TvimIcon(Icons.Default.Add, "Add", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(text = title, color = GrayText, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp, modifier = Modifier.padding(vertical = 12.dp))
}

private fun formatDays(days: List<Int>, lang: String): String {
    if (days.isEmpty()) return Strings.get("once", lang)
    if (days.size == 7) return Strings.get("every_day", lang)
    val dayNames = if (lang == "ru") {
        listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС")
    } else {
        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    }
    return days.sorted().joinToString(", ") { dayNames[it - 1] }
}

@Composable
fun TvimBottomNavigation(selectedTab: Int, lang: String, onTabSelected: (Int) -> Unit) {
    NavigationBar(containerColor = Color.Black, tonalElevation = 0.dp) {
        val items = listOf(
            Triple(Strings.get("tab_alarm", lang), Icons.Outlined.Alarm, 0),
            Triple(Strings.get("tab_stopwatch", lang), Icons.Outlined.Timer, 1),
            Triple(Strings.get("tab_timer", lang), Icons.Outlined.HourglassEmpty, 2),
            Triple(Strings.get("tab_settings", lang), Icons.Outlined.Settings, 3)
        )
        items.forEach { item ->
            val isSelected = selectedTab == item.third
            val selectedColor = if (item.third == 1) Color.White else MaterialTheme.colorScheme.primary
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(item.third) },
                icon = { TvimIcon(item.second, null, tint = if (isSelected) selectedColor else GrayText, modifier = Modifier.size(24.dp)) },
                label = { Text(item.first, fontSize = 9.sp, color = if (isSelected) selectedColor else GrayText) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
            )
        }
    }
}
