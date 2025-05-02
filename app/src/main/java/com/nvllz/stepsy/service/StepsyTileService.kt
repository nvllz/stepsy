/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.nvllz.stepsy.service

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.nvllz.stepsy.R

/**
 * A TileService that provides a quick settings tile for pausing and resuming step counting.
 */

@Suppress("DEPRECATION")
class StepsyTileService : TileService() {
    private var isPaused = false

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        isPaused = isPaused()

        val intent = Intent(this, MotionService::class.java)
        intent.action = if (isPaused) {
            MotionService.ACTION_RESUME_COUNTING
        } else {
            MotionService.ACTION_PAUSE_COUNTING
        }
        startService(intent)

        isPaused = !isPaused
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        isPaused = isPaused()

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

    private fun isPaused(): Boolean {
        val sharedPrefs = applicationContext.getSharedPreferences("StepsyPrefs", MODE_MULTI_PROCESS)
        return sharedPrefs.getBoolean(MotionService.KEY_IS_PAUSED, false)
    }
}
