/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.nvllz.stepsy.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import java.util.*

internal object Util {
    enum class DistanceUnit {
        METRIC, IMPERIAL
    }

    var firstDayOfWeek: Int = Calendar.MONDAY

    fun init(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        distanceUnit = when (prefs.getString("unit_system", "metric")) {
            "imperial" -> DistanceUnit.IMPERIAL
            else -> DistanceUnit.METRIC
        }
        height = prefs.getInt("height_cm", 180)
        weight = prefs.getInt("weight_kg", 70)
        firstDayOfWeek = prefs.getString("first_day_of_week", Calendar.MONDAY.toString())?.toIntOrNull()
            ?: prefs.getInt("first_day_of_week", Calendar.MONDAY)
    }

    /**
     * Get calendar with zeroed time (hours, minutes, seconds)
     *
     * @return Calendar with start of the day
     */
    internal val calendar: Calendar
        get() {
            val calendar = Calendar.getInstance()
            calendar.firstDayOfWeek = firstDayOfWeek
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar
        }

    var height = 180
    var weight = 70

    var distanceUnit: DistanceUnit = DistanceUnit.METRIC

    internal fun stepsToDistance(steps: Number): Double {
        val meters = (steps.toInt() * height * 0.41) / 100000
        return when (distanceUnit) {
            DistanceUnit.METRIC -> meters
            DistanceUnit.IMPERIAL -> meters * 0.621371
        }
    }

    internal fun getDistanceUnitString(): String {
        return when (distanceUnit) {
            DistanceUnit.METRIC -> "km"
            DistanceUnit.IMPERIAL -> "mi"
        }
    }

    internal fun stepsToCalories(steps:Number): Int {
        return (steps.toInt() * weight * 0.0005).toInt()
    }

    internal fun applyTheme(theme: String) {
        when (theme) {
            "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }
}
