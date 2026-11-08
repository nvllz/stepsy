package com.nvllz.stepsy.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
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
import java.text.NumberFormat

class WidgetRingProvider : AppWidgetProvider() {

    companion object {

        private const val TRACK_ALPHA = 150
        private const val RING_BASE_DP = 96f
        private const val RING_STROKE_DP = 9f

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

        fun createRingBitmap(
            context: Context,
            ringColor: Int,
            progress: Float,
            scaleFactor: Float,
        ): Bitmap {
            val density = context.resources.displayMetrics.density
            val sizePx = (RING_BASE_DP * density * scaleFactor).toInt().coerceAtLeast(1)
            val strokeWidthPx = RING_STROKE_DP * density * scaleFactor

            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val pad = strokeWidthPx / 2f
            val rect = RectF(pad, pad, sizePx - pad, sizePx - pad)

            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = strokeWidthPx
                color = ColorUtils.setAlphaComponent(ringColor, TRACK_ALPHA)
                strokeCap = Paint.Cap.ROUND
            }
            val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = strokeWidthPx
                color = ringColor
                strokeCap = Paint.Cap.ROUND
            }
            val startDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = ringColor
            }

            canvas.drawArc(rect, 0f, 360f, false, trackPaint)
            canvas.drawCircle(sizePx / 2f, pad, strokeWidthPx / 2f, startDotPaint)
            if (progress > 0f) {
                canvas.drawArc(rect, -90f, progress.coerceIn(0f, 1f) * 360f, false, arcPaint)
            }
            return bitmap
        }

        fun buildRemoteViews(
            context: Context,
            steps: Int,
            values: WidgetConfigValues,
        ): RemoteViews {
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_ring)

            val goal = AppPreferences.dailyGoalTarget
            val hasGoal = goal > 0
            val progress = if (hasGoal) steps / goal.toFloat() else 0f

            remoteViews.setTextViewText(
                R.id.widget_ring_steps,
                values.stepCountFormat.format(context, steps)
            )
            if (hasGoal) {
                val percent = NumberFormat.getIntegerInstance().format(
                    (progress * 100f).toInt()
                )
                remoteViews.setViewVisibility(R.id.widget_ring_percent, View.VISIBLE)
                remoteViews.setTextViewText(R.id.widget_ring_percent, "$percent%")
            } else {
                val distanceStr = context.getString(
                    R.string.distance_today, Util.stepsToDistance(steps), Util.distanceUnit()
                )
                remoteViews.setViewVisibility(R.id.widget_ring_percent, View.VISIBLE)
                remoteViews.setTextViewText(R.id.widget_ring_percent, distanceStr)
            }

            val scaleFactor = values.textScale / 100f
            val resolvedContext = themedContext(context, values.themeMode.pref)

            if (values.useDynamicColors && android.os.Build.VERSION.SDK_INT >= 31) {
                val primaryColor = ContextCompat.getColor(resolvedContext, R.color.widgetPrimary)
                val secondaryColor = ContextCompat.getColor(resolvedContext, R.color.widgetSecondary)
                val bgColor = ContextCompat.getColor(resolvedContext, R.color.widgetBackground)
                val alphaBgColor = ColorUtils.setAlphaComponent(bgColor, (255 * (values.opacity / 100f)).toInt())

                remoteViews.setViewVisibility(R.id.widget_ring_background, View.GONE)
                remoteViews.setInt(R.id.widget_ring_container, "setBackgroundColor", alphaBgColor)
                remoteViews.setTextColor(R.id.widget_ring_steps, primaryColor)
                remoteViews.setTextColor(R.id.widget_ring_percent, secondaryColor)
                remoteViews.setImageViewBitmap(
                    R.id.widget_ring_image,
                    createRingBitmap(context, primaryColor, progress, scaleFactor)
                )
            } else {
                val primaryColor = ContextCompat.getColor(resolvedContext, R.color.widgetPrimary_default)
                val secondaryColor = ContextCompat.getColor(resolvedContext, R.color.widgetSecondary_default)
                val bgColor = ContextCompat.getColor(resolvedContext, R.color.widgetBackground_default)
                val alphaBgColor = ColorUtils.setAlphaComponent(bgColor, (255 * (values.opacity / 100f)).toInt())

                remoteViews.setViewVisibility(R.id.widget_ring_background, View.GONE)
                remoteViews.setInt(R.id.widget_ring_container, "setBackgroundColor", alphaBgColor)
                remoteViews.setTextColor(R.id.widget_ring_steps, primaryColor)
                remoteViews.setTextColor(R.id.widget_ring_percent, secondaryColor)
                remoteViews.setImageViewBitmap(
                    R.id.widget_ring_image,
                    createRingBitmap(context, primaryColor, progress, scaleFactor)
                )
            }

            remoteViews.setTextViewTextSize(
                R.id.widget_ring_steps,
                TypedValue.COMPLEX_UNIT_SP,
                20f * scaleFactor
            )
            remoteViews.setTextViewTextSize(
                R.id.widget_ring_percent,
                TypedValue.COMPLEX_UNIT_SP,
                11f * scaleFactor
            )

            return remoteViews
        }

        fun updateWidget(context: Context, appWidgetId: Int, steps: Int) {
            val values = WidgetPrefs(context, appWidgetId)
                .load(WidgetConfigType.RING.defaultStepCountFormat)
            val remoteViews = buildRemoteViews(context, steps, values)

            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )
            remoteViews.setOnClickPendingIntent(R.id.widget_ring_container, pendingIntent)

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
