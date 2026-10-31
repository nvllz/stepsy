package com.nvllz.stepsy.util

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.nvllz.stepsy.ui.WidgetCompactProvider
import com.nvllz.stepsy.ui.WidgetIconProvider
import com.nvllz.stepsy.ui.WidgetPlainProvider

object WidgetManager {
    private val updateHandler = Handler(Looper.getMainLooper())
    private var isUpdateQueued = false

    fun updateAllWidgets(context: Context, steps: Int? = null, immediate: Boolean = false) {

        val stepsToUse = steps ?: AppPreferences.steps

        if (immediate) {
            performWidgetUpdates(context, stepsToUse)
        } else {
            synchronized(this) {
                if (!isUpdateQueued) {
                    isUpdateQueued = true
                    updateHandler.postDelayed({
                        performWidgetUpdates(context, stepsToUse)
                        isUpdateQueued = false
                    }, 150)
                }
            }
        }
    }

    private fun performWidgetUpdates(context: Context, steps: Int) {
        val appWidgetManager = AppWidgetManager.getInstance(context)

        appWidgetManager.getAppWidgetIds(
            ComponentName(context, WidgetCompactProvider::class.java)
        ).forEach { widgetId ->
            WidgetCompactProvider.updateWidget(context, widgetId, steps)
        }

        appWidgetManager.getAppWidgetIds(
            ComponentName(context, WidgetIconProvider::class.java)
        ).forEach { widgetId ->
            WidgetIconProvider.updateWidget(context, widgetId, steps)
        }

        appWidgetManager.getAppWidgetIds(
            ComponentName(context, WidgetPlainProvider::class.java)
        ).forEach { widgetId ->
            WidgetPlainProvider.updateWidget(context, widgetId, steps)
        }
    }
}