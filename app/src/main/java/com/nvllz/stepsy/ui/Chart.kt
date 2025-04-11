/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.nvllz.stepsy.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.nvllz.stepsy.util.Database
import com.nvllz.stepsy.util.Util
import java.util.*

/**
 * The chart in the UI that shows the weekly step distribution with a bar chart.
 */
internal class Chart : BarChart {
    private val yVals = ArrayList<BarEntry>()

    constructor(context: Context) : super(context) {
        initializeChart()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initializeChart()
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        initializeChart()
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
        xAxis.valueFormatter = DayFormatter()
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
        }
    }

    internal fun clearDiagram() {
        yVals.forEach { it.y = 0f }
    }

    internal fun setDiagramEntry(entry: Database.Entry) {
        val dayOfWeek = getDayOfWeekFromTimestamp(entry.timestamp)
        updateBarEntryForDay(dayOfWeek, entry.steps.toFloat())
    }

    private fun getDayOfWeekFromTimestamp(timestamp: Long): Int {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Util.firstDayOfWeek
        cal.timeInMillis = timestamp

        val dayIndex = (cal.get(Calendar.DAY_OF_WEEK) - cal.firstDayOfWeek + 7) % 7
        return dayIndex
    }


    private fun updateBarEntryForDay(dayOfWeek: Int, steps: Float) {
        yVals[dayOfWeek].y = steps
    }

    internal fun setCurrentSteps(currentSteps: Int) {
        val currentDay = getDayOfWeekFromTimestamp(System.currentTimeMillis())
        yVals[currentDay].y = currentSteps.toFloat()
    }

    internal fun update() {
        if (yVals.isEmpty()) return

        val minSteps = yVals.minOfOrNull { it.y } ?: 0f
        val maxSteps = yVals.maxOfOrNull { it.y } ?: 1f  // Avoid division by zero

        val dataSet = BarDataSet(yVals, "Step Data").apply {
            setDrawIcons(false)

            // Generate colors dynamically based on values
            val colors = yVals.map { entry ->
                getColorForValue(entry.y, minSteps, maxSteps)
            }

            setColors(colors) // Apply dynamic colors
            setDrawValues(true)  // Ensure values are drawn above the bars
            valueFormatter = IntValueFormatter() // Custom formatter for integer values
        }

        val data = BarData(dataSet).apply {
            setValueTextSize(10f)
            setValueTextColor(Color.GRAY)
        }

        data.barWidth = 0.9f

        clear()  // Reset chart data
        setData(data)  // Set new data

        animateChart()
    }

    /**
     * Assigns a shade of gray based on the step count.
     * - Highest value → Lightest Gray (`#DCDCDC`)
     * - Lowest value → Darkest Gray (`#333333`)
     */
    private fun getColorForValue(value: Float, min: Float, max: Float): Int {
        if (max == min) return Color.parseColor("#B7B7B7") // Neutral gray if all values are equal

        val factor = (value - min) / (max - min) // Normalize value to 0-1 range
        val grayValue = (183 + (63 - 183) * (1 - factor)).toInt() // Linear interpolation between light & dark gray
        return Color.rgb(grayValue, grayValue, grayValue)
    }

    private fun animateChart() {
        animateXY(2000, 2000)  // Smooth animation for the data update
        invalidate()
    }

    internal class DayFormatter : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            val cal = Calendar.getInstance()
            cal.firstDayOfWeek = Util.firstDayOfWeek
            cal.set(Calendar.DAY_OF_WEEK, ((value.toInt() + Util.firstDayOfWeek - 1) % 7 + 1))
            return cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()) ?: ""
        }
    }


    // Custom value formatter to display integer values over the bars
    internal class IntValueFormatter : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            return value.toInt().toString() // Convert the float value to an integer and return as string
        }
    }
}
