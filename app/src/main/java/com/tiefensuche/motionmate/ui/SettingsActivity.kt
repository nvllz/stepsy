/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.motionmate.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import com.tiefensuche.motionmate.R
import com.tiefensuche.motionmate.util.Database
import com.tiefensuche.motionmate.util.Util
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.Exception

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            findPreference<SeekBarPreference>("step_width")?.setOnPreferenceChangeListener { _, newValue ->
                Util.stepWidth = newValue as Int
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
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/*"
                    putExtra(Intent.EXTRA_TITLE, "step_data.csv")
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
                }
            } catch (ex: Exception) {
                println("Can not open file, ${ex.message}")
                Toast.makeText(context, "Can not open file", Toast.LENGTH_LONG).show()
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
    }
}