package com.example.tvim

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Shared by alarms and scheduled greetings; inherits the active Nothing typography. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvimTimePicker(state: TimePickerState, input: Boolean = false) {
    val colors = TimePickerDefaults.colors(
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
    if (input) TimeInput(state = state, colors = colors)
    else TimePicker(state = state, colors = colors)
}
