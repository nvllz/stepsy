/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.nvllz.stepsy.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nvllz.stepsy.R
import com.nvllz.stepsy.ui.MainActivity
import java.text.NumberFormat
import java.util.Locale

object GoalNotificationWorker {
    private const val DAILY_GOAL_CHANNEL_ID = "daily_goal_notifications"
    private const val DAILY_GOAL_NOTIFICATION_ID = 1001
    private const val ENCOURAGING_NOTIFICATION_ID = 1002

    private var shown15PercentNotification = false
    private var shown75PercentNotification = false

    fun createNotificationChannels(context: Context) {
        val channel = NotificationChannel(
            DAILY_GOAL_CHANNEL_ID,
            context.getString(R.string.daily_goal_notifications),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.daily_goal_notification_channel_desc)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    fun showDailyGoalNotification(context: Context, target: Int) {
        if (!AppPreferences.dailyGoalNotification) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun formatNumber(number: Int) = if (number >= 10_000) {
            NumberFormat.getIntegerInstance(Locale.getDefault()).format(number)
        } else {
            number.toString()
        }

        val targetFormatted = formatNumber(target)
        val targetString = context.resources.getQuantityString(
            R.plurals.steps_formatted,
            target,
            targetFormatted
        )

        val title = context.getString(R.string.goal_achieved_title)
        val message = context.getString(R.string.goal_achieved_message, targetString)

        val notification = NotificationCompat.Builder(context, DAILY_GOAL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        with(NotificationManagerCompat.from(context)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                NotificationManagerCompat.from(context).notify(DAILY_GOAL_NOTIFICATION_ID, notification)
            }
        }
    }

    fun showEncouragingNotification(context: Context, target: Int, currentSteps: Int) {
        if (!AppPreferences.encouragingNotifications || target <= 0) return

        val progressPercentage = (currentSteps.toFloat() / target * 100).toInt()
        if (progressPercentage >= 100) { return }

        if (progressPercentage >= 75 && !shown75PercentNotification) {
            shown75PercentNotification = true
            sendEncouragingNotification(context, target, currentSteps, progressPercentage, true)
        } else if (progressPercentage >= 15 && !shown15PercentNotification) {
            shown15PercentNotification = true
            sendEncouragingNotification(context, target, currentSteps, progressPercentage, false)
        }
    }

    private fun sendEncouragingNotification(context: Context, target: Int, currentSteps: Int,
                                            progressPercentage: Int, isHighProgress: Boolean) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = context.getString(R.string.encouraging_notification_title)
        val message = if (isHighProgress) {
            val messages = listOf(
                R.string.encouraging_notification_75_percent,
                R.string.encouraging_notification_75_percent_alt1,
                R.string.encouraging_notification_75_percent_alt2,
                R.string.encouraging_notification_75_percent_alt3
            )
            val randomMessage = messages.random()
            context.getString(randomMessage, progressPercentage, target - currentSteps)
        } else {
            val messages = listOf(
                R.string.encouraging_notification_15_percent,
                R.string.encouraging_notification_15_percent_alt1,
                R.string.encouraging_notification_15_percent_alt2,
                R.string.encouraging_notification_15_percent_alt3
            )
            val randomMessage = messages.random()
            context.getString(randomMessage, progressPercentage, target - currentSteps)
        }

        val notification = NotificationCompat.Builder(context, DAILY_GOAL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        with(NotificationManagerCompat.from(context)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                NotificationManagerCompat.from(context).notify(ENCOURAGING_NOTIFICATION_ID, notification)
            }
        }
    }

    fun resetEncouragingNotificationFlags() {
        shown15PercentNotification = false
        shown75PercentNotification = false
    }
}