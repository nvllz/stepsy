package com.nvllz.stepsy.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nvllz.stepsy.util.Util

class MidnightResetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "MidnightResetReceiver triggered")

        Util.refreshDayStartCache()

        val serviceIntent = Intent(context, MotionService::class.java).apply {
            putExtra("FORCE_UPDATE", true)
        }
        context.startService(serviceIntent)

        scheduleNextMidnightAlarm(context)
    }

    companion object {
        private const val TAG = "MidnightResetReceiver"
        private const val REQUEST_CODE = 9876

        fun scheduleNextMidnightAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, MidnightResetReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerTime = Util.nextLabelChangeMs(System.currentTimeMillis()) + 1_000L

            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                10 * 60 * 1000L,
                pendingIntent
            )

            Log.d(TAG, "Scheduled next day change alarm for: $triggerTime")
        }

        fun cancelMidnightAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, MidnightResetReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled midnight alarm")
        }
    }
}