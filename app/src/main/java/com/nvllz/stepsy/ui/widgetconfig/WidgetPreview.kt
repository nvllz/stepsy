package com.nvllz.stepsy.ui.widgetconfig

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nvllz.stepsy.R
import com.nvllz.stepsy.util.Util
import kotlin.math.ceil

private const val SAMPLE_STEPS = 12345
private const val SAMPLE_DISTANCE = 12.34f

private val PREVIEW_FONT_FAMILY = FontFamily.Default

private const val PREVIEW_FONT_NUDGE = 1.04f

private val PLATE_AIR_W_TOTAL = 68.dp
private val PLATE_AIR_H_TOTAL = 46.dp

@Composable
fun WidgetPreview(
    type: WidgetConfigType,
    palette: WidgetPalette,
    textScale: Int,
    inverseBackdrop: Boolean,
    modifier: Modifier = Modifier,
    backdropPadding: Dp = 20.dp,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val stepsSample = if (type == WidgetConfigType.PLAIN) plainSampleText() else "12345"
    val unit = remember { Util.distanceUnit() }
    val distanceSample = stringResource(R.string.distance_today, SAMPLE_DISTANCE, unit)
    val plateSize = remember(distanceSample) {
        fixedPlateSize(density, measurer, distanceSample)
    }

    Box(
        modifier
            .fillMaxWidth()
            .background(
                colorResource(if (inverseBackdrop) R.color.colorOnSurface else R.color.colorSurface))
            .padding(backdropPadding),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(plateSize)
                .clip(RoundedCornerShape(dimensionResource(R.dimen.widgetRoundness)))
                .background(palette.background)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            when (type) {
                WidgetConfigType.ICON -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.widget_icon),
                        contentDescription = null,
                        tint = palette.primary,
                        modifier = Modifier.size(
                            width = (type.iconBaseWidthDp!! * textScale / 100f).dp,
                            height = (type.iconBaseHeightDp!! * textScale / 100f).dp,
                        ),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        StepsText(type, palette.primary, textScale, stepsSample)
                        DistanceText(palette.secondary, type.distanceBaseSp!!, textScale)
                    }
                }
                WidgetConfigType.COMPACT -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    StepsText(type, palette.primary, textScale, stepsSample)
                    DistanceText(palette.secondary, type.distanceBaseSp!!, textScale)
                }
                WidgetConfigType.PLAIN -> StepsText(
                    type, palette.primary, textScale, stepsSample)
            }
        }
    }
}

private fun fixedPlateSize(
    density: Density,
    measurer: TextMeasurer,
    distanceSample: String,
): DpSize {
    val icon = WidgetConfigType.ICON

    fun previewStyle(sp: Float, bold: Boolean) = TextStyle(
        fontFamily = PREVIEW_FONT_FAMILY,
        fontWeight = if (bold) FontWeight.Bold else null,
        fontSize = (sp * PREVIEW_FONT_NUDGE).sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

    val steps = measurer.measure(
        AnnotatedString("12345"), previewStyle(icon.stepsBaseSp, icon.stepsBold)).size
    val distance = measurer.measure(
        AnnotatedString(distanceSample), previewStyle(icon.distanceBaseSp!!, false)).size

    val contentWpx = with(density) {
        icon.iconBaseWidthDp!!.dp.toPx() + 16.dp.toPx() +
            maxOf(steps.width, distance.width).toFloat()
    }
    val contentHpx = with(density) {
        maxOf(icon.iconBaseHeightDp!!.dp.toPx(), (steps.height + distance.height).toFloat())
    }

    fun pxToAirDp(px: Float): Dp {
        val air = ceil(px / density.density).dp
        return air + 0.5.dp
    }

    return DpSize(
        width = pxToAirDp(contentWpx) + PLATE_AIR_W_TOTAL,
        height = pxToAirDp(contentHpx) + PLATE_AIR_H_TOTAL,
    )
}

@Composable
private fun StepsText(
    type: WidgetConfigType,
    color: Color,
    textScale: Int,
    text: String,
) {
    Text(
        text,
        fontSize = (type.stepsBaseSp * textScale / 100f * PREVIEW_FONT_NUDGE).sp,
        fontWeight = if (type.stepsBold) FontWeight.Bold else null,
        color = color,
        textAlign = TextAlign.Center,
        style = TextStyle(
            fontFamily = PREVIEW_FONT_FAMILY,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
    )
}

@Composable
private fun DistanceText(color: Color, baseSp: Float, textScale: Int) {
    val unit = remember { Util.distanceUnit() }
    Text(
        stringResource(R.string.distance_today, SAMPLE_DISTANCE, unit),
        fontSize = (baseSp * textScale / 100f * PREVIEW_FONT_NUDGE).sp,
        color = color,
        textAlign = TextAlign.Center,
        style = TextStyle(
            fontFamily = PREVIEW_FONT_FAMILY,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
    )
}

@Composable
private fun plainSampleText(): String {
    val context = LocalContext.current
    return remember(context) {
        context.resources.getQuantityString(R.plurals.steps_formatted, SAMPLE_STEPS, SAMPLE_STEPS)
    }
}
