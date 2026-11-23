package com.nvllz.stepsy.util

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.nvllz.stepsy.ui.WidgetCompactProvider
import com.nvllz.stepsy.ui.WidgetIconProvider
import com.nvllz.stepsy.ui.WidgetPlainProvider
import com.nvllz.stepsy.ui.WidgetRingProvider

object WidgetManager {
    private val updateHandler = Handler(Looper.getMainLooper())
    private var isUpdateQueued = false
    private var lastRenderedSteps: Int? = null
    private var lastRenderedGoal: Int? = null

    fun updateAllWidgets(context: Context, steps: Int? = null, immediate: Boolean = false) {

        val stepsToUse = steps ?: AppPreferences.steps
        val goal = AppPreferences.dailyGoalTarget

        if (stepsToUse == lastRenderedSteps && goal == lastRenderedGoal) return

        if (immediate) {
            performWidgetUpdates(context, stepsToUse, goal)
        } else {
            synchronized(this) {
                if (!isUpdateQueued) {
                    isUpdateQueued = true
                    updateHandler.postDelayed({
                        performWidgetUpdates(context, stepsToUse, goal)
                        isUpdateQueued = false
                    }, 150)
                }
            }
        }
    }

    private fun performWidgetUpdates(context: Context, steps: Int, goal: Int) {
        lastRenderedSteps = steps
        lastRenderedGoal = goal
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

        appWidgetManager.getAppWidgetIds(
            ComponentName(context, WidgetRingProvider::class.java)
        ).forEach { widgetId ->
            WidgetRingProvider.updateWidget(context, widgetId, steps)
        }
    }
}