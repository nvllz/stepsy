package com.nvllz.stepsy.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nvllz.stepsy.R
import com.nvllz.stepsy.ui.theme.Chivo
import com.nvllz.stepsy.ui.theme.StepsyTheme
import com.nvllz.stepsy.util.DayPartStats
import java.text.NumberFormat

private val displayOrder = listOf(1, 2, 3, 0)

@Composable
fun DayPartsPanel(
    stats: DayPartStats?,
    labels: List<String>,
    icons: List<Int>,
    headline: String?,
    detail: String?,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val partColors = listOf(
        colorResource(R.color.colorGoal),
        colorResource(R.color.colorSpecial),
        colorResource(R.color.colorFlame),
        colorResource(R.color.colorDistance),
    )
    val percentFmt = remember { NumberFormat.getPercentInstance() }
    val balanced = LocalTextStyle.current.copy(
        lineBreak = LineBreak(
            strictness = LineBreak.Strictness.Default,
            wordBreak = LineBreak.WordBreak.Default,
            strategy = LineBreak.Strategy.Balanced,
        ),
    )
    val enough = stats != null && stats.included >= 3 && stats.included * 2 >= stats.rowsInRange
    val helpApplies = stats != null && stats.included < stats.rowsInRange

    Panel(modifier.clip(RoundedCornerShape(24.dp)).clickable(enabled = helpApplies, role = Role.Button, onClick = onHelp)) {
        headline?.let {
            Text(
                it,
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                fontSize = 16.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, color = onSurface,
                style = balanced,
            )
        }
        detail?.let {
            Text(
                it,
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                fontSize = 13.sp, lineHeight = 18.sp,
                textAlign = TextAlign.Center, color = onSurface.copy(alpha = 0.7f),
                style = balanced,
            )
        }
        Spacer(Modifier.height(16.dp))
        when {
            stats == null -> Spacer(Modifier.fillMaxWidth().height(116.dp))
            !enough -> Box(
                Modifier.fillMaxWidth().height(116.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.reports_rhythm_not_enough),
                    fontSize = 13.sp, lineHeight = 18.sp,
                    textAlign = TextAlign.Center, color = StepsyTheme.colors.accent,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    style = balanced,
                )
            }
            else -> {
                val reveal = remember { Animatable(0f) }
                LaunchedEffect(Unit) { reveal.animateTo(1f, spring(0.6f, Spring.StiffnessLow)) }
                val leader = stats.leader
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(116.dp)) {
                            val stroke = 18.dp.toPx()
                            val inset = stroke / 2
                            val arcSize = Size(size.width - stroke, size.height - stroke)
                            val topLeft = Offset(inset, inset)
                            drawArc(
                                onSurface.copy(alpha = 0.08f), 0f, 360f, false,
                                topLeft = topLeft, size = arcSize,
                                style = Stroke(stroke, cap = StrokeCap.Butt),
                            )
                            var start = -90f
                            displayOrder.forEach { part ->
                                val full = stats.share(part) * 360f
                                val sweep = full * reveal.value
                                if (sweep > 0f) {
                                    drawArc(
                                        partColors[part], start, sweep, false,
                                        topLeft = topLeft, size = arcSize,
                                        style = Stroke(stroke, cap = StrokeCap.Butt),
                                    )
                                }
                                start += full
                            }
                        }
                        val centerIcon = leader?.let { icons.getOrNull(it) }
                        if (centerIcon != null) {
                            Icon(
                                painterResource(centerIcon), null,
                                tint = onSurface, modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        displayOrder.forEach { part ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val icon = icons.getOrNull(part)
                                if (icon != null) {
                                    Icon(
                                        painterResource(icon), null,
                                        tint = partColors[part],
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    labels.getOrNull(part).orEmpty(),
                                    fontSize = 14.sp, color = partColors[part],
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    percentFmt.format(stats.share(part).toDouble()),
                                    fontSize = 15.sp, fontFamily = Chivo,
                                    color = partColors[part],
                                    style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
