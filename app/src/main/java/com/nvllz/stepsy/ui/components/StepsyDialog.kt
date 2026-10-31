package com.nvllz.stepsy.ui.components

import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import com.nvllz.stepsy.ui.theme.StepsyTheme

@Composable
fun StepsyDialog(
    title: String? = null,
    onDismiss: () -> Unit,
    confirmText: String? = null,
    onConfirm: () -> Unit = {},
    dismissText: String? = stringResource(android.R.string.cancel),
    confirmEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        val window = generateSequence(LocalView.current) { it.parent as? View }
            .filterIsInstance<DialogWindowProvider>().firstOrNull()?.window
        LaunchedEffect(window) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window?.setDimAmount(0.8f)
        }
        val shape = RoundedCornerShape(28.dp)
        Column(
            Modifier.clip(shape).background(StepsyTheme.colors.dialogSurface)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.11f), shape)
                .padding(24.dp),
        ) {
            title?.let {
                Text(it, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 16.dp))
            }
            content()
            if (dismissText != null || confirmText != null) {
                Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (dismissText != null) SecondaryButton(dismissText, onDismiss, Modifier.weight(1f))
                    if (confirmText != null) PrimaryButton(confirmText, onConfirm, Modifier.weight(1f), enabled = confirmEnabled)
                }
            }
        }
    }
}
