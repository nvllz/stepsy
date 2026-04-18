package com.nvllz.stepsy.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import com.nvllz.stepsy.util.AppPreferences
import kotlinx.coroutines.launch

internal class ActivityRecognitionManager(private val context: Context) {

    private var pendingIntent: PendingIntent? = null
    private var receiver: ActivityBroadcastReceiver? = null

    @Volatile
    var isInVehicle: Boolean = false
        private set

    private var lastActivityTime: Long = 0
    private var pendingVehicleState: Boolean = false
    private val stabilityWindowMs = 12_000L

    fun start() {
        val gps = GoogleApiAvailability.getInstance()
        if (gps.isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) {
            Log.w(TAG, "Google Play Services unavailable, disabling vehicle filter")
            AppPreferences.vehicleFilterEnabled = false
            return
        }

        if (!AppPreferences.vehicleFilterEnabled) return

        registerReceiver()
        requestUpdates()
    }

    fun stop() {
        pendingIntent?.let {
            try {
                ActivityRecognition.getClient(context).removeActivityUpdates(it)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove activity updates: ${e.message}")
            }
            it.cancel()
        }
        pendingIntent = null

        receiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }
        receiver = null
        isInVehicle = false
    }

    private fun registerReceiver() {
        val r = ActivityBroadcastReceiver()
        val filter = IntentFilter(ACTION_ACTIVITY_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(r, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(r, filter)
        }
        receiver = r
    }

    private fun requestUpdates() {
        val intent = Intent(ACTION_ACTIVITY_UPDATE).apply { setPackage(context.packageName) }
        val pi = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        pendingIntent = pi
        ActivityRecognition.getClient(context)
            .requestActivityUpdates(UPDATE_INTERVAL_MS, pi)
            .addOnFailureListener { e ->
                Log.w(TAG, "Failed to request activity updates: ${e.message}")
            }
    }

    private fun onActivityResult(result: ActivityRecognitionResult) {
        val top = result.mostProbableActivity
        val confidence = top.confidence

        if (confidence < CONFIDENCE_THRESHOLD) return

        val detectedVehicle = when (top.type) {
            DetectedActivity.IN_VEHICLE, DetectedActivity.ON_BICYCLE -> true
            DetectedActivity.WALKING, DetectedActivity.ON_FOOT,
            DetectedActivity.RUNNING, DetectedActivity.STILL -> false
            else -> return
        }

        val now = System.currentTimeMillis()

        if (detectedVehicle != pendingVehicleState) {
            pendingVehicleState = detectedVehicle
            lastActivityTime = now
        } else if (detectedVehicle != isInVehicle && now - lastActivityTime >= stabilityWindowMs) {
            isInVehicle = detectedVehicle
            Log.d(TAG, "Vehicle state -> $isInVehicle (activity=${top.type}, confidence=$confidence)")
        }
    }

    inner class ActivityBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ActivityRecognitionResult.hasResult(intent)) {
                onActivityResult(ActivityRecognitionResult.extractResult(intent)!!)
            }
        }
    }

    companion object {
        private const val TAG = "ActivityRecognitionMgr"
        private const val ACTION_ACTIVITY_UPDATE = "com.nvllz.stepsy.ACTIVITY_UPDATE"
        private const val UPDATE_INTERVAL_MS = 15_000L
        private const val CONFIDENCE_THRESHOLD = 60
    }
}
