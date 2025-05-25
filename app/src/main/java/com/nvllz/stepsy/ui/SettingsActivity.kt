/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.nvllz.stepsy.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.InputType
import android.text.format.DateUtils
import android.text.method.DigitsKeyListener
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
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
import java.lang.Exception
import java.util.Locale
import androidx.core.graphics.drawable.toDrawable
import androidx.preference.EditTextPreference
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.text.HtmlCompat
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import com.nvllz.stepsy.BuildConfig
import com.nvllz.stepsy.util.AppPreferences
import com.nvllz.stepsy.util.BackupScheduler
import kotlinx.coroutines.launch
import java.text.NumberFormat
import androidx.core.net.toUri

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

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

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            findPreference<Preference>("about")?.apply {
                val version = BuildConfig.VERSION_NAME
                summary = "${getString(R.string.about_version)}: $version\n${getString(R.string.about_license)}: GPL-3.0"
            }

            importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.data?.let { uri -> import(uri) }
                }
            }

            val currentLocales = AppCompatDelegate.getApplicationLocales()
            val currentLanguage = when {
                currentLocales.isEmpty -> "system"
                else -> currentLocales[0]?.language ?: "system"
            }

            var heightPreference : EditTextPreference? = findPreference("height")
            heightPreference?.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_NUMBER
                editText.keyListener = DigitsKeyListener.getInstance("0123456789")
                editText.setSelection(editText.text.length)
            }

            findPreference<EditTextPreference>("height")?.setOnPreferenceChangeListener { _, newValue ->
                try {
                    val height = newValue.toString().toInt()
                    if (height in 1..250) {
                        lifecycleScope.launch {
                            AppPreferences.dataStore.edit { preferences ->
                                preferences[AppPreferences.PreferenceKeys.HEIGHT] = height.toString()
                            }
                            stepLengthCalculations()
                        }
                        true
                    } else {
                        Toast.makeText(context, R.string.enter_valid_value, Toast.LENGTH_SHORT).show()
                        false
                    }
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.enter_valid_value, Toast.LENGTH_SHORT).show()
                    false
                }
            }

            stepLengthCalculations()

            findPreference<EditTextPreference>("step_length")?.setOnPreferenceChangeListener { _, newValue ->
                val input = newValue.toString().trim()
                if (input.isEmpty()) {
                    AppPreferences.resetStepLength()
                    return@setOnPreferenceChangeListener true
                }

                try {
                    val normalizedInput = input.replace(',', '.')
                    val stepLength = normalizedInput.toFloat()
                    if (stepLength in 1.00..150.00) {
                        lifecycleScope.launch {
                            AppPreferences.dataStore.edit { preferences ->
                                preferences[AppPreferences.PreferenceKeys.STEP_LENGTH] = stepLength
                            }
                        }
                        true
                    } else {
                        Toast.makeText(context, R.string.enter_valid_value, Toast.LENGTH_SHORT).show()
                        false
                    }
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.enter_valid_value, Toast.LENGTH_SHORT).show()
                    false
                }
            }

            var weightPreference : EditTextPreference? = findPreference("weight")
            weightPreference?.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_NUMBER
                editText.keyListener = DigitsKeyListener.getInstance("0123456789")
                editText.setSelection(editText.text.length)
            }

            findPreference<EditTextPreference>("weight")?.setOnPreferenceChangeListener { _, newValue ->
                try {
                    val weight = newValue.toString().toInt()
                    if (weight in 1..500) {
                        lifecycleScope.launch {
                            AppPreferences.dataStore.edit { preferences ->
                                preferences[AppPreferences.PreferenceKeys.WEIGHT] = weight.toString()
                            }
                        }
                        true
                    } else {
                        Toast.makeText(context, R.string.enter_valid_value, Toast.LENGTH_SHORT).show()
                        false
                    }
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.enter_valid_value, Toast.LENGTH_SHORT).show()
                    false
                }
            }

            findPreference<ListPreference>("unit_system")?.setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch {
                    val selectedUnit = if (newValue.toString() == "imperial") {
                        Util.DistanceUnit.IMPERIAL
                    } else {
                        Util.DistanceUnit.METRIC
                    }

                    AppPreferences.dataStore.edit { preferences ->
                        preferences[AppPreferences.PreferenceKeys.UNIT_SYSTEM] =
                            if (selectedUnit == Util.DistanceUnit.IMPERIAL) "imperial" else "metric"
                    }

                    restartMotionService(requireContext())
                }
                true
            }

            findPreference<ListPreference>("date_format")?.setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch {
                    AppPreferences.dataStore.edit { preferences ->
                        preferences[AppPreferences.PreferenceKeys.DATE_FORMAT] = newValue.toString()
                    }
                }
                true
            }

            findPreference<ListPreference>("first_day_of_week")?.setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch {
                    AppPreferences.dataStore.edit { preferences ->
                        preferences[AppPreferences.PreferenceKeys.FIRST_DAY_OF_WEEK] = newValue.toString()
                    }

                    val intent = Intent(requireContext(), MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                    activity?.finish()
                }
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
                lifecycleScope.launch {
                    AppPreferences.dataStore.edit { preferences ->
                        preferences[AppPreferences.PreferenceKeys.THEME] = newValue.toString()
                    }

                    Util.applyTheme(newValue.toString())
                    activity?.recreate()
                }
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

            findPreference<Preference>("backup_settings")?.setOnPreferenceClickListener {
                parentFragmentManager
                    .beginTransaction()
                    .replace(R.id.settings, BackupPreferenceFragment())
                    .addToBackStack(null)
                    .commit()
                true
            }

            findPreference<Preference>("about")?.setOnPreferenceClickListener {
                val version = BuildConfig.VERSION_NAME
                val html = getString(R.string.about_html, version)

                val textView = TextView(requireContext()).apply {
                    text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
                    movementMethod = LinkMovementMethod.getInstance()
                    setPadding(50, 30, 50, 10)
                    setLinkTextColor(ContextCompat.getColor(context, R.color.colorAccent))
                }

                AlertDialog.Builder(requireContext())
                    .setTitle("About")
                    .setView(textView)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()

                true
            }
        }

        private fun import(uri: Uri) {
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

                    val overridingTodaySteps = todaySteps > 0 && todaySteps > AppPreferences.steps

                    if (overridingTodaySteps) {
                        lifecycleScope.launch {
                            AppPreferences.dataStore.edit { preferences ->
                                preferences[AppPreferences.PreferenceKeys.STEPS] = todaySteps
                                preferences[AppPreferences.PreferenceKeys.DATE] = today
                            }

                            val intent = Intent(requireContext(), MotionService::class.java).apply {
                                putExtra("FORCE_UPDATE", true)
                                putExtra(KEY_STEPS, todaySteps)
                                putExtra(KEY_DATE, today)
                            }
                            requireContext().startService(intent)
                        }
                    }

                    val successCount = entries - failed
                    val todaySetText = if (overridingTodaySteps) {
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

        class BackupPreferenceFragment : PreferenceFragmentCompat() {
            private lateinit var backupLocationLauncher: ActivityResultLauncher<Intent>
            private val TAG = "BackupPreferenceFragment"

            override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
                setPreferencesFromResource(R.xml.backup_preferences, rootKey)

                backupLocationLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    if (result.resultCode == RESULT_OK) {
                        result.data?.data?.let { uri ->
                            lifecycleScope.launch {
                                updateBackupLocationSummary(uri)

                                BackupScheduler.ensureBackupScheduled(requireContext())
                            }
                        }
                    }
                }

                lifecycleScope.launch {
                    initializePreferences()
                }

                findPreference<Preference>("backup_location")?.setOnPreferenceClickListener {
                    promptForBackupLocation()
                    true
                }

                findPreference<ListPreference>("backup_frequency")?.setOnPreferenceChangeListener { _, newValue ->
                    val frequency = newValue.toString().toInt()
                    lifecycleScope.launch {
                        AppPreferences.dataStore.edit { preferences ->
                            preferences[AppPreferences.PreferenceKeys.BACKUP_FREQUENCY] = frequency.toString()
                        }

                        updateDependentPreferences(frequency)

                        BackupScheduler.cancelBackup(requireContext())
                        BackupScheduler.scheduleBackup(requireContext())

                        Log.d(TAG, "Backup rescheduled with new frequency: $frequency days")
                    }
                    true
                }

                findPreference<EditTextPreference>("backup_retention_count")?.apply {
                    setOnBindEditTextListener { editText ->
                        editText.inputType = InputType.TYPE_CLASS_NUMBER
                        editText.keyListener = DigitsKeyListener.getInstance("0123456789")
                        editText.setSelection(editText.text.length)
                        editText.hint = getString(R.string.backup_retention_hint)
                    }
                    setOnPreferenceChangeListener { _, newValue ->
                        try {
                            val retention = newValue.toString().toInt()
                            if (retention >= 0) {
                                lifecycleScope.launch {
                                    AppPreferences.dataStore.edit { preferences ->
                                        preferences[AppPreferences.PreferenceKeys.BACKUP_RETENTION_COUNT] = retention
                                    }

                                    if (AppPreferences.backupFrequency > 0) {
                                        BackupScheduler.scheduleImmediateCleanup(requireContext())
                                    }
                                }
                                true
                            } else {
                                Toast.makeText(context, R.string.enter_valid_value, Toast.LENGTH_SHORT).show()
                                false
                            }
                        } catch (_: Exception) {
                            Toast.makeText(context, R.string.enter_valid_value, Toast.LENGTH_SHORT).show()
                            false
                        }
                    }
                }

                findPreference<Preference>("manual_backup")?.setOnPreferenceClickListener {
                    if (AppPreferences.backupLocationUri == null) {
                        Toast.makeText(context, R.string.select_backup_location_first, Toast.LENGTH_SHORT).show()
                        promptForBackupLocation()
                        return@setOnPreferenceClickListener true
                    }

                    BackupScheduler.scheduleManualExport(requireContext())
                    Toast.makeText(context, R.string.manual_backup_successful, Toast.LENGTH_SHORT).show()
                    true
                }
            }

            private suspend fun initializePreferences() {
                val frequency = AppPreferences.backupFrequency
                findPreference<ListPreference>("backup_frequency")?.value = frequency.toString()

                val retention = AppPreferences.backupRetention
                findPreference<EditTextPreference>("backup_retention_count")?.text = retention.toString()

                updateBackupLocationSummary(AppPreferences.backupLocationUri?.toUri())

                updateDependentPreferences(frequency)
            }

            private fun updateDependentPreferences(frequency: Int) {
                val isBackupEnabled = frequency > 0
                findPreference<ListPreference>("backup_frequency")?.isEnabled =
                        AppPreferences.backupLocationUri != null
                findPreference<EditTextPreference>("backup_retention_count")?.isEnabled = isBackupEnabled
                findPreference<Preference>("manual_backup")?.isEnabled =
                        AppPreferences.backupLocationUri != null
            }

            private fun promptForBackupLocation() {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                }
                backupLocationLauncher.launch(intent)
            }

            private suspend fun updateBackupLocationSummary(uri: Uri?) {
                val locationPref = findPreference<Preference>("backup_location") ?: return

                if (uri == null) {
                    locationPref.summary = getString(R.string.backup_location_not_set)
                    findPreference<ListPreference>("backup_frequency")?.isEnabled = false
                    findPreference<Preference>("manual_backup")?.isEnabled = false
                    return
                }

                try {
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )

                    AppPreferences.dataStore.edit { preferences ->
                        preferences[AppPreferences.PreferenceKeys.BACKUP_LOCATION_URI] = uri.toString()
                    }

                    val displayPath = try {
                        DocumentsContract.getTreeDocumentId(uri)
                            .substringAfter(':', "")
                    } catch (_: Exception) {
                        ""
                    }

                    val displayName = if (displayPath.isNotEmpty()) {
                        displayPath
                    } else {
                        ""
                    }

                    locationPref.summary = displayName

                    findPreference<ListPreference>("backup_frequency")?.isEnabled = true
                    findPreference<Preference>("manual_backup")?.isEnabled = true

                    Log.d(TAG, "Backup location set to: $displayName (URI: $uri)")

                } catch (e: Exception) {
                    Log.e(TAG, "Error setting backup location URI permissions", e)
                    locationPref.summary = getString(R.string.backup_location_not_set)
                }
            }
        }

        fun restartMotionService(context: Context) {
            val serviceIntent = Intent(context, MotionService::class.java)
            context.stopService(serviceIntent)
            ContextCompat.startForegroundService(context, serviceIntent)
        }

        private fun stepLengthCalculations() {
            val stepLengthPreference: EditTextPreference? = findPreference("step_length")
            stepLengthPreference?.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                editText.keyListener = DigitsKeyListener.getInstance("0123456789.,")
                editText.setSelection(editText.text.length)

                val locale = Locale.getDefault()
                val formatter = NumberFormat.getNumberInstance(locale).apply {
                    maximumFractionDigits = 2
                    minimumFractionDigits = 2
                    isGroupingUsed = false
                }

                val hintValue = formatter.format(AppPreferences.height * 0.415)
                editText.hint = hintValue
            }

            stepLengthPreference?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
                val value = pref.text
                val locale = Locale.getDefault()
                val formatter = NumberFormat.getNumberInstance(locale).apply {
                    maximumFractionDigits = 2
                    minimumFractionDigits = 2
                    isGroupingUsed = false
                }

                if (!value.isNullOrEmpty()) {
                    val displayValue = try {
                        val normalized = value.replace(',', '.')
                        val floatVal = normalized.toFloat()
                        formatter.format(floatVal)
                    } catch (_: Exception) {
                        value
                    }
                    "$displayValue cm"
                } else {
                    val defaultStepLength = formatter.format(AppPreferences.height * 0.415)
                    "~$defaultStepLength cm"
                }
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