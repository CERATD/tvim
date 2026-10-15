package com.example.tvim

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tvim.ui.theme.GrayText
import com.example.tvim.ui.theme.RedAccent

@Composable
fun DayOfWeekPicker(selectedDays: List<Int>, lang: String, onToggle: (List<Int>) -> Unit) {
    val dayLabels = if (lang == "ru") {
        listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС")
    } else {
        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        dayLabels.forEachIndexed { index, label ->
            val day = index + 1
            val isSelected = selectedDays.contains(day)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF1A1A1A))
                    .clickable {
                        val newList = if (isSelected) {
                            selectedDays.filter { it != day }
                        } else {
                            selectedDays + day
                        }
                        onToggle(newList)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) Color.Black else Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    value: String? = null,
    content: @Composable (RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(Color(0xFF1B1B1B)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, color = GrayText, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = GrayText, modifier = Modifier.size(20.dp))
        } else if (content != null) {
            content()
        }
    }
}

@Composable
fun SoundPickerDialog(
    currentSound: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    lang: String
) {
    val context = LocalContext.current
    val defaultSounds = AlarmSounds.builtIns.keys.toList()
    var selected by remember { mutableStateOf(currentSound) }
    val preview = remember { AlarmAudio(context) }
    DisposableEffect(Unit) { onDispose { preview.stop() } }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) preview.stop()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); preview.stop() }
    }
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val candidate = it.toString()
                check(preview.start(candidate, 0.7f, fallback = false))
                selected = candidate
            } catch (_: Exception) {
                android.widget.Toast.makeText(context, if (lang == "ru") "Не удалось открыть аудиофайл" else "Cannot open audio file", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    DesignSheet(Strings.get("melody", lang), lang, onDismiss, { preview.stop(); onSelect(selected) }) {
        OutlinedButton(onClick = { filePickerLauncher.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.UploadFile, null)
            Spacer(Modifier.width(12.dp))
            Text(Strings.get("import_sound", lang))
        }
        val sounds = if (selected in defaultSounds) defaultSounds else listOf(selected) + defaultSounds
        sounds.forEach { sound ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B1B)),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().clickable { selected = sound }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected == sound, { selected = sound })
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(AlarmSounds.title(context, sound), maxLines = 2)
                        Text(if (lang == "ru") "Звук будильника" else "Alarm sound", color = GrayText, fontSize = 12.sp)
                    }
                    IconButton(onClick = { selected = sound; preview.start(sound, 0.7f) }) {
                        Icon(Icons.Default.PlayArrow, if (lang == "ru") "Прослушать" else "Preview", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        OutlinedButton(onClick = { preview.stop() }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Stop, null)
            Text(if (lang == "ru") "Остановить прослушивание" else "Stop preview")
        }
    }
}

@Composable
fun SettingsItem(title: String, subtitle: String, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(Color(0xFF1B1B1B)).clickable { onClick() }.padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp)
            Text(subtitle, color = GrayText, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = GrayText)
    }
}

fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor.use {
            if (it != null && it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = it.getString(index)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}
