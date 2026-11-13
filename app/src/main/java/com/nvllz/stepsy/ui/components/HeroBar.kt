package com.nvllz.stepsy.ui.components

import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import com.nvllz.stepsy.R
import com.nvllz.stepsy.ui.theme.StepsyTheme

private val HeroBarHeight = 20.dp
private const val HERO_MAX_LAPS = 10
private const val HERO_LEAD_EPS = 0.002f
private const val HERO_LEAD_FADE = 0.03f
private const val HERO_LAP1_LEFT_ALPHA = 0.5f
private const val HERO_UPPER_LEFT_ALPHA = 0.18f
private const val HERO_KNOB_ALPHA = 0.35f
private const val HERO_SHEEN_KNOB_ALPHA = 0.32f
private const val HERO_HAIRLINE_ALPHA = 0.40f

private class HeroBarKit(
    val trail: Array<Brush>,
    val knob: Array<Brush>,
    val sheenKnob: Brush,
    val hairline: Brush,
)

private fun buildHeroKit(palette: List<Color>, peak: Color, sheen: Color, knobR: Float, sheenR: Float): HeroBarKit {
    val last = palette.last()
    val extra = (HERO_MAX_LAPS - palette.size).coerceAtLeast(1).toFloat()
    val base = Array(HERO_MAX_LAPS) { k ->
        if (k < palette.size) palette[k] else lerp(last, peak, (k - palette.size + 1) / extra)
    }
    val trail = Array<Brush>(HERO_MAX_LAPS) { k ->
        val c = base[k]
        if (k == 0) {
            Brush.horizontalGradient(listOf(c.copy(alpha = HERO_LAP1_LEFT_ALPHA), c))
        } else {
            val lift = minOf(0.12f + 0.05f * k, 0.45f)
            Brush.horizontalGradient(listOf(c.copy(alpha = HERO_UPPER_LEFT_ALPHA), lerp(c, sheen, lift)))
        }
    }
    val knob = Array<Brush>(HERO_MAX_LAPS) { k ->
        Brush.radialGradient(
            listOf(base[k].copy(alpha = HERO_KNOB_ALPHA), Color.Transparent),
            center = Offset.Zero, radius = knobR,
        )
    }
    val sheenKnob = Brush.radialGradient(
        listOf(sheen, sheen.copy(alpha = 0f)), center = Offset.Zero, radius = sheenR,
    )
    val hairline = Brush.horizontalGradient(listOf(sheen.copy(alpha = 0f), sheen))
    return HeroBarKit(trail, knob, sheenKnob, hairline)
}

@Composable
fun HeroBar(progress: Float, modifier: Modifier = Modifier) {
    val shown = animateFloatAsState(
        progress.coerceIn(0f, HERO_MAX_LAPS.toFloat()),
        tween(1400, easing = EaseInOutCubic), label = "bar",
    )
    HeroBar(shown, modifier)
}

@Composable
fun HeroBar(shown: State<Float>, modifier: Modifier = Modifier) {
    val bed = StepsyTheme.colors.muted
    val palette = lapPalette()
    val peak = colorResource(R.color.colorLapPeak)
    val sheen = colorResource(R.color.colorLapSheen)
    val hPx = with(LocalDensity.current) { HeroBarHeight.toPx() }
    val kit = remember(palette, peak, sheen, hPx) {
        buildHeroKit(palette, peak, sheen, knobR = hPx * 0.9f, sheenR = hPx * 0.62f)
    }
    Canvas(modifier.fillMaxWidth().height(HeroBarHeight)) {
        drawHeroBar(kit, bed, shown.value.coerceIn(0f, HERO_MAX_LAPS.toFloat()))
    }
}

private fun DrawScope.drawHeroBar(kit: HeroBarKit, bed: Color, v: Float) {
    val w = size.width
    val h = size.height
    val r = CornerRadius(h / 2f)
    drawRoundRect(bed, cornerRadius = r)

    val full = v.toInt()
    val part = v - full
    val lead = part > HERO_LEAD_EPS && full < HERO_MAX_LAPS
    val tipA = if (lead) ((part - HERO_LEAD_EPS) / HERO_LEAD_FADE).coerceIn(0f, 1f) else 1f

    for (k in 0 until full) {
        drawRoundRect(kit.trail[k], cornerRadius = r)
    }

    var tipX = w
    if (lead) {
        val lw = maxOf(w * part, h)
        val sx = lw / w
        withTransform({ scale(sx, 1f, Offset.Zero) }) {
            drawRoundRect(kit.trail[full], cornerRadius = CornerRadius(h / 2f / sx, h / 2f), alpha = tipA)
        }
        tipX = lw
    }

    val e = ((v - 1f) / (HERO_MAX_LAPS - 1f)).coerceIn(0f, 1f)
    val depth = e * (2f - e)
    if (depth > 0.001f) {
        val y = h * 0.24f
        drawLine(
            kit.hairline, Offset(h / 2f, y), Offset(w - h / 2f, y),
            strokeWidth = h * 0.05f, cap = StrokeCap.Round, alpha = HERO_HAIRLINE_ALPHA * depth,
        )
    }

    if (lead) {
        heroKnob(kit, full, tipX - h / 2f, h, 0.5f + 0.5f * tipA, tipA, depth)
        if (full >= 1 && tipA < 1f) heroKnob(kit, full - 1, w - h / 2f, h, 1f, 1f - tipA, depth)
    } else if (full >= 1) {
        heroKnob(kit, full - 1, w - h / 2f, h, 1f, 1f, depth)
    }
}

private fun DrawScope.heroKnob(kit: HeroBarKit, lap: Int, x: Float, h: Float, scale: Float, alpha: Float, depth: Float) {
    withTransform({ translate(x, h / 2f); scale(scale, scale, Offset.Zero) }) {
        drawCircle(kit.knob[lap], radius = h * 0.9f, center = Offset.Zero, alpha = alpha)
        val sa = HERO_SHEEN_KNOB_ALPHA * depth * alpha
        if (sa > 0.002f) drawCircle(kit.sheenKnob, radius = h * 0.62f, center = Offset.Zero, alpha = sa)
    }
}
