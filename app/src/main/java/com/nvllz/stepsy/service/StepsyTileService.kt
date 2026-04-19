package com.nvllz.stepsy.service

import android.app.PendingIntent
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
import android.widget.Toast
import com.nvllz.stepsy.R
import com.nvllz.stepsy.ui.TileDialogActivity
import androidx.core.content.edit

class StepsyTileService : TileService() {

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
        val filter = IntentFilter("com.nvllz.stepsy.STATE_UPDATE")
        registerReceiver(stateReceiver, filter, RECEIVER_NOT_EXPORTED)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile(isPaused())
    }

    override fun onClick() {
        super.onClick()

        if (isPaused()) {
            resumeCounting()
        } else {
            val intent = Intent(this, TileDialogActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // API 34+
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                // Older Android
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }

    private fun resumeCounting() {
        Intent(applicationContext, MotionService::class.java).also {
            it.action = MotionService.ACTION_RESUME_COUNTING
            startService(it)
        }

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

    private fun isPaused(): Boolean =
        applicationContext
            .getSharedPreferences("StepsyPrefs", MODE_MULTI_PROCESS)
            .getBoolean(MotionService.KEY_IS_PAUSED, false)

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(stateReceiver)
    }
}