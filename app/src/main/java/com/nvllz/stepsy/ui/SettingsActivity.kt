/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.nvllz.stepsy.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.nvllz.stepsy.R
import com.nvllz.stepsy.service.MotionService
import com.nvllz.stepsy.service.MotionService.Companion.KEY_DATE
import com.nvllz.stepsy.service.MotionService.Companion.KEY_STEPS
import com.nvllz.stepsy.util.Database
import com.nvllz.stepsy.util.Util
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.Exception
import java.util.Date
import java.util.Locale
import androidx.core.graphics.drawable.toDrawable
import androidx.preference.EditTextPreference

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        Util.init(this)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        val color = ContextCompat.getColor(this, R.color.colorBackground)
        supportActionBar?.setBackgroundDrawable(color.toDrawable())

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

            val currentLocales = AppCompatDelegate.getApplicationLocales()
            val currentLanguage = when {
                currentLocales.isEmpty -> "system"
                else -> currentLocales[0]?.language ?: "system"
            }

            findPreference<EditTextPreference>("height")?.setOnPreferenceChangeListener { _, newValue ->
                try {
                    val height = newValue.toString()
                    if (height.toInt() <= 250) {
                        Util.height = height.toInt()
                        prefs.edit {
                            putString("height", height)
                        }
                        true
                    } else {
                        false
                    }
                } catch (e: NumberFormatException) {
                    Toast.makeText(context, R.string.valid_number_toast, Toast.LENGTH_SHORT).show()
                    false
                }
            }

            findPreference<EditTextPreference>("weight")?.setOnPreferenceChangeListener { _, newValue ->
                try {
                    val weight = newValue.toString()
                    if (weight.toInt() <= 500) {
                        Util.weight = weight.toInt()
                        prefs.edit {
                            putString("weight", weight)
                        }
                        true
                    } else {
                        false
                    }
                } catch (e: NumberFormatException) {
                    Toast.makeText(context, R.string.valid_number_toast, Toast.LENGTH_SHORT).show()
                    false
                }
            }

            findPreference<ListPreference>("unit_system")?.setOnPreferenceChangeListener { _, newValue ->
                Util.distanceUnit = when (newValue.toString()) {
                    "imperial" -> Util.DistanceUnit.IMPERIAL
                    else -> Util.DistanceUnit.METRIC
                }
                prefs.edit {
                    putString("unit_system", newValue.toString())
                }
                true
            }

            findPreference<ListPreference>("date_format")?.setOnPreferenceChangeListener { _, newValue ->
                val dateFormat = newValue.toString()
                Util.dateFormatString = dateFormat
                prefs.edit {
                    putString("date_format", dateFormat)
                }
                true
            }

            findPreference<ListPreference>("first_day_of_week")?.setOnPreferenceChangeListener { _, newValue ->
                val value = newValue.toString().toInt()
                Util.firstDayOfWeek = value
                prefs.edit {
                    putString("first_day_of_week", value.toString())
                }

                // restart the activity to apply changes
                val intent = Intent(requireContext(), MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                activity?.finish()
                true
            }

            findPreference<ListPreference>("language")?.let { languagePref ->
                languagePref.value = currentLanguage

                languagePref.setOnPreferenceChangeListener { _, newValue ->
                    val localeCode = newValue.toString()

                    val newLocale = when (localeCode) {
                        "system" -> LocaleListCompat.getEmptyLocaleList() // Use system's default language
                        else -> LocaleListCompat.create(Locale(localeCode)) // Create a new locale from the selected language
                    }

                    // Apply the new locale
                    AppCompatDelegate.setApplicationLocales(newLocale)

                    // Restart the app to apply the language change
                    restartApp()

                    true
                }
            }


            findPreference<ListPreference>("theme")?.setOnPreferenceChangeListener { _, newValue ->
                Util.applyTheme(newValue.toString())
                true
            }

            findPreference<Preference>("import")?.setOnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/*"
                }
                startActivityForResult(intent, 1)
                true
            }

            findPreference<Preference>("export")?.setOnPreferenceClickListener {
                val dateFormat = java.text.SimpleDateFormat("yyyyMMdd-HHmmSS", Locale.getDefault())
                val currentDate = dateFormat.format(Date())
                val fileName = "${currentDate}_stepsy.csv"

                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/*"
                    putExtra(Intent.EXTRA_TITLE, fileName)
                }
                startActivityForResult(intent, 2)
                true
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
            super.onActivityResult(requestCode, resultCode, resultData)
            if (resultCode != RESULT_OK)
                return
            resultData?.data?.also { uri ->
                when (requestCode) {
                    1 -> import(uri)
                    2 -> export(uri)
                }
            }
        }

        private fun import(uri: Uri) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val db = Database.getInstance(requireContext())
            val today = Util.calendar.timeInMillis
            var todaySteps = 0
            var entries = 0
            var failed = 0

            try {
                requireContext().contentResolver.openFileDescriptor(uri, "r")?.use {
                    FileInputStream(it.fileDescriptor).bufferedReader().use { reader ->
                        for (line in reader.readLines()) {
                            entries += 1
                            try {
                                val split = line.split(",")
                                val timestamp = split[0].toLong()
                                val steps = split[1].toInt()

                                if (DateUtils.isToday(timestamp)) {
                                    todaySteps = steps
                                }
                                db.addEntry(timestamp, steps)
                            } catch (ex: Exception) {
                                println("Cannot import entry, ${ex.message}")
                                failed += 1
                            }
                        }
                    }

                    if (todaySteps > 0) {
                        prefs.edit {
                            putInt(KEY_STEPS, todaySteps)
                            putLong(KEY_DATE, today)
                        }

                        val intent = Intent(requireContext(), MotionService::class.java).apply {
                            putExtra("FORCE_UPDATE", true)
                            putExtra(KEY_STEPS, todaySteps)
                            putExtra(KEY_DATE, today)
                        }
                        requireContext().startService(intent)
                    }

                    Toast.makeText(
                        context,
                        "Imported ${entries - failed} entries ($failed failed). " +
                                if (todaySteps > 0) "Today's steps set to $todaySteps." else "",
                        Toast.LENGTH_LONG
                    ).show()
                    restartApp()
                }
            } catch (ex: Exception) {
                println("Cannot open file, ${ex.message}")
                Toast.makeText(context, "Cannot open file", Toast.LENGTH_LONG).show()
            }
        }

        private fun export(uri: Uri) {
            val db = Database.getInstance(requireContext())

            try {
                requireContext().contentResolver.openFileDescriptor(uri, "w")?.use {
                    FileOutputStream(it.fileDescriptor).bufferedWriter().use {
                        var entries = 0
                        for (entry in db.getEntries(db.firstEntry, db.lastEntry)) {
                            it.write("${entry.timestamp},${entry.steps}\r\n")
                            entries += 1
                        }
                        Toast.makeText(context, "Exported $entries entries.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (ex: Exception) {
                println("Can not open file, ${ex.message}")
                Toast.makeText(context, "Can not open file", Toast.LENGTH_LONG).show()
            }
        }

        private fun restartApp() {
            val intent = Intent(requireContext(), MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
            Runtime.getRuntime().exit(0)
        }
    }
}