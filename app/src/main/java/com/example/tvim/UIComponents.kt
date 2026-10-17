package com.example.tvim

import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
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
        TvimIcon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, color = GrayText, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
            Spacer(modifier = Modifier.width(8.dp))
            TvimIcon(Icons.Default.ChevronRight, contentDescription = null, tint = GrayText, modifier = Modifier.size(20.dp))
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
    val library = remember { SoundLibrary(context) }
    var imported by remember { mutableStateOf(library.entries()) }
    var importing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        imported = withContext(Dispatchers.IO) { library.rememberLegacySounds(); library.entries() }
    }
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
        if (uri != null && !importing) {
            importing = true
            preview.stop()
            scope.launch {
                try {
                    val entry = withContext(Dispatchers.IO) { library.importSound(uri) }
                    imported = library.entries()
                    selected = entry.id
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    android.widget.Toast.makeText(context, if (lang == "ru") "Не удалось сохранить аудио. Выберите доступный аудиофайл до 50 МБ."
                        else "Cannot save audio. Choose an accessible audio file up to 50 MB.", android.widget.Toast.LENGTH_LONG).show()
                } finally { importing = false }
            }
        }
    }

    DesignSheet(Strings.get("melody", lang), lang, onDismiss, { preview.stop(); onSelect(selected) }, saveEnabled = !importing) {
        OutlinedButton(onClick = { filePickerLauncher.launch(arrayOf("audio/*")) }, enabled = !importing, modifier = Modifier.fillMaxWidth()) {
            TvimIcon(Icons.Default.UploadFile, null)
            Spacer(Modifier.width(12.dp))
            Text(Strings.get("import_sound", lang))
        }
        if (importing) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(if (lang == "ru") "Сохраняю мелодию…" else "Saving sound…", color = GrayText)
        }
        Text(if (lang == "ru") "Импорт один раз — мелодия доступна всем будильникам. До 50 МБ."
            else "Import once and use in any alarm. Up to 50 MB.", color = GrayText, fontSize = 12.sp)
        val custom = (imported.map { it.id } + listOf(selected).filter { it !in defaultSounds }).distinct()
        val groups = listOf(
            (if (lang == "ru") "МОИ МЕЛОДИИ" else "MY SOUNDS") to custom,
            (if (lang == "ru") "ВСТРОЕННЫЕ" else "BUILT-IN") to defaultSounds)
        groups.forEach { (heading, sounds) ->
            if (sounds.isNotEmpty()) Text(heading, color = GrayText, fontSize = 12.sp)
            sounds.forEach { sound ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B1B)),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)) {
                    Row(Modifier.fillMaxWidth().clickable(enabled = !importing) { selected = sound }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected == sound, { selected = sound }, enabled = !importing)
                        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            Text(AlarmSounds.title(context, sound), maxLines = 2)
                            Text(AlarmSounds.description(sound, lang), color = GrayText, fontSize = 12.sp)
                        }
                        IconButton(enabled = !importing, onClick = {
                            if (preview.start(sound, 0.7f, fallback = false)) selected = sound
                            else android.widget.Toast.makeText(context, if (lang == "ru") "Файл недоступен. Импортируйте его снова."
                                else "File unavailable. Import it again.", android.widget.Toast.LENGTH_LONG).show()
                        }) {
                            TvimIcon(Icons.Default.PlayArrow, if (lang == "ru") "Прослушать" else "Preview", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = { preview.stop() }, modifier = Modifier.fillMaxWidth()) {
            TvimIcon(Icons.Default.Stop, null)
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
        TvimIcon(Icons.Default.ChevronRight, null, tint = GrayText)
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
        result = uri.path?.replace('\\', '/')
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}
