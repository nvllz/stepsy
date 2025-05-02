/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.nvllz.stepsy.service

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.edit
import com.nvllz.stepsy.R

/**
 * A TileService that provides a quick settings tile for pausing and resuming step counting.
 */

class StepsyTileService : TileService() {
    private lateinit var sharedPreferences: SharedPreferences
    private var isPaused = false

    override fun onCreate() {
        super.onCreate()
        sharedPreferences = applicationContext.getSharedPreferences("StepsyPrefs", MODE_PRIVATE)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        isPaused = sharedPreferences.getBoolean(MotionService.KEY_IS_PAUSED, false)

        val intent = Intent(this, MotionService::class.java)
        intent.action = if (isPaused) {
            MotionService.ACTION_RESUME_COUNTING
        } else {
            MotionService.ACTION_PAUSE_COUNTING
        }
        startService(intent)

        isPaused = !isPaused
        sharedPreferences.edit {
            putBoolean(MotionService.KEY_IS_PAUSED, isPaused)
        }

        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        isPaused = sharedPreferences.getBoolean(MotionService.KEY_IS_PAUSED, false)

        tile.label = getString(R.string.app_name)
        tile.state = if (isPaused) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isPaused) getString(R.string.step_counting_paused) else ""
            tile.state = if (isPaused) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            tile.icon = Icon.createWithResource(this, R.drawable.ic_quick_tile)
        }

        tile.updateTile()
    }
}
