package com.example.tvim

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.tvim.ui.theme.GrayText
import com.example.tvim.ui.theme.RedAccent
import com.example.tvim.ui.theme.TvimTheme
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val appSettings = remember { AppSettings(context) }
            var currentPrimaryColor by remember { mutableStateOf(appSettings.primaryColor) }
            var currentLanguage by remember { mutableStateOf(appSettings.language) }
            
            TvimTheme(primaryColor = currentPrimaryColor) {
                val storage = remember { AlarmStorage(context) }
                val scheduler = remember { AlarmScheduler(context) }
                val updateManager = remember { UpdateManager(context) }
                var alarms by remember { mutableStateOf(storage.getAlarms()) }
                var showAddDialog by remember { mutableStateOf(false) }
                var showUpdateDialog by remember { mutableStateOf<GitHubRelease?>(null) }
                var showDownloadStatus by remember { mutableStateOf(intent.getBooleanExtra("SHOW_UPDATES", false)) }
                var editingAlarm by remember { mutableStateOf<Alarm?>(null) }

                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { isGranted ->
                        if (isGranted) {
                            scheduler.scheduleAll()
                            CountdownTimer(context).restore()

                        }
                    }
                )

                LaunchedEffect(Unit) {
                    val savedProgress = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { UpdateDownloads(context).progress() }
                    if (savedProgress.phase == UpdatePhase.READY) showDownloadStatus = true
                    val release = updateManager.checkForUpdates()
                    if (release != null) {
                        showUpdateDialog = release
                    }

                }
                LaunchedEffect(Unit) {
                    scheduler.scheduleAll()
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
                DisposableEffect(lifecycle) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                            scheduler.scheduleAll()
                            CountdownTimer(context).restore()
                            alarms = storage.getAlarms()
                        }
                    }
                    lifecycle.addObserver(observer)
                    val prefs = context.getSharedPreferences("alarms_prefs", Context.MODE_PRIVATE)
                    val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                        if (key == "alarms_list") alarms = storage.getAlarms()
                    }
                    prefs.registerOnSharedPreferenceChangeListener(listener)
                    onDispose { lifecycle.removeObserver(observer); prefs.unregisterOnSharedPreferenceChangeListener(listener) }
                }

                if (showUpdateDialog != null || showDownloadStatus) {
                    UpdateDialog(initial = showUpdateDialog, lang = currentLanguage,
                        onDismiss = { showUpdateDialog = null; showDownloadStatus = false })
                }

                Scaffold(
                    containerColor = Color.Black
                ) { padding ->
                    Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                        TvimDashboard(
                            alarms = alarms,
                            storage = storage,
                            appSettings = appSettings,
                            lang = currentLanguage,
                            onPrimaryColorChange = { currentPrimaryColor = it },
                            onLanguageChange = { 
                                appSettings.language = it
                                currentLanguage = it 
                            },
                            onAlarmToggle = { alarm, enabled ->
                                val updated = alarm.copy(isEnabled = enabled)
                                storage.updateAlarm(updated)
                                if (enabled) scheduler.schedule(updated) else scheduler.cancel(updated.id)
                                alarms = storage.getAlarms()
                            },
                            onAlarmDelete = { alarm ->
                                scheduler.cancel(alarm.id)
                                storage.deleteAlarm(alarm.id)
                                alarms = storage.getAlarms()
                            },
                            onAlarmEdit = { alarm ->
                                editingAlarm = alarm
                            },
                            onAddAlarm = { showAddDialog = true }
                        )
                    }

                    if (showAddDialog) {
                        AlarmSettingsDialog(
                            onDismiss = { showAddDialog = false },
                            onSave = { updatedAlarm ->
                                storage.addAlarm(updatedAlarm)
                                scheduler.schedule(updatedAlarm)
                                alarms = storage.getAlarms()
                                showAddDialog = false
                            },
                            lang = currentLanguage
                        )
                    }

                    editingAlarm?.let { alarm ->
                        AlarmSettingsDialog(
                            alarm = alarm,
                            onDismiss = { editingAlarm = null },
                            onSave = { updatedAlarm ->
                                storage.updateAlarm(updatedAlarm)
                                if (updatedAlarm.isEnabled) scheduler.schedule(updatedAlarm) else scheduler.cancel(updatedAlarm.id)
                                alarms = storage.getAlarms()
                                editingAlarm = null
                            },
                            onDelete = { id ->
                                scheduler.cancel(id)
                                storage.deleteAlarm(id)
                                alarms = storage.getAlarms()
                                editingAlarm = null
                            },
                            lang = currentLanguage
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmSettingsDialog(
    alarm: Alarm? = null,
    onDismiss: () -> Unit,
    onSave: (Alarm) -> Unit,
    onDelete: ((String) -> Unit)? = null,
    lang: String
) {
    val timePickerState = rememberTimePickerState(
        initialHour = alarm?.hour ?: 8,
        initialMinute = alarm?.minute ?: 0,
        is24Hour = true
    )
    
    var label by remember(lang) { mutableStateOf(alarm?.label ?: Strings.get("tab_alarm", lang)) }
    var selectedDays by remember { mutableStateOf(alarm?.days ?: emptyList()) }
    var selectedMission by remember { mutableStateOf(alarm?.missionType ?: MissionType.NONE) }
    var ringtoneName by remember { mutableStateOf(alarm?.ringtoneName ?: "Morning Light") }
    var volume by remember { mutableFloatStateOf(alarm?.volume ?: 0.7f) }
    var isVibrationEnabled by remember { mutableStateOf(alarm?.isVibrationEnabled ?: true) }
    var vibrationPattern by remember { mutableStateOf(alarm?.vibrationPattern ?: "Standard") }
    var gradualWakeup by remember { mutableIntStateOf(alarm?.gradualWakeup ?: 0) }
    
    var showSoundPicker by remember { mutableStateOf(false) }
    var showMissionPicker by remember { mutableStateOf(false) }
    var showVibrationPicker by remember { mutableStateOf(false) }
    var showGradualPicker by remember { mutableStateOf(false) }
    var showLabelDialog by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding().imePadding()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                    Text(
                        text = if (alarm == null) Strings.get("add_alarm", lang) else Strings.get("edit_alarm", lang),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.width(48.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            TimePicker(
                                state = timePickerState,
                                colors = TimePickerDefaults.colors(
                                    clockDialColor = Color(0xFF1A1A1A),
                                    clockDialSelectedContentColor = Color.Black,
                                    clockDialUnselectedContentColor = Color.White,
                                    selectorColor = MaterialTheme.colorScheme.primary,
                                    periodSelectorSelectedContainerColor = MaterialTheme.colorScheme.primary,
                                    periodSelectorUnselectedContainerColor = Color(0xFF1A1A1A),
                                    periodSelectorSelectedContentColor = Color.Black,
                                    periodSelectorUnselectedContentColor = Color.White,
                                    timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.primary,
                                    timeSelectorUnselectedContainerColor = Color(0xFF1A1A1A),
                                    timeSelectorSelectedContentColor = Color.Black,
                                    timeSelectorUnselectedContentColor = Color.White
                                )
                            )
                        }
                    }

                    item {
                        Text(Strings.get("repeat", lang), color = GrayText, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                        DayOfWeekPicker(selectedDays = selectedDays, lang = lang) {
                            selectedDays = it
                        }
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            SettingsRow(
                                icon = Icons.Default.Label,
                                title = Strings.get("name", lang),
                                value = if (label.isEmpty()) Strings.get("tab_alarm", lang) else label,
                                onClick = { showLabelDialog = true }
                            )
                            SettingsRow(
                                icon = Icons.Default.MusicNote,
                                title = Strings.get("melody", lang),
                                value = AlarmSounds.title(LocalContext.current, ringtoneName),
                                onClick = { showSoundPicker = true }
                            )
                            
                            SettingsRow(
                                icon = Icons.Default.VolumeUp,
                                title = Strings.get("volume", lang),
                                content = {
                                    Slider(
                                        value = volume,
                                        onValueChange = { volume = it },
                                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.primary,
                                            activeTrackColor = MaterialTheme.colorScheme.primary,
                                            inactiveTrackColor = Color.DarkGray
                                        )
                                    )
                                }
                            )

                            SettingsRow(
                                icon = Icons.Default.Vibration,
                                title = Strings.get("vibration", lang),
                                value = if (isVibrationEnabled) vibrationPattern else "Off",
                                onClick = { showVibrationPicker = true }
                            )

                            SettingsRow(
                                icon = Icons.Default.Extension,
                                title = Strings.get("mission", lang),
                                value = when(selectedMission) {
                                    MissionType.NONE -> Strings.get("mission_none", lang)
                                    MissionType.MATH -> Strings.get("mission_math", lang)
                                    MissionType.SHAKE -> Strings.get("mission_shake", lang)
                                    MissionType.TYPING -> Strings.get("mission_typing", lang)
                                },
                                onClick = { showMissionPicker = true }
                            )

                            SettingsRow(
                                icon = Icons.Default.Timer,
                                title = Strings.get("gradual_wakeup", lang),
                                value = if (gradualWakeup == 0) "Off" else "$gradualWakeup min",
                                onClick = { showGradualPicker = true }
                            )
                        }

                        Spacer(Modifier.height(24.dp))
                    Button(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp), onClick = {
                        val newAlarm = (alarm ?: Alarm(hour = 0, minute = 0)).copy(
                            hour = timePickerState.hour,
                            minute = timePickerState.minute,
                            label = label,
                            days = selectedDays,
                            missionType = selectedMission,
                            ringtoneName = ringtoneName,
                            volume = volume,
                            isVibrationEnabled = isVibrationEnabled,
                            vibrationPattern = vibrationPattern,
                            gradualWakeup = gradualWakeup
                        )
                        onSave(newAlarm)
                    }) {
                        Text(Strings.get("save", lang), color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                        if (alarm != null) {
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = { onDelete?.invoke(alarm.id) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.Gray),
                                border = BorderStroke(1.dp, Color.DarkGray),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(Strings.get("delete_alarm", lang))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSoundPicker) {
        SoundPickerDialog(
            currentSound = ringtoneName,
            onDismiss = { showSoundPicker = false },
            onSelect = { 
                ringtoneName = it
                showSoundPicker = false
            },
            lang = lang
        )
    }

    if (showMissionPicker) {
        MissionPickerDialog(
            currentMission = selectedMission,
            onDismiss = { showMissionPicker = false },
            onSelect = {
                selectedMission = it
                showMissionPicker = false
            },
            lang = lang
        )
    }

    if (showVibrationPicker) {
        VibrationPickerDialog(
            currentPattern = if (isVibrationEnabled) vibrationPattern else "Off",
            onDismiss = { showVibrationPicker = false },
            onSelect = {
                vibrationPattern = it
                isVibrationEnabled = it != "Off"
                showVibrationPicker = false
            },
            lang = lang
        )
    }

    if (showGradualPicker) {
        GradualWakeupPickerDialog(
            currentValue = gradualWakeup,
            onDismiss = { showGradualPicker = false },
            onSelect = {
                gradualWakeup = it
                showGradualPicker = false
            },
            lang = lang
        )
    }

    if (showLabelDialog) {
        LabelEditDialog(
            initialLabel = label,
            onDismiss = { showLabelDialog = false },
            onSave = {
                label = it
                showLabelDialog = false
            },
            lang = lang
        )
    }
}

@Composable
fun LabelEditDialog(initialLabel: String, onDismiss: () -> Unit, onSave: (String) -> Unit, lang: String) {
    var text by remember { mutableStateOf(initialLabel) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = { Text(Strings.get("name", lang), color = Color.White) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text("OK", color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.get("cancel", lang), color = Color.Gray) }
        }
    )
}

@Composable
fun VibrationPickerDialog(currentPattern: String, onDismiss: () -> Unit, onSelect: (String) -> Unit, lang: String) {
    val context = LocalContext.current
    val preview = remember { AlarmVibration(context) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) preview.stop()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); preview.stop() }
    }
    val patterns = listOf("Standard" to "vibration_standard", "Pulse" to "vibration_pulse",
        "Knock" to "vibration_knock", "Melody" to "vibration_melody")
    var selected by remember { mutableStateOf(if (currentPattern == "Off") "Standard" else currentPattern) }
    var enabled by remember { mutableStateOf(currentPattern != "Off") }
    DesignSheet(Strings.get("vibration", lang), lang, onDismiss, { onSelect(if (enabled) selected else "Off") }) {
        SettingsRow(Icons.Default.Vibration, Strings.get("vibration", lang), content = {
            Switch(enabled, { enabled = it; if (it) preview.start(selected, repeat = false) else preview.stop() })
        })
        if (!preview.available()) Text(if (lang == "ru") "На этом устройстве нет вибромотора." else "This device has no vibrator.", color = GrayText)
        OutlinedButton(onClick = { preview.start(selected, repeat = false) }, enabled = enabled && preview.available(),
            modifier = Modifier.fillMaxWidth()) {
            Text(if (lang == "ru") "Проверить вибрацию" else "Test vibration")
        }
        patterns.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEachIndexed { index, (pattern, key) ->
                    Card(onClick = { selected = pattern; enabled = true; preview.start(pattern, repeat = false) }, modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, if (enabled && selected == pattern) MaterialTheme.colorScheme.primary else Color.Transparent),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B1B)),
                        shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(Icons.Default.Vibration, null, Modifier.size(32.dp),
                                tint = if (enabled && selected == pattern) MaterialTheme.colorScheme.primary else Color.Gray)
                            Text(Strings.get(key, lang), textAlign = TextAlign.Center)
                            RadioButton(enabled && selected == pattern, { selected = pattern; enabled = true; preview.start(pattern, repeat = false) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GradualWakeupPickerDialog(currentValue: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit, lang: String) {
    var selected by remember { mutableIntStateOf(currentValue) }
    DesignSheet(Strings.get("gradual_wakeup", lang), lang, onDismiss, { onSelect(selected) }) {
        Text(if (lang == "ru") "Громкость плавно увеличивается до выбранного уровня." else "Volume gradually rises to your selected level.", color = GrayText)
        listOf(0, 1, 3, 5, 10, 15).chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { value ->
                    FilterChip(selected == value, { selected = value },
                        label = { Text(if (value == 0) (if (lang == "ru") "Выкл." else "Off") else "$value " + if (lang == "ru") "мин" else "min") },
                        modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun MissionPickerDialog(
    currentMission: MissionType,
    onDismiss: () -> Unit,
    onSelect: (MissionType) -> Unit,
    lang: String
) {
    val missions = listOf(
        MissionType.NONE to ("mission_none" to Icons.Default.Notifications),
        MissionType.MATH to ("mission_math" to Icons.Default.Calculate),
        MissionType.SHAKE to ("mission_shake" to Icons.Default.Vibration),
        MissionType.TYPING to ("mission_typing" to Icons.Default.Keyboard)
    )

    var selected by remember { mutableStateOf(currentMission) }
    DesignSheet(Strings.get("mission", lang), lang, onDismiss, { onSelect(selected) }) {
        missions.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (type, info) ->
                    Card(
                        onClick = { selected = type },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, if (selected == type) MaterialTheme.colorScheme.primary else Color.Transparent),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B1B))
                    ) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(info.second, null, Modifier.size(36.dp), tint = if (selected == type) MaterialTheme.colorScheme.primary else Color.Gray)
                            Text(Strings.get(info.first, lang), textAlign = TextAlign.Center)
                            RadioButton(selected == type, { selected = type })
                        }
                    }
                }
            }
        }
    }
}
