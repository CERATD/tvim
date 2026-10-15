package com.example.tvim

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun DesignSheet(title: String, lang: String, onDismiss: () -> Unit, onSave: () -> Unit,
    content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, Strings.get("cancel", lang))
                    }
                    Text(title, style = MaterialTheme.typography.titleLarge)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(16.dp)) {
                    Text(Strings.get("save", lang), color = Color.Black)
                }
            }
        }
    }
}
