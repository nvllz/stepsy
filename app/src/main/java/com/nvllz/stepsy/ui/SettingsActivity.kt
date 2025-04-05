/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.nvllz.stepsy.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import com.nvllz.stepsy.R
import com.nvllz.stepsy.service.MotionService
import com.nvllz.stepsy.util.Database
import com.nvllz.stepsy.util.Util
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.Exception
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
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
            findPreference<SeekBarPreference>("height_cm")?.setOnPreferenceChangeListener { _, newValue ->
                Util.height = newValue as Int
                true
            }
            findPreference<SeekBarPreference>("weight_kg")?.setOnPreferenceChangeListener { _, newValue ->
                Util.weight = newValue as Int
                true
            }
            findPreference<ListPreference>("date_format")?.setOnPreferenceChangeListener { _, newValue ->
                // Handle the date format change
                val dateFormat = newValue.toString()
                Toast.makeText(context, "Date format set to: $dateFormat", Toast.LENGTH_SHORT).show()
                true
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
                // Generate the filename using the current date in yyyyMMdd format
                val dateFormat = java.text.SimpleDateFormat("yyyyMMdd-HHmmSS", Locale.getDefault())
                val currentDate = dateFormat.format(Date())
                val fileName = "${currentDate}_motionmate.csv"

                // Create the export intent with the new filename
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/*"
                    putExtra(Intent.EXTRA_TITLE, fileName) // Use the generated filename
                }
                startActivityForResult(intent, 2)
                true
            }
        }

        override fun onActivityResult(
            requestCode: Int, resultCode: Int, resultData: Intent?) {
            super.onActivityResult(requestCode, resultCode, resultData)
            if (resultCode != Activity.RESULT_OK)
                return
            resultData?.data?.also { uri ->
                when (requestCode) {
                    1 -> import(uri)
                    2 -> export(uri)
                }
            }
        }

        private fun import(uri: Uri) {
            val db = Database.getInstance(requireContext())
            try {
                requireContext().contentResolver.openFileDescriptor(uri, "r")?.use {
                    FileInputStream(it.fileDescriptor).bufferedReader().use {
                        var entries = 0
                        var failed = 0
                        for (line in it.readLines()) {
                            entries += 1
                            try {
                                val split = line.split(",")
                                db.addEntry(split[0].toLong(), split[1].toInt())
                            } catch (ex: Exception) {
                                println("Can not import entry, ${ex.message}")
                                failed += 1
                            }
                        }
                        Toast.makeText(context, "Imported ${entries - failed} entries ($failed failed).", Toast.LENGTH_LONG).show()
                    }
                    restartApp()
                }
            } catch (ex: Exception) {
                println("Can not open file, ${ex.message}")
                Toast.makeText(context, "Can not open file", Toast.LENGTH_LONG).show()
            }
        }

        private fun export(uri: Uri) {
            val db = Database.getInstance(requireContext())
            val currentDate = Util.calendar.timeInMillis
            val todaysSteps = MotionService().getTodaysSteps()

            db.addEntry(currentDate, todaysSteps)
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
            Runtime.getRuntime().exit(0) // This will kill the current process
        }
    }
}