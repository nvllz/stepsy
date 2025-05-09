package com.nvllz.stepsy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nvllz.stepsy.service.MotionService

class AppUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val serviceIntent = Intent(context, MotionService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
