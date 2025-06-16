/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.nvllz.stepsy.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.text.method.LinkMovementMethod
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
import com.nvllz.stepsy.util.Util
import java.lang.Exception
import java.util.Locale
import androidx.core.graphics.drawable.toDrawable
import androidx.preference.EditTextPreference
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.core.text.HtmlCompat
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import com.nvllz.stepsy.BuildConfig
import com.nvllz.stepsy.util.AppPreferences
import kotlinx.coroutines.launch
import java.text.NumberFormat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        val color = ContextCompat.getColor(this, R.color.colorBackground)
        supportActionBar?.setBackgroundDrawable(color.toDrawable())
        supportActionBar?.elevation = 0f

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
                    .setTitle(R.string.about_stepsy)
                    .setView(textView)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()

                true
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