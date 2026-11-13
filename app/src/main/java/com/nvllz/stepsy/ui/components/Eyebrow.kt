package com.nvllz.stepsy.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.nvllz.stepsy.ui.theme.StepsyTheme

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    Text(
        text.uppercase(), fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold,
        color = StepsyTheme.colors.accent,
        modifier = modifier, textAlign = textAlign,
    )
}
