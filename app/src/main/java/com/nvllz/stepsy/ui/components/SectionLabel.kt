package com.nvllz.stepsy.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nvllz.stepsy.ui.theme.StepsyTheme

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, topPadding: Dp = 24.dp) {
    Text(
        text.uppercase(), fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold,
        color = StepsyTheme.colors.accent,
        modifier = modifier.fillMaxWidth().padding(start = 8.dp, top = topPadding, bottom = 10.dp),
    )
}
