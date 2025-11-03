/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.nvllz.stepsy.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.*

class MidnightResetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "MidnightResetReceiver triggered")

        val serviceIntent = Intent(context, MotionService::class.java).apply {
            putExtra("FORCE_UPDATE", true)
            putExtra(MotionService.KEY_STEPS, 0)
            putExtra(MotionService.KEY_DATE, System.currentTimeMillis())
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

            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 1)
                set(Calendar.MILLISECOND, 0)
            }

            val triggerTime = calendar.timeInMillis

            // Using setWindow for battery efficiency
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                10 * 60 * 1000L,
                pendingIntent
            )

            Log.d(TAG, "Scheduled next midnight alarm for: ${calendar.time}")
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