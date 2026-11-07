package com.nvllz.stepsy.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as GraphicsCanvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import com.nvllz.stepsy.R
import com.nvllz.stepsy.ui.HourlyAdjustment
import com.nvllz.stepsy.ui.HourlyPoint
import com.nvllz.stepsy.ui.theme.Chivo
import com.nvllz.stepsy.ui.theme.StepsyTheme
import com.nvllz.stepsy.util.Util
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun DayLineChart(
    points: List<HourlyPoint>,
    adjustments: List<HourlyAdjustment>,
    startMinute: Int,
    endMinute: Int,
    total: Int,
    goal: Int,
    partial: Boolean,
    untimed: Int,
    todayPoints: List<HourlyPoint> = emptyList(),
    dayShiftMinutes: Int = 0,
    resetKey: Any? = Unit,
    revealInstant: Boolean = false,
    onReveal: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val is24 = android.text.format.DateFormat.is24HourFormat(ctx)
    val ticks = remember(is24, dayShiftMinutes) {
        listOf(0, 360, 720, 1080, 1440).map { it to Util.timeLabel(ctx, it + dayShiftMinutes) }
    }
    val xs = remember(points) { FloatArray(points.size) { points[it].minute.toFloat() } }
    val ys = remember(points) { FloatArray(points.size) { points[it].value.toFloat() } }
    val peak = remember(points, adjustments, endMinute) {
        var highest = 0f
        var cumulative = 0
        var index = 0
        for (i in xs.indices) {
            while (index < adjustments.size && adjustments[index].minute <= xs[i]) {
                cumulative += adjustments[index].delta
                index++
            }
            highest = maxOf(highest, ys[i] + cumulative)
        }
        highest
    }
    val reveal = remember { Animatable(if (revealInstant) 1f else 0f) }
    val linePath = remember { Path() }
    LaunchedEffect(Unit) {
        if (reveal.value < 1f) reveal.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        onReveal()
    }

    val onSurface = MaterialTheme.colorScheme.onSurface
    val axisGray = Color(0xFF888888)
    val pillBase = StepsyTheme.colors.background
    val pillOverlay = StepsyTheme.colors.accentOpaque
    val targetMax = maxOf(1f, goal.toFloat(), total.toFloat(), peak) * 1.05f
    val shot = remember {
        val to = lineFracs(xs, ys, adjustments, targetMax)
        val rx = ringXs(adjustments, endMinute)
        val rt = ringFracs(xs, ys, adjustments, rx, targetMax)
        mutableStateOf(
            LineShot(xs, to, to, rx, rt, rt, 1f / targetMax, 1f / targetMax, goal,
                hintValuesFor(targetMax, goal), false, false),
        )
    }
    val morph = remember { Animatable(1f) }
    val lastKey = remember { mutableStateOf(resetKey) }
    LaunchedEffect(points.hashCode(), adjustments.hashCode(), goal, resetKey) {
        val keyChanged = lastKey.value != resetKey
        lastKey.value = resetKey
        val duration = if (keyChanged) 450 else 300
        val cur = shot.value
        val m = morph.value
        val to = lineFracs(xs, ys, adjustments, targetMax)
        val ringX = ringXs(adjustments, endMinute)
        val ringTo = ringFracs(xs, ys, adjustments, ringX, targetMax)
        val toInv = 1f / targetMax
        if (cur.xs.contentEquals(xs) && cur.to.contentEquals(to) && cur.ringX.contentEquals(ringX) &&
            cur.ringTo.contentEquals(ringTo) && cur.toInv == toInv && cur.goal == goal
        ) {
            if (m < 1f) morph.animateTo(1f, tween(duration, easing = FastOutSlowInEasing))
            return@LaunchedEffect
        }
        val curFrac = FloatArray(cur.xs.size) { cur.from[it] + (cur.to[it] - cur.from[it]) * m }
        val from = when {
            cur.xs.size < 2 -> to
            cur.xs.contentEquals(xs) -> curFrac
            else -> FloatArray(xs.size) { linearAt(cur.xs, curFrac, xs[it]) }
        }
        val sameRings = cur.ringX.contentEquals(ringX)
        val ringFrom = FloatArray(ringX.size) { k ->
            if (sameRings) cur.ringFrom[k] + (cur.ringTo[k] - cur.ringFrom[k]) * m
            else linearAt(xs, from, ringX[k])
        }
        val fromInv = cur.fromInv + (cur.toInv - cur.fromInv) * m
        val hints = hintValuesFor(targetMax, goal)
        shot.value = LineShot(
            xs, from, to, ringX, ringFrom, ringTo, fromInv, toInv, goal, hints,
            !hints.contentEquals(cur.hints), (cur.goal > 0) != (goal > 0),
        )
        morph.snapTo(0f)
        morph.animateTo(1f, tween(duration, easing = FastOutSlowInEasing))
    }
    var selectedHour by remember(resetKey) { mutableStateOf<Int?>(null) }
    val todayLabel = stringResource(R.string.hourly_today_short)
    val adjustedLabel = stringResource(R.string.hourly_hour_adjusted)
    val (adjustSums, adjustMarks) = remember(adjustments) { adjustHourSums(adjustments) }
    val tickStyle = TextStyle(color = axisGray, fontSize = 10.sp, fontFamily = Chivo)
    val hourStyle = TextStyle(color = onSurface, fontSize = 12.sp, fontFamily = Chivo)
    val adjustedStyle = hourStyle.copy(color = onSurface.copy(alpha = 0.65f))
    val pillOutline = onSurface.copy(alpha = 0.16f)
    val measurer = rememberTextMeasurer()

    Column(modifier) {
        Canvas(
            Modifier.fillMaxWidth().weight(1f)
                .pointerInput(resetKey) {
                    detectTapGestures { offset ->
                        val hour = ((offset.x / size.width * 1440f) / 60f).toInt().coerceIn(0, 23)
                        selectedHour = if (selectedHour == hour) null else hour
                    }
                }
                .pointerInput(resetKey) {
                    fun hourAt(offset: Offset) =
                        ((offset.x / size.width * 1440f) / 60f).toInt().coerceIn(0, 23)
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val slop = viewConfiguration.touchSlop
                        var totalX = 0f
                        var totalY = 0f
                        var claimed = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) break
                            val delta = change.positionChange()
                            totalX += delta.x
                            totalY += delta.y
                            when {
                                claimed -> {
                                    selectedHour = hourAt(change.position)
                                    change.consume()
                                }
                                abs(totalX) >= slop && abs(totalX) > abs(totalY) -> {
                                    claimed = true
                                    selectedHour = hourAt(down.position)
                                    change.consume()
                                }
                                abs(totalY) >= slop && abs(totalY) >= abs(totalX) -> break
                            }
                            if (!change.pressed) break
                        }
                    }
                }
        ) {
            val base = size.height - 24.dp.toPx()
            val top = 24.dp.toPx()
            val span = base - top
            val s = shot.value
            val m = morph.value
            val inv = s.fromInv + (s.toInv - s.fromInv) * m
            val ramp = ((m - 0.5f) * 2f).coerceIn(0f, 1f)
            val xFor = { minute: Float -> minute / 1440f * size.width }

            fun drawPill(layout: TextLayoutResult, x: Float, y: Float, alpha: Float = 1f) {
                val topLeft = Offset(x - 3.dp.toPx(), y - 2.dp.toPx())
                val pillSize = Size(layout.size.width + 6.dp.toPx(), layout.size.height + 4.dp.toPx())
                val radius = CornerRadius((layout.size.height + 4.dp.toPx()) / 2f)
                drawRoundRect(pillBase, topLeft = topLeft, size = pillSize, cornerRadius = radius, alpha = alpha)
                drawRoundRect(pillOverlay, topLeft = topLeft, size = pillSize, cornerRadius = radius, alpha = alpha)
                drawText(layout, topLeft = Offset(x, y), alpha = alpha)
            }

            if (s.goal > 0) {
                val k = if (s.goalFade) s.toInv else inv
                val a = if (s.goalFade) ramp else 1f
                if (a > 0) {
                    val y = base - (s.goal * k).coerceIn(0f, 1f) * span
                    drawLine(
                        axisGray.copy(alpha = 0.4f * a), Offset(0f, y), Offset(size.width, y), 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 12f)),
                    )
                }
            }

            drawLine(
                axisGray.copy(alpha = 0.3f),
                Offset(0f, base),
                Offset(size.width, base),
                0.75.dp.toPx(),
            )

            val hintStyle = tickStyle.copy(color = tickStyle.color.copy(alpha = 0.55f))
            val hintAlpha = if (s.hintsFade) ramp else 1f
            var hintMaxRight = 0f
            if (s.hints.isNotEmpty()) {
                val pillLeft = 4.dp.toPx()
                val pillTop = top + 2.dp.toPx()
                val hintInv = if (s.hintsFade) s.toInv else inv
                s.hints.forEach { value ->
                    val layout = measurer.measure(axisLabel(value), hintStyle)
                    val y = (base - (value * hintInv).coerceIn(0f, 1f) * span - layout.size.height / 2f)
                        .coerceIn(pillTop, base - layout.size.height)
                    if (pillLeft + layout.size.width > hintMaxRight) hintMaxRight = pillLeft + layout.size.width
                    if (hintAlpha > 0.01f) drawPill(layout, pillLeft, y, hintAlpha)
                }
            }

            val selHour = selectedHour
            if (selHour != null) {
                val bandFrom = xFor((selHour * 60).toFloat())
                val bandTo = xFor((selHour * 60 + 60).toFloat())
                drawRect(
                    onSurface.copy(alpha = 0.07f),
                    topLeft = Offset(bandFrom, top),
                    size = Size(bandTo - bandFrom, base - top),
                )
                drawLine(
                    axisGray.copy(alpha = 0.35f), Offset(bandFrom, top), Offset(bandFrom, base), 1.dp.toPx(),
                )
                drawLine(
                    axisGray.copy(alpha = 0.35f), Offset(bandTo, top), Offset(bandTo, base), 1.dp.toPx(),
                )
            }

            if (partial && startMinute > 0) {
                drawRect(
                    onSurface.copy(alpha = 0.05f),
                    topLeft = Offset(0f, top),
                    size = Size(xFor(startMinute.toFloat()), base - top),
                )
            }

            clipRect(right = size.width * reveal.value) {
                if (s.xs.size >= 2) {
                    linePath.rewind()
                    for (i in s.xs.indices) {
                        val x = xFor(s.xs[i])
                        val y = base - (s.from[i] + (s.to[i] - s.from[i]) * m) * span
                        if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
                    }
                    drawPath(
                        linePath, onSurface,
                        style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }

                for (k in s.ringX.indices) {
                    val f = s.ringFrom[k] + (s.ringTo[k] - s.ringFrom[k]) * m
                    drawCircle(
                        onSurface.copy(alpha = 0.75f), 3.dp.toPx(),
                        Offset(xFor(s.ringX[k]), base - f * span),
                        style = Stroke(1.5.dp.toPx()),
                    )
                }
            }

            if (selHour != null) {
                val steps = hourSteps(points, selHour)
                val todaySteps = if (todayPoints.isEmpty()) null else hourSteps(todayPoints, selHour)
                var text = Util.timeLabel(ctx, selHour * 60 + dayShiftMinutes) + "–" +
                        Util.timeLabel(ctx, (selHour + 1) * 60 + dayShiftMinutes) +
                        (steps?.let { " · " + Util.formatSteps(it) } ?: "")
                if (todaySteps != null) text += " · " + String.format(todayLabel, Util.formatSteps(todaySteps))
                val first = measurer.measure(text, hourStyle)
                val second = if (adjustMarks[selHour]) measurer.measure(
                    String.format(adjustedLabel, signedSteps(adjustSums[selHour])),
                    adjustedStyle,
                ) else null
                val pillSize = hourPillSize(first, second)
                val bandFrom = xFor((selHour * 60).toFloat())
                val bandTo = xFor((selHour * 60 + 60).toFloat())
                val x = if (bandFrom + 6.dp.toPx() + pillSize.width <= size.width - 6.dp.toPx())
                    bandFrom + 6.dp.toPx()
                else bandTo - 6.dp.toPx() - pillSize.width
                val y = top + 6.dp.toPx()
                val finalX = if (x < hintMaxRight + 6.dp.toPx()) hintMaxRight + 6.dp.toPx() else x
                if (finalX >= 3.dp.toPx() && finalX + pillSize.width <= size.width - 3.dp.toPx()) {
                    drawHourPill(pillBase, pillOverlay, pillOutline, first, second, finalX, y)
                }
            }

            ticks.forEach { (minute, text) ->
                val layout = measurer.measure(text, tickStyle)
                val x = (xFor(minute.toFloat()) - layout.size.width / 2f)
                    .coerceIn(0f, size.width - layout.size.width)
                drawText(layout, topLeft = Offset(x, base + 4.dp.toPx()))
            }
        }

        val partialHeight by animateDpAsState(
            if (partial) 38.dp else 0.dp,
            tween(250, easing = FastOutSlowInEasing),
            label = "partialTexts",
        )
        Column(
            Modifier.fillMaxWidth().height(partialHeight).clipToBounds(),
        ) {
            if (partial) {
                Text(
                    stringResource(R.string.hourly_no_data_before, Util.timeLabel(ctx, startMinute + dayShiftMinutes)),
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    color = StepsyTheme.colors.accent,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    stringResource(R.string.hourly_untimed_steps, Util.formatSteps(untimed)),
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    color = StepsyTheme.colors.accent,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
    }
}

@Composable
fun DayBarChart(
    points: List<HourlyPoint>,
    adjustments: List<HourlyAdjustment>,
    dayShiftMinutes: Int = 0,
    resetKey: Any? = Unit,
    revealInstant: Boolean = false,
    onReveal: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val (adjustSums, adjustMarks) = remember(adjustments) { adjustHourSums(adjustments) }
    val adjustedLabel = stringResource(R.string.hourly_hour_adjusted)
    val errorIcon = run {
        val paint = StepsyTheme.colors.accent
        val density = LocalDensity.current
        remember(paint, density.density) {
            val px = with(density) { 15.dp.toPx().roundToInt().coerceAtLeast(1) }
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            AppCompatResources.getDrawable(ctx, R.drawable.ic_adjustment)?.apply {
                setTint(paint.toArgb())
                setBounds(0, 0, px, px)
                draw(GraphicsCanvas(bmp))
            }
            bmp.asImageBitmap()
        }
    }
    val is24 = android.text.format.DateFormat.is24HourFormat(ctx)
    val ticks = remember(is24, dayShiftMinutes) {
        listOf(0, 360, 720, 1080, 1440).map { it to Util.timeLabel(ctx, it + dayShiftMinutes) }
    }
    val values = remember(points) { IntArray(24) { hourSteps(points, it) ?: 0 } }
    val chipAlphas = remember { Array(24) { Animatable(if (adjustMarks[it]) 1f else 0f) } }
    val chipMounted = remember { mutableStateOf(false) }
    LaunchedEffect(adjustments) {
        if (!chipMounted.value) {
            chipMounted.value = true
            for (i in 0 until 24) {
                val target = if (adjustMarks[i]) 1f else 0f
                if (chipAlphas[i].value != target) {
                    chipAlphas[i].snapTo(target)
                }
            }
        } else {
            for (i in 0 until 24) {
                val target = if (adjustMarks[i]) 1f else 0f
                val alpha = chipAlphas[i]
                if (alpha.targetValue != target) {
                    launch { alpha.animateTo(target, tween(150, easing = FastOutSlowInEasing)) }
                }
            }
        }
    }
    val targetMax = maxOf(values.maxOrNull() ?: 0, 1).toFloat() * 1.05f
    val animated = values.map {
        animateFloatAsState(it.toFloat() / targetMax, tween(400, easing = FastOutSlowInEasing), label = "hourBar")
    }
    val grow = remember { Animatable(if (revealInstant) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (grow.value < 1f) grow.animateTo(1f, tween(350, easing = FastOutSlowInEasing))
        onReveal()
    }
    var selectedHour by remember(resetKey) { mutableStateOf<Int?>(null) }

    val onSurface = MaterialTheme.colorScheme.onSurface
    val axisGray = Color(0xFF888888)
    val pillBase = StepsyTheme.colors.background
    val pillOverlay = StepsyTheme.colors.accentOpaque
    val barColor = onSurface.copy(alpha = 0.35f)
    val tickStyle = TextStyle(color = axisGray, fontSize = 10.sp, fontFamily = Chivo)
    val hourStyle = TextStyle(color = onSurface, fontSize = 12.sp, fontFamily = Chivo)
    val adjustedStyle = hourStyle.copy(color = onSurface.copy(alpha = 0.65f))
    val pillOutline = onSurface.copy(alpha = 0.16f)
    val hintStyle = tickStyle.copy(color = tickStyle.color.copy(alpha = 0.55f))
    val measurer = rememberTextMeasurer()

    Canvas(
        modifier
            .pointerInput(resetKey) {
                detectTapGestures { offset ->
                    val hour = (offset.x / size.width * 24f).toInt().coerceIn(0, 23)
                    selectedHour = if (selectedHour == hour) null else hour
                }
            }
            .pointerInput(resetKey) {
                fun selectAt(offset: Offset) {
                    selectedHour = (offset.x / size.width * 24f).toInt().coerceIn(0, 23)
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val slop = viewConfiguration.touchSlop
                    var totalX = 0f
                    var totalY = 0f
                    var claimed = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) break
                        val delta = change.positionChange()
                        totalX += delta.x
                        totalY += delta.y
                        when {
                            claimed -> {
                                selectAt(change.position)
                                change.consume()
                            }
                            abs(totalX) >= slop && abs(totalX) > abs(totalY) -> {
                                claimed = true
                                selectAt(down.position)
                                change.consume()
                            }
                            abs(totalY) >= slop && abs(totalY) >= abs(totalX) -> break
                        }
                        if (!change.pressed) break
                    }
                }
            }
    ) {
        val base = size.height - 24.dp.toPx()
        val top = 24.dp.toPx()
        val slot = size.width / 24f
        val barWidth = (slot * 0.55f).coerceAtLeast(1.dp.toPx())
        val radius = CornerRadius(2.dp.toPx())

        fun drawPill(layout: TextLayoutResult, x: Float, y: Float) {
            val topLeft = Offset(x - 3.dp.toPx(), y - 2.dp.toPx())
            val pillSize = Size(layout.size.width + 6.dp.toPx(), layout.size.height + 4.dp.toPx())
            val corner = CornerRadius((layout.size.height + 4.dp.toPx()) / 2f)
            drawRoundRect(pillBase, topLeft = topLeft, size = pillSize, cornerRadius = corner)
            drawRoundRect(pillOverlay, topLeft = topLeft, size = pillSize, cornerRadius = corner)
            drawText(layout, topLeft = Offset(x, y))
        }

        drawLine(
            axisGray.copy(alpha = 0.3f),
            Offset(0f, base),
            Offset(size.width, base),
            0.75.dp.toPx(),
        )

        val maxValue = values.maxOrNull() ?: 0
        var hintMaxRight = 0f
        if (maxValue > 0) {
            val layout = measurer.measure(axisLabel(maxValue), hintStyle)
            drawPill(layout, 4.dp.toPx(), top + 2.dp.toPx())
            hintMaxRight = 4.dp.toPx() + layout.size.width
        }

        val selHour = selectedHour
        if (selHour != null) {
            drawRect(
                onSurface.copy(alpha = 0.07f),
                topLeft = Offset(slot * selHour, top),
                size = Size(slot, base - top),
            )
        }

        val growValue = grow.value
        for (i in 0 until 24) {
            val height = (animated[i].value * (base - top) * growValue).coerceIn(0f, base - top)
            if (height > 1f) {
                val color = if (selHour == i) onSurface else barColor
                val left = slot * i + (slot - barWidth) / 2f
                drawRoundRect(
                    color,
                    topLeft = Offset(left, base - height),
                    size = Size(barWidth, height),
                    cornerRadius = radius,
                )
            }
        }

        val iconPx = 15.dp.toPx()
        val chipPad = 1.dp.toPx()
        val chipSize = iconPx + chipPad * 2
        val chipGap = 4.dp.toPx()
        for (i in 0 until 24) {
            val chipAlpha = chipAlphas[i].value
            if (chipAlpha <= 0.01f) continue
            val barH = (animated[i].value * (base - top) * growValue).coerceIn(0f, base - top)
            val cx = slot * i + slot / 2f
            val cy = (base - barH - chipGap - chipSize / 2f).coerceAtLeast(top + chipSize / 2f)
            drawCircle(pillBase, chipSize / 2f, Offset(cx, cy), alpha = chipAlpha)
            drawCircle(pillOverlay, chipSize / 2f, Offset(cx, cy), alpha = chipAlpha)
            drawImage(
                errorIcon,
                dstOffset = IntOffset(
                    (cx - iconPx / 2f).roundToInt(),
                    (cy - iconPx / 2f).roundToInt(),
                ),
                alpha = chipAlpha,
            )
        }

        if (selHour != null) {
            val steps = hourSteps(points, selHour)
            var text = Util.timeLabel(ctx, selHour * 60 + dayShiftMinutes) + "–" +
                    Util.timeLabel(ctx, (selHour + 1) * 60 + dayShiftMinutes) +
                    (steps?.let { " · " + Util.formatSteps(it) } ?: "")
            val first = measurer.measure(text, hourStyle)
            val second = if (adjustMarks[selHour]) measurer.measure(
                String.format(adjustedLabel, signedSteps(adjustSums[selHour])),
                adjustedStyle,
            ) else null
            val pillSize = hourPillSize(first, second)
            val x = if (slot * selHour + 6.dp.toPx() + pillSize.width <= size.width - 6.dp.toPx())
                slot * selHour + 6.dp.toPx()
            else slot * (selHour + 1) - 6.dp.toPx() - pillSize.width
            val y = top + 6.dp.toPx()
            val finalX = if (x < hintMaxRight + 6.dp.toPx()) hintMaxRight + 6.dp.toPx() else x
            if (finalX >= 3.dp.toPx() && finalX + pillSize.width <= size.width - 3.dp.toPx()) {
                drawHourPill(pillBase, pillOverlay, pillOutline, first, second, finalX, y)
            }
        }

        ticks.forEach { (minute, text) ->
            val layout = measurer.measure(text, tickStyle)
            val x = (minute / 1440f * size.width - layout.size.width / 2f)
                .coerceIn(0f, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(x, base + 4.dp.toPx()))
        }
    }
}

private fun hourSteps(points: List<HourlyPoint>, hour: Int): Int? {
    if (points.isEmpty()) return null
    val hourStart = hour * 60
    val baseline = points.lastOrNull { it.minute <= hourStart } ?: return null
    val endPoint = points.firstOrNull { it.minute >= hourStart + 60 }
    if (endPoint != null) return endPoint.value - baseline.value
    val last = points.last()
    return if (last.minute > hourStart) last.value - baseline.value else null
}

private fun adjustHourSums(adjustments: List<HourlyAdjustment>): Pair<IntArray, BooleanArray> {
    val sums = IntArray(24)
    val present = BooleanArray(24)
    for (adjustment in adjustments) {
        val hour = (adjustment.minute / 60).coerceIn(0, 23)
        sums[hour] += adjustment.delta
        present[hour] = true
    }
    return sums to present
}

private fun signedSteps(delta: Int): String = when {
    delta > 0 -> "+" + Util.formatSteps(delta)
    delta < 0 -> "-" + Util.formatSteps(-delta)
    else -> "0"
}

private class LineShot(
    val xs: FloatArray,
    val from: FloatArray,
    val to: FloatArray,
    val ringX: FloatArray,
    val ringFrom: FloatArray,
    val ringTo: FloatArray,
    val fromInv: Float,
    val toInv: Float,
    val goal: Int,
    val hints: IntArray,
    val hintsFade: Boolean,
    val goalFade: Boolean,
)

private fun lineFracs(
    xs: FloatArray,
    ys: FloatArray,
    adjustments: List<HourlyAdjustment>,
    axisMax: Float,
): FloatArray {
    val out = FloatArray(xs.size)
    var index = 0
    var offset = 0
    for (i in xs.indices) {
        val minute = xs[i]
        while (index < adjustments.size && adjustments[index].minute <= minute) {
            offset += adjustments[index].delta
            index++
        }
        out[i] = ((ys[i] + offset) / axisMax).coerceIn(0f, 1f)
    }
    return out
}

private fun ringXs(adjustments: List<HourlyAdjustment>, endMinute: Int): FloatArray {
    val out = mutableListOf<Float>()
    for (adjustment in adjustments) {
        if (adjustment.minute > endMinute) break
        out.add(adjustment.minute.toFloat().coerceAtMost(endMinute.toFloat()))
    }
    return out.toFloatArray()
}

private fun ringFracs(
    xs: FloatArray,
    ys: FloatArray,
    adjustments: List<HourlyAdjustment>,
    ringX: FloatArray,
    axisMax: Float,
): FloatArray {
    val out = FloatArray(ringX.size)
    var cumulative = 0
    for (k in ringX.indices) {
        cumulative += adjustments[k].delta
        out[k] = ((linearAt(xs, ys, ringX[k]) + cumulative) / axisMax).coerceIn(0f, 1f)
    }
    return out
}

private fun hintValuesFor(targetMax: Float, goal: Int): IntArray {
    val hintStep = Y_STEP_CANDIDATES.firstOrNull { it >= targetMax / 4f }
        ?: (((targetMax / 4f + 9999f) / 10000f).toInt() * 10000)
    val hintTicks = mutableListOf<Int>()
    var value = hintStep
    while (value <= targetMax && hintTicks.size < 4) {
        hintTicks.add(value)
        value += hintStep
    }
    return if (goal > 0 && goal <= targetMax &&
        hintTicks.all { abs(it - goal) >= hintStep * 0.45f }
    ) (hintTicks + goal).sorted().toIntArray() else hintTicks.toIntArray()
}

private fun DrawScope.hourPillSize(first: TextLayoutResult, second: TextLayoutResult?): Size {
    val wide = second != null
    val padH = if (wide) 8.dp.toPx() else 6.dp.toPx()
    val padV = if (wide) 3.dp.toPx() else 2.dp.toPx()
    val width = maxOf(first.size.width, second?.size?.width ?: 0) + padH * 2
    val height = first.size.height + (second?.size?.height ?: 0) +
            (if (wide) 2.dp.toPx() else 0f) + padV * 2
    return Size(width, height)
}

private fun DrawScope.drawHourPill(
    base: Color,
    overlay: Color,
    outline: Color,
    first: TextLayoutResult,
    second: TextLayoutResult?,
    x: Float,
    y: Float,
) {
    val size = hourPillSize(first, second)
    val radius = if (second == null) CornerRadius(size.height / 2f) else CornerRadius(10.dp.toPx())
    drawRoundRect(base, topLeft = Offset(x, y), size = size, cornerRadius = radius)
    drawRoundRect(overlay, topLeft = Offset(x, y), size = size, cornerRadius = radius)
    drawRoundRect(
        outline, topLeft = Offset(x, y), size = size, cornerRadius = radius,
        style = Stroke(0.9.dp.toPx()),
    )
    val padV = if (second == null) 2.dp.toPx() else 3.dp.toPx()
    drawText(first, topLeft = Offset(x + (size.width - first.size.width) / 2f, y + padV))
    if (second != null) {
        val sx = x + (size.width - second.size.width) / 2f
        drawText(second, topLeft = Offset(sx, y + padV + first.size.height + 2.dp.toPx()))
    }
}

private val Y_STEP_CANDIDATES = intArrayOf(100, 200, 250, 500, 1000, 2000, 2500, 5000, 10000)

private var axisLabelLocale: Locale? = null
private var axisLabelFormat: NumberFormat? = null

private fun axisLabel(value: Int): String {
    val locale = Locale.getDefault()
    if (axisLabelFormat == null || axisLabelLocale != locale) {
        axisLabelLocale = locale
        axisLabelFormat = NumberFormat.getIntegerInstance(locale)
    }
    return axisLabelFormat?.format(value) ?: value.toString()
}

internal fun linearAt(x: FloatArray, y: FloatArray, value: Float): Float {
    if (x.isEmpty()) return 0f
    if (x.size == 1 || value <= x[0]) return y[0]
    if (value >= x[x.size - 1]) return y[y.size - 1]
    var i = 0
    while (i < x.size - 2 && value > x[i + 1]) i++
    val h = x[i + 1] - x[i]
    if (h == 0f) return y[i]
    val t = (value - x[i]) / h
    return y[i] + (y[i + 1] - y[i]) * t
}
