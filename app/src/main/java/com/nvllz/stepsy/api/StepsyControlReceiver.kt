package com.nvllz.stepsy.api

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.nvllz.stepsy.service.MotionService

class StepsyControlReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        val serviceIntent = Intent(context, MotionService::class.java).apply {
            action = when (intent.action) {
                StepsyApi.ACTION_PAUSE -> MotionService.ACTION_PAUSE_COUNTING
                StepsyApi.ACTION_RESUME -> MotionService.ACTION_RESUME_COUNTING
                else -> return
            }

            putExtras(intent)
        }

        ContextCompat.startForegroundService(context, serviceIntent)
    }
}