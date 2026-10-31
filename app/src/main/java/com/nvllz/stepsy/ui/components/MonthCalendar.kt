package com.nvllz.stepsy.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nvllz.stepsy.R
import com.nvllz.stepsy.ui.monthTitle
import com.nvllz.stepsy.ui.theme.StepsyTheme
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as DayStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private fun lapPhase(lapIdx: Int) = if (lapIdx <= 1) 0 else 1

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MonthCalendar(
    selected: LocalDate, minDate: LocalDate, maxDate: LocalDate, firstDay: DayOfWeek,
    heat: Map<LocalDate, Float>, onSelect: (LocalDate) -> Unit, jumpKey: Int = 0,
    laps: Map<LocalDate, Float> = emptyMap(),
    streak: Set<LocalDate> = emptySet(),
    onMonthClick: (year: Int, month: Int) -> Unit = { _, _ -> },
) {
    val first = YearMonth.from(minDate)
    val count = ChronoUnit.MONTHS.between(first, YearMonth.from(maxDate)).toInt() + 1
    fun pageOf(d: LocalDate) = ChronoUnit.MONTHS.between(first, YearMonth.from(d)).toInt().coerceIn(0, count - 1)
    val pager = rememberPagerState(pageOf(selected)) { count }
    val scope = rememberCoroutineScope()
    LaunchedEffect(selected, jumpKey) {
        val p = pageOf(selected)
        if (pager.currentPage != p) pager.animateScrollToPage(p)
    }
    val goal = StepsyTheme.colors.goal
    val onSurface = MaterialTheme.colorScheme.onSurface
    val dark = (LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val shown = first.plusMonths(pager.currentPage.toLong())
    val title = monthTitle(shown.year, shown.monthValue)
    val washBrushes = remember(dark, goal) {
        val crown = if (dark) listOf(0.40f, 0.55f) else listOf(0.30f, 0.44f)
        val sink = if (dark) listOf(0.22f, 0.32f) else listOf(0.55f, 0.72f)
        List(2) { i ->
            Brush.verticalGradient(
                listOf(
                    lerp(goal, Color.White, crown[i]),
                    goal,
                    lerp(goal, Color.Black, sink[i]),
                )
            )
        }
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }, enabled = pager.currentPage > 0) {
                Icon(
                    painterResource(R.drawable.ic_chevron_right), null,
                    Modifier.size(24.dp).rotate(180f),
                )
            }
            Text(
                title,
                Modifier.weight(1f).clip(CircleShape)
                    .clickable { onMonthClick(shown.year, shown.monthValue) }
                    .padding(vertical = 6.dp),
                fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
            Box(
                Modifier.size(48.dp).clip(CircleShape).combinedClickable(
                    enabled = pager.currentPage < count - 1,
                    onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    onLongClick = { scope.launch { pager.animateScrollToPage(count - 1) } },
                ),
                contentAlignment = Alignment.Center,
            ) {
                val canGoNext = pager.currentPage < count - 1
                Icon(painterResource(R.drawable.ic_chevron_right), null,
                    modifier = Modifier
                        .size(24.dp)
                        .then(
                            if (!canGoNext) Modifier.graphicsLayer(alpha = 0.2f)
                            else Modifier
                        ),
                )
            }
        }
        Row(Modifier.padding(vertical = 8.dp)) {
            (0L until 7L).forEach {
                Text(firstDay.plus(it).getDisplayName(DayStyle.NARROW_STANDALONE, Locale.getDefault()), Modifier.weight(1f),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
        }
        HorizontalPager(pager, beyondViewportPageCount = 1) { page ->
            val month = first.plusMonths(page.toLong())
            val leading = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
            Column {
                repeat(6) { week ->
                    Row {
                        repeat(7) { wd ->
                            val day = week * 7 + wd - leading + 1
                            Box(Modifier.weight(1f).height(44.dp), contentAlignment = Alignment.Center) {
                                if (day in 1..month.lengthOfMonth()) {
                                    val date = month.atDay(day)
                                    val h = heat[date] ?: 0f
                                    val met = h >= 1f
                                    val enabled = date in minDate..maxDate
                                    val lapIdx = (laps[date] ?: 0f).toInt() - 1
                                    val fill = when {
                                        met -> goal
                                        h > 0f -> goal.copy(alpha = 0.06f + 0.32f * h)
                                        else -> Color.Transparent
                                    }
                                    Box(
                                        Modifier.size(37.dp).clip(RoundedCornerShape(11.dp)).background(
                                            if (met && lapIdx >= 1) {
                                                washBrushes[lapPhase(lapIdx)]
                                            } else {
                                                SolidColor(fill)
                                            },
                                        )
                                            .then(if (date == selected) Modifier.border(1.5.dp, onSurface, RoundedCornerShape(11.dp)) else Modifier)
                                            .clickable(enabled = enabled) { onSelect(date) },
                                        contentAlignment = Alignment.Center,
                                    )
                                    {
                                        Text(
                                            day.toString(), fontSize = 14.sp,
                                            fontWeight = if (date == maxDate || date == selected || met) FontWeight.Bold else FontWeight.Normal,
                                            style = TextStyle(
                                                shadow = Shadow(
                                                    (if (dark) StepsyTheme.colors.background else onSurface)
                                                        .copy(alpha = 0.10f * (if (enabled) 1f else 0.15f)),
                                                    Offset(0f, with(LocalDensity.current) { 1.dp.toPx() }),
                                                    with(LocalDensity.current) { 1.dp.toPx() },
                                                )
                                            ),
                                            color = (if (met) StepsyTheme.colors.background else onSurface)
                                                .copy(alpha = if (enabled) 1f else 0.3f),
                                        )
                                    }
                                    if (date in streak) {
                                        Box(
                                            Modifier.align(Alignment.BottomCenter).offset(y = 0.65.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Box(
                                                Modifier.width(10.dp).height(3.dp)
                                                    .background(StepsyTheme.colors.special.copy(alpha = 0.22f), RoundedCornerShape(50)),
                                            )
                                            Box(
                                                Modifier.width(6.dp).height(1.5.dp)
                                                    .background(StepsyTheme.colors.special, RoundedCornerShape(50)),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
