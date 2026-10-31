package com.nvllz.stepsy.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StatPill(icon: Int, value: Int, format: (Int) -> String, color: Color, modifier: Modifier = Modifier, glow: Boolean = false) {
    val glowAlpha by animateFloatAsState(if (glow) 1f else 0f, tween(500, easing = FastOutSlowInEasing), label = "pillGlow")
    val pulse by rememberInfiniteTransition(label = "pillPulse").animateFloat(
        0.8f, 1f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pillPulse",
    )
    Row(
        modifier
            .clip(CircleShape)
            .drawBehind {
                if (glowAlpha > 0.01f) {
                    drawRoundRect(
                        Brush.radialGradient(
                            listOf(color.copy(alpha = 0.26f * glowAlpha * pulse), Color.Transparent),
                            radius = maxOf(size.width, size.height),
                        ),
                        cornerRadius = CornerRadius(size.height / 2),
                    )
                }
            }
            .background(color.copy(alpha = 0.12f))
            .padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, tint = color, modifier = Modifier.padding(end = 8.dp).size(20.dp))
        CountText(value, format, TextStyle(fontSize = 16.sp, color = color), duration = pillCountDuration)
    }
}

private const val pillCountDuration = 140
