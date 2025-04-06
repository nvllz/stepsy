/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.nvllz.stepsy.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ResultReceiver
import android.preference.PreferenceManager
import androidx.core.app.NotificationCompat
import android.text.format.DateUtils
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.nvllz.stepsy.R
import com.nvllz.stepsy.ui.MainActivity
import com.nvllz.stepsy.util.Database
import com.nvllz.stepsy.util.Util
import java.util.*
import androidx.core.content.edit

internal class MotionService : Service() {
    private lateinit var sharedPreferences: SharedPreferences
    private var mTodaysSteps: Int = 0
    private var mLastSteps = -1
    private var mCurrentDate: Long = 0
    private var receiver: ResultReceiver? = null
    private lateinit var mListener: SensorEventListener
    private lateinit var mNotificationManager: NotificationManager
    private lateinit var mBuilder: NotificationCompat.Builder
    private var isCountingPaused = false

    private val PAUSE_CHANNEL_ID = "com.nvllz.stepsy.PAUSE_CHANNEL_ID"
    private val PAUSE_NOTIFICATION_ID = 3844

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        Log.d(TAG, "Creating MotionService")
        startService()

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        mCurrentDate = sharedPreferences.getLong(KEY_DATE, Util.calendar.timeInMillis)
        mTodaysSteps = sharedPreferences.getInt(KEY_STEPS, 0)

        isCountingPaused = sharedPreferences.getBoolean(KEY_IS_PAUSED, false)

        val mSensorManager = getSystemService(SENSOR_SERVICE) as? SensorManager
            ?: throw IllegalStateException("Could not get sensor service")

        if (packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER) &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED)
        ) {
            Log.d(TAG, "Using step counter sensor")
            val mStepSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

            if (mStepSensor == null) {
                Toast.makeText(this, getString(R.string.no_sensor), Toast.LENGTH_LONG).show()
                stopSelf()
                return
            }

            mListener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    handleEvent(event.values[0].toInt())
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
            }

            mSensorManager.registerListener(mListener, mStepSensor, SensorManager.SENSOR_DELAY_UI, 1000000)
        } else {
            Toast.makeText(this, getString(R.string.no_sensor), Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val delayedWriteRunnable = Runnable {
        handleStepUpdate()
    }

    private fun handleEvent(value: Int) {
        if (!isCountingPaused) {
            if (mLastSteps == -1 || value < mLastSteps) {
                mLastSteps = value
                return
            }

            val delta = value - mLastSteps
            mTodaysSteps += delta
            mLastSteps = value

            handleStepUpdate()

            // reset the delayed write runnable
            handler.removeCallbacks(delayedWriteRunnable)
            handler.postDelayed(delayedWriteRunnable, 10_000)

        } else {
            mLastSteps = value
        }
    }

    private var lastWriteTime: Long = 0
    private val writeInterval = 10000

    private fun handleStepUpdate() {
        val currentDate = Util.calendar.timeInMillis

        if (!DateUtils.isToday(mCurrentDate)) {
            Database.getInstance(this).addEntry(mCurrentDate, mTodaysSteps)
            mTodaysSteps = 0
            mCurrentDate = currentDate
            mLastSteps = -1
            sharedPreferences.edit {
                putInt(KEY_STEPS, mTodaysSteps)
                putLong(KEY_DATE, mCurrentDate)
            }
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastWriteTime > writeInterval) {
            Database.getInstance(this).addEntry(mCurrentDate, mTodaysSteps)
            sharedPreferences.edit {
                putInt(KEY_STEPS, mTodaysSteps)
            }
            lastWriteTime = currentTime
        }

        sendUpdate()
    }

    private fun sendUpdate() {
        if (isCountingPaused) {
            sendPauseNotification()
            sendBundleUpdate(isCountingPaused)
            return
        } else {
            dismissPauseNotification()
        }

        val notificationText = String.format(
            Locale.getDefault(),
            getString(R.string.steps_format),
            Util.stepsToMeters(mTodaysSteps),
            mTodaysSteps
        )

        mBuilder.setContentText(notificationText)
        mNotificationManager.notify(FOREGROUND_ID, mBuilder.build())

        sendBundleUpdate(isCountingPaused)
    }

    private fun sendBundleUpdate(paused: Boolean = false) {
        receiver?.let {
            val bundle = Bundle().apply {
                putInt(KEY_STEPS, mTodaysSteps)
                if (paused) putBoolean(KEY_IS_PAUSED, true)
            }
            it.send(0, bundle)
        }
    }


    private fun sendPauseNotification() {
        val resumeIntent = Intent(this, MotionService::class.java).apply {
            action = ACTION_RESUME_COUNTING
        }

        val resumePendingIntent = PendingIntent.getService(
            this,
            0,
            resumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseNotification = NotificationCompat.Builder(this, PAUSE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_step_counting_paused))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(
                R.drawable.ic_notification,
                getString(R.string.resume),
                resumePendingIntent
            )
            .build()

        mNotificationManager.notify(PAUSE_NOTIFICATION_ID, pauseNotification)
    }

    private fun dismissPauseNotification() {
        mNotificationManager.cancel(PAUSE_NOTIFICATION_ID)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Received start id $startId: $intent")

        intent?.let {
            when (it.action) {
                ACTION_SUBSCRIBE -> receiver = it.getParcelableExtra(MainActivity.RECEIVER_TAG)
                ACTION_PAUSE_COUNTING -> {
                    isCountingPaused = true
                    sharedPreferences.edit { putBoolean(KEY_IS_PAUSED, true) }
                    Toast.makeText(this, R.string.step_counting_paused, Toast.LENGTH_SHORT).show()
                }
                ACTION_RESUME_COUNTING -> {
                    isCountingPaused = false
                    sharedPreferences.edit { putBoolean(KEY_IS_PAUSED, false) }
                    Toast.makeText(this, R.string.step_counting_resumed, Toast.LENGTH_SHORT).show()
                }
            }

            // handle forced update with new values
            if (it.hasExtra("FORCE_UPDATE")) {
                mTodaysSteps = it.getIntExtra(KEY_STEPS, mTodaysSteps)
                mCurrentDate = it.getLongExtra(KEY_DATE, mCurrentDate)
                mLastSteps = -1 // reset step counter to avoid incorrect delta calculations
                sharedPreferences.edit {
                    putInt(KEY_STEPS, mTodaysSteps)
                    putLong(KEY_DATE, mCurrentDate)
                }
            }

            sendUpdate()
        }

        return START_STICKY
    }

    private fun startService() {
        mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
            ?: throw IllegalStateException("Could not get notification service")

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        createStepNotificationChannel()
        createPauseNotificationChannel()

        mBuilder = NotificationCompat.Builder(this, STEP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOnlyAlertOnce(true)
        startForeground(FOREGROUND_ID, mBuilder.build())
    }

    private fun createStepNotificationChannel() {
        if (mNotificationManager.getNotificationChannel(STEP_CHANNEL_ID) == null) {
            val stepNotificationChannel = NotificationChannel(
                STEP_CHANNEL_ID,
                getString(R.string.notification_category_steps_day),
                NotificationManager.IMPORTANCE_MIN
            )
            stepNotificationChannel.description = getString(R.string.notification_description_steps_day)
            mNotificationManager.createNotificationChannel(stepNotificationChannel)
        }
    }

    private fun createPauseNotificationChannel() {
        if (mNotificationManager.getNotificationChannel(PAUSE_CHANNEL_ID) == null) {
            val pauseNotificationChannel = NotificationChannel(
                PAUSE_CHANNEL_ID,
                getString(R.string.notification_category_counting_paused),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            pauseNotificationChannel.description = getString(R.string.notification_description_paused)
            mNotificationManager.createNotificationChannel(pauseNotificationChannel)
        }
    }

    companion object {
        private val TAG = MotionService::class.java.simpleName
        internal const val ACTION_SUBSCRIBE = "ACTION_SUBSCRIBE"
        internal const val KEY_STEPS = "STEPS"
        internal const val KEY_DATE = "DATE"
        internal const val KEY_IS_PAUSED = "IS_PAUSED"
        internal const val ACTION_PAUSE_COUNTING = "ACTION_PAUSE_COUNTING"
        internal const val ACTION_RESUME_COUNTING = "ACTION_RESUME_COUNTING"
        private const val FOREGROUND_ID = 3843
        private const val STEP_CHANNEL_ID = "com.nvllz.stepsy.STEP_CHANNEL_ID"
    }
}