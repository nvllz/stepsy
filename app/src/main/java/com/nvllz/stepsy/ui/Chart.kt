/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.nvllz.stepsy.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.nvllz.stepsy.R
import com.nvllz.stepsy.util.AppPreferences
import com.nvllz.stepsy.util.Database
import java.util.*

/**
 * The chart in the UI that shows the weekly step distribution with a bar chart.
 */
internal class Chart : BarChart {
    private val yVals = ArrayList<BarEntry>()
    private val oldYVals = ArrayList<BarEntry>()
    private var isPast7DaysMode = false
    private var past7DaysStartTime = 0L
    private val dayFormatter = DayFormatter()

    constructor(context: Context) : super(context) {
        initializeChart()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initializeChart()
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        initializeChart()
    }

    internal fun setPast7DaysMode(isPast7Days: Boolean, startTime: Long = 0L) {
        isPast7DaysMode = isPast7Days
        past7DaysStartTime = startTime
        dayFormatter.setPast7DaysMode(isPast7Days, startTime)
    }

    private fun initializeChart() {
        // Disable description text
        description.isEnabled = false


        // Other chart styling and configuration
        setDrawBarShadow(false)
        setDrawValueAboveBar(true)
        setTouchEnabled(false)
        setViewPortOffsets(0f, 20f, 0f, 50f)

        configureXAxis()
        configureAxes()
        configureLegend()

        initializeData()
    }

    private fun configureXAxis() {
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f
        xAxis.setDrawGridLines(false)
        xAxis.textColor = Color.GRAY
        xAxis.valueFormatter = dayFormatter
    }

    private fun configureAxes() {
        axisLeft.isEnabled = false
        axisRight.isEnabled = false

        axisLeft.axisMinimum = 0f
        axisLeft.spaceBottom = 10f
    }

    private fun configureLegend() {
        legend.isEnabled = false
    }

    // Initialize yVals with 7 entries, all set to 0
    private fun initializeData() {
        for (i in 0..6) {
            yVals.add(BarEntry(i.toFloat(), 0f))
            oldYVals.add(BarEntry(i.toFloat(), 0f)) // keep previous values
        }
    }

    internal fun clearDiagram() {
        yVals.forEach { it.y = 0f }
    }

    internal fun setDiagramEntry(entry: Database.Entry) {
        val dayIndex = if (isPast7DaysMode) {
            getDayIndexForPast7Days(entry.timestamp)
        } else {
            getDayOfWeekFromTimestamp(entry.timestamp)
        }

        if (dayIndex >= 0 && dayIndex < 7) {
            updateBarEntryForDay(dayIndex, entry.steps.toFloat())
        }
    }

    private fun getDayIndexForPast7Days(timestamp: Long): Int {
        val daysDiff = ((timestamp - past7DaysStartTime) / (24 * 60 * 60 * 1000)).toInt()
        return if (daysDiff in 0..6) daysDiff else -1
    }

    private fun getDayOfWeekFromTimestamp(timestamp: Long): Int {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = AppPreferences.firstDayOfWeek
        cal.timeInMillis = timestamp

        val dayIndex = (cal.get(Calendar.DAY_OF_WEEK) - cal.firstDayOfWeek + 7) % 7
        return dayIndex
    }

    private fun updateBarEntryForDay(dayOfWeek: Int, steps: Float) {
        yVals[dayOfWeek].y = steps
    }

    internal fun setCurrentSteps(currentSteps: Int) {
        val currentDay = if (isPast7DaysMode) {
            6
        } else {
            getDayOfWeekFromTimestamp(System.currentTimeMillis())
        }
        yVals[currentDay].y = currentSteps.toFloat()
    }

    internal fun update() {
        val typeface = ResourcesCompat.getFont(context, R.font.open_sans_regular)
        if (yVals.isEmpty()) return

        val fromVals = oldYVals.map { it.y }
        val toVals = yVals.map { it.y }

        // Pre-calculate colors based on new values
        val finalMin = yVals.minOfOrNull { it.y } ?: 0f
        val finalMax = yVals.maxOfOrNull { it.y } ?: 1f
        val finalColors = yVals.map { getColorForValue(it.y, finalMin, finalMax) }

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200
            interpolator = Easing.EaseInOutCubic
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float

                val interpolatedVals = yVals.mapIndexed { index, entry ->
                    BarEntry(entry.x, fromVals[index] + (toVals[index] - fromVals[index]) * progress)
                }

                // Apply pre-calculated colors (prevent flickering)
                BarDataSet(interpolatedVals, "Step Data").apply {
                    setDrawIcons(false)
                    colors = finalColors
                    setDrawValues(true)
                    valueFormatter = IntValueFormatter()
                    valueTypeface = typeface
                }.let { dataSet ->
                    BarData(dataSet).apply {
                        setValueTextSize(10f)
                        setValueTextColor(Color.GRAY)
                        barWidth = 0.92f
                    }
                }.also { data ->
                    val interpolatedMax = (fromVals.maxOrNull() ?: 1f) +
                            ((toVals.maxOrNull() ?: 1f) - (fromVals.maxOrNull() ?: 1f)) * progress
                    axisLeft.axisMaximum = maxOf(interpolatedMax * 1.05f, 1f)
                    axisLeft.axisMinimum = 0f

                    setData(data)
                    invalidate()
                }
            }
        }.start()

        for (i in 0..6) oldYVals[i].y = yVals[i].y
    }

    private fun getColorForValue(value: Float, min: Float, max: Float): Int {
        val baseColor = ContextCompat.getColor(context, R.color.colorPrimary)

        if (max == min) return baseColor

        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(baseColor, hsl)

        val factor = (value - min) / (max - min)

        val isDarkTheme = when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> {
                val uiMode = resources.configuration.uiMode
                (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }

        // Bar tint:
        // > Light theme: higher = darker
        // > Dark theme: higher = lighter
        val lightnessRange = if (isDarkTheme) 0.3f to 0.75f else 0.75f to 0.3f
        hsl[2] = lightnessRange.first + (lightnessRange.second - lightnessRange.first) * factor

        return ColorUtils.HSLToColor(hsl)
    }

    internal class DayFormatter : ValueFormatter() {
        private var isPast7DaysMode = false
        private var past7DaysStartTime = 0L

        fun setPast7DaysMode(isPast7Days: Boolean, startTime: Long = 0L) {
            isPast7DaysMode = isPast7Days
            past7DaysStartTime = startTime
        }

        override fun getFormattedValue(value: Float): String {
            return if (isPast7DaysMode) {
                // For past 7 days, show actual day names based on the chronological order
                val cal = Calendar.getInstance()
                cal.timeInMillis = past7DaysStartTime + (value.toInt() * 24 * 60 * 60 * 1000L)
                cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()) ?: ""
            } else {
                // Original logic for week view
                val cal = Calendar.getInstance()
                cal.firstDayOfWeek = AppPreferences.firstDayOfWeek
                cal.set(Calendar.DAY_OF_WEEK, ((value.toInt() + AppPreferences.firstDayOfWeek - 1) % 7 + 1))
                cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()) ?: ""
            }
        }
    }


    internal class IntValueFormatter : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            return if (value > 0f) value.toInt().toString() else ""
        }
    }
}
