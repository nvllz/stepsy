/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.nvllz.stepsy.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.nvllz.stepsy.R

/**
 * A TileService that provides a quick settings tile for pausing and resuming step counting.
 */

@Suppress("DEPRECATION")
class StepsyTileService : TileService() {
    private var isPaused = false
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.nvllz.stepsy.STATE_UPDATE") {
                Handler(Looper.getMainLooper()).postDelayed({
                    updateTile(isPaused())
                }, 500)

            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction("com.nvllz.stepsy.STATE_UPDATE")
        }
        registerReceiver(stateReceiver, filter, RECEIVER_NOT_EXPORTED)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile(isPaused())
    }

    override fun onClick() {
        super.onClick()

        isPaused = isPaused()

        val intent = Intent(applicationContext, MotionService::class.java)
        intent.action = if (isPaused) {
            MotionService.ACTION_RESUME_COUNTING
        } else {
            MotionService.ACTION_PAUSE_COUNTING
        }
        startService(intent)

        isPaused = !isPaused
        updateTile(isPaused)
    }

    private fun updateTile(isPaused: Boolean) {
        val tile = qsTile ?: return

        tile.label = getString(R.string.app_name)
        tile.state = if (isPaused) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isPaused) getString(R.string.notification_step_counting_paused) else ""
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

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(stateReceiver)
    }
}
