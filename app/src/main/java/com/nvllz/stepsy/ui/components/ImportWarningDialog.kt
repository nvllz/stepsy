package com.nvllz.stepsy.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.nvllz.stepsy.R

@Composable
fun ImportWarningDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    message: String = stringResource(R.string.import_warning_message),
) {
    StepsyDialog(
        title = stringResource(R.string.import_warning_title),
        onDismiss = onDismiss,
        confirmText = stringResource(android.R.string.ok),
        onConfirm = onConfirm,
    ) {
        Text(
            message,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
