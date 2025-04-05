/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.nvllz.stepsy.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * BroadcastReceiver to automatically start the [MotionService] when the device booted.
 *
 *
 * Created by tiefensuche on 10.02.18.
 */
internal class OnBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action?.equals(Intent.ACTION_BOOT_COMPLETED, ignoreCase = true) == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(Intent(context, MotionService::class.java))
            } else {
                context.startService(Intent(context, MotionService::class.java))
            }
        }
    }
}
