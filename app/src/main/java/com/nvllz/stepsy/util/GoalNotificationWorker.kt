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
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import com.nvllz.stepsy.R
import com.nvllz.stepsy.service.MuteMilestoneReceiver
import com.nvllz.stepsy.ui.AchievementsActivity
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
            NotificationManager.IMPORTANCE_HIGH
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

    fun showEncouragingNotification(context: Context, target: Int, currentSteps: Int, demo: Boolean = false) {
        if (!AppPreferences.encouragingNotifications || target <= 0) return

        val progressPercentage = (currentSteps.toFloat() / target * 100).toInt()
        if (progressPercentage >= 100) return

        if (progressPercentage >= 75 && !shown75PercentNotification) {
            shown75PercentNotification = true
            sendEncouragingNotification(context, target, currentSteps, progressPercentage, true)
        } else if (progressPercentage >= 15 && !shown15PercentNotification) {
            shown15PercentNotification = true
            sendEncouragingNotification(context, target, currentSteps, progressPercentage, false)
        } else if (demo) {
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
            .setPriority(NotificationCompat.PRIORITY_HIGH)
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

    fun showMilestoneNotification(context: Context, milestone: Long) {
        val notificationManager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        val channelId = "com.nvllz.stepsy.MILESTONE_CHANNEL_ID"

        if (notificationManager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.notification_category_milestone),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_description_milestone)
                setSound(null, null)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val badge = when {
            milestone >= 20_000_000L -> "🏁"
            milestone >= 15_000_000L -> "♾️"
            milestone >= 12_500_000L -> "🪬"
            milestone >= 10_000_000L -> "👑"
            milestone >=  9_000_000L -> "🦄"
            milestone >=  8_000_000L -> "🐉"
            milestone >=  7_000_000L -> "💫"
            milestone >=  6_000_000L -> "🏆"
            milestone >=  5_000_000L -> "💎"
            milestone >=  4_000_000L -> "🪐"
            milestone >=  3_000_000L -> "🚀"
            milestone >=  2_000_000L -> "🥇"
            milestone >=  1_500_000L -> "⚡"
            milestone >=  1_000_000L -> "🗿"
            milestone >=    750_000L -> "⛳"
            milestone >=    500_000L -> "🌟"
            milestone >=    100_000L -> "🔥"
            milestone >=     50_000L -> "💪"
            else                     -> "🎯"
        }

        fun formatNumber(number: Long) = NumberFormat.getIntegerInstance(Locale.getDefault()).format(number)

        val shortMilestone = when {
            milestone >= 1_000_000L -> {
                val millions = milestone / 1_000_000.0
                if (millions == millions.toInt().toDouble()) {
                    context.getString(R.string.milestone_short_millions, millions.toInt())
                } else {
                    context.getString(R.string.milestone_short_millions_decimal, millions)
                }
            }
            milestone >= 1_000L -> {
                context.getString(R.string.milestone_short_thousands, (milestone / 1_000).toInt())
            }
            else -> formatNumber(milestone)
        }

        val fullMilestone = formatNumber(milestone)

        val notificationId = 5000 + (milestone / 1000).toInt().coerceAtMost(50000)

        val pendingIntent = TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(Intent(context, AchievementsActivity::class.java))
            getPendingIntent(notificationId, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        val mutePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            Intent(context, MuteMilestoneReceiver::class.java).apply {
                putExtra(MuteMilestoneReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.milestone_notification_title, "$badge $shortMilestone"))
            .setContentText(context.getString(R.string.milestone_notification_text, fullMilestone))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup("com.nvllz.stepsy.MILESTONE_GROUP")
            .setGroupSummary(false)
            .addAction(R.drawable.ic_notification, context.getString(R.string.mute_milestone_notifications), mutePendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    fun resetEncouragingNotificationFlags() {
        shown15PercentNotification = false
        shown75PercentNotification = false
    }
}