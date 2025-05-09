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

class WidgetCompactProvider : AppWidgetProvider() {

    companion object {
        fun updateWidget(context: Context, appWidgetId: Int, steps: Int) {

            val prefs = context.getSharedPreferences("widget_prefs_$appWidgetId", Context.MODE_MULTI_PROCESS)

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_compact)

            // Update text content
            val distanceStr = String.format(Locale.getDefault(), context.getString(R.string.distance_today),
                Util.stepsToDistance(steps),
                Util.getDistanceUnitString())

            remoteViews.setTextViewText(R.id.widget_compact_steps, steps.toString())
            remoteViews.setTextViewText(R.id.widget_compact_distance, distanceStr)

            // Load widget-specific preferences
            val useDynamicColors = prefs.getBoolean("use_dynamic_colors", android.os.Build.VERSION.SDK_INT >= 31)
            val opacity = prefs.getInt("opacity", 100)
            val textScale = prefs.getInt("text_scale", 100)
            val scaleFactor = textScale / 100f

            // Resolve colors

            if (useDynamicColors && android.os.Build.VERSION.SDK_INT >= 31) {
                remoteViews.setFloat(R.id.widget_compact_background, "setAlpha", opacity / 100f)
                remoteViews.setColor(R.id.widget_compact_background, "setColorFilter", R.color.widgetBackground)
                remoteViews.setColor(R.id.widget_compact_steps, "setTextColor", R.color.widgetPrimary)
                remoteViews.setColor(R.id.widget_compact_distance, "setTextColor", R.color.widgetSecondary)
            } else {
                val primaryColor = ContextCompat.getColor(context, R.color.widgetPrimary_default)
                val secondaryColor = ContextCompat.getColor(context, R.color.widgetSecondary_default)
                val bgColor = ContextCompat.getColor(context, R.color.widgetBackground_default)
                val alphaBgColor =
                    ColorUtils.setAlphaComponent(bgColor, (255 * (opacity / 100f)).toInt())

                // Apply styles
                remoteViews.setInt(R.id.widget_compact_background, "setColorFilter", alphaBgColor)
                remoteViews.setTextColor(R.id.widget_compact_steps, primaryColor)
                remoteViews.setTextColor(R.id.widget_compact_distance, secondaryColor)
            }

            remoteViews.setTextViewTextSize(
                R.id.widget_compact_steps,
                TypedValue.COMPLEX_UNIT_SP,
                28f * scaleFactor
            )
            remoteViews.setTextViewTextSize(
                R.id.widget_compact_distance,
                TypedValue.COMPLEX_UNIT_SP,
                11f * scaleFactor
            )

            // Widget click behavior
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )
            remoteViews.setOnClickPendingIntent(R.id.widget_compact_container, pendingIntent)

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