package com.example.tvim

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun UpdateDialog(initial: GitHubRelease? = null, lang: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val downloads = remember { UpdateDownloads(context) }
    var release by remember { mutableStateOf(downloads.release() ?: initial) }
    var progress by remember { mutableStateOf(UpdateProgress(UpdatePhase.IDLE)) }
    var checking by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var installRequested by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (downloads.canInstall()) installRequested = true
        else message = if (lang == "ru") "Для установки разрешите обновления из Tvim и нажмите «Установить» ещё раз."
            else "Allow installations from Tvim, then tap Install again."
    }
    LaunchedEffect(Unit) {
        progress = withContext(Dispatchers.IO) { downloads.progress() }
        if (progress.phase == UpdatePhase.IDLE) {
            release = initial
            if (release == null) {
                val manager = UpdateManager(context)
                release = manager.checkForUpdates()
                if (manager.lastCheckFailed) message = if (lang == "ru") "Не удалось проверить обновления. Повторите позже."
                    else "Could not check for updates. Try again later."
            }
        }
        checking = false
        while (true) {
            progress = withContext(Dispatchers.IO) { downloads.progress() }
            delay(750)
        }
    }
    LaunchedEffect(installRequested) {
        if (!installRequested) return@LaunchedEffect
        busy = true; message = null
        try {
            val file = withContext(Dispatchers.IO) { downloads.verifiedFile() }
            if (downloads.canInstall()) context.startActivity(downloads.installIntent(file))
            else permission.launch(downloads.permissionIntent())
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            message = if (lang == "ru") "Не удалось проверить или установить APK. Удалите загрузку и скачайте заново."
                else "Cannot verify or install this APK. Remove the download and try again."
        } finally { busy = false; installRequested = false }
    }
    val active = progress.phase in setOf(UpdatePhase.DOWNLOADING, UpdatePhase.WAITING)
    AlertDialog(onDismissRequest = onDismiss, containerColor = Color(0xFF1B1B1B),
        title = { Text(if (lang == "ru") "Обновление Tvim" else "Tvim update") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                release?.let { Text(if (lang == "ru") "Версия ${it.tagName}" else "Version ${it.tagName}") }
                Text(when {
                    checking -> if (lang == "ru") "Проверяю обновления…" else "Checking for updates…"
                    busy -> if (lang == "ru") "Подготавливаю обновление…" else "Preparing update…"
                    progress.phase == UpdatePhase.READY -> if (lang == "ru") "Загрузка завершена. Можно установить обновление." else "Download complete. Ready to install."
                    progress.phase == UpdatePhase.WAITING -> if (lang == "ru") "Ожидаю сеть или начала загрузки…" else "Waiting for network or download…"
                    active -> if (lang == "ru") "Обновление скачивается в фоне." else "Update is downloading in the background."
                    progress.phase == UpdatePhase.FAILED -> if (lang == "ru") "Загрузка не завершена. Проверьте сеть и свободное место, затем повторите." else "Download failed. Check connection and storage, then retry."
                    release != null -> if (lang == "ru") "Скачать обновление внутри приложения?" else "Download the update in the app?"
                    message != null -> if (lang == "ru") "Проверка обновлений не завершена." else "Update check did not complete."
                    else -> if (lang == "ru") "Новых обновлений не найдено." else "No new update found."
                })
                if (active) {
                    if (progress.total > 0) {
                        val fraction = (progress.bytes.toFloat() / progress.total).coerceIn(0f, 1f)
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        Text("${(fraction * 100).toInt()}% · ${progress.bytes / 1048576} / ${progress.total / 1048576} MB")
                    } else LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (release != null) Text(if (lang == "ru") "Можно закрыть это окно: загрузка продолжится. Установку подтвердит Android."
                    else "You can close this window; downloading continues. Android will ask you to confirm installation.",
                    style = MaterialTheme.typography.bodySmall)
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                if (progress.phase != UpdatePhase.IDLE) TextButton(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        try { withContext(Dispatchers.IO) { downloads.cancel() }; progress = UpdateProgress(UpdatePhase.IDLE); message = null }
                        finally { busy = false }
                    }
                }) { Text(if (lang == "ru") "Удалить загрузку" else "Remove download") }
            }
        },
        confirmButton = {
            if (!checking && release != null && !active) TextButton(enabled = !busy, onClick = {
                if (progress.phase == UpdatePhase.READY) installRequested = true
                else scope.launch {
                    busy = true; message = null
                    try {
                        withContext(Dispatchers.IO) { downloads.begin(requireNotNull(release)) }
                        progress = withContext(Dispatchers.IO) { downloads.progress() }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { message = if (lang == "ru") "Не удалось начать загрузку. Проверьте свободное место и системный диспетчер загрузок."
                        else "Cannot start download. Check storage and the system download manager." }
                    finally { busy = false }
                }
            }) { Text(if (progress.phase == UpdatePhase.READY) (if (lang == "ru") "Установить" else "Install")
                else if (lang == "ru") "Скачать" else "Download") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (lang == "ru") "Закрыть" else "Close") } })
}

class UpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val downloads = UpdateDownloads(context)
        if (downloads.id() < 0 || intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != downloads.id()) return
        val progress = downloads.progress()
        if (progress.phase !in setOf(UpdatePhase.READY, UpdatePhase.FAILED)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel("tvim_updates", "Tvim updates", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 170,
            Intent(context, MainActivity::class.java).putExtra("SHOW_UPDATES", true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val ru = AppSettings(context).language == "ru"
        val notification = NotificationCompat.Builder(context, "tvim_updates")
            .setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle("Tvim")
            .setContentText(if (progress.phase == UpdatePhase.READY) (if (ru) "Обновление скачано. Нажмите для установки." else "Update downloaded. Tap to install.")
                else if (ru) "Не удалось скачать обновление. Нажмите для повтора." else "Update download failed. Tap to retry.")
            .setContentIntent(open).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(context).notify(170, notification) }
        catch (_: SecurityException) {
            // Downloads remain available in Settings when notifications are denied.
        }
    }
}
