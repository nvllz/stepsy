package com.nvllz.stepsy.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, topPadding: Dp = 24.dp) {
    Eyebrow(
        text,
        modifier = modifier.fillMaxWidth().padding(start = 8.dp, top = topPadding, bottom = 10.dp),
    )
}
