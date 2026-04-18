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
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.nvllz.stepsy.R
import com.nvllz.stepsy.util.AppPreferences
import com.nvllz.stepsy.util.Database
import java.util.*

internal class Chart : BarChart {
    private val yVals = ArrayList<BarEntry>()
    private val oldYVals = ArrayList<BarEntry>()
    private var isPast7DaysMode = false
    private var past7DaysStartDate = ""
    private var weekStartDate = ""
    private val dayFormatter = DayFormatter()

    constructor(context: Context) : super(context) { initializeChart() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initializeChart() }
    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) { initializeChart() }

    internal fun setPast7DaysMode(isPast7Days: Boolean, startTime: Long = 0L) {
        isPast7DaysMode = isPast7Days
        if (isPast7Days) {
            past7DaysStartDate = calToDateString(Calendar.getInstance().apply { timeInMillis = startTime })
        } else {
            weekStartDate = calToDateString(Calendar.getInstance().apply { timeInMillis = startTime })
        }
        dayFormatter.setPast7DaysMode(isPast7Days, startTime)
    }

    private fun initializeChart() {
        description.isEnabled = false
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

    private fun initializeData() {
        for (i in 0..6) {
            yVals.add(BarEntry(i.toFloat(), 0f))
            oldYVals.add(BarEntry(i.toFloat(), 0f))
        }
    }

    internal fun clearDiagram() {
        yVals.forEach { it.y = 0f }
    }

    internal fun setDiagramEntry(entry: Database.Entry) {
        val dayIndex = if (isPast7DaysMode) {
            daysBetweenDates(past7DaysStartDate, entry.date)
        } else {
            daysBetweenDates(weekStartDate, entry.date)
        }
        if (dayIndex in 0..6) {
            yVals[dayIndex].y = entry.steps.toFloat()
        }
    }

    private fun daysBetweenDates(from: String, to: String): Int {
        if (from.isEmpty() || to.isEmpty()) return -1
        return try {
            val f = parseDateString(from)
            val t = parseDateString(to)
            ((t - f) / 86_400_000L).toInt()
        } catch (_: Exception) {
            -1
        }
    }

    private fun parseDateString(date: String): Long {
        val parts = date.split("-")
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun calToDateString(cal: Calendar): String {
        return "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    internal fun setCurrentSteps(currentSteps: Int) {
        val today = calToDateString(Calendar.getInstance())
        val dayIndex = if (isPast7DaysMode) {
            daysBetweenDates(past7DaysStartDate, today)
        } else {
            daysBetweenDates(weekStartDate, today)
        }
        if (dayIndex in 0..6) {
            yVals[dayIndex].y = currentSteps.toFloat()
        }
    }

    internal fun update() {
        val typeface = ResourcesCompat.getFont(context, R.font.open_sans_regular)
        if (yVals.isEmpty()) return

        val fromVals = oldYVals.map { it.y }
        val toVals = yVals.map { it.y }

        val finalMin = yVals.minOfOrNull { it.y } ?: 0f
        val finalMax = yVals.maxOfOrNull { it.y } ?: 1f
        val finalColors = yVals.map { getColorForValue(it.y, finalMin, finalMax) }

        val dailyGoal = if (AppPreferences.dailyGoalTarget > 0 && AppPreferences.dailyGoalChartLine) {
            AppPreferences.dailyGoalTarget.toFloat()
        } else {
            0f
        }

        updateGoalLine(dailyGoal)

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200
            interpolator = Easing.EaseInOutCubic
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float

                val interpolatedVals = yVals.mapIndexed { index, entry ->
                    BarEntry(entry.x, fromVals[index] + (toVals[index] - fromVals[index]) * progress)
                }

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
                    val fromMax = fromVals.maxOrNull() ?: 1f
                    val toMax = toVals.maxOrNull() ?: 1f
                    val interpolatedDataMax = fromMax + (toMax - fromMax) * progress

                    val axisMax = if (dailyGoal > 0f) {
                        maxOf(interpolatedDataMax, (dailyGoal * 1.1f)) * 1.05f
                    } else {
                        maxOf(interpolatedDataMax * 1.05f, 1f)
                    }
                    axisLeft.axisMaximum = maxOf(axisMax, 1f)
                    axisLeft.axisMinimum = 0f

                    setData(data)
                    invalidate()
                }
            }
        }.start()

        for (i in 0..6) oldYVals[i].y = yVals[i].y
    }

    private fun updateGoalLine(dailyGoal: Float) {
        axisLeft.removeAllLimitLines()

        if (dailyGoal <= 0f) return

        val accentColor = ContextCompat.getColor(context, R.color.colorAccent)

        val subtleColor = ColorUtils.setAlphaComponent(accentColor, 100)

        val limitLine = LimitLine(dailyGoal).apply {
            lineWidth = 1f
            lineColor = subtleColor
            enableDashedLine(24f, 12f, 0f)
            textSize = 0f
        }

        axisLeft.setDrawLimitLinesBehindData(true)
        axisLeft.isEnabled = true
        axisLeft.setDrawLabels(false)
        axisLeft.setDrawGridLines(false)
        axisLeft.setDrawAxisLine(false)
        axisLeft.addLimitLine(limitLine)
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
                (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }

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
                val cal = Calendar.getInstance()
                cal.timeInMillis = past7DaysStartTime
                cal.add(Calendar.DAY_OF_YEAR, value.toInt())
                cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()) ?: ""
            } else {
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
