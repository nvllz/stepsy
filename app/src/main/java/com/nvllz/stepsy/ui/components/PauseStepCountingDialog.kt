package com.nvllz.stepsy.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nvllz.stepsy.R

@Composable
fun PauseStepCountingDialog(
    onDismiss: () -> Unit,
    onPauseForMinutes: (Int) -> Unit,
    onCustomTime: () -> Unit,
    onPauseIndefinitely: () -> Unit,
) {
    StepsyDialog(
        title = stringResource(R.string.pause_step_counting),
        onDismiss = onDismiss,
        dismissText = stringResource(android.R.string.cancel),
    ) {
        val maxOptionsHeight = (LocalConfiguration.current.screenHeightDp * 0.42f).dp
            .coerceIn(120.dp, 360.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxOptionsHeight)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            PauseOption(stringResource(R.string.pause_30_minutes)) { onPauseForMinutes(30) }
            PauseOption(stringResource(R.string.pause_1_hour)) { onPauseForMinutes(60) }
            PauseOption(stringResource(R.string.pause_2_hours)) { onPauseForMinutes(120) }
            PauseOption(stringResource(R.string.pause_custom_time), onCustomTime)
            PauseOption(stringResource(R.string.pause_indefinitely), onPauseIndefinitely)
        }
    }
}

@Composable
private fun PauseOption(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
    )
}
