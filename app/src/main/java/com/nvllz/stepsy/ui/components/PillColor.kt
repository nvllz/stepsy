package com.nvllz.stepsy.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

@Composable
fun pillColor(base: Color, progress: Float): Color {
    val t = progress.coerceIn(0f, 1f)
    val target = lerp(base, Color(0xFF8E8E8E), 0.40f * (1f - t))
    return animateColorAsState(target, tween(300, easing = FastOutSlowInEasing), label = "pillColor").value
}
