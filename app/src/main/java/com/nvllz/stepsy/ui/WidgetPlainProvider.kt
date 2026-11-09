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
import com.nvllz.stepsy.ui.widgetconfig.WidgetStepCountFormat
import com.nvllz.stepsy.util.AppPreferences

class WidgetPlainProvider : AppWidgetProvider() {

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
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_plain)

            val stepsStr = context.resources.getQuantityString(
                R.plurals.steps_formatted,
                if (values.stepCountFormat == WidgetStepCountFormat.SHORT && steps >= 1_000) 1_000_000 else steps,
                values.stepCountFormat.format(context, steps)
            )
            remoteViews.setTextViewText(R.id.widget_plain_steps, stepsStr)

            val scaleFactor = values.textScale / 100f
            val resolvedContext = themedContext(context, values.themeMode.pref)

            if (values.useDynamicColors && android.os.Build.VERSION.SDK_INT >= 31) {
                val primaryColor = ContextCompat.getColor(resolvedContext, R.color.widgetPrimary)
                val bgColor = ContextCompat.getColor(resolvedContext, R.color.widgetBackground)
                val alphaBgColor = ColorUtils.setAlphaComponent(bgColor, (255 * (values.opacity / 100f)).toInt())

                remoteViews.setViewVisibility(R.id.widget_plain_background, View.GONE)
                remoteViews.setInt(R.id.widget_plain_container, "setBackgroundColor", alphaBgColor)
                remoteViews.setTextColor(R.id.widget_plain_steps, primaryColor)
            } else {
                remoteViews.setViewVisibility(R.id.widget_plain_background, View.GONE)
                val primaryColor = ContextCompat.getColor(resolvedContext, R.color.widgetPrimary_default)
                val bgColor = ContextCompat.getColor(resolvedContext, R.color.widgetBackground_default)
                val alphaBgColor = ColorUtils.setAlphaComponent(bgColor, (255 * (values.opacity / 100f)).toInt())

                remoteViews.setInt(R.id.widget_plain_container, "setBackgroundColor", alphaBgColor)
                remoteViews.setTextColor(R.id.widget_plain_steps, primaryColor)
            }

            remoteViews.setTextViewTextSize(
                R.id.widget_plain_steps,
                TypedValue.COMPLEX_UNIT_SP,
                22f * scaleFactor
            )

            return remoteViews
        }

        fun updateWidget(context: Context, appWidgetId: Int, steps: Int) {
            val values = WidgetPrefs(context, appWidgetId)
                .load(WidgetConfigType.PLAIN.defaultStepCountFormat)
            val remoteViews = buildRemoteViews(context, steps, values)

            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )
            remoteViews.setOnClickPendingIntent(R.id.widget_plain_container, pendingIntent)

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
