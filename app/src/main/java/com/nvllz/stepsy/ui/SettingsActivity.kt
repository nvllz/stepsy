/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.nvllz.stepsy.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
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
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts


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

        private lateinit var importLauncher: ActivityResultLauncher<Intent>
        private lateinit var exportLauncher: ActivityResultLauncher<Intent>

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            val prefs = requireContext().getSharedPreferences("StepsyPrefs", MODE_PRIVATE)

            importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.data?.let { uri -> import(uri) }
                }
            }

            exportLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.data?.let { uri -> export(uri) }
                }
            }

            val currentLocales = AppCompatDelegate.getApplicationLocales()
            val currentLanguage = when {
                currentLocales.isEmpty -> "system"
                else -> currentLocales[0]?.language ?: "system"
            }

            findPreference<EditTextPreference>("height")?.setOnPreferenceChangeListener { _, newValue ->
                try {
                    val height = newValue.toString().toInt()
                    if (height in 1..250) {
                        requireContext().getSharedPreferences("StepsyPrefs", MODE_PRIVATE)
                            .edit { putString("height", height.toString()) }
                        Util.height = height
                        true
                    } else {
                        Toast.makeText(context, R.string.enter_valid_number, Toast.LENGTH_SHORT).show()
                        false
                    }
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.enter_valid_number, Toast.LENGTH_SHORT).show()
                    false
                }
            }

            findPreference<EditTextPreference>("weight")?.setOnPreferenceChangeListener { _, newValue ->
                try {
                    val weight = newValue.toString().toInt()
                    if (weight in 1..500) {
                        requireContext().getSharedPreferences("StepsyPrefs", MODE_PRIVATE)
                            .edit { putString("weight", weight.toString()) }
                        Util.weight = weight
                        true
                    } else {
                        Toast.makeText(context, R.string.enter_valid_number, Toast.LENGTH_SHORT).show()
                        false
                    }
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.enter_valid_number, Toast.LENGTH_SHORT).show()
                    false
                }
            }

            findPreference<ListPreference>("unit_system")?.setOnPreferenceChangeListener { _, newValue ->
                Util.distanceUnit = if (newValue.toString() == "imperial") Util.DistanceUnit.IMPERIAL else Util.DistanceUnit.METRIC
                prefs.edit { putString("unit_system", newValue.toString()) }
                true
            }

            findPreference<ListPreference>("date_format")?.setOnPreferenceChangeListener { _, newValue ->
                val dateFormat = newValue.toString()
                Util.dateFormatString = dateFormat
                prefs.edit { putString("date_format", dateFormat) }
                true
            }

            findPreference<ListPreference>("first_day_of_week")?.setOnPreferenceChangeListener { _, newValue ->
                val value = newValue.toString().toInt()
                Util.firstDayOfWeek = value
                prefs.edit { putString("first_day_of_week", value.toString()) }

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
                    val newLocale = if (localeCode == "system") {
                        LocaleListCompat.getEmptyLocaleList()
                    } else {
                        LocaleListCompat.create(Locale(localeCode))
                    }
                    AppCompatDelegate.setApplicationLocales(newLocale)
                    restartApp()
                    true
                }
            }

            findPreference<ListPreference>("theme")?.setOnPreferenceChangeListener { _, newValue ->
                val theme = newValue.toString()
                requireContext().getSharedPreferences("StepsyPrefs", MODE_PRIVATE).edit {
                    putString("theme", theme)
                    apply()
                }
                Util.applyTheme(theme)
                activity?.recreate()
                true
            }

            findPreference<Preference>("import")?.setOnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/*"
                }
                importLauncher.launch(intent)
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
                exportLauncher.launch(intent)
                true
            }
        }

        private fun import(uri: Uri) {
            val prefs = requireContext().getSharedPreferences("StepsyPrefs", MODE_PRIVATE)
            val db = Database.getInstance(requireContext())
            val today = Util.calendar.timeInMillis
            var todaySteps = 0
            var entries = 0
            var failed = 0

            try {
                requireContext().contentResolver.openFileDescriptor(uri, "r")?.use {
                    FileInputStream(it.fileDescriptor).bufferedReader().use { reader ->
                        for (line in reader.readLines()) {
                            entries++
                            try {
                                val split = line.split(",")
                                val timestamp = split[0].toLong()
                                val steps = split[1].toInt()
                                if (DateUtils.isToday(timestamp)) {
                                    todaySteps = steps
                                }
                                db.addEntry(timestamp, steps)
                            } catch (ex: Exception) {
                                Log.e("SettingsFragment", "Cannot import entry", ex)
                                failed++
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

                    val successCount = entries - failed
                    val todaySetText = if (todaySteps > 0) {
                        getString(R.string.today_steps_set, todaySteps)
                    } else {
                        ""
                    }

                    val resultText = getString(R.string.import_result, successCount, failed, todaySetText)

                    Toast.makeText(context, resultText, Toast.LENGTH_LONG).show()

                    restartApp()
                }
            } catch (ex: Exception) {
                Log.e("SettingsFragment", "Cannot open file", ex)
                Toast.makeText(context, getString(R.string.cannot_open_file), Toast.LENGTH_SHORT).show()
            }
        }

        private fun export(uri: Uri) {
            val db = Database.getInstance(requireContext())
            try {
                requireContext().contentResolver.openFileDescriptor(uri, "w")?.use {
                    FileOutputStream(it.fileDescriptor).bufferedWriter().use { writer ->
                        var entries = 0
                        for (entry in db.getEntries(db.firstEntry, db.lastEntry)) {
                            writer.write("${entry.timestamp},${entry.steps}\r\n")
                            entries++
                        }

                        val resultText = getString(R.string.export_result, entries)
                        Toast.makeText(context, resultText, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (ex: Exception) {
                Log.e("SettingsFragment", "Cannot write file", ex)
                Toast.makeText(context, getString(R.string.cannot_write_file), Toast.LENGTH_SHORT).show()
            }
        }

        private fun restartApp() {
            val intent = Intent(requireContext(), MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
            activity?.finishAffinity()
        }
    }
}
