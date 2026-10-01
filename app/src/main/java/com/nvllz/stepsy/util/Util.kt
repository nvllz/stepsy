package com.nvllz.stepsy.util

import androidx.appcompat.app.AppCompatDelegate
import java.util.*

object Util {
    enum class UnitSystem {
        METRIC, IMPERIAL
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
        15_000_000L, 20_000_000L
    )

    internal fun milestoneBadge(milestoneSteps: Int): String = when {
        milestoneSteps >= 20_000_000 -> "🏁"
        milestoneSteps >= 15_000_000 -> "♾️"
        milestoneSteps >= 12_500_000 -> "🪬"
        milestoneSteps >= 10_000_000 -> "👑"
        milestoneSteps >=  9_000_000 -> "🦄"
        milestoneSteps >=  8_000_000 -> "🐉"
        milestoneSteps >=  7_000_000 -> "💫"
        milestoneSteps >=  6_000_000 -> "🏆"
        milestoneSteps >=  5_000_000 -> "💎"
        milestoneSteps >=  4_000_000 -> "🪐"
        milestoneSteps >=  3_000_000 -> "🚀"
        milestoneSteps >=  2_000_000 -> "🥇"
        milestoneSteps >=  1_500_000 -> "⚡"
        milestoneSteps >=  1_000_000 -> "🗿"
        milestoneSteps >=    750_000 -> "⛳"
        milestoneSteps >=    500_000 -> "🌟"
        milestoneSteps >=    100_000 -> "🔥"
        milestoneSteps >=     50_000 -> "💪"
        milestoneSteps >=     10_000 -> "🎯"
        else                         -> "🎯"
    }

    internal fun todayDateString(): String {
        val cal = Calendar.getInstance()
        return "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

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
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    internal fun calendarToDateString(cal: Calendar): String {
        return "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    internal fun distanceUnit(): String = if (AppPreferences.unitSystem == UnitSystem.IMPERIAL) "mi" else "km"
    internal fun weightUnit(): String   = if (AppPreferences.unitSystem == UnitSystem.IMPERIAL) "lbs" else "kg"
    internal fun heightUnit(): String   = if (AppPreferences.unitSystem == UnitSystem.IMPERIAL) "ft/in" else "cm"
    internal fun stepLengthUnit(): String = if (AppPreferences.unitSystem == UnitSystem.IMPERIAL) "in" else "cm"

    fun stepsToDistance(steps: Number): Float {
        val meters = (steps.toInt() * AppPreferences.stepLength) / 100000
        return when (AppPreferences.unitSystem) {
            UnitSystem.METRIC   -> meters
            UnitSystem.IMPERIAL -> meters * 0.621371f
        }
    }

    internal fun stepsToCalories(steps: Number): Int {
        return (steps.toInt() * AppPreferences.weight * 0.0005).toInt()
    }

    internal fun applyTheme(theme: String) {
        when (theme) {
            "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            "light"  -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark"   -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }
}