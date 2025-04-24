/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.nvllz.stepsy.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

internal class OnBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action?.equals(Intent.ACTION_BOOT_COMPLETED, ignoreCase = true) == true) {
            context.startForegroundService(Intent(context, MotionService::class.java))
        }
    }
}
