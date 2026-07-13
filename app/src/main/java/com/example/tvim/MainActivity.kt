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
                var editingAlarm by remember { mutableStateOf<Alarm?>(null) }

                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { isGranted ->
                        if (isGranted) {
                            scheduler.scheduleAll()
                            NotificationReceiver.scheduleAlarms(context)
                        }
                    }
                )

                LaunchedEffect(Unit) {
                    val release = updateManager.checkForUpdates()
                    if (release != null) {
                        showUpdateDialog = release
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        scheduler.scheduleAll()
                        NotificationReceiver.scheduleAlarms(context)
                    }
                }

                // Update Dialog
                showUpdateDialog?.let { release ->
                    AlertDialog(
                        onDismissRequest = { showUpdateDialog = null },
                        title = { Text(Strings.get("check_updates", currentLanguage), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                        text = { Text("Version ${release.tagName} is available. Update now?", color = Color.White) },
                        containerColor = Color(0xFF1A1A1A),
                        confirmButton = {
                            TextButton(onClick = {
                                val apkUrl = release.assets.firstOrNull { it.name.endsWith(".apk") }?.downloadUrl
                                apkUrl?.let { updateManager.downloadAndInstall(it) }
                                showUpdateDialog = null
                            }) {
                                Text(Strings.get("save", currentLanguage), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showUpdateDialog = null }) {
                                Text(Strings.get("cancel", currentLanguage), color = Color.Gray)
                            }
                        }
                    )
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
                    .statusBarsPadding()
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
                    TextButton(onClick = {
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
                        Text(Strings.get("save", lang), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
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
                                value = ringtoneName,
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
            currentPattern = vibrationPattern,
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
    val patterns = listOf(
        "Off" to "vibration_off",
        "Standard" to "vibration_standard",
        "Pulse" to "vibration_pulse",
        "Knock" to "vibration_knock",
        "Melody" to "vibration_melody"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = { Text(Strings.get("vibration", lang), color = Color.White) },
        text = {
            Column {
                patterns.forEach { (pattern, key) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(pattern) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentPattern == pattern,
                            onClick = { onSelect(pattern) },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Text(Strings.get(key, lang), color = Color.White, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.get("cancel", lang), color = Color.Gray) }
        }
    )
}

@Composable
fun GradualWakeupPickerDialog(currentValue: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit, lang: String) {
    val values = listOf(0, 1, 3, 5, 10, 15)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = { Text(Strings.get("gradual_wakeup", lang), color = Color.White) },
        text = {
            Column {
                values.forEach { value ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentValue == value,
                            onClick = { onSelect(value) },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Text(if (value == 0) "Off" else "$value min", color = Color.White, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.get("cancel", lang), color = Color.Gray) }
        }
    )
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

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = { Text(Strings.get("mission", lang), color = Color.White) },
        text = {
            Column {
                missions.forEach { (type, info) ->
                    val labelKey = info.first
                    val icon = info.second
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(type) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentMission == type,
                            onClick = { onSelect(type) },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Text(Strings.get(labelKey, lang), color = Color.White, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.get("cancel", lang), color = Color.Gray) }
        }
    )
}
