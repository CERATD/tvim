package com.example.tvim

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvinSettings(lang: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { TvinMessages(context) }
    var enabled by remember { mutableStateOf(store.enabled) }
    var messages by remember(lang) { mutableStateOf(store.messages()) }
    var page by rememberSaveable { mutableStateOf("home") }
    var scheduleId by rememberSaveable { mutableIntStateOf(0) }
    var editingIndex by rememberSaveable { mutableIntStateOf(-1) }
    var editText by rememberSaveable { mutableStateOf("") }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var notificationsAllowed by remember { mutableStateOf(store.notificationsAllowed()) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) notificationsAllowed = store.notificationsAllowed()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val ru = lang == "ru"
    val selected = messages.first { it.id == scheduleId }
    fun period(id: Int) = if (id == 0) { if (ru) "Утро" else "Morning" } else { if (ru) "Вечер" else "Evening" }
    fun save(message: TvinMessage) { store.save(message); messages = store.messages() }
    fun back() { if (page == "home") onDismiss() else page = "home" }
    Dialog(onDismissRequest = { back() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(onClick = { back() }) { Text(if (ru) "Назад" else "Back") }
                Text(when (page) {
                    "messages" -> (if (ru) "Сообщения · " else "Messages · ") + period(scheduleId)
                    "time" -> (if (ru) "Время · " else "Time · ") + period(scheduleId)
                    else -> if (ru) "Режим TVIM" else "TVIM mode"
                }, fontSize = 24.sp)
                when (page) {
                    "home" -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (ru) "Сообщения по расписанию" else "Scheduled messages", Modifier.weight(1f))
                            TvimSwitch(enabled, { enabled = it; store.enabled = it })
                        }
                        Text(if (ru) "Случайное пожелание из списка, без повторов подряд." else "A random greeting from your list, with no back-to-back repeats.", color = Color.LightGray)
                        if (enabled && !notificationsAllowed) {
                            Text(if (ru) "Разрешите уведомления, чтобы получать сообщения TVIM." else "Allow notifications to receive TVIM messages.")
                            TextButton(onClick = {
                                val intent = if (android.os.Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
                                context.startActivity(intent)
                            }) { Text(if (ru) "Настройки уведомлений" else "Notification settings") }
                        }
                        messages.forEach { message ->
                            Surface(color = Color(0xFF1A1A1A), contentColor = Color.White, shape = MaterialTheme.shapes.large) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(period(message.id), fontSize = 20.sp)
                                    Text(String.format(Locale.ROOT, "%02d:%02d", message.hour, message.minute), fontSize = 28.sp)
                                    TextButton(onClick = { scheduleId = message.id; page = "time" }) { Text(if (ru) "Изменить время" else "Change time") }
                                    TextButton(onClick = { scheduleId = message.id; page = "messages" }) {
                                        Text((if (ru) "Сообщения" else "Messages") + " (${message.texts.size})")
                                    }
                                }
                            }
                        }
                        Text(if (ru) "При энергосбережении Android может задерживать сообщения. Будильники работают отдельно." else "Android may delay messages to save battery. Alarms work separately.", color = Color.LightGray)
                    }
                    "time" -> key(scheduleId) {
                        val time = rememberTimePickerState(selected.hour, selected.minute, is24Hour = true)
                        var input by rememberSaveable { mutableStateOf(false) }
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            TvimTimePicker(time, input)
                            TextButton(onClick = { input = !input }) {
                                Text(if (input) { if (ru) "Циферблат" else "Clock dial" } else { if (ru) "Ввести цифрами" else "Enter digits" })
                            }
                            Button(onClick = { save(selected.copy(hour = time.hour, minute = time.minute)); page = "home" },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(if (ru) "Сохранить" else "Save") }
                        }
                    }
                    "messages" -> {
                        Text(if (ru) "Каждая карточка — отдельный вариант. Нажмите на неё, чтобы изменить текст." else "Each card is a separate choice. Tap it to edit the message.", color = Color.LightGray)
                        selected.texts.forEachIndexed { index, text ->
                            Surface(onClick = { editingIndex = index; editText = text; showEditor = true },
                                color = Color(0xFF1A1A1A), contentColor = Color.White, shape = MaterialTheme.shapes.large) {
                                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                    Text("${index + 1}", color = MaterialTheme.colorScheme.primary)
                                    Text(text, Modifier.padding(top = 8.dp))
                                }
                            }
                        }
                        Button(enabled = selected.texts.size < 30, onClick = { editingIndex = -1; editText = ""; showEditor = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(if (ru) "Добавить сообщение" else "Add message") }
                        Text("${selected.texts.size}/30", color = Color.LightGray)
                    }
                }
            }
        }
    }
    if (showEditor) {
        val duplicate = selected.texts.withIndex().any { (index, text) -> index != editingIndex && text == editText.trim() }
        AlertDialog(onDismissRequest = { showEditor = false }, containerColor = Color(0xFF1A1A1A),
            title = { Text(if (ru) "Сообщение TVIM" else "TVIM message", color = Color.White) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(value = editText, onValueChange = { if (it.length <= 300) editText = it },
                        label = { Text(if (ru) "Текст сообщения" else "Message text") }, minLines = 3, maxLines = 6,
                        isError = duplicate,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        supportingText = { Text(if (duplicate) { if (ru) "Такой вариант уже есть" else "This choice already exists" } else "${editText.length}/300", color = Color.LightGray) })
                    if (editingIndex >= 0) TextButton(enabled = selected.texts.size > 1, onClick = {
                        save(selected.copy(texts = selected.texts.filterIndexed { index, _ -> index != editingIndex })); showEditor = false
                    }) { Text(if (ru) "Удалить сообщение" else "Delete message") }
                }
            },
            confirmButton = { TextButton(enabled = editText.isNotBlank() && !duplicate, onClick = {
                val updated = selected.texts.toMutableList()
                if (editingIndex < 0) updated.add(editText.trim()) else updated[editingIndex] = editText.trim()
                save(selected.copy(texts = updated)); showEditor = false
            }) { Text(if (ru) "Сохранить" else "Save") } },
            dismissButton = { TextButton(onClick = { showEditor = false }) { Text(if (ru) "Отмена" else "Cancel") } })
    }
}
