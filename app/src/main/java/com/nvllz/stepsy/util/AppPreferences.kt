/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.nvllz.stepsy.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.nvllz.stepsy.util.Util.DistanceUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import java.util.Calendar
import androidx.core.content.edit

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

object AppPreferences {
    object PreferenceKeys {
        val STEPS = intPreferencesKey("STEPS")
        val DATE = longPreferencesKey("DATE")
        val THEME = stringPreferencesKey("theme")
        val HEIGHT = stringPreferencesKey("height")
        val WEIGHT = stringPreferencesKey("weight")
        val UNIT_SYSTEM = stringPreferencesKey("unit_system")
        val DATE_FORMAT = stringPreferencesKey("date_format")
        val FIRST_DAY_OF_WEEK = stringPreferencesKey("first_day_of_week")
        val SHAREDPREFS_MIGRATION_COMPLETED = booleanPreferencesKey("sharedprefs_migration_completed")
        val APP_VERSION_CODE = intPreferencesKey("app_version_code")
    }

    lateinit var dataStore: DataStore<Preferences>
        private set

    fun init(context: Context) {
        if (!::dataStore.isInitialized) {
            dataStore = context.appDataStore
        }

        runBlocking {
            sharedPrefsToDataStoreMigrationCheck(context)
            updateAppVersion(context)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun sharedPrefsToDataStoreMigrationCheck(context: Context) {
        GlobalScope.launch(Dispatchers.IO) {
            val migrationCompleted = dataStore.data.map {
                it[PreferenceKeys.SHAREDPREFS_MIGRATION_COMPLETED] == true
            }.first()

            if (!migrationCompleted) {
                migrateFromSharedPreferences(context)
                Log.d("StepsyMigration", "${PreferenceKeys.SHAREDPREFS_MIGRATION_COMPLETED}")

                // Mark migration as completed
                dataStore.edit { preferences ->
                    preferences[PreferenceKeys.SHAREDPREFS_MIGRATION_COMPLETED] = true
                }
            }
        }
    }

    private suspend fun updateAppVersion(context: Context) {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }

            val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }

            dataStore.edit { preferences ->
                preferences[PreferenceKeys.APP_VERSION_CODE] = currentVersionCode
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Steps
    fun stepsFlow(): Flow<Int> = dataStore.data.map { it[PreferenceKeys.STEPS] ?: 0 }

    var steps: Int
        get() = runBlocking { stepsFlow().first() }
        set(value) = runBlocking {
            dataStore.edit { it[PreferenceKeys.STEPS] = value }
        }

    // Date
    fun dateFlow(): Flow<Long> = dataStore.data.map { it[PreferenceKeys.DATE] ?: Util.calendar.timeInMillis }

    var date: Long
        get() = runBlocking { dateFlow().first() }
        set(value) = runBlocking {
            dataStore.edit { it[PreferenceKeys.DATE] = value }
        }

    // Theme
    fun themeFlow(): Flow<String> = dataStore.data.map { it[PreferenceKeys.THEME] ?: "system" }

    var theme: String
        get() = runBlocking { themeFlow().first() }
        set(value) = runBlocking {
            dataStore.edit { it[PreferenceKeys.THEME] = value }
        }

    // Height
    fun heightFlow(): Flow<Int> = dataStore.data.map {
        it[PreferenceKeys.HEIGHT]?.toIntOrNull() ?: 180
    }

    var height: Int
        get() = runBlocking { heightFlow().first() }
        set(value) = runBlocking {
            dataStore.edit { it[PreferenceKeys.HEIGHT] = value.toString() }
        }

    // Weight
    fun weightFlow(): Flow<Int> = dataStore.data.map {
        it[PreferenceKeys.WEIGHT]?.toIntOrNull() ?: 70
    }

    var weight: Int
        get() = runBlocking { weightFlow().first() }
        set(value) = runBlocking {
            dataStore.edit { it[PreferenceKeys.WEIGHT] = value.toString() }
        }

    // Distance Unit
    fun distanceUnitFlow(): Flow<DistanceUnit> = dataStore.data.map {
        when (it[PreferenceKeys.UNIT_SYSTEM]) {
            "imperial" -> DistanceUnit.IMPERIAL
            else -> DistanceUnit.METRIC
        }
    }

    var distanceUnit: DistanceUnit
        get() = runBlocking { distanceUnitFlow().first() }
        set(value) = runBlocking {
            dataStore.edit {
                it[PreferenceKeys.UNIT_SYSTEM] = if (value == DistanceUnit.IMPERIAL) "imperial" else "metric"
            }
        }

    // Date Format
    fun dateFormatStringFlow(): Flow<String> =
        dataStore.data.map { it[PreferenceKeys.DATE_FORMAT] ?: "yyyy-MM-dd" }

    var dateFormatString: String
        get() = runBlocking { dateFormatStringFlow().first() }
        set(value) = runBlocking {
            dataStore.edit { it[PreferenceKeys.DATE_FORMAT] = value }
        }

    // First Day of Week
    fun firstDayOfWeekFlow(): Flow<Int> = dataStore.data.map {
        it[PreferenceKeys.FIRST_DAY_OF_WEEK]?.toIntOrNull() ?: Calendar.MONDAY
    }

    var firstDayOfWeek: Int
        get() = runBlocking { firstDayOfWeekFlow().first() }
        set(value) = runBlocking {
            dataStore.edit { it[PreferenceKeys.FIRST_DAY_OF_WEEK] = value.toString() }
        }

    // Migration from SharedPreferences
    @Deprecated(
        message = "To be removed in 1.5.0+",
        level = DeprecationLevel.WARNING
    )
    suspend fun migrateFromSharedPreferences(context: Context) {
        val sharedPrefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)

        dataStore.edit { preferences ->
            if (sharedPrefs.contains("STEPS")) {
                preferences[PreferenceKeys.STEPS] = sharedPrefs.getInt("STEPS", 0)
            }

            if (sharedPrefs.contains("DATE")) {
                preferences[PreferenceKeys.DATE] = sharedPrefs.getLong("DATE", System.currentTimeMillis())
            }

            if (sharedPrefs.contains("theme")) {
                preferences[PreferenceKeys.THEME] = sharedPrefs.getString("theme", "system") ?: "system"
            }

            if (sharedPrefs.contains("height")) {
                preferences[PreferenceKeys.HEIGHT] = sharedPrefs.getString("height", "180") ?: "180"
            }

            if (sharedPrefs.contains("weight")) {
                preferences[PreferenceKeys.WEIGHT] = sharedPrefs.getString("weight", "70") ?: "70"
            }

            if (sharedPrefs.contains("unit_system")) {
                preferences[PreferenceKeys.UNIT_SYSTEM] = sharedPrefs.getString("unit_system", "metric") ?: "metric"
            }

            if (sharedPrefs.contains("date_format")) {
                preferences[PreferenceKeys.DATE_FORMAT] = sharedPrefs.getString("date_format", "yyyy-MM-dd") ?: "yyyy-MM-dd"
            }

            if (sharedPrefs.contains("first_day_of_week")) {
                preferences[PreferenceKeys.FIRST_DAY_OF_WEEK] =
                    sharedPrefs.getString("first_day_of_week", Calendar.MONDAY.toString()) ?: Calendar.MONDAY.toString()
            }
        }

         sharedPrefs.edit { clear() }
    }
}
