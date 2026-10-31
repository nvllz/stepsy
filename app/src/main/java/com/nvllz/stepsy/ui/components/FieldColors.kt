package com.nvllz.stepsy.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.nvllz.stepsy.ui.theme.StepsyTheme

@Composable
fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = StepsyTheme.colors.accentOpaque,
    unfocusedContainerColor = StepsyTheme.colors.accentOpaque,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    cursorColor = MaterialTheme.colorScheme.onSurface,
)
