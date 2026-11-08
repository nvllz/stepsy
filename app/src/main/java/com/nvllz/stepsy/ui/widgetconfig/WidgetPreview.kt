package com.nvllz.stepsy.ui.widgetconfig

import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nvllz.stepsy.R
import com.nvllz.stepsy.util.Util
import kotlin.math.ceil

private const val SAMPLE_STEPS = 12345
private const val SAMPLE_DISTANCE = 12.34f

private val PREVIEW_FONT_FAMILY = FontFamily.Default

private const val PREVIEW_FONT_NUDGE = 1.04f

private const val DEFAULT_GRID_COLUMNS = 4

private val PLATE_AIR_H_TOTAL = 46.dp

private const val PLATFORM_HOST_THEME = android.R.style.Theme_DeviceDefault_DayNight

@Composable
fun WidgetPreview(
    type: WidgetConfigType,
    values: WidgetConfigValues,
    inverseBackdrop: Boolean,
    modifier: Modifier = Modifier,
    backdropPadding: Dp = 20.dp,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val activityContext = LocalContext.current
    val widgetHostContext = remember(activityContext) {
        ContextThemeWrapper(activityContext, PLATFORM_HOST_THEME)
    }
    val unit = remember { Util.distanceUnit() }
    val distanceSample = stringResource(R.string.distance_today, SAMPLE_DISTANCE, unit)
    val plateSize = remember(distanceSample, screenWidthDp) {
        plateSizeFor(type, screenWidthDp, density, measurer, distanceSample)
    }

    Box(
        modifier
            .fillMaxWidth()
            .background(
                colorResource(
                    if (inverseBackdrop) R.color.colorOnSurface else R.color.colorSurface
                ),
            )
            .padding(backdropPadding),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(plateSize)
                .clip(RoundedCornerShape(dimensionResource(R.dimen.widgetRoundness))),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize().clearAndSetSemantics {},
                factory = { context -> FrameLayout(context) },
                update = { host ->
                    val remoteViews = type.previewRemoteViews(widgetHostContext, SAMPLE_STEPS, values)
                    if (host.tag == null) {
                        host.tag = remoteViews
                        host.addView(remoteViews.apply(widgetHostContext, host))
                    } else {
                        remoteViews.reapply(widgetHostContext, host)
                    }
                },
            )
        }
    }
}

private fun measuredLines(
    measurer: TextMeasurer,
    distanceSample: String,
): Pair<IntSize, IntSize> {
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

    return steps to distance
}

private fun plateSideDp(px: Float, density: Density): Dp {
    val air = ceil(px / density.density).dp
    return air + 0.5.dp
}

private fun plateSizeFor(
    type: WidgetConfigType,
    screenWidthDp: Int,
    density: Density,
    measurer: TextMeasurer,
    distanceSample: String,
): DpSize {
    val icon = WidgetConfigType.ICON
    val (steps, distance) = measuredLines(measurer, distanceSample)

    val cellDp = (screenWidthDp / DEFAULT_GRID_COLUMNS).dp
    val widthDp = when (type) {
        WidgetConfigType.COMPACT -> cellDp
        WidgetConfigType.ICON, WidgetConfigType.PLAIN -> cellDp * 2
    }
    val contentHpx = with(density) {
        maxOf(icon.iconBaseHeightDp!!.dp.toPx(), (steps.height + distance.height).toFloat())
    }

    return DpSize(
        width = widthDp,
        height = plateSideDp(contentHpx, density) + PLATE_AIR_H_TOTAL,
    )
}
