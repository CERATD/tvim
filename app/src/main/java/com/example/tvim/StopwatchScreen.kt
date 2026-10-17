package com.example.tvim

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import com.example.tvim.ui.theme.LocalNothingDesign
import com.example.tvim.ui.theme.NothingDisplayFont

internal fun stopwatchTime(milliseconds: Long): String {
    val hundredths = milliseconds / 10
    return String.format(Locale.ROOT, "%02d:%02d:%02d.%02d", hundredths / 360000,
        hundredths / 6000 % 60, hundredths / 100 % 60, hundredths % 100)
}

@Composable
fun StopwatchScreen(lang: String) {
    val nothingDesign = LocalNothingDesign.current
    val context = LocalContext.current
    val store = remember { StopwatchStore(context) }
    var state by remember { mutableStateOf(store.snapshot()) }
    LaunchedEffect(Unit) {
        while (true) { state = store.snapshot(); kotlinx.coroutines.delay(if (state.running) 40 else 250) }
    }
    val splits = state.laps.mapIndexed { index, total -> total - (state.laps.getOrNull(index - 1) ?: 0) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(Strings.get("tab_stopwatch", lang), style = MaterialTheme.typography.headlineSmall) }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B1B))) {
                Column(Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    TvimDigitalTime(stopwatchTime(state.elapsed), color = Color.White,
                        fontFamily = if (nothingDesign) NothingDisplayFont else FontFamily.Monospace,
                        fontSize = 28.sp, letterSpacing = if (nothingDesign) 1.sp else 0.sp,
                        nothingDesign = nothingDesign)
                    Spacer(Modifier.height(12.dp))
                    Text(if (state.running) (if (lang == "ru") "Идёт отсчёт" else "Running")
                        else if (lang == "ru") "На паузе" else "Paused", color = Color.Gray)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { if (state.running) store.lap() else store.reset(); state = store.snapshot() },
                    enabled = if (state.running) state.laps.size < 500 else state.elapsed > 0,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White, disabledContentColor = Color.DarkGray),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (state.running || state.elapsed > 0) Color.Gray else Color(0xFF303030))) {
                    Text(if (state.running) (if (lang == "ru") "Круг" else "Lap") else if (lang == "ru") "Сбросить" else "Reset")
                }
                Button(onClick = { if (state.running) store.pause() else store.start(); state = store.snapshot() },
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF303030), contentColor = Color.White,
                        disabledContainerColor = Color(0xFF202020), disabledContentColor = Color.Gray)) {
                    Text(if (state.running) (if (lang == "ru") "Пауза" else "Pause")
                        else if (state.elapsed > 0) (if (lang == "ru") "Продолжить" else "Resume")
                        else if (lang == "ru") "Старт" else "Start")
                }
            }
        }
        if (state.laps.isNotEmpty()) item {
            Text(if (lang == "ru") "КРУГИ · время круга / общее" else "LAPS · split / total", color = Color.Gray, fontSize = 12.sp)
        }
        itemsIndexed(state.laps.asReversed()) { reverseIndex, total ->
            val index = state.laps.lastIndex - reverseIndex
            val split = splits[index]
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("#${index + 1}", color = Color.White)
                    Column(horizontalAlignment = Alignment.End) {
                        TvimDigitalTime(stopwatchTime(split), color = Color.White,
                            fontFamily = if (nothingDesign) NothingDisplayFont else FontFamily.Monospace,
                            letterSpacing = if (nothingDesign) 0.7.sp else 0.sp, fontSize = 16.sp,
                            nothingDesign = nothingDesign)
                        TvimDigitalTime(stopwatchTime(total), color = Color.Gray, fontSize = 12.sp,
                            fontFamily = if (nothingDesign) NothingDisplayFont else FontFamily.Monospace,
                            nothingDesign = nothingDesign)
                    }
                }
            }
        }
        if (state.laps.size >= 500) item { Text(if (lang == "ru") "Сохранено 500 кругов. Отсчёт продолжается." else "500 laps saved. Timing continues.") }
    }
}
