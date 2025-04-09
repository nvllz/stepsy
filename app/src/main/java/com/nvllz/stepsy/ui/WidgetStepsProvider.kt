package com.nvllz.stepsy.ui

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.nvllz.stepsy.R
import com.nvllz.stepsy.util.Util

class WidgetStepsProvider : AppWidgetProvider() {

    companion object {
        @SuppressLint("DefaultLocale")
        fun updateWidget(context: Context, steps: Int) {
            Util.init(context)

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetComponent = ComponentName(context, WidgetStepsProvider::class.java)
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_steps)

            val distance = Util.stepsToDistance(steps)
            val distanceStr = String.format("%.2f %s", distance, Util.getDistanceUnitString())

            remoteViews.setTextViewText(R.id.widget_steps, steps.toString())
            remoteViews.setTextViewText(R.id.widget_distance, distanceStr)

            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )
            remoteViews.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

            appWidgetManager.updateAppWidget(widgetComponent, remoteViews)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Util.init(context)

        val prefs = context.getSharedPreferences("com.nvllz.stepsy_preferences", Context.MODE_PRIVATE)
        val steps = prefs.getInt("STEPS", 0)

        updateWidget(context, steps)
    }
}
