package com.nvllz.stepsy.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.nvllz.stepsy.R
import com.nvllz.stepsy.service.MotionService

object NotificationChannels {
    const val GOAL = "daily_goal_notifications"
    const val REMINDERS = "encouraging_reminders"
    const val MILESTONE = "com.nvllz.stepsy.MILESTONE_CHANNEL_ID"

    val LIVE: String get() = MotionService.STEP_CHANNEL_ID

    fun ensureAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val ctx = context.applicationContext
        val existingGoal = nm.getNotificationChannel(GOAL)

        nm.createNotificationChannel(
            NotificationChannel(
                GOAL,
                ctx.getString(R.string.channel_goal_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = ctx.getString(R.string.channel_goal_desc) },
        )

        val existingReminders = nm.getNotificationChannel(REMINDERS)
        val importance = existingReminders?.importance ?: run {
            val inherited = existingGoal?.importance
            if (inherited != null && inherited < NotificationManager.IMPORTANCE_DEFAULT) {
                inherited
            } else {
                NotificationManager.IMPORTANCE_DEFAULT
            }
        }
        nm.createNotificationChannel(
            NotificationChannel(
                REMINDERS,
                ctx.getString(R.string.channel_reminders_name),
                importance,
            ).apply { description = ctx.getString(R.string.channel_reminders_desc) },
        )
    }

    fun isBlocked(context: Context, channelId: String): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return true
        val nm = context.getSystemService(NotificationManager::class.java) ?: return true
        val channel = nm.getNotificationChannel(channelId) ?: return false
        return channel.importance == NotificationManager.IMPORTANCE_NONE
    }

    fun open(context: Context, channelId: String) {
        ensureAll(context)
        val app = context.applicationContext
        val candidates = if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) {
            listOf(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", app.packageName, null)),
            )
        } else {
            listOf(
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, channelId),
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", app.packageName, null)),
            )
        }
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { app.startActivity(intent) }.isSuccess) return
        }
    }
}
