package com.nvllz.stepsy.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.nvllz.stepsy.R
import com.nvllz.stepsy.util.AppPreferences
import com.nvllz.stepsy.util.Util
import java.util.Locale

class WidgetIconProvider : AppWidgetProvider() {

    companion object {
        fun updateWidget(context: Context, appWidgetId: Int, steps: Int) {

            val prefs = context.getSharedPreferences("widget_prefs_$appWidgetId", Context.MODE_MULTI_PROCESS)

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_icon)

            // Update text content
            val distance = Util.stepsToDistance(steps)
            val distanceStr = String.format(Locale.getDefault(), "%.2f %s", distance, Util.getDistanceUnitString())

            remoteViews.setTextViewText(R.id.widget_icon_steps, steps.toString())
            remoteViews.setTextViewText(R.id.widget_icon_distance, distanceStr)

            // Load preferences
            val useDynamicColors = prefs.getBoolean("use_dynamic_colors", android.os.Build.VERSION.SDK_INT >= 31)
            val opacity = prefs.getInt("opacity", 100)
            val textScale = prefs.getInt("text_scale", 100)
            val scaleFactor = textScale / 100f

            // Resolve colors
            val primaryColor = ContextCompat.getColor(
                context,
                if (useDynamicColors && android.os.Build.VERSION.SDK_INT >= 31)
                    R.color.widgetPrimary else R.color.widgetPrimary_default
            )
            val secondaryColor = ContextCompat.getColor(
                context,
                if (useDynamicColors && android.os.Build.VERSION.SDK_INT >= 31)
                    R.color.widgetSecondary else R.color.widgetSecondary_default
            )
            val bgColor = ContextCompat.getColor(
                context,
                if (useDynamicColors && android.os.Build.VERSION.SDK_INT >= 31)
                    R.color.widgetBackground else R.color.widgetBackground_default
            )
            val alphaBgColor = ColorUtils.setAlphaComponent(bgColor, (255 * (opacity / 100f)).toInt())

            // Apply styles
            remoteViews.setInt(R.id.widget_icon_container, "setBackgroundColor", alphaBgColor)
            remoteViews.setTextColor(R.id.widget_icon_steps, primaryColor)
            remoteViews.setTextColor(R.id.widget_icon_distance, secondaryColor)
            remoteViews.setInt(R.id.widget_icon_img, "setColorFilter", primaryColor)

            remoteViews.setTextViewTextSize(
                R.id.widget_icon_steps,
                TypedValue.COMPLEX_UNIT_SP,
                24f * scaleFactor
            )
            remoteViews.setTextViewTextSize(
                R.id.widget_icon_distance,
                TypedValue.COMPLEX_UNIT_SP,
                14f * scaleFactor
            )

            // Set widget click behavior
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )
            remoteViews.setOnClickPendingIntent(R.id.widget_icon_container, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val steps = AppPreferences.steps

        appWidgetIds.forEach { id ->
            updateWidget(context, id, steps)
        }
    }
}
