package com.nvllz.stepsy.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.nvllz.stepsy.R
import com.nvllz.stepsy.ui.widgetconfig.WidgetConfigType
import com.nvllz.stepsy.ui.widgetconfig.WidgetConfigValues
import com.nvllz.stepsy.ui.widgetconfig.WidgetPrefs
import com.nvllz.stepsy.util.AppPreferences
import com.nvllz.stepsy.util.Util

class WidgetIconProvider : AppWidgetProvider() {

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

        fun buildRemoteViews(
            context: Context,
            steps: Int,
            values: WidgetConfigValues,
        ): RemoteViews {
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_icon)

            val distanceStr = context.getString(
                R.string.distance_today, Util.stepsToDistance(steps), Util.distanceUnit()
            )

            remoteViews.setTextViewText(R.id.widget_icon_steps, values.stepCountFormat.format(context, steps))
            remoteViews.setTextViewText(R.id.widget_icon_distance, distanceStr)

            val scaleFactor = values.textScale / 100f
            val resolvedContext = themedContext(context, values.themeMode.pref)

            if (values.useDynamicColors && android.os.Build.VERSION.SDK_INT >= 31) {
                val primaryColor = ContextCompat.getColor(resolvedContext, R.color.widgetPrimary)
                val secondaryColor = ContextCompat.getColor(resolvedContext, R.color.widgetSecondary)
                val bgColor = ContextCompat.getColor(resolvedContext, R.color.widgetBackground)
                val alphaBgColor = ColorUtils.setAlphaComponent(bgColor, (255 * (values.opacity / 100f)).toInt())

                remoteViews.setViewVisibility(R.id.widget_icon_background, View.GONE)
                remoteViews.setInt(R.id.widget_icon_container, "setBackgroundColor", alphaBgColor)
                remoteViews.setTextColor(R.id.widget_icon_steps, primaryColor)
                remoteViews.setTextColor(R.id.widget_icon_distance, secondaryColor)
                remoteViews.setInt(R.id.widget_icon_img, "setColorFilter", primaryColor)
            } else {
                remoteViews.setViewVisibility(R.id.widget_icon_background, View.GONE)
                val primaryColor = ContextCompat.getColor(resolvedContext, R.color.widgetPrimary_default)
                val secondaryColor = ContextCompat.getColor(resolvedContext, R.color.widgetSecondary_default)
                val bgColor = ContextCompat.getColor(resolvedContext, R.color.widgetBackground_default)
                val alphaBgColor = ColorUtils.setAlphaComponent(bgColor, (255 * (values.opacity / 100f)).toInt())

                remoteViews.setInt(R.id.widget_icon_container, "setBackgroundColor", alphaBgColor)
                remoteViews.setTextColor(R.id.widget_icon_steps, primaryColor)
                remoteViews.setTextColor(R.id.widget_icon_distance, secondaryColor)
                remoteViews.setInt(R.id.widget_icon_img, "setColorFilter", primaryColor)
            }

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

            if (android.os.Build.VERSION.SDK_INT >= 31) {
                remoteViews.setViewLayoutWidth(
                    R.id.widget_icon_img,
                    34f * scaleFactor,
                    TypedValue.COMPLEX_UNIT_DIP
                )
                remoteViews.setViewLayoutHeight(
                    R.id.widget_icon_img,
                    44f * scaleFactor,
                    TypedValue.COMPLEX_UNIT_DIP
                )
            } else {
                remoteViews.setFloat(R.id.widget_icon_img, "setScaleX", scaleFactor)
                remoteViews.setFloat(R.id.widget_icon_img, "setScaleY", scaleFactor)
            }

            return remoteViews
        }

        fun updateWidget(context: Context, appWidgetId: Int, steps: Int) {
            val values = WidgetPrefs(context, appWidgetId)
                .load(WidgetConfigType.ICON.defaultStepCountFormat)
            val remoteViews = buildRemoteViews(context, steps, values)

            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )
            remoteViews.setOnClickPendingIntent(R.id.widget_icon_container, pendingIntent)

            AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, remoteViews)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val steps = AppPreferences.steps
        appWidgetIds.forEach { id ->
            updateWidget(context, id, steps)
        }
    }
}