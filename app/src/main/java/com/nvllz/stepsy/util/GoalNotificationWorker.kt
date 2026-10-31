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
import com.nvllz.stepsy.service.MuteEncouragingReceiver
import com.nvllz.stepsy.service.MuteMilestoneReceiver
import com.nvllz.stepsy.ui.AchievementsActivity
import com.nvllz.stepsy.ui.MainActivity
import java.text.NumberFormat

object GoalNotificationWorker {
    private const val DAILY_GOAL_NOTIFICATION_ID = 1001
    private const val ENCOURAGING_NOTIFICATION_ID = 1002
    private const val MAX_ENCOURAGING_PER_DAY = 2

    private var stateDay: String? = null
    private var sentToday = 0
    private val sentThresholds = mutableSetOf<Int>()
    private var cachedThresholds: Set<Int>? = null

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

        val targetFormatted = Util.formatSteps(target)
        val targetString = context.resources.getQuantityString(
            R.plurals.steps_formatted,
            target,
            targetFormatted
        )

        val title = context.getString(R.string.goal_achieved_title)
        val message = context.getString(R.string.goal_achieved_message, targetString)

        val notification = NotificationCompat.Builder(context, NotificationChannels.GOAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
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

        if (demo) {
            sendEncouragingNotification(context, target, currentSteps, progressPercentage.coerceIn(1, 99))
            return
        }

        if (progressPercentage >= 100) return

        syncDayState()
        if (sentToday >= MAX_ENCOURAGING_PER_DAY) return

        val crossed = thresholds().filter { progressPercentage >= it && it !in sentThresholds }
        if (crossed.isEmpty()) return

        sentThresholds.addAll(crossed)
        sentToday++
        persistDayState()
        sendEncouragingNotification(context, target, currentSteps, progressPercentage)
    }

    fun invalidateEncouragingSettings() {
        cachedThresholds = null
    }

    private fun thresholds(): Set<Int> =
        cachedThresholds ?: AppPreferences.encouragingThresholds.also { cachedThresholds = it }

    private fun syncDayState() {
        val today = Util.todayDateString()
        if (stateDay == today) return
        stateDay = today
        sentToday = 0
        sentThresholds.clear()
        val parts = AppPreferences.encouragingState.split('|')
        if (parts.size == 3 && parts[0] == today) {
            sentToday = parts[1].toIntOrNull() ?: 0
            sentThresholds += parts[2].split(',').mapNotNull { it.toIntOrNull() }
        }
    }

    private fun persistDayState() {
        val day = stateDay ?: return
        AppPreferences.encouragingState = "$day|$sentToday|${sentThresholds.joinToString(",")}"
    }

    private fun sendEncouragingNotification(context: Context, target: Int, currentSteps: Int,
                                            progressPercentage: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val percent = NumberFormat.getPercentInstance().format(progressPercentage / 100.0)
        val remaining = (target - currentSteps).coerceAtLeast(0)
        val left = context.resources.getQuantityString(
            R.plurals.steps_formatted, remaining, Util.formatSteps(remaining),
        )
        val messages = if (progressPercentage >= 70) {
            listOf(R.string.encouraging_late_1, R.string.encouraging_late_2)
        } else {
            listOf(R.string.encouraging_early_1, R.string.encouraging_early_2)
        }
        val title = context.getString(R.string.encouraging_title)
        val message = context.getString(messages.random(), percent, left)

        val mutePendingIntent = PendingIntent.getBroadcast(
            context,
            ENCOURAGING_NOTIFICATION_ID,
            Intent(context, MuteEncouragingReceiver::class.java).apply {
                putExtra(MuteEncouragingReceiver.EXTRA_NOTIFICATION_ID, ENCOURAGING_NOTIFICATION_ID)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.mute_notifications_action),
                mutePendingIntent,
            )
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

        val channelId = NotificationChannels.MILESTONE

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

        val badge = Util.milestoneBadge(milestone.toInt())

        val shortMilestone = Util.formatShortSteps(milestone, context)

        val fullMilestone = NumberFormat.getIntegerInstance().format(milestone)

        val notificationId = 5000 + (milestone / 1000).toInt().coerceAtMost(50000)

        val pendingIntent = TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(
                Intent(context, AchievementsActivity::class.java)
                    .putExtra(AchievementsActivity.EXTRA_MILESTONE, milestone.toInt())
            )
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
            .addAction(R.drawable.ic_notification, context.getString(R.string.mute_notifications_action), mutePendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    fun resetEncouragingNotificationFlags() {
        stateDay = null
        sentToday = 0
        sentThresholds.clear()
        cachedThresholds = null
        AppPreferences.encouragingState = ""
    }
}