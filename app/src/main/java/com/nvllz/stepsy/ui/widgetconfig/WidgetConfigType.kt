package com.nvllz.stepsy.ui.widgetconfig

import android.content.Context
import com.nvllz.stepsy.ui.WidgetCompactProvider
import com.nvllz.stepsy.ui.WidgetIconProvider
import com.nvllz.stepsy.ui.WidgetPlainProvider

enum class WidgetConfigType(
    val stepsBaseSp: Float,
    val distanceBaseSp: Float?,
    val stepsBold: Boolean,
    val textScaleFrom: Float,
    val textScaleTo: Float,
    val iconBaseWidthDp: Float?,
    val iconBaseHeightDp: Float?,
) {
    ICON(24f, 14f, true, 75f, 175f, 30f, 40f),
    COMPACT(24f, 14f, true, 75f, 150f, null, null),
    PLAIN(22f, null, false, 65f, 165f, null, null);

    fun update(context: Context, appWidgetId: Int, steps: Int) = when (this) {
        ICON    -> WidgetIconProvider.updateWidget(context, appWidgetId, steps)
        COMPACT -> WidgetCompactProvider.updateWidget(context, appWidgetId, steps)
        PLAIN   -> WidgetPlainProvider.updateWidget(context, appWidgetId, steps)
    }

    companion object {
        const val TEXT_SCALE_STEP = 5f
    }
}
