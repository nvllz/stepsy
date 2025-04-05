/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.nvllz.stepsy.util

import androidx.appcompat.app.AppCompatDelegate
import java.util.*

internal object Util {

    /**
     * Get calendar with zeroed time (hours, minutes, seconds)
     *
     * @return Calendar with start of the day
     */
    internal val calendar: Calendar
        get() {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar
        }

    var height = 180
    var weight = 70

    internal fun stepsToMeters(steps: Number): Double {
        return (steps.toInt() * height * 0.41) / 100000
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
