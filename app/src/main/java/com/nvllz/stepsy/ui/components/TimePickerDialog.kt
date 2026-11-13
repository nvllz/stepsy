package com.nvllz.stepsy.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePickerLayoutType
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.nvllz.stepsy.ui.theme.StepsyTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
    is24Hour: Boolean = true,
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = is24Hour,
    )
    val config = LocalConfiguration.current
    val compact = config.screenHeightDp < 640 || config.screenWidthDp < 360
    val colors = TimePickerDefaults.colors(
        clockDialColor = StepsyTheme.colors.muted,
        clockDialSelectedContentColor = StepsyTheme.colors.background,
        clockDialUnselectedContentColor = MaterialTheme.colorScheme.onSurface,
        selectorColor = StepsyTheme.colors.accent,
        containerColor = StepsyTheme.colors.dialogSurface,
        periodSelectorBorderColor = StepsyTheme.colors.accent,
        periodSelectorSelectedContainerColor = StepsyTheme.colors.accent,
        periodSelectorUnselectedContainerColor = Color.Transparent,
        periodSelectorSelectedContentColor = StepsyTheme.colors.background,
        periodSelectorUnselectedContentColor = MaterialTheme.colorScheme.onSurface,
        timeSelectorSelectedContainerColor = StepsyTheme.colors.accent,
        timeSelectorUnselectedContainerColor = StepsyTheme.colors.muted,
        timeSelectorSelectedContentColor = StepsyTheme.colors.background,
        timeSelectorUnselectedContentColor = MaterialTheme.colorScheme.onSurface,
    )
    StepsyDialog(
        title = title,
        onDismiss = onDismiss,
        confirmText = stringResource(android.R.string.ok),
        onConfirm = { onConfirm(state.hour, state.minute) },
    ) {
        if (compact) {
            TimeInput(state = state, modifier = Modifier.fillMaxWidth(), colors = colors)
        } else {
            TimePicker(
                state = state,
                modifier = Modifier.fillMaxWidth(),
                colors = colors,
                layoutType = TimePickerLayoutType.Vertical,
            )
        }
    }
}
