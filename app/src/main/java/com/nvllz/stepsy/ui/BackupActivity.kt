package com.nvllz.stepsy.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import com.nvllz.stepsy.R
import com.nvllz.stepsy.ui.SettingsActivity.SettingsFragment.BackupPreferenceFragment

class BackupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        val color = ContextCompat.getColor(this, R.color.colorBackground)
        supportActionBar?.setBackgroundDrawable(color.toDrawable())

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.backup_container, BackupPreferenceFragment())
                .commit()
        }
    }
}
