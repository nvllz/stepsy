package com.nvllz.stepsy.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.nvllz.stepsy.R
import java.text.NumberFormat
import java.util.*

object Util {
    enum class UnitSystem {
        METRIC, IMPERIAL
    }

    internal const val CM_PER_INCH = 2.54f
    internal const val LB_PER_KG = 2.20462f
    internal const val MILES_PER_KM = 0.621371f

    private lateinit var appContext: Context

    internal fun init(context: Context) {
        if (!::appContext.isInitialized) appContext = context.applicationContext
    }

    internal val calendar: Calendar
        get() {
            val calendar = Calendar.getInstance()
            calendar.firstDayOfWeek = AppPreferences.firstDayOfWeek
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar
        }

    internal val MILESTONES = listOf(
        10_000L, 50_000L, 100_000L, 500_000L, 750_000L,
        1_000_000L, 1_500_000L, 2_000_000L, 3_000_000L,
        4_000_000L, 5_000_000L, 6_000_000L, 7_000_000L,
        8_000_000L, 9_000_000L, 10_000_000L, 12_500_000L,
        15_000_000L, 17_500_000L, 20_000_000L
    )

    internal val GOAL_PRESETS = listOf(0, 5_000, 7_000, 10_000)

    internal fun formatSteps(steps: Int): String = if (steps >= 10_000) {
        NumberFormat.getIntegerInstance().format(steps)
    } else {
        steps.toString()
    }

    internal fun formatShortSteps(steps: Long, context: Context): String = when {
        steps >= 1_000_000L -> {
            val millions = steps / 1_000_000.0
            if (millions == millions.toInt().toDouble()) context.getString(R.string.milestone_short_millions, millions.toInt())
            else context.getString(R.string.milestone_short_millions_decimal, millions)
        }
        steps >= 1_000L -> context.getString(R.string.milestone_short_thousands, (steps / 1_000).toInt())
        else -> NumberFormat.getIntegerInstance().format(steps)
    }

    internal fun estimatedStepLengthCm(heightCm: Int): Float = heightCm * 0.415f

    internal fun milestoneBadge(milestoneSteps: Int): String = when {
        milestoneSteps >= 20_000_000 -> "♾️"
        milestoneSteps >= 17_500_000 -> "🛸"
        milestoneSteps >= 15_000_000 -> "👽"
        milestoneSteps >= 12_500_000L -> "👑"
        milestoneSteps >=  10_000_000L -> "🦄"
        milestoneSteps >=  9_000_000L -> "💀"
        milestoneSteps >=  8_000_000L -> "🐉"
        milestoneSteps >=  7_000_000L -> "💫"
        milestoneSteps >=  6_000_000 -> "🏆"
        milestoneSteps >=  5_000_000 -> "💎"
        milestoneSteps >=  4_000_000 -> "👻"
        milestoneSteps >=  3_000_000 -> "🚀"
        milestoneSteps >=  2_000_000 -> "🥇"
        milestoneSteps >=  1_500_000 -> "🧭"
        milestoneSteps >=  1_000_000 -> "🗿"
        milestoneSteps >=    750_000 -> "🍀"
        milestoneSteps >=    500_000 -> "🌟"
        milestoneSteps >=    100_000 -> "🔥"
        milestoneSteps >=     50_000 -> "💪"
        milestoneSteps >=     10_000 -> "🎯"
        else                         -> "🎯"
    }

    internal fun todayDateString(): String {
        val cal = Calendar.getInstance()
        return "%04d-%02d-%02d".format(
            Locale.ROOT,
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    internal fun logicalToday(): java.time.LocalDate = java.time.LocalDate.now()

    internal fun dateStringToCalendarMillis(date: String): Long {
        return try {
            val parts = date.split("-")
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, parts[0].toInt())
                set(Calendar.MONTH, parts[1].toInt() - 1)
                set(Calendar.DAY_OF_MONTH, parts[2].toInt())
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            cal.timeInMillis
        } catch (_: Exception) {
            0L
        }
    }

    internal fun millisToDateString(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return "%04d-%02d-%02d".format(
            Locale.ROOT,
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    internal fun calendarToDateString(cal: Calendar): String {
        return "%04d-%02d-%02d".format(
            Locale.ROOT,
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    internal fun distanceUnit(): String =
        if (AppPreferences.unitSystem == UnitSystem.IMPERIAL) appContext.getString(R.string.unit_mi)
        else appContext.getString(R.string.unit_km)

    internal fun distanceFor(steps: Int, stepLengthCm: Float, system: UnitSystem): Float {
        val km = (steps * stepLengthCm) / 100000
        return when (system) {
            UnitSystem.METRIC   -> km
            UnitSystem.IMPERIAL -> km * MILES_PER_KM
        }
    }

    internal fun caloriesFor(steps: Int, weightKg: Int): Int = (steps * weightKg * 0.0005).toInt()

    fun stepsToDistance(steps: Number): Float =
        distanceFor(steps.toInt(), AppPreferences.stepLength, AppPreferences.unitSystem)

    internal fun stepsToCalories(steps: Number): Int =
        caloriesFor(steps.toInt(), AppPreferences.weight)

    internal fun applyTheme(theme: String) {
        when (theme) {
            "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            "light"  -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark"   -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }
}