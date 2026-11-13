package com.nvllz.stepsy.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nvllz.stepsy.ui.theme.StepsyTheme

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick, modifier.heightIn(min = 48.dp), shape = CircleShape, elevation = null,
        colors = ButtonDefaults.buttonColors(
            containerColor = StepsyTheme.colors.muted, contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}
