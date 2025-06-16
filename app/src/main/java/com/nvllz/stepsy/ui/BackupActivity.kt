package com.nvllz.stepsy.ui

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.InputType
import android.text.format.DateUtils
import android.text.method.DigitsKeyListener
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.nvllz.stepsy.R
import com.nvllz.stepsy.service.MotionService
import com.nvllz.stepsy.service.MotionService.Companion.KEY_DATE
import com.nvllz.stepsy.service.MotionService.Companion.KEY_STEPS
import com.nvllz.stepsy.util.AppPreferences
import com.nvllz.stepsy.util.BackupScheduler
import com.nvllz.stepsy.util.Database
import com.nvllz.stepsy.util.Util
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.lang.Exception

class BackupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        val color = ContextCompat.getColor(this, R.color.colorBackground)
        supportActionBar?.setBackgroundDrawable(color.toDrawable())
        supportActionBar?.elevation = 0f

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.backup_container, BackupPreferenceFragment())
                .commit()
        }
    }
}

class BackupPreferenceFragment : PreferenceFragmentCompat() {
    private lateinit var backupLocationLauncher: ActivityResultLauncher<Intent>
    private val TAG = "BackupPreferenceFragment"
    private lateinit var importLauncher: ActivityResultLauncher<Intent>

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

        importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri -> importData(uri) }
            }
        }

        findPreference<Preference>("import")?.setOnPreferenceClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/*"
            }
            importLauncher.launch(intent)
            true
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

    private fun importData(uri: Uri) {
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
                            Log.e("BackupPreferenceFragment", "Cannot import entry", ex)
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
            Log.e("BackupPreferenceFragment", "Cannot open file", ex)
            Toast.makeText(context, getString(R.string.cannot_open_file), Toast.LENGTH_SHORT).show()
        }
    }

    private fun restartApp() {
        val intent = Intent(requireContext(), MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        activity?.finishAffinity()
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
