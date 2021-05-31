/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.motionmate.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.tiefensuche.motionmate.util.Database
import java.util.*

/**
 * The chart in the UI that shows the weekly step distribution with a bar chart.
 */
internal class Chart : BarChart {
    private val yVals = ArrayList<BarEntry>()

    constructor(context: Context) : super(context) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        initialize()
    }

    private fun initialize() {
        setViewPortOffsets(0f, 0f, 0f, 0f)

        // no description text
        description.isEnabled = false

        setDrawBarShadow(false)
        setDrawValueAboveBar(true)
        setTouchEnabled(false)

        val x = xAxis
        x.textColor = Color.WHITE
        x.position = XAxis.XAxisPosition.BOTTOM_INSIDE
        x.axisLineColor = Color.WHITE
        x.granularity = 1f
        x.setDrawGridLines(false)
        x.valueFormatter = DayFormatter()

        axisLeft.isEnabled = false
        axisRight.isEnabled = false

        legend.isEnabled = false

        // initialize seven days with zero
        for (i in 0..6) {
            yVals.add(BarEntry(i.toFloat(), 0f))
        }
    }

    internal fun clearDiagram() {
        for (i in yVals.indices) {
            yVals[i].y = 0f
        }
    }

    internal fun setDiagramEntry(entry: Database.Entry) {
        val cal = Calendar.getInstance()
        cal.timeInMillis = entry.timestamp

        // update the day the entry belongs to
        for (j in yVals.indices) {
            val barEntry = yVals[j]
            if (barEntry.x.toInt() == (cal.get(Calendar.DAY_OF_WEEK) - cal.firstDayOfWeek + 7) % 7) {
                barEntry.y = entry.steps.toFloat()
                break
            }
        }
    }

    internal fun setCurrentSteps(currentSteps: Int) {
        val cal = Calendar.getInstance()
        val currentDay = (cal.get(Calendar.DAY_OF_WEEK) - cal.firstDayOfWeek + 7) % 7
        yVals[currentDay].y = currentSteps.toFloat()
    }

    internal fun update() {
        val set = BarDataSet(yVals, "StepData")
        set.setDrawIcons(false)
        set.setColors(*ColorTemplate.MATERIAL_COLORS)

        val data = BarData(set)
        data.setValueTextSize(10f)
        data.setValueTextColor(Color.WHITE)
        data.barWidth = 0.9f

        clear()
        setData(data)

        animateXY(2000, 2000)
        invalidate()
    }

    internal class DayFormatter : ValueFormatter() {
        override fun getFormattedValue(value: Float, axis: AxisBase?): String? {
            val cal = Calendar.getInstance()
            cal.set(Calendar.DAY_OF_WEEK, (value + cal.firstDayOfWeek).toInt() % 7)
            return cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault())
        }
    }
}
