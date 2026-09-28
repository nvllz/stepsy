package com.nvllz.stepsy.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nvllz.stepsy.util.AppPreferences

class MuteMilestoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppPreferences.milestoneNotificationsEnabled = false

        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (notificationId != -1) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notificationId)
        }
    }

    companion object {
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}