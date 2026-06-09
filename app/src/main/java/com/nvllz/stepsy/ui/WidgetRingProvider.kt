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
import com.nvllz.stepsy.util.AppPreferences
import java.util.Locale

class WidgetRingProvider : AppWidgetProvider() {

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

        private const val TRACK_ALPHA = 150

        fun activeColor(context: Context, primaryColor: Int, progress: Float): Int =
            if (progress >= 1f) ContextCompat.getColor(context, R.color.widgetGoalReached) else primaryColor

        fun createRingBitmap(
            context: Context,
            arcColor: Int,
            trackColor: Int,
            progress: Float,
            scaleFactor: Float,
            compact: Boolean = false
        ): Bitmap {
            val density = context.resources.displayMetrics.density
            val sizePx = (96f * density * scaleFactor).toInt().coerceAtLeast(1)
            val strokeDp = if (compact) 14f else 9f
            val strokeWidthPx = strokeDp * density * scaleFactor

            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val pad = strokeWidthPx / 2f
            val rect = RectF(pad, pad, sizePx - pad, sizePx - pad)

            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = strokeWidthPx
                color = ColorUtils.setAlphaComponent(trackColor, TRACK_ALPHA)
                strokeCap = Paint.Cap.ROUND
            }
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = strokeWidthPx
                color = arcColor
                strokeCap = Paint.Cap.ROUND
            }

            val startDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = arcColor
            }

            canvas.drawArc(rect, 0f, 360f, false, trackPaint)
            canvas.drawCircle(sizePx / 2f, pad, strokeWidthPx / 2f, startDotPaint)
            val sweep = progress.coerceIn(0f, 1f) * 360f
            if (sweep > 0f) {
                canvas.drawArc(rect, -90f, sweep, false, ringPaint)
            }
            return bitmap
        }

        fun updateWidget(context: Context, appWidgetId: Int, steps: Int) {

            val prefs = context.getSharedPreferences("widget_prefs_$appWidgetId", Context.MODE_MULTI_PROCESS)

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_ring)

            val goal = AppPreferences.dailyGoalTarget.coerceAtLeast(1)
            val progress = steps.toFloat() / goal
            val percent = (progress * 100f).toInt()
            val percentStr = String.format(Locale.getDefault(), context.getString(R.string.widget_goal_progress), percent)

            remoteViews.setTextViewText(R.id.widget_ring_steps, steps.toString())
            remoteViews.setTextViewText(R.id.widget_ring_percent, percentStr)

            val compact = prefs.getBoolean("compact", false)
            remoteViews.setViewVisibility(R.id.widget_ring_text, if (compact) View.GONE else View.VISIBLE)

            val padPx = ((if (compact) 6f else 14f) * context.resources.displayMetrics.density).toInt()
            remoteViews.setViewPadding(R.id.widget_ring_container, padPx, padPx, padPx, padPx)

            val useDynamicColors = prefs.getBoolean("use_dynamic_colors", android.os.Build.VERSION.SDK_INT >= 31)
            val opacity = prefs.getInt("opacity", 100)
            val textScale = prefs.getInt("text_scale", 100)
            val scaleFactor = textScale / 100f
            val themeMode = prefs.getString("theme_mode", "system") ?: "system"

            val resolvedContext = themedContext(context, themeMode)

            if (useDynamicColors && android.os.Build.VERSION.SDK_INT >= 31) {
                val ringActiveColor = ContextCompat.getColor(resolvedContext, R.color.widgetRingActive)
                val secondaryColor = ContextCompat.getColor(resolvedContext, R.color.widgetSecondary)
                val bgColor = ContextCompat.getColor(resolvedContext, R.color.widgetRingBg)
                val alphaBgColor = ColorUtils.setAlphaComponent(bgColor, (255 * (opacity / 100f)).toInt())
                val arcColor = activeColor(resolvedContext, ringActiveColor, progress)

                remoteViews.setViewVisibility(R.id.widget_ring_background, View.GONE)
                remoteViews.setInt(R.id.widget_ring_container, "setBackgroundColor", alphaBgColor)
                remoteViews.setTextColor(R.id.widget_ring_steps, arcColor)
                remoteViews.setTextColor(R.id.widget_ring_percent, secondaryColor)
                remoteViews.setImageViewBitmap(
                    R.id.widget_ring_image,
                    createRingBitmap(resolvedContext, arcColor, ringActiveColor, progress, scaleFactor, compact)
                )
            } else {
                remoteViews.setViewVisibility(R.id.widget_ring_background, View.GONE)
                val ringActiveColor = ContextCompat.getColor(resolvedContext, R.color.widgetRingActive_default)
                val secondaryColor = ContextCompat.getColor(resolvedContext, R.color.widgetSecondary_default)
                val bgColor = ContextCompat.getColor(resolvedContext, R.color.widgetRingBg_default)
                val alphaBgColor = ColorUtils.setAlphaComponent(bgColor, (255 * (opacity / 100f)).toInt())
                val arcColor = activeColor(resolvedContext, ringActiveColor, progress)

                remoteViews.setInt(R.id.widget_ring_container, "setBackgroundColor", alphaBgColor)
                remoteViews.setTextColor(R.id.widget_ring_steps, arcColor)
                remoteViews.setTextColor(R.id.widget_ring_percent, secondaryColor)
                remoteViews.setImageViewBitmap(
                    R.id.widget_ring_image,
                    createRingBitmap(resolvedContext, arcColor, ringActiveColor, progress, scaleFactor, compact)
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
                12f * scaleFactor
            )

            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )
            remoteViews.setOnClickPendingIntent(R.id.widget_ring_container, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val steps = AppPreferences.steps

        appWidgetIds.forEach { id ->
            updateWidget(context, id, steps)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        updateWidget(context, appWidgetId, AppPreferences.steps)
    }
}
