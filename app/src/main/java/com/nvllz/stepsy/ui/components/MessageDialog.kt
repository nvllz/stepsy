package com.nvllz.stepsy.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
fun MessageDialog(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    StepsyDialog(title, onDismiss, stringResource(android.R.string.ok), onConfirm) { Text(message) }
}
