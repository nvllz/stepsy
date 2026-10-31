package com.nvllz.stepsy.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AppUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val serviceIntent = Intent(context, MotionService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}