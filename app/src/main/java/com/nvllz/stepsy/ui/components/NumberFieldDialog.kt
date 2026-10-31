package com.nvllz.stepsy.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nvllz.stepsy.R
import com.nvllz.stepsy.ui.theme.StepsyTheme

@Composable
fun NumberFieldDialog(
    title: String,
    initial: String,
    suffix: String? = null,
    isValid: (String) -> Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    allowDecimal: Boolean = false,
    supporting: (@Composable () -> Unit)? = null,
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    StepsyDialog(
        title = title,
        onDismiss = onDismiss,
        confirmText = stringResource(android.R.string.ok),
        onConfirm = { onConfirm(value.text.trim()) },
        confirmEnabled = isValid(value.text.trim()),
        dismissText = stringResource(android.R.string.cancel),
    ) {
        Column {
            TextField(
                value = value,
                onValueChange = { next ->
                    val filtered = next.text.filter { char ->
                        char.isDigit() || (allowDecimal && (char == '.' || char == ','))
                    }
                    value = next.copy(text = filtered, selection = TextRange(filtered.length))
                },
                suffix = suffix?.let { { Text(it, color = StepsyTheme.colors.accent) } },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = fieldColorsWithAccentSupporting(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            supporting?.invoke()
        }
    }
}

@Composable
private fun fieldColorsWithAccentSupporting() = TextFieldDefaults.colors(
    focusedContainerColor = StepsyTheme.colors.accentOpaque,
    unfocusedContainerColor = StepsyTheme.colors.accentOpaque,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    cursorColor = MaterialTheme.colorScheme.onSurface,
    focusedSupportingTextColor = StepsyTheme.colors.accent,
    unfocusedSupportingTextColor = StepsyTheme.colors.accent,
)
