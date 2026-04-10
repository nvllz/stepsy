package com.nvllz.stepsy.ui

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup)

        supportActionBar?.title = getString(R.string.header_data_backup)
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
    private var nextBackupTextView: TextView? = null
    private val TAG = "BackupPreferenceFragment"
    private lateinit var importLauncher: ActivityResultLauncher<Intent>
    private lateinit var exportLauncher: ActivityResultLauncher<Intent>

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
                result.data?.data?.let { uri ->
                    if (isImportFileValid(uri)) {
                        showImportWarningDialog(uri)
                    } else {
                        Toast.makeText(context, R.string.import_invalid_file, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        exportLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    lifecycleScope.launch {
                        exportToUri(uri)
                    }
                }
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

                updateNextBackupInfo()

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
            promptManualExport()
            true
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = super.onCreateView(inflater, container, savedInstanceState)

        val footer = inflater.inflate(R.layout.backup_preferences_footer, container, false)
        nextBackupTextView = footer.findViewById(R.id.nextBackupTextView)
        (view as? ViewGroup)?.addView(footer)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            initializePreferences()
        }
    }

    override fun onResume() {
        super.onResume()
        updateNextBackupInfo()
    }

    private fun updateNextBackupInfo() {
        nextBackupTextView?.let { textView ->
            lifecycleScope.launch {
                val frequency = AppPreferences.backupFrequency
                val locationSet = AppPreferences.backupLocationUri != null

                if (frequency > 0 && locationSet) {
                    val nextBackupTime = BackupScheduler.getNextBackupTime(requireContext())
                    val dateFormat = SimpleDateFormat(AppPreferences.dateFormatString, Locale.getDefault())
                    val formattedDate = dateFormat.format(Date(nextBackupTime))
                    val formattedTime = android.text.format.DateFormat.getTimeFormat(requireContext()).format(nextBackupTime)

                    textView.text = getString(R.string.next_backup_scheduled, formattedDate, formattedTime)
                    textView.visibility = View.VISIBLE
                } else {
                    textView.visibility = View.GONE
                }
            }
        }
    }

    private fun isImportFileValid(uri: Uri): Boolean {
        try {
            requireContext().contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).bufferedReader().use { reader ->
                    for (line in reader.readLines()) {
                        if (line.isBlank()) continue
                        try {
                            val split = line.split(",")
                            if (split.size < 2) continue
                            split[1].trim().toInt()
                            parseImportDate(split[0].trim())
                            return true
                        } catch (_: Exception) { }
                    }
                }
            }
        } catch (_: Exception) {
            return false
        }
        return false
    }

    private fun showImportWarningDialog(uri: Uri) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.import_warning_title)
            .setMessage(R.string.import_warning_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                importDataWithClear(uri)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun importDataWithClear(uri: Uri) {
        val db = Database.getInstance(requireContext())
        val today = Util.todayDateString()
        val entries = mutableListOf<Pair<String, Int>>()
        var failed = 0
        var importedTodaySteps = 0

        try {
            requireContext().contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).bufferedReader().use { reader ->
                    for (line in reader.readLines()) {
                        if (line.isBlank()) continue
                        try {
                            val split = line.split(",")
                            if (split.size < 2) { failed++; continue }
                            val steps = split[1].trim().toInt()
                            val dateStr = parseImportDate(split[0].trim())
                            entries.add(dateStr to steps)
                            if (dateStr == today) importedTodaySteps += steps
                        } catch (ex: Exception) {
                            Log.e(TAG, "Cannot parse line", ex)
                            failed++
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Cannot open file", ex)
            Toast.makeText(context, R.string.cannot_open_file, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            db.clearAllAndImport(entries)
        } catch (ex: Exception) {
            Log.e(TAG, "Atomic import failed", ex)
            Toast.makeText(context, R.string.cannot_open_file, Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            AppPreferences.dataStore.edit { preferences ->
                preferences[AppPreferences.PreferenceKeys.STEPS] = importedTodaySteps
                preferences[AppPreferences.PreferenceKeys.DATE] = today
            }
            requireContext().startService(
                Intent(requireContext(), MotionService::class.java).apply {
                    putExtra("FORCE_UPDATE", true)
                    putExtra(KEY_STEPS, importedTodaySteps)
                    putExtra(KEY_DATE, today)
                }
            )
        }

        val todayNote = if (importedTodaySteps > 0)
            getString(R.string.today_steps_set, importedTodaySteps) else ""
        Toast.makeText(
            context,
            getString(R.string.import_result, entries.size, failed, todayNote),
            Toast.LENGTH_LONG
        ).show()

        restartApp()
    }

    private fun parseImportDate(raw: String): String {
        return if (raw.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            raw
        } else {
            Database.snapTimestampToDate(raw.toLong())
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
    }

    private fun promptForBackupLocation() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        }
        backupLocationLauncher.launch(intent)
    }

    private fun promptManualExport() {
        val fileName = "stepsy_${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())}.csv"

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, fileName)

            AppPreferences.backupLocationUri?.let { uriString ->
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, uriString.toUri())
            }
        }

        exportLauncher.launch(intent)
    }

    private fun exportToUri(uri: Uri) {
        val db = Database.getInstance(requireContext())

        try {
            requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.bufferedWriter().use { writer ->
                    val firstDate = db.firstEntry
                    val lastDate = db.lastEntry

                    if (firstDate != null && lastDate != null) {
                        for (entry in db.getEntries(firstDate, lastDate)) {
                            writer.write("${entry.date},${entry.steps}\r\n")
                        }
                    }
                    writer.flush()
                }
            }

            Toast.makeText(context, R.string.manual_backup_successful, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Manual export failed", e)
            Toast.makeText(context, R.string.cannot_open_file, Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun updateBackupLocationSummary(uri: Uri?) {
        val locationPref = findPreference<Preference>("backup_location") ?: return

        if (uri == null) {
            locationPref.summary = getString(R.string.backup_location_not_set)
            findPreference<ListPreference>("backup_frequency")?.isEnabled = false
            updateNextBackupInfo()
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
                DocumentsContract.getTreeDocumentId(uri).substringAfter(':', "")
            } catch (_: Exception) {
                ""
            }

            locationPref.summary = displayPath

            findPreference<ListPreference>("backup_frequency")?.isEnabled = true

            Log.d(TAG, "Backup location set to: $displayPath (URI: $uri)")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting backup location URI permissions", e)
            locationPref.summary = getString(R.string.backup_location_not_set)
        }
    }
}