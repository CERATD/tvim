package com.example.tvim

import android.app.TimePickerDialog
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

@Composable
fun TvinSettings(lang: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { TvinMessages(context) }
    var enabled by remember { mutableStateOf(store.enabled) }
    var messages by remember { mutableStateOf(store.messages()) }
    var editing by remember { mutableStateOf<TvinMessage?>(null) }
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
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
            Column(Modifier.systemBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(onClick = onDismiss) { Text(if (ru) "Назад" else "Back") }
                Text(if (ru) "Режим Tvin" else "Tvin mode", fontSize = 24.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (ru) "Сообщения по расписанию" else "Scheduled messages", Modifier.weight(1f))
                    TvimSwitch(enabled, { enabled = it; store.enabled = it })
                }
                Text(if (ru) "Ежедневные пожелания в выбранное время. Можно изменить текст и время каждого сообщения."
                    else "Daily greetings at your chosen times. Edit each message and its time.", color = Color.LightGray)
                if (enabled && !notificationsAllowed) {
                    Text(if (ru) "Разрешите уведомления, чтобы получать сообщения Tvin." else "Allow notifications to receive Tvin messages.")
                    TextButton(onClick = {
                        val settings = if (android.os.Build.VERSION.SDK_INT >= 26) {
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
                        context.startActivity(settings)
                    }) { Text(if (ru) "Настройки уведомлений" else "Notification settings") }
                }
                messages.forEach { message ->
                    Surface(color = Color(0xFF1A1A1A), contentColor = Color.White, shape = MaterialTheme.shapes.large) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(String.format(Locale.ROOT, "%02d:%02d", message.hour, message.minute), fontSize = 24.sp)
                            Text(message.text, Modifier.padding(vertical = 12.dp))
                            TextButton(onClick = { editing = message }) { Text(if (ru) "Изменить" else "Edit") }
                        }
                    }
                }
                Text(if (ru) "При энергосбережении Android может задерживать сообщения. Будильники работают отдельно."
                    else "Android may delay messages to save battery. Alarms work separately.", color = Color.LightGray)
            }
        }
    }
    editing?.let { original ->
        var text by remember(original) { mutableStateOf(original.text) }
        var hour by remember(original) { mutableIntStateOf(original.hour) }
        var minute by remember(original) { mutableIntStateOf(original.minute) }
        AlertDialog(onDismissRequest = { editing = null }, containerColor = Color(0xFF1A1A1A),
            title = { Text(if (ru) "Сообщение Tvin" else "Tvin message", color = Color.White) },
            text = {
                Column {
                    OutlinedTextField(value = text, onValueChange = { if (it.length <= 300) text = it },
                        label = { Text(if (ru) "Текст" else "Text") }, minLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        supportingText = { Text("${text.length}/300", color = Color.LightGray) })
                    TextButton(onClick = {
                        TimePickerDialog(context, { _, h, m -> hour = h; minute = m }, hour, minute, true).show()
                    }) { Text((if (ru) "Время: " else "Time: ") + String.format(Locale.ROOT, "%02d:%02d", hour, minute)) }
                }
            },
            confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = {
                store.save(original.copy(hour = hour, minute = minute, text = text))
                messages = store.messages(); editing = null
            }) { Text(if (ru) "Сохранить" else "Save") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(if (ru) "Отмена" else "Cancel") } })
    }
}
