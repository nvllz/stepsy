/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.nvllz.stepsy.util

import android.content.Context
import android.content.Context.MODE_PRIVATE
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import java.util.*
import androidx.core.content.edit

internal object Util {
    enum class DistanceUnit {
        METRIC, IMPERIAL
    }

    var firstDayOfWeek: Int = Calendar.MONDAY

    fun init(context: Context) {
        migrateDefaultPreferences(context)
        val prefs = context.getSharedPreferences("StepsyPrefs", MODE_PRIVATE)

        height = try {
            prefs.getString("height", "180")?.toInt() ?: 180
        } catch (_: Exception) {
            180
        }

        weight = try {
            prefs.getString("weight", "70")?.toInt() ?: 70
        } catch (_: Exception) {
            70
        }

        distanceUnit = when (prefs.getString("unit_system", "metric")) {
            "imperial" -> DistanceUnit.IMPERIAL
            else -> DistanceUnit.METRIC
        }

        dateFormatString = prefs.getString("date_format", "yyyy-MM-dd") ?: "yyyy-MM-dd"

        firstDayOfWeek = try {
            prefs.getString("first_day_of_week", Calendar.MONDAY.toString())?.toIntOrNull()
                ?: Calendar.MONDAY
        } catch (_: Exception) {
            Calendar.MONDAY
        }
    }

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
    var dateFormatString: String = "yyyy-MM-dd"
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

    private fun migrateDefaultPreferences(context: Context) {
        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val newPrefs = context.getSharedPreferences("StepsyPrefs", MODE_PRIVATE)

        val migrationDoneKey = "prefs_migration_1.4.9"
        if (!newPrefs.getBoolean(migrationDoneKey, false)) {
            newPrefs.edit {
                val allEntries = defaultPrefs.all

                for ((key, value) in allEntries) {
                    when (value) {
                        is String -> putString(key, value)
                        is Int -> putInt(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Float -> putFloat(key, value)
                        is Long -> putLong(key, value)
                        is Set<*> -> {
                            @Suppress("UNCHECKED_CAST")
                            if (value.all { it is String }) {
                                putStringSet(key, value as Set<String>)
                            }
                        }
                    }
                }

                putBoolean(migrationDoneKey, true)
            }
        }
    }

}
