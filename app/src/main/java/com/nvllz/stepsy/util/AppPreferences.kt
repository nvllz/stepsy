/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.nvllz.stepsy.util

import android.content.Context
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.nvllz.stepsy.util.Util.DistanceUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Calendar
import androidx.core.text.HtmlCompat
import androidx.preference.PreferenceManager
import com.nvllz.stepsy.BuildConfig
import com.nvllz.stepsy.R
import com.nvllz.stepsy.util.AppPreferences.PreferenceKeys
import com.nvllz.stepsy.util.AppPreferences.dataStore
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        val APP_VERSION_CODE = intPreferencesKey("app_version_code")
        val ALERTDIALOG_LAST_VERSION_CODE = intPreferencesKey("alertdialog_last")
    }

    lateinit var dataStore: DataStore<Preferences>
        private set

    fun init(context: Context) {
        if (!::dataStore.isInitialized) {
            dataStore = context.appDataStore
        }

        runBlocking {
            updateAppVersion()
            stepDataStoreMigration(context)
        }
    }

    private fun updateAppVersion() {
        val currentVersionCode = BuildConfig.VERSION_CODE

        runBlocking {
            dataStore.edit { it[PreferenceKeys.APP_VERSION_CODE] = currentVersionCode }
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

    @Deprecated(message = "Drop SHAREDPREFERENCES check after 1.4.9")
    @OptIn(DelicateCoroutinesApi::class)
    fun welcomeDialog(context: Context) {
        val dialogTargetVersion = 8 //ver 1.4.9

        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (!sharedPrefs.contains("STEPS")) {
            return
        }

        GlobalScope.launch(Dispatchers.IO) {
            val lastShownVersion = dataStore.data.map {
                it[PreferenceKeys.ALERTDIALOG_LAST_VERSION_CODE] ?: 0
            }.first()

            val currentVersion = BuildConfig.VERSION_CODE

            if (lastShownVersion < dialogTargetVersion && currentVersion == dialogTargetVersion) {
                withContext(Dispatchers.Main) {
                    showDialogAndUpdateVersion(context, dialogTargetVersion)
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun stepDataStoreMigration(context: Context) {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (!sharedPrefs.contains("STEPS")) {
            return
        }

        GlobalScope.launch(Dispatchers.IO) {
            val currentDataStoreSteps = dataStore.data.map {
                it[PreferenceKeys.STEPS] ?: 0
            }.first()

            val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
            val sharedPrefsSteps = sharedPrefs.getInt("STEPS", 0)

            if (sharedPrefsSteps > currentDataStoreSteps) {
                dataStore.edit { preferences ->
                    preferences[PreferenceKeys.STEPS] = sharedPrefsSteps
                }
                sharedPrefs.edit().clear().apply()
            }
        }
    }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun showDialogAndUpdateVersion(context: Context, dialogVersion: Int) {
        val version = BuildConfig.VERSION_NAME
        val html = """
        This update may reset your preferences once again, I'm sorry. Version $version makes use of the DataStore, which I hope will fix all the problems you have been reporting to me over the past few days.
        <br><br>
        Your step data is unaffected by these changes as it is stored in the internal sqlite database. Anyway, I recommend backing it up from time to time.
        <br><br><br>
        Best regards,<br>
        nvllz
    """.trimIndent()

        val textView = TextView(context).apply {
            text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
            movementMethod = LinkMovementMethod.getInstance()
            setPadding(50, 30, 50, 10)
            setLinkTextColor(ContextCompat.getColor(context, R.color.colorAccent))
        }

        AlertDialog.Builder(context)
            .setTitle("Stepsy v$version")
            .setView(textView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                GlobalScope.launch(Dispatchers.IO) {
                    dataStore.edit { preferences ->
                        preferences[PreferenceKeys.ALERTDIALOG_LAST_VERSION_CODE] = dialogVersion
                    }
                }
            }
            .show()
    }
