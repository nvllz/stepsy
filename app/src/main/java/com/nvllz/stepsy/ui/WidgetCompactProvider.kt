package com.nvllz.stepsy.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.nvllz.stepsy.R
import com.nvllz.stepsy.util.AppPreferences
import com.nvllz.stepsy.util.Util
import java.util.Locale

class WidgetCompactProvider : AppWidgetProvider() {

    companion object {

        private fun themedContext(context: Context, themeMode: String): Context {
            val uiMode = when (themeMode) {
                "light" -> Configuration.UI_MODE_NIGHT_NO
                "dark"  -> Configuration.UI_MODE_NIGHT_YES
                else    -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            }
            val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            if (uiMode == currentNightMode) return context
            val config = Configuration(context.resources.configuration)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or uiMode
            return context.createConfigurationContext(config)
        }

        fun updateWidget(context: Context, appWidgetId: Int, steps: Int) {

            val prefs = context.getSharedPreferences("widget_prefs_$appWidgetId", Context.MODE_MULTI_PROCESS)

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_compact)

            val distanceStr = String.format(Locale.getDefault(), context.getString(R.string.distance_today),
                Util.stepsToDistance(steps),
                Util.distanceUnit())

            remoteViews.setTextViewText(R.id.widget_compact_steps, steps.toString())
            remoteViews.setTextViewText(R.id.widget_compact_distance, distanceStr)

            val useDynamicColors = prefs.getBoolean("use_dynamic_colors", android.os.Build.VERSION.SDK_INT >= 31)
            val opacity = prefs.getInt("opacity", 100)
            val textScale = prefs.getInt("text_scale", 100)
            val scaleFactor = textScale / 100f
            val themeMode = prefs.getString("theme_mode", "system") ?: "system"

            val resolvedContext = themedContext(context, themeMode)

            if (useDynamicColors && android.os.Build.VERSION.SDK_INT >= 31) {
                val primaryColor = ContextCompat.getColor(resolvedContext, R.color.widgetPrimary)
                val secondaryColor = ContextCompat.getColor(resolvedContext, R.color.widgetSecondary)
                val bgColor = ContextCompat.getColor(resolvedContext, R.color.widgetBackground)
                val alphaBgColor = ColorUtils.setAlphaComponent(bgColor, (255 * (opacity / 100f)).toInt())

                remoteViews.setViewVisibility(R.id.widget_compact_background, View.GONE)
                remoteViews.setInt(R.id.widget_compact_container, "setBackgroundColor", alphaBgColor)
                remoteViews.setTextColor(R.id.widget_compact_steps, primaryColor)
                remoteViews.setTextColor(R.id.widget_compact_distance, secondaryColor)
            } else {
                remoteViews.setViewVisibility(R.id.widget_compact_background, View.GONE)
                val primaryColor = ContextCompat.getColor(resolvedContext, R.color.widgetPrimary_default)
                val secondaryColor = ContextCompat.getColor(resolvedContext, R.color.widgetSecondary_default)
                val bgColor = ContextCompat.getColor(resolvedContext, R.color.widgetBackground_default)
                val alphaBgColor = ColorUtils.setAlphaComponent(bgColor, (255 * (opacity / 100f)).toInt())

                remoteViews.setInt(R.id.widget_compact_container, "setBackgroundColor", alphaBgColor)
                remoteViews.setTextColor(R.id.widget_compact_steps, primaryColor)
                remoteViews.setTextColor(R.id.widget_compact_distance, secondaryColor)
            }

            remoteViews.setTextViewTextSize(
                R.id.widget_compact_steps,
                TypedValue.COMPLEX_UNIT_SP,
                24f * scaleFactor
            )
            remoteViews.setTextViewTextSize(
                R.id.widget_compact_distance,
                TypedValue.COMPLEX_UNIT_SP,
                14f * scaleFactor
            )

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