/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.nvllz.stepsy.util

import androidx.appcompat.app.AppCompatDelegate
import java.util.*

object Util {
    enum class DistanceUnit {
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

    fun stepsToDistance(steps: Number): Float {
        val meters = (steps.toInt() * AppPreferences.stepLength) / 100000
        return when (AppPreferences.distanceUnit) {
            DistanceUnit.METRIC -> meters
            DistanceUnit.IMPERIAL -> meters * 0.621371f
        }
    }

    internal fun getDistanceUnitString(): String {
        return when (AppPreferences.distanceUnit) {
            DistanceUnit.METRIC -> "km"
            DistanceUnit.IMPERIAL -> "mi"
        }
    }

    internal fun stepsToCalories(steps: Number): Int {
        return (steps.toInt() * AppPreferences.weight * 0.0005).toInt()
    }

    internal fun applyTheme(theme: String) {
        when (theme) {
            "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }
}