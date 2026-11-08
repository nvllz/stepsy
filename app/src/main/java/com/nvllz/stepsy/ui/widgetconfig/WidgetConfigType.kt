package com.nvllz.stepsy.ui.widgetconfig

import android.content.Context
import android.widget.RemoteViews
import com.nvllz.stepsy.ui.WidgetCompactProvider
import com.nvllz.stepsy.ui.WidgetIconProvider
import com.nvllz.stepsy.ui.WidgetPlainProvider
import com.nvllz.stepsy.ui.WidgetRingProvider

enum class WidgetConfigType(
    val stepsBaseSp: Float,
    val distanceBaseSp: Float?,
    val stepsBold: Boolean,
    val textScaleFrom: Float,
    val textScaleTo: Float,
    val iconBaseWidthDp: Float?,
    val iconBaseHeightDp: Float?,
    val defaultStepCountFormat: WidgetStepCountFormat,
) {
    ICON(24f, 14f, true, 75f, 175f, 34f, 44f, WidgetStepCountFormat.PLAIN),
    COMPACT(24f, 14f, true, 75f, 150f, null, null, WidgetStepCountFormat.DELIMITED),
    PLAIN(22f, null, false, 65f, 165f, null, null, WidgetStepCountFormat.PLAIN),
    RING(20f, 11f, true, 75f, 150f, null, null, WidgetStepCountFormat.DELIMITED);

    fun update(context: Context, appWidgetId: Int, steps: Int) = when (this) {
        ICON    -> WidgetIconProvider.updateWidget(context, appWidgetId, steps)
        COMPACT -> WidgetCompactProvider.updateWidget(context, appWidgetId, steps)
        PLAIN   -> WidgetPlainProvider.updateWidget(context, appWidgetId, steps)
        RING    -> WidgetRingProvider.updateWidget(context, appWidgetId, steps)
    }

    fun previewRemoteViews(
        context: Context,
        steps: Int,
        values: WidgetConfigValues,
    ): RemoteViews = when (this) {
        ICON    -> WidgetIconProvider.buildRemoteViews(context, steps, values)
        COMPACT -> WidgetCompactProvider.buildRemoteViews(context, steps, values)
        PLAIN   -> WidgetPlainProvider.buildRemoteViews(context, steps, values)
        RING    -> WidgetRingProvider.buildRemoteViews(context, steps, values)
    }

    companion object {
        const val TEXT_SCALE_STEP = 5f
    }
}
