package com.nvllz.stepsy.util

import android.content.Context
import androidx.core.content.edit
import com.nvllz.stepsy.R
import java.text.SimpleDateFormat
import java.util.*

object TimedPauseManager {
    private const val TAG = "TimedPauseManager"
    private const val PREFS_NAME = "TimedPausePrefs"
    private const val KEY_PAUSE_END_TIME = "pause_end_time"
    private const val KEY_ORIGINAL_PAUSE_DURATION = "original_pause_duration"

    fun setPauseEndTime(context: Context, endTimeMillis: Long, durationMinutes: Int) {

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putLong(KEY_PAUSE_END_TIME, endTimeMillis)
            putInt(KEY_ORIGINAL_PAUSE_DURATION, durationMinutes)
            commit() // Use commit() to ensure immediate write
        }
    }

    fun getPauseEndTime(context: Context): Long {
        val endTime = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_PAUSE_END_TIME, 0L)
        return endTime
    }

    fun clearPauseEndTime(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            remove(KEY_PAUSE_END_TIME)
            remove(KEY_ORIGINAL_PAUSE_DURATION)
            commit()
        }
    }

    fun isTimedPauseActive(context: Context): Boolean {
        val endTime = getPauseEndTime(context)
        val now = System.currentTimeMillis()
        val isActive = endTime > 0L && now < endTime

        return isActive
    }

    fun shouldResumeCounting(context: Context): Boolean {
        val endTime = getPauseEndTime(context)
        val now = System.currentTimeMillis()
        val shouldResume = endTime > 0L && now >= endTime

        return shouldResume
    }

    fun getRemainingTimeText(context: Context): String? {
        val endTime = getPauseEndTime(context)
        if (endTime <= 0L) {
            return null
        }

        val now = System.currentTimeMillis()
        if (now >= endTime) {
            return null
        }

        val endTimeFormatted = SimpleDateFormat("HH:mm", Locale.getDefault())
            .format(Date(endTime))

        val result = context.getString(R.string.step_counting_paused_until, endTimeFormatted)

        return result
    }
}