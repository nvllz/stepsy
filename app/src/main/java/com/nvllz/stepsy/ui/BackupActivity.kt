package com.nvllz.stepsy.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
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

    private val TAG = "BackupActivity"

    private lateinit var backupLocationRow: View
    private lateinit var backupLocationSummary: TextView
    private lateinit var backupFrequencyRow: View
    private lateinit var backupFrequencySummary: TextView
    private lateinit var backupRetentionRow: View
    private lateinit var backupRetentionSummary: TextView
    private lateinit var manualBackupRow: View
    private lateinit var importRow: View
    private lateinit var nextBackupText: TextView

    private lateinit var backupLocationLauncher: ActivityResultLauncher<Intent>
    private lateinit var importLauncher: ActivityResultLauncher<Intent>
    private lateinit var exportLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup)

        supportActionBar?.apply {
            title = getString(R.string.header_data_backup)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            setBackgroundDrawable(
                ContextCompat.getColor(this@BackupActivity, R.color.colorBackground).toDrawable()
            )
            elevation = 0f
        }

        registerLaunchers()
        bindViews()
        setupClickListeners()

        lifecycleScope.launch {
            initializeSummaries()
        }
    }

    override fun onResume() {
        super.onResume()
        updateNextBackupInfo()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun registerLaunchers() {
        backupLocationLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    lifecycleScope.launch {
                        updateBackupLocationSummary(uri)
                        BackupScheduler.ensureBackupScheduled(this@BackupActivity)
                    }
                }
            }
        }

        importLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    if (isImportFileValid(uri)) {
                        showImportWarningDialog(uri)
                    } else {
                        Snackbar.make(
                            findViewById(android.R.id.content),
                            R.string.import_invalid_file,
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        exportLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    lifecycleScope.launch { exportToUri(uri) }
                }
            }
        }
    }

    private fun bindViews() {
        backupLocationRow      = findViewById(R.id.pref_backup_location)
        backupLocationSummary  = findViewById(R.id.pref_backup_location_summary)
        backupFrequencyRow     = findViewById(R.id.pref_backup_frequency)
        backupFrequencySummary = findViewById(R.id.pref_backup_frequency_summary)
        backupRetentionRow     = findViewById(R.id.pref_backup_retention)
        backupRetentionSummary = findViewById(R.id.pref_backup_retention_summary)
        manualBackupRow        = findViewById(R.id.pref_manual_backup)
        importRow              = findViewById(R.id.pref_import)
        nextBackupText         = findViewById(R.id.next_backup_text)
    }

    private suspend fun initializeSummaries() {
        val frequency = AppPreferences.backupFrequency
        refreshFrequencySummary(frequency)

        val retention = AppPreferences.backupRetention
        refreshRetentionSummary(retention)

        updateBackupLocationSummary(AppPreferences.backupLocationUri?.toUri())
        updateDependentState(frequency)
        updateNextBackupInfo()
    }

    private fun refreshFrequencySummary(frequency: Int) {
        val entries = resources.getStringArray(R.array.backup_frequency_entries)
        val values  = resources.getStringArray(R.array.backup_frequency_values)
        val idx     = values.indexOf(frequency.toString())
        backupFrequencySummary.text = if (idx >= 0) entries[idx] else frequency.toString()
    }

    private fun refreshRetentionSummary(retention: Int) {
        backupRetentionSummary.text = if (retention == 0) {
            getString(R.string.backup_retention_unlimited)
        } else {
            retention.toString()
        }
    }

    private fun updateDependentState(frequency: Int) {
        val locationSet  = AppPreferences.backupLocationUri != null
        val isEnabled    = frequency > 0
        backupFrequencyRow.isEnabled    = locationSet
        backupFrequencyRow.alpha        = if (locationSet) 1f else 0.4f
        backupRetentionRow.isEnabled    = isEnabled
        backupRetentionRow.alpha        = if (isEnabled) 1f else 0.4f
    }

    private fun updateNextBackupInfo() {
        lifecycleScope.launch {
            val frequency   = AppPreferences.backupFrequency
            val locationSet = AppPreferences.backupLocationUri != null

            if (frequency > 0 && locationSet) {
                val nextBackupTime  = BackupScheduler.getNextBackupTime(this@BackupActivity)
                val dateFormat      = SimpleDateFormat(AppPreferences.dateFormatString, Locale.getDefault())
                val formattedDate   = dateFormat.format(Date(nextBackupTime))
                val formattedTime   = android.text.format.DateFormat
                    .getTimeFormat(this@BackupActivity).format(nextBackupTime)

                nextBackupText.text       = getString(R.string.next_backup_scheduled, formattedDate, formattedTime)
                nextBackupText.visibility = View.VISIBLE
            } else {
                nextBackupText.visibility = View.GONE
            }
        }
    }

    private fun setupClickListeners() {
        backupLocationRow.setOnClickListener { promptForBackupLocation() }

        backupFrequencyRow.setOnClickListener {
            val entries  = resources.getStringArray(R.array.backup_frequency_entries)
            val values   = resources.getStringArray(R.array.backup_frequency_values)
            val current  = AppPreferences.backupFrequency.toString()
            val checked  = values.indexOf(current).coerceAtLeast(0)

            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.backup_frequency)
                .setSingleChoiceItems(entries, checked) { dialog, which ->
                    val frequency = values[which].toInt()
                    lifecycleScope.launch {
                        AppPreferences.dataStore.edit { prefs ->
                            prefs[AppPreferences.PreferenceKeys.BACKUP_FREQUENCY] = frequency.toString()
                        }
                        refreshFrequencySummary(frequency)
                        updateDependentState(frequency)

                        BackupScheduler.cancelBackup(this@BackupActivity)
                        BackupScheduler.scheduleBackup(this@BackupActivity)
                        updateNextBackupInfo()

                        Log.d(TAG, "Backup rescheduled with new frequency: $frequency days")
                    }
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        backupRetentionRow.setOnClickListener {
            val current = AppPreferences.backupRetention.toString()
            showNumberInputDialog(
                title   = getString(R.string.backup_retention_count),
                current = current,
                hint    = getString(R.string.backup_retention_hint)
            ) { input ->
                try {
                    val retention = input.toInt()
                    if (retention >= 0) {
                        lifecycleScope.launch {
                            AppPreferences.dataStore.edit { prefs ->
                                prefs[AppPreferences.PreferenceKeys.BACKUP_RETENTION_COUNT] = retention
                            }
                            refreshRetentionSummary(retention)

                            if (AppPreferences.backupFrequency > 0) {
                                BackupScheduler.scheduleImmediateCleanup(this@BackupActivity)
                            }
                        }
                    } else {
                        Snackbar.make(
                            findViewById(android.R.id.content),
                            R.string.enter_valid_value,
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                } catch (_: Exception) {
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        R.string.enter_valid_value,
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
        }

        manualBackupRow.setOnClickListener { promptManualExport() }

        importRow.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/*"
            }
            importLauncher.launch(intent)
        }
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
        if (uri == null) {
            backupLocationSummary.text = getString(R.string.backup_location_not_set)
            updateDependentState(0)
            updateNextBackupInfo()
            return
        }

        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            AppPreferences.dataStore.edit { prefs ->
                prefs[AppPreferences.PreferenceKeys.BACKUP_LOCATION_URI] = uri.toString()
            }

            val displayPath = try {
                DocumentsContract.getTreeDocumentId(uri).substringAfter(':', "")
            } catch (_: Exception) { "" }

            backupLocationSummary.text = displayPath

            updateDependentState(AppPreferences.backupFrequency)
            Log.d(TAG, "Backup location set to: $displayPath (URI: $uri)")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting backup location URI permissions", e)
            backupLocationSummary.text = getString(R.string.backup_location_not_set)
        }
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
        val db = Database.getInstance(this)
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.bufferedWriter().use { writer ->
                    val firstDate = db.firstEntry
                    val lastDate  = db.lastEntry
                    if (firstDate != null && lastDate != null) {
                        for (entry in db.getEntries(firstDate, lastDate)) {
                            writer.write("${entry.date},${entry.steps}\r\n")
                        }
                    }
                    writer.flush()
                }
            }
            Snackbar.make(
                findViewById(android.R.id.content),
                R.string.manual_backup_successful,
                Snackbar.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "Manual export failed", e)
            Snackbar.make(
                findViewById(android.R.id.content),
                R.string.cannot_open_file,
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private fun isImportFileValid(uri: Uri): Boolean {
        try {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
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
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.import_warning_title)
            .setMessage(R.string.import_warning_message)
            .setPositiveButton(android.R.string.ok) { _, _ -> importDataWithClear(uri) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun importDataWithClear(uri: Uri) {
        val db      = Database.getInstance(this)
        val today   = Util.todayDateString()
        val entries = mutableListOf<Pair<String, Int>>()
        var failed  = 0
        var importedTodaySteps = 0

        try {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).bufferedReader().use { reader ->
                    for (line in reader.readLines()) {
                        if (line.isBlank()) continue
                        try {
                            val split = line.split(",")
                            if (split.size < 2) { failed++; continue }
                            val steps   = split[1].trim().toInt()
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
            Snackbar.make(
                findViewById(android.R.id.content),
                R.string.cannot_open_file,
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        try {
            db.clearAllAndImport(entries)
        } catch (ex: Exception) {
            Log.e(TAG, "Atomic import failed", ex)
            Snackbar.make(
                findViewById(android.R.id.content),
                R.string.cannot_open_file,
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        lifecycleScope.launch {
            AppPreferences.dataStore.edit { prefs ->
                prefs[AppPreferences.PreferenceKeys.STEPS] = importedTodaySteps
                prefs[AppPreferences.PreferenceKeys.DATE]  = today
            }
            startService(
                Intent(this@BackupActivity, MotionService::class.java).apply {
                    putExtra("FORCE_UPDATE", true)
                    putExtra(KEY_STEPS, importedTodaySteps)
                    putExtra(KEY_DATE, today)
                }
            )
        }

        val todayNote = if (importedTodaySteps > 0)
            getString(R.string.today_steps_set, importedTodaySteps) else ""
        Snackbar.make(
            findViewById(android.R.id.content),
            getString(R.string.import_result, entries.size, failed, todayNote),
            Snackbar.LENGTH_LONG
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
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finishAffinity()
    }

    private fun showNumberInputDialog(
        title:   String,
        current: String,
        hint:    String,
        onSave:  (String) -> Unit
    ) {
        val layout = layoutInflater.inflate(R.layout.dialog_input, null)
        val til    = layout.findViewById<TextInputLayout>(R.id.dialog_input_layout)
        val et     = layout.findViewById<TextInputEditText>(R.id.dialog_input_edit)

        til.hint     = hint
        et.inputType = InputType.TYPE_CLASS_NUMBER
        et.keyListener = DigitsKeyListener.getInstance("0123456789")
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
}