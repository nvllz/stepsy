package com.nvllz.stepsy.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.ContextCompat

class ExternalControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Toast.makeText(context, "Got intent: ${intent.action}", Toast.LENGTH_SHORT).show()

        val serviceIntent = Intent(context, MotionService::class.java).apply {
            action = intent.action
        }

        ContextCompat.startForegroundService(context, serviceIntent)
    }
}