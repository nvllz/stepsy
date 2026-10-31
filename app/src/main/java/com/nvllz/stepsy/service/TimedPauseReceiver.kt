package com.nvllz.stepsy.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class TimedPauseReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != MotionService.ACTION_TIMED_PAUSE_END) return

        val svc = Intent(ctx, MotionService::class.java)
            .setAction(MotionService.ACTION_TIMED_PAUSE_END)
        ContextCompat.startForegroundService(ctx, svc)
    }
}
