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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
    onPrimaryColorChange: (Color) -> Unit,
    onLanguageChange: (String) -> Unit,
    onAlarmToggle: (Alarm, Boolean) -> Unit,
    onAlarmDelete: (Alarm) -> Unit,
    onAlarmEdit: (Alarm) -> Unit,
    onAddAlarm: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

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
                1 -> LockedScreen("tab_analysis", lang)
                2 -> LockedScreen("tab_sleep", lang)
                3 -> SettingsScreen(appSettings, onPrimaryColorChange, onLanguageChange, lang)
            }
        }
    }
}

@Composable
fun LockedScreen(tabKey: String, lang: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Lock, 
            contentDescription = null, 
            tint = MaterialTheme.colorScheme.primary, 
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = Strings.get(tabKey, lang).uppercase(),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = Strings.get("closed_temp", lang),
            color = GrayText,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = Strings.get("coming_soon", lang),
            color = GrayText.copy(alpha = 0.6f),
            fontSize = 12.sp
        )
    }
}

@Composable
fun MainAlarmScreen(
    alarms: List<Alarm>,
    onAlarmToggle: (Alarm, Boolean) -> Unit,
    onAlarmEdit: (Alarm) -> Unit,
    lang: String
) {
    val nextAlarm = alarms.filter { it.isEnabled }.minByOrNull { it.hour * 60 + it.minute }
    val remainingAlarms = if (nextAlarm != null) alarms.filter { it.id != nextAlarm.id } else alarms

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
fun NextAlarmCard(alarm: Alarm, onToggle: (Alarm, Boolean) -> Unit, onEdit: (Alarm) -> Unit, lang: String) {
    var currentTime by remember { mutableStateOf(Calendar.getInstance()) }
    
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Calendar.getInstance()
            kotlinx.coroutines.delay(1000)
        }
    }

    val alarmTime = Calendar.getInstance().apply {
        this.set(Calendar.HOUR_OF_DAY, alarm.hour)
        this.set(Calendar.MINUTE, alarm.minute)
        this.set(Calendar.SECOND, 0)
        if (this.before(currentTime)) {
            this.add(Calendar.DATE, 1)
        }
    }

    val totalDayMillis = 24 * 60 * 60 * 1000f
    val remainingMillis = alarmTime.timeInMillis - currentTime.timeInMillis
    val progress = (1f - (remainingMillis.toFloat() / totalDayMillis)).coerceIn(0f, 1f)
    val sweepAngle = 360f * progress
    
    val primaryColor = MaterialTheme.colorScheme.primary
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
                Text(
                    text = String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute),
                    color = Color.White,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Box(contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(80.dp)) {
                        drawCircle(color = Color.Black.copy(alpha = 0.3f), style = Stroke(width = 8.dp.toPx()))
                        drawArc(
                            color = dynamicCircleColor,
                            startAngle = -90f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            style = Stroke(width = 8.dp.toPx())
                        )
                    }
                    Switch(
                        checked = alarm.isEnabled,
                        onCheckedChange = { onToggle(alarm, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color.DarkGray
                        )
                    )
                }
            }
            Text(
                text = formatDays(alarm.days, lang),
                color = GrayText,
                fontSize = 12.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
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
                Text(
                    text = String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute),
                    color = if (alarm.isEnabled) Color.White else GrayText,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Normal
                )
                Switch(
                    checked = alarm.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color.DarkGray
                    )
                )
            }
            Text(text = formatDays(alarm.days, lang), color = GrayText, fontSize = 11.sp)
            Text(text = if (alarm.label == "Будильник" || alarm.label == "Alarm") Strings.get("tab_alarm", lang) else alarm.label, color = GrayText, fontSize = 11.sp)
        }
    }
}

@Composable
fun SettingsScreen(appSettings: AppSettings, onColorChange: (Color) -> Unit, onLanguageChange: (String) -> Unit, lang: String) {
    var showColorPicker by remember { mutableStateOf(false) }
    var showLangPicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var isCheckingUpdate by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(Strings.get("settings_title", lang), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        
        SectionHeader(Strings.get("appearance", lang))
        SettingsItem(Strings.get("theme_color", lang), Strings.get("theme_custom", lang), onClick = { showColorPicker = true })
        SettingsItem(Strings.get("language", lang), if (appSettings.language == "ru") "Русский" else "English", onClick = { showLangPicker = true })
        
        Spacer(modifier = Modifier.height(16.dp))
        SectionHeader(Strings.get("notifications", lang))
        SettingsItem(
            title = Strings.get("check_updates", lang), 
            subtitle = if (isCheckingUpdate) "..." else "v1.2.0",
            onClick = {
                scope.launch {
                    isCheckingUpdate = true
                    kotlinx.coroutines.delay(2000)
                    isCheckingUpdate = false
                }
            }
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
                }
            }
        )
    }

    if (showColorPicker) {
        ColorPickerDialog(
            currentColor = appSettings.primaryColor,
            onDismiss = { showColorPicker = false },
            onSelect = { 
                appSettings.primaryColor = it
                onColorChange(it)
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
    var hue by remember { mutableFloatStateOf(0f) }
    
    val presetColors = listOf(
        Color.Red, Color(0xFFFF5722), Color(0xFFFF9800), Color(0xFFFFC107),
        Color.Green, Color(0xFF4CAF50), Color(0xFF00BCD4), Color.Cyan,
        Color.Blue, Color(0xFF2196F3), Color(0xFF673AB7), Color.Magenta,
        Color(0xFFE91E63), Color.White, Color.Gray, Color.DarkGray
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = { Text(Strings.get("theme_color", lang), color = Color.White) },
        text = {
            Column {
                Text(Strings.get("palette", lang), color = GrayText, fontSize = 10.sp, modifier = Modifier.padding(bottom = 12.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.height(200.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(presetColors) { color ->
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (currentColor == color) 3.dp else 1.dp,
                                    color = if (currentColor == color) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { onSelect(color) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                Text(Strings.get("gradient", lang), color = GrayText, fontSize = 10.sp, modifier = Modifier.padding(bottom = 8.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                                )
                            )
                        )
                )
                
                Slider(
                    value = hue,
                    onValueChange = { 
                        hue = it
                        onSelect(Color.hsv(hue, 0.8f, 1f))
                    },
                    valueRange = 0f..360f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.hsv(hue, 0.8f, 1f),
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    )
                )
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
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = Strings.get("app_name", lang), color = MaterialTheme.colorScheme.primary, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
        IconButton(onClick = onAddClick) {
            Icon(Icons.Default.Add, "Add", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
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
            Triple(Strings.get("tab_analysis", lang), Icons.Outlined.BarChart, 1),
            Triple(Strings.get("tab_sleep", lang), Icons.Outlined.Bedtime, 2),
            Triple(Strings.get("tab_settings", lang), Icons.Outlined.Settings, 3)
        )
        items.forEach { item ->
            val isSelected = selectedTab == item.third
            val primaryColor = MaterialTheme.colorScheme.primary
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(item.third) },
                icon = { Icon(item.second, null, tint = if (isSelected) primaryColor else GrayText, modifier = Modifier.size(24.dp)) },
                label = { Text(item.first, fontSize = 9.sp, color = if (isSelected) primaryColor else GrayText) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
            )
        }
    }
}
