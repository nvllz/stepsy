package com.nvllz.stepsy.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.text.method.LinkMovementMethod
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.os.LocaleListCompat
import androidx.core.text.HtmlCompat
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.nvllz.stepsy.BuildConfig
import com.nvllz.stepsy.R
import com.nvllz.stepsy.service.MotionService
import com.nvllz.stepsy.service.isPlayServicesAvailable
import com.nvllz.stepsy.util.AppPreferences
import com.nvllz.stepsy.util.Util
import com.nvllz.stepsy.util.Util.UnitSystem
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class SettingsActivity : AppCompatActivity() {

    private lateinit var heightSummary: TextView
    private lateinit var heightTitle: TextView
    private lateinit var stepLengthSummary: TextView
    private lateinit var stepLengthTitle: TextView
    private lateinit var weightSummary: TextView
    private lateinit var weightTitle: TextView
    private lateinit var languageSummary: TextView
    private lateinit var themeSummary: TextView
    private lateinit var unitSystemSummary: TextView
    private lateinit var dateFormatSummary: TextView
    private lateinit var firstDaySummary: TextView
    private lateinit var aboutSummary: TextView
    private lateinit var vehicleFilterSummary: TextView
    private lateinit var vehicleFilterSwitch: MaterialSwitch
    private lateinit var vehicleFilterPrefCard: View
    private var vehicleFilterProgrammatic = false

    private val isImperial: Boolean
        get() = AppPreferences.unitSystem == UnitSystem.IMPERIAL

    private fun cmToTotalInches(cm: Int): Int = (cm / 2.54).roundToInt()

    private fun totalInchesToCm(inches: Int): Int = (inches * 2.54).roundToInt()

    private fun inchesToFtIn(totalInches: Int): String {
        val ft  = totalInches / 12
        val ins = totalInches % 12
        return "${ft}′${ins}″"
    }

    private fun heightDisplayString(cm: Int): String = if (isImperial) {
        inchesToFtIn(cmToTotalInches(cm))
    } else {
        "$cm ${Util.heightUnit()}"
    }

    private fun stepLengthDisplayString(cm: Float): String {
        val fmt = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            maximumFractionDigits = 2; minimumFractionDigits = 2; isGroupingUsed = false
        }
        return if (isImperial) {
            "${fmt.format(cm / 2.54f)} ${Util.stepLengthUnit()}"
        } else {
            "${fmt.format(cm)} ${Util.stepLengthUnit()}"
        }
    }

    private fun weightDisplayString(kg: Int): String = if (isImperial) {
        "${(kg * 2.20462).roundToInt()} ${Util.weightUnit()}"
    } else {
        "$kg ${Util.weightUnit()}"
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        supportActionBar?.apply {
            title = getString(R.string.settings)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            setBackgroundDrawable(
                ContextCompat.getColor(this@SettingsActivity, R.color.colorBackground).toDrawable()
            )
            elevation = 0f
        }

        bindViews()
        initSummaries()
        setupClickListeners()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun bindViews() {
        heightSummary         = findViewById(R.id.pref_height_summary)
        heightTitle           = findViewById(R.id.pref_height_title)
        stepLengthSummary     = findViewById(R.id.pref_step_length_summary)
        stepLengthTitle       = findViewById(R.id.pref_step_length_title)
        weightSummary         = findViewById(R.id.pref_weight_summary)
        weightTitle           = findViewById(R.id.pref_weight_title)
        languageSummary       = findViewById(R.id.pref_language_summary)
        themeSummary          = findViewById(R.id.pref_theme_summary)
        unitSystemSummary     = findViewById(R.id.pref_unit_system_summary)
        dateFormatSummary     = findViewById(R.id.pref_date_format_summary)
        firstDaySummary       = findViewById(R.id.pref_first_day_summary)
        aboutSummary          = findViewById(R.id.pref_about_summary)
        vehicleFilterSummary  = findViewById(R.id.pref_vehicle_filter_summary)
        vehicleFilterSwitch   = findViewById(R.id.pref_vehicle_filter_switch)
        vehicleFilterPrefCard = findViewById(R.id.pref_vehicle_filter_card)
    }

    private fun initSummaries() {
        refreshPersonalDataTitles()
        refreshHeightSummary()
        refreshWeightSummary()
        refreshStepLengthSummary()

        val locales  = AppCompatDelegate.getApplicationLocales()
        val langCode = if (locales.isEmpty) "system" else locales[0]?.language ?: "system"
        languageSummary.text = labelFromEntries(R.array.language_names, R.array.language_values, langCode)

        themeSummary.text = labelFromEntries(R.array.theme_entries, R.array.theme_values, AppPreferences.theme)

        val unitValue = if (isImperial) "imperial" else "metric"
        unitSystemSummary.text = labelFromEntries(R.array.unit_system_entries, R.array.unit_system_values, unitValue)

        dateFormatSummary.text = AppPreferences.dateFormatString

        firstDaySummary.text = labelFromEntries(
            R.array.weekdays, R.array.weekdays_values, AppPreferences.firstDayOfWeek.toString()
        )

        aboutSummary.text = "${getString(R.string.about_version)}: ${BuildConfig.VERSION_NAME}  •  GPL-3.0"

        val isFullBuild = BuildConfig.HAS_PROPRIETARY_LIBRARIES
        val hasPlayServices = isFullBuild && isPlayServicesAvailable(this)
        vehicleFilterSwitch.isEnabled = isFullBuild
        vehicleFilterProgrammatic = true
        vehicleFilterSwitch.isChecked = isFullBuild && hasPlayServices && AppPreferences.vehicleFilterEnabled
        vehicleFilterProgrammatic = false
        vehicleFilterSummary.text = if (!isFullBuild) {
            getString(R.string.pref_vehicle_filter_summary_foss)
        } else if (!hasPlayServices) {
            getString(R.string.vehicle_filter_unavailable)
        } else {
            getString(R.string.pref_vehicle_filter_summary)
        }
        vehicleFilterPrefCard.alpha = if (hasPlayServices) 1f else 0.4f
    }

    private fun refreshPersonalDataTitles() {
        heightTitle.text     = getString(R.string.pref_height)
        weightTitle.text     = getString(R.string.pref_weight)
        stepLengthTitle.text = getString(R.string.pref_step_length)
    }

    private fun refreshHeightSummary() {
        heightSummary.text = heightDisplayString(AppPreferences.height)
    }

    private fun refreshWeightSummary() {
        weightSummary.text = weightDisplayString(AppPreferences.weight)
    }

    private fun refreshStepLengthSummary() {
        val storedCm    = AppPreferences.stepLength
        val estimatedCm = (AppPreferences.height * 0.415).toFloat()
        val isDefault   = abs(storedCm - estimatedCm) < 0.01f
        stepLengthSummary.text = if (isDefault) {
            "~${stepLengthDisplayString(estimatedCm)}"
        } else {
            stepLengthDisplayString(storedCm)
        }
    }

    private fun labelFromEntries(entriesRes: Int, valuesRes: Int, currentValue: String): String {
        val entries = resources.getStringArray(entriesRes)
        val values  = resources.getStringArray(valuesRes)
        val idx     = values.indexOf(currentValue)
        return if (idx >= 0) entries[idx] else currentValue
    }

    private fun setupClickListeners() {
        findViewById<View>(R.id.pref_height).setOnClickListener {
            if (isImperial) {
                showHeightImperialDialog()
            } else {
                showNumberInputDialog(
                    title   = getString(R.string.pref_height),
                    current = AppPreferences.height.toString(),
                    hint    = "${Util.heightUnit()} (1-250)",
                    decimal = false
                ) { input ->
                    val v = input.toIntOrNull()
                    if (v != null && v in 1..250) {
                        lifecycleScope.launch {
                            AppPreferences.dataStore.edit { prefs ->
                                prefs[AppPreferences.PreferenceKeys.HEIGHT] = v.toString()
                            }
                            refreshHeightSummary()
                            refreshStepLengthSummary()
                        }
                    } else {
                        Snackbar.make(
                            findViewById(android.R.id.content),
                            R.string.enter_valid_value,
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        findViewById<View>(R.id.pref_step_length).setOnClickListener {
            val estimatedCm = AppPreferences.height * 0.415f
            val fmt = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
                maximumFractionDigits = 2; minimumFractionDigits = 2; isGroupingUsed = false
            }

            if (isImperial) {
                val hintIn = fmt.format(estimatedCm / 2.54f)
                showNumberInputDialog(
                    title   = getString(R.string.pref_step_length),
                    current = fmt.format(AppPreferences.stepLength / 2.54f),
                    hint    = "~$hintIn ${Util.stepLengthUnit()}",
                    decimal = true
                ) { input ->
                    if (input.isBlank()) {
                        AppPreferences.resetStepLength()
                        refreshStepLengthSummary()
                        return@showNumberInputDialog
                    }
                    val inches = input.replace(',', '.').toFloatOrNull()
                    if (inches != null && inches in 0.5f..60f) {
                        val cm = inches * 2.54f
                        lifecycleScope.launch {
                            AppPreferences.dataStore.edit { prefs ->
                                prefs[AppPreferences.PreferenceKeys.STEP_LENGTH] = cm
                            }
                            refreshStepLengthSummary()
                        }
                    } else {
                        Snackbar.make(
                            findViewById(android.R.id.content),
                            R.string.enter_valid_value,
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                val hintCm = fmt.format(estimatedCm)
                showNumberInputDialog(
                    title   = getString(R.string.pref_step_length),
                    current = AppPreferences.stepLength.toString(),
                    hint    = "~$hintCm ${Util.stepLengthUnit()}",
                    decimal = true
                ) { input ->
                    if (input.isBlank()) {
                        AppPreferences.resetStepLength()
                        refreshStepLengthSummary()
                        return@showNumberInputDialog
                    }
                    val v = input.replace(',', '.').toFloatOrNull()
                    if (v != null && v in 1f..150f) {
                        lifecycleScope.launch {
                            AppPreferences.dataStore.edit { prefs ->
                                prefs[AppPreferences.PreferenceKeys.STEP_LENGTH] = v
                            }
                            refreshStepLengthSummary()
                        }
                    } else {
                        Snackbar.make(
                            findViewById(android.R.id.content),
                            R.string.enter_valid_value,
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        findViewById<View>(R.id.pref_weight).setOnClickListener {
            if (isImperial) {
                val currentLbs = (AppPreferences.weight * 2.20462).roundToInt()
                showNumberInputDialog(
                    title   = getString(R.string.pref_weight),
                    current = currentLbs.toString(),
                    hint    = "${Util.weightUnit()} (1-1100)",
                    decimal = false
                ) { input ->
                    val lbs = input.toIntOrNull()
                    if (lbs != null && lbs in 1..1100) {
                        val kg = (lbs / 2.20462).roundToInt().coerceIn(1, 500)
                        lifecycleScope.launch {
                            AppPreferences.dataStore.edit { prefs ->
                                prefs[AppPreferences.PreferenceKeys.WEIGHT] = kg.toString()
                            }
                            refreshWeightSummary()
                        }
                    } else {
                        Snackbar.make(
                            findViewById(android.R.id.content),
                            R.string.enter_valid_value,
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                showNumberInputDialog(
                    title   = getString(R.string.pref_weight),
                    current = AppPreferences.weight.toString(),
                    hint    = "${Util.weightUnit()} (1-500)",
                    decimal = false
                ) { input ->
                    val v = input.toIntOrNull()
                    if (v != null && v in 1..500) {
                        lifecycleScope.launch {
                            AppPreferences.dataStore.edit { prefs ->
                                prefs[AppPreferences.PreferenceKeys.WEIGHT] = v.toString()
                            }
                            refreshWeightSummary()
                        }
                    } else {
                        Snackbar.make(
                            findViewById(android.R.id.content),
                            R.string.enter_valid_value,
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        findViewById<View>(R.id.pref_language).setOnClickListener {
            val entries = resources.getStringArray(R.array.language_names)
            val values  = resources.getStringArray(R.array.language_values)
            val locales = AppCompatDelegate.getApplicationLocales()
            val current = if (locales.isEmpty) "system" else locales[0]?.language ?: "system"
            showSingleChoiceDialog(getString(R.string.app_language), entries, values, current) { chosen ->
                val newLocale = if (chosen == "system") {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.create(Locale(chosen))
                }
                AppCompatDelegate.setApplicationLocales(newLocale)
            }
        }

        findViewById<View>(R.id.pref_theme).setOnClickListener {
            val entries = resources.getStringArray(R.array.theme_entries)
            val values  = resources.getStringArray(R.array.theme_values)
            showSingleChoiceDialog(getString(R.string.theme), entries, values, AppPreferences.theme) { chosen ->
                lifecycleScope.launch {
                    AppPreferences.dataStore.edit { prefs ->
                        prefs[AppPreferences.PreferenceKeys.THEME] = chosen
                    }
                    Util.applyTheme(chosen)
                }
            }
        }

        findViewById<View>(R.id.pref_unit_system).setOnClickListener {
            val entries = resources.getStringArray(R.array.unit_system_entries)
            val values  = resources.getStringArray(R.array.unit_system_values)
            val current = if (isImperial) "imperial" else "metric"
            showSingleChoiceDialog(getString(R.string.unit_system), entries, values, current) { chosen ->
                lifecycleScope.launch {
                    AppPreferences.dataStore.edit { prefs ->
                        prefs[AppPreferences.PreferenceKeys.UNIT_SYSTEM] = chosen
                    }
                    unitSystemSummary.text = labelFromEntries(
                        R.array.unit_system_entries, R.array.unit_system_values, chosen
                    )
                    refreshPersonalDataTitles()
                    refreshHeightSummary()
                    refreshWeightSummary()
                    refreshStepLengthSummary()
                }
                restartMotionService(this)
            }
        }

        findViewById<View>(R.id.pref_date_format).setOnClickListener {
            val entries = resources.getStringArray(R.array.date_format_options)
            val values  = resources.getStringArray(R.array.date_format_values)
            showSingleChoiceDialog(getString(R.string.date_format), entries, values, AppPreferences.dateFormatString) { chosen ->
                lifecycleScope.launch {
                    AppPreferences.dataStore.edit { prefs ->
                        prefs[AppPreferences.PreferenceKeys.DATE_FORMAT] = chosen
                    }
                    dateFormatSummary.text = chosen
                }
            }
        }

        findViewById<View>(R.id.pref_first_day_of_week).setOnClickListener {
            val entries = resources.getStringArray(R.array.weekdays)
            val values  = resources.getStringArray(R.array.weekdays_values)
            val current = AppPreferences.firstDayOfWeek.toString()
            showSingleChoiceDialog(getString(R.string.first_day_of_the_week), entries, values, current) { chosen ->
                lifecycleScope.launch {
                    AppPreferences.dataStore.edit { prefs ->
                        prefs[AppPreferences.PreferenceKeys.FIRST_DAY_OF_WEEK] = chosen
                    }
                    val intent = Intent(this@SettingsActivity, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                    finish()
                }
            }
        }

        vehicleFilterPrefCard.setOnClickListener {
            if (vehicleFilterSwitch.isEnabled) {
                vehicleFilterSwitch.performClick()
            }
        }

        vehicleFilterSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (vehicleFilterProgrammatic) return@setOnCheckedChangeListener

            if (isChecked && !isPlayServicesAvailable(this)) {
                Snackbar.make(
                    findViewById(android.R.id.content),
                    R.string.vehicle_filter_unavailable,
                    Snackbar.LENGTH_LONG
                ).show()
                vehicleFilterProgrammatic = true
                vehicleFilterSwitch.isChecked = false
                vehicleFilterProgrammatic = false
                lifecycleScope.launch {
                    AppPreferences.dataStore.edit { prefs ->
                        prefs[AppPreferences.PreferenceKeys.VEHICLE_FILTER_ENABLED] = false
                    }
                }
                return@setOnCheckedChangeListener
            }

            lifecycleScope.launch {
                AppPreferences.dataStore.edit { prefs ->
                    prefs[AppPreferences.PreferenceKeys.VEHICLE_FILTER_ENABLED] = isChecked
                }
            }
        }

        findViewById<View>(R.id.pref_about).setOnClickListener {
            val html     = getString(R.string.about_html, BuildConfig.VERSION_NAME)
            val textView = TextView(this).apply {
                text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
                movementMethod = LinkMovementMethod.getInstance()
                setPadding(64, 32, 64, 16)
                setLinkTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.colorAccent))
            }
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.about_stepsy)
                .setView(textView)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun showHeightImperialDialog() {
        val totalInches = cmToTotalInches(AppPreferences.height)
        val currentFt   = totalInches / 12
        val currentIn   = totalInches % 12

        val layout = layoutInflater.inflate(R.layout.dialog_input_ft_in, null)
        val etFt   = layout.findViewById<TextInputEditText>(R.id.dialog_input_ft)
        val etIn   = layout.findViewById<TextInputEditText>(R.id.dialog_input_in)

        etFt.hint = "ft"
        etIn.hint = "in"

        etFt.setText(currentFt.toString())
        etIn.setText(currentIn.toString())
        etFt.setSelection(etFt.text?.length ?: 0)

        etIn.requestFocus()

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.pref_height))
            .setView(layout)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val ft  = etFt.text.toString().trim().toIntOrNull() ?: 0
                val ins = etIn.text.toString().trim().toIntOrNull() ?: 0
                val totalIn = ft * 12 + ins
                if (totalIn in 12..98) {
                    val cm = totalInchesToCm(totalIn).coerceIn(1, 250)
                    lifecycleScope.launch {
                        AppPreferences.dataStore.edit { prefs ->
                            prefs[AppPreferences.PreferenceKeys.HEIGHT] = cm.toString()
                        }
                        refreshHeightSummary()
                        refreshStepLengthSummary()
                    }
                } else {
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        R.string.enter_valid_value,
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
    }

    private fun showNumberInputDialog(
        title:   String,
        current: String,
        hint:    String,
        decimal: Boolean,
        onSave:  (String) -> Unit
    ) {
        val layout = layoutInflater.inflate(R.layout.dialog_input, null)
        val til    = layout.findViewById<TextInputLayout>(R.id.dialog_input_layout)
        val et     = layout.findViewById<TextInputEditText>(R.id.dialog_input_edit)

        til.hint = hint
        et.inputType = if (decimal)
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        else
            InputType.TYPE_CLASS_NUMBER
        et.keyListener = DigitsKeyListener.getInstance(if (decimal) "0123456789.," else "0123456789")
        et.setText(current)
        et.setSelection(et.text?.length ?: 0)
        et.requestFocus()

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(layout)
            .setPositiveButton(android.R.string.ok) { _, _ -> onSave(et.text.toString().trim()) }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
    }

    private fun showSingleChoiceDialog(
        title:   String,
        entries: Array<String>,
        values:  Array<String>,
        current: String,
        onSave:  (String) -> Unit
    ) {
        val checked = values.indexOf(current).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setSingleChoiceItems(entries, checked) { dialog, which ->
                onSave(values[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun restartMotionService(context: Context) {
        val intent = Intent(context, MotionService::class.java)
        context.stopService(intent)
        ContextCompat.startForegroundService(context, intent)
    }
}