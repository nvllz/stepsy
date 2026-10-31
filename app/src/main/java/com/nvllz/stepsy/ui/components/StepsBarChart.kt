package com.nvllz.stepsy.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nvllz.stepsy.ui.theme.Chivo
import com.nvllz.stepsy.ui.theme.StepsyTheme

@Composable
fun StepsBarChart(
    values: List<Int>, labels: List<String>, goal: Int, selectedIndex: Int?, boldIndex: Int? = null,
    onBarClick: (Int) -> Unit, modifier: Modifier = Modifier,
) {
    val n = values.size
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(900, easing = LinearEasing)) }
    val onClick by rememberUpdatedState(onBarClick)

    val animated = values.map { animateFloatAsState(it.toFloat(), spring(0.6f, Spring.StiffnessLow), label = "bar").value }
    val targetMax = maxOf(values.maxOrNull() ?: 0, 1).toFloat().coerceAtLeast(goal * 1.1f) * 1.05f
    val axis by animateFloatAsState(targetMax, spring(0.6f, Spring.StiffnessLow), label = "axis")

    val onSurface = MaterialTheme.colorScheme.onSurface
    val goalColor = StepsyTheme.colors.goal
    val colors = values.mapIndexed { i, v ->
        val met = goal > 0 && v >= goal
        val dim = if (selectedIndex != null && selectedIndex != i) 0.5f else 1f
        val base = if (met) goalColor else onSurface.copy(alpha = 0.35f)
        animateColorAsState(base.copy(alpha = base.alpha * dim), tween(350), label = "c").value
    }

    val measurer = rememberTextMeasurer()
    val axisGray = Color(0xFF888888)
    val small = TextStyle(color = axisGray, fontSize = 10.sp, fontFamily = Chivo)
    val label = small.copy(fontSize = 12.sp)
    val stagger = if (n > 1) minOf(0.07f, 0.5f / (n - 1)) else 0f
    fun growth(i: Int) = EaseOutBack.transform(((appear.value - i * stagger) / (1f - stagger * (n - 1))).coerceIn(0f, 1f))

    Canvas(
        modifier.pointerInput(n) {
            detectTapGestures { o -> onClick((o.x / (size.width / n)).toInt().coerceIn(0, n - 1)) }
        },
    ) {
        val base = size.height - 24.dp.toPx()
        val top = 24.dp.toPx()
        val slot = size.width / n
        val barW = slot * 0.62f
        if (goal > 0) {
            val y = base - goal / axis * (base - top)
            drawLine(axisGray.copy(alpha = 0.4f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 12f)))
        }
        animated.forEachIndexed { i, a ->
            val g = growth(i)
            val h = (a / axis * (base - top) * g).coerceIn(0f, base - top)
            val left = i * slot + (slot - barW) / 2
            val bold = boldIndex == i
            val metBrush = if (goal > 0 && values[i] >= goal) Brush.verticalGradient(
                listOf(lerp(goalColor, Color.White, 0.24f), goalColor, lerp(goalColor, Color.Black, 0.16f)),
                startY = base - h, endY = base,
            ) else null
            if (metBrush != null) {
                drawRoundRect(metBrush, Offset(left, base - h), Size(barW, h),
                    CornerRadius(minOf(barW / 2, 8.dp.toPx(), h / 2)), alpha = colors[i].alpha)
            } else {
                drawRoundRect(colors[i], Offset(left, base - h), Size(barW, h), CornerRadius(minOf(barW / 2, 8.dp.toPx(), h / 2)))
            }
            labels.getOrNull(i)?.let {
                val st = if (bold) label.copy(fontWeight = FontWeight.SemiBold, color = onSurface) else label
                val l = measurer.measure(it, st)
                drawText(l, topLeft = Offset(i * slot + (slot - l.size.width) / 2, base + 4.dp.toPx()))
            }
            if (values[i] > 0 && g > 0.98f) {
                val st = if (bold) small.copy(fontWeight = FontWeight.SemiBold, color = onSurface) else small
                val l = measurer.measure(values[i].toString(), st)
                drawText(l, topLeft = Offset(i * slot + (slot - l.size.width) / 2, base - h - l.size.height - 2.dp.toPx()))
            }
        }
    }
}
