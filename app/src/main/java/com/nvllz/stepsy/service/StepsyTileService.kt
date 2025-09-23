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
import android.view.LayoutInflater
import android.widget.TimePicker
import android.widget.Toast
import android.app.AlertDialog
import com.nvllz.stepsy.R
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import androidx.core.content.edit

/**
 * A TileService that provides a quick settings tile for pausing and resuming step counting.
 */

@Suppress("DEPRECATION")
class StepsyTileService : TileService() {
    private val TAG = "StepsyTileService"

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

        val currentlyPaused = isPaused()

        if (currentlyPaused) {
            resumeCounting()
        } else {
            showPauseOptionsDialog()
        }
    }

    private fun showPauseOptionsDialog() {
        val options = arrayOf(
            getString(R.string.pause_30_minutes),
            getString(R.string.pause_1_hour),
            getString(R.string.pause_2_hours),
            getString(R.string.pause_custom_time),
            getString(R.string.pause_indefinitely)
        )

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.pause_step_counting))
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> pauseForDuration(30)
                    1 -> pauseForDuration(60)
                    2 -> pauseForDuration(120)
                    3 -> showCustomDurationDialog()
                    4 -> pauseIndefinitely()
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(android.R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        showDialog(dialog)
    }

    private fun showCustomDurationDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_custom_duration, null)
        val timePicker = dialogView.findViewById<TimePicker>(R.id.timePicker)

        val calendar = Calendar.getInstance()
        timePicker.hour = calendar.get(Calendar.HOUR_OF_DAY)
        timePicker.minute = calendar.get(Calendar.MINUTE) + 1
        timePicker.setIs24HourView(true)

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.resume_at_time))
            .setView(dialogView)
            .setPositiveButton(getString(android.R.string.ok)) { _, _ ->
                val selectedHour = timePicker.hour
                val selectedMinute = timePicker.minute

                val now = Calendar.getInstance()
                val resumeTime = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, selectedHour)
                    set(Calendar.MINUTE, selectedMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)

                    if (before(now)) {
                        add(Calendar.DAY_OF_MONTH, 1)
                    }
                }

                val durationMinutes = ((resumeTime.timeInMillis - now.timeInMillis) / (1000 * 60)).toInt()
                pauseForDuration(durationMinutes, resumeTime.timeInMillis)
            }
            .setNegativeButton(getString(android.R.string.cancel), null)
            .create()

        showDialog(dialog)
    }

    private fun pauseForDuration(durationMinutes: Int, specificEndTime: Long = 0L) {
        val endTime = if (specificEndTime > 0L) {
            specificEndTime
        } else {
            System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(durationMinutes.toLong())
        }

        val intent = Intent(applicationContext, MotionService::class.java).apply {
            action = MotionService.ACTION_PAUSE_COUNTING
            putExtra("TIMED_PAUSE", true)
            putExtra("END_TIME", endTime)
            putExtra("DURATION_MINUTES", durationMinutes)
        }
        startService(intent)

        getSharedPreferences("StepsyPrefs", MODE_PRIVATE).edit {
            putBoolean(MotionService.KEY_IS_PAUSED, true)
        }

        val endTimeFormatted = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(endTime))
        Toast.makeText(this, getString(R.string.step_counting_paused_until, endTimeFormatted), Toast.LENGTH_LONG).show()

        updateTile(true)
    }

    private fun pauseIndefinitely() {
        val intent = Intent(applicationContext, MotionService::class.java).apply {
            action = MotionService.ACTION_PAUSE_COUNTING
            putExtra("TIMED_PAUSE", false)
        }
        startService(intent)

        getSharedPreferences("StepsyPrefs", MODE_PRIVATE).edit {
            putBoolean(MotionService.KEY_IS_PAUSED, true)
        }

        Toast.makeText(this, R.string.step_counting_paused, Toast.LENGTH_SHORT).show()

        updateTile(true)
    }

    private fun resumeCounting() {
        val intent = Intent(applicationContext, MotionService::class.java).apply {
            action = MotionService.ACTION_RESUME_COUNTING
        }
        startService(intent)

        getSharedPreferences("StepsyPrefs", MODE_PRIVATE).edit {
            putBoolean(MotionService.KEY_IS_PAUSED, false)
        }

        Toast.makeText(this, R.string.step_counting_resumed, Toast.LENGTH_SHORT).show()

        updateTile(false)
    }

    private fun updateTile(isPaused: Boolean) {
        val tile = qsTile ?: return

        tile.label = getString(R.string.app_name)
        tile.state = if (isPaused) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isPaused) getString(R.string.notification_step_counting_paused) else ""
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