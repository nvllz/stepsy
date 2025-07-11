/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.nvllz.stepsy.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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
import android.os.PowerManager
import android.os.ResultReceiver
import androidx.core.app.NotificationCompat
import android.text.format.DateUtils
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.nvllz.stepsy.R
import com.nvllz.stepsy.ui.MainActivity
import com.nvllz.stepsy.util.Database
import com.nvllz.stepsy.util.Util
import java.util.*
import com.nvllz.stepsy.util.AppPreferences
import com.nvllz.stepsy.util.GoalNotificationWorker
import com.nvllz.stepsy.util.Util.getDistanceUnitString
import com.nvllz.stepsy.util.WidgetManager
import java.text.NumberFormat

internal class MotionService : Service() {
    private var mTodaysSteps: Int = 0
    private var mLastSteps = -1
    private var mCurrentDate: Long = 0
    private var mCachedDailyTarget: Int = 0
    private var mCachedShowProgressbar: Boolean = false
    private var receiver: ResultReceiver? = null
    private lateinit var mListener: SensorEventListener
    private lateinit var mNotificationManager: NotificationManager
    private lateinit var mBuilder: NotificationCompat.Builder
    private var isCountingPaused = false
    private var goalReachedToday = false

    private val pauseChannelId = "com.nvllz.stepsy.PAUSE_CHANNEL_ID"
    private val pauseNotificationId = 3844

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        Log.d(TAG, "Creating MotionService")
        startService()

        mCurrentDate = AppPreferences.date
        mTodaysSteps = AppPreferences.steps
        mCachedDailyTarget = AppPreferences.dailyGoalTarget
        mCachedShowProgressbar = AppPreferences.dailyGoalNotificationProgressbar
        isCountingPaused = getSharedPreferences("StepsyPrefs", MODE_PRIVATE).getBoolean(KEY_IS_PAUSED, false)
        goalReachedToday = AppPreferences.dailyGoalNotification
                && AppPreferences.dailyGoalTarget > 0
                && mTodaysSteps >= AppPreferences.dailyGoalTarget

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
            Toast.makeText(this, getString(R.string.no_activity_permission), Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val delayedWriteRunnable = Runnable {
        handleStepUpdate(delayedTrigger = true)
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

            // Check if goal was just reached
            val target = AppPreferences.dailyGoalTarget
            if (target > 0 && mTodaysSteps >= target && !goalReachedToday) {
                goalReachedToday = true
                GoalNotificationWorker.showDailyGoalNotification(this, target)
            }

            handleStepUpdate()

            // reset the delayed write runnable
            handler.removeCallbacks(delayedWriteRunnable)
            handler.postDelayed(delayedWriteRunnable, dbWriteInterval)
        } else {
            mLastSteps = value
        }
    }

    private var lastSharedPrefsWriteTime: Long = 0
    private var lastDbWriteTime: Long = 0
    private var lastWidgetUpdateTime: Long = 0

    private val dataStoreWriteInterval: Long
        get() = if (isBatterySavingEnabled(this)) 15_000L else 7_500L
    private val dbWriteInterval: Long
        get() = if (isBatterySavingEnabled(this)) 60_000L else 30_000L
    private val widgetsUpdateInterval: Long
        get() = if (isBatterySavingEnabled(this)) 15_000L else 7_500L

    private fun handleStepUpdate(manualStepCountChange: Boolean = false, delayedTrigger: Boolean = false) {
        val currentTime = System.currentTimeMillis()

        if (!DateUtils.isToday(mCurrentDate)) {
            val currentDate = Util.calendar.timeInMillis

            if (!manualStepCountChange) {
                Database.getInstance(this).addEntry(mCurrentDate, mTodaysSteps)
                mTodaysSteps = 0
            }

            mCurrentDate = currentDate
            mLastSteps = -1
            goalReachedToday = false
            AppPreferences.date = mCurrentDate
            AppPreferences.steps = mTodaysSteps
            lastSharedPrefsWriteTime = currentTime.also { lastDbWriteTime = it }
        }

        if (currentTime - lastSharedPrefsWriteTime >= dataStoreWriteInterval && !manualStepCountChange) {
            AppPreferences.steps = mTodaysSteps
            lastSharedPrefsWriteTime = currentTime
        }

        if (currentTime - lastDbWriteTime >= dbWriteInterval || manualStepCountChange) {
            Database.getInstance(this).addEntry(mCurrentDate, mTodaysSteps)
            lastDbWriteTime = currentTime
        }

        if (currentTime - lastWidgetUpdateTime >= widgetsUpdateInterval || delayedTrigger || manualStepCountChange) {
            updateAllWidgets()
            lastWidgetUpdateTime = currentTime
        }

        sendUpdate()
    }

    private fun updateAllWidgets() {
        WidgetManager.updateAllWidgets(
            context = applicationContext,
            steps = mTodaysSteps,
            immediate = true
        )
    }


    private fun sendUpdate() {
        sendBroadcast(Intent("com.nvllz.stepsy.STATE_UPDATE"))

        if (isCountingPaused) {
            sendPauseNotification()
            sendBundleUpdate(isCountingPaused)
            return
        } else {
            dismissPauseNotification()
        }

        val builder = createStepsNotification(mCachedShowProgressbar, mCachedDailyTarget)
        mNotificationManager.notify(FOREGROUND_ID, builder.build())

        sendBundleUpdate(isCountingPaused)
    }

    private fun createStepsNotification(
        showProgressbar: Boolean = AppPreferences.dailyGoalNotificationProgressbar,
        dailyTarget: Int = AppPreferences.dailyGoalTarget
    ): NotificationCompat.Builder {

        fun formatNumber(number: Int) = if (number >= 10_000) {
            NumberFormat.getIntegerInstance(Locale.getDefault()).format(number)
        } else {
            number.toString()
        }

        val formattedSteps = formatNumber(mTodaysSteps)
        val formattedTarget = formatNumber(dailyTarget)

        val stepsPlural = resources.getQuantityString(R.plurals.steps_text, mTodaysSteps, formattedSteps)
        val stepGoalPercentage = (mTodaysSteps.toFloat() / dailyTarget * 100).toInt()

        val notificationTextProgress = getString(R.string.notification_step_goal_progress)
            .format(Locale.getDefault(), stepGoalPercentage, formattedTarget)

        val notificationTextRaw = getString(R.string.steps_format)
            .format(Locale.getDefault(), stepsPlural, Util.stepsToDistance(mTodaysSteps), getDistanceUnitString())

        val pausePendingIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MotionService::class.java).apply { action = ACTION_PAUSE_COUNTING },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, STEP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(R.drawable.ic_notification, getString(R.string.action_pause), pausePendingIntent)
            .apply {
                if (showProgressbar) {
                    val progress = stepGoalPercentage.coerceIn(0, 100)
                    if (progress < 100) {
                        setStyle(NotificationCompat.BigTextStyle().bigText(notificationTextProgress))
                        setContentText(notificationTextProgress)
                        setProgress(100, progress, false)
                    } else {
                        setContentText(getString(R.string.notification_step_goal_completed))
                    }
                    setContentTitle(notificationTextRaw)
                } else {
                    setContentText(notificationTextRaw)
                }
            }
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

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val pauseNotification = NotificationCompat.Builder(this, pauseChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_step_counting_paused))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(
                R.drawable.ic_notification,
                getString(R.string.action_resume),
                resumePendingIntent
            )
            .build()

        mNotificationManager.notify(pauseNotificationId, pauseNotification)
    }

    private fun dismissPauseNotification() {
        mNotificationManager.cancel(pauseNotificationId)
    }

    fun isBatterySavingEnabled(context: Context): Boolean {
        val powerManager = context.getSystemService(POWER_SERVICE) as PowerManager
        return powerManager.isPowerSaveMode
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Received start id $startId: $intent")

        intent?.let {
            when (it.action) {
                ACTION_SUBSCRIBE -> receiver = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    it.getParcelableExtra(MainActivity.RECEIVER_TAG, ResultReceiver::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    it.getParcelableExtra(MainActivity.RECEIVER_TAG)
                }
                ACTION_PAUSE_COUNTING -> {
                    isCountingPaused = true
                    Toast.makeText(this, R.string.step_counting_paused, Toast.LENGTH_SHORT).show()
                }
                ACTION_RESUME_COUNTING -> {
                    isCountingPaused = false
                    Toast.makeText(this, R.string.step_counting_resumed, Toast.LENGTH_SHORT).show()
                }
                "UPDATE_NOTIFICATION" -> {
                    mCachedShowProgressbar = intent.getBooleanExtra("show_progressbar", mCachedShowProgressbar)
                    mCachedDailyTarget = intent.getIntExtra("daily_target", mCachedDailyTarget)

                    val builder = createStepsNotification(mCachedShowProgressbar, mCachedDailyTarget)
                    mNotificationManager.notify(FOREGROUND_ID, builder.build())
                }
            }

            getSharedPreferences("StepsyPrefs", MODE_PRIVATE).edit {
                putBoolean(
                    KEY_IS_PAUSED,
                    isCountingPaused
                )
            }

            // handle forced update
            if (it.hasExtra("FORCE_UPDATE")) {
                mTodaysSteps = it.getIntExtra(KEY_STEPS, mTodaysSteps)
                mCurrentDate = it.getLongExtra(KEY_DATE, mCurrentDate)
                mLastSteps = -1 // reset step counter to avoid incorrect delta calculations
                AppPreferences.steps = mTodaysSteps
                AppPreferences.date = mCurrentDate
                handleStepUpdate()
            }

            // handle manual step count update
            if (it.hasExtra("MANUAL_STEP_COUNT_CHANGE")) {
                mTodaysSteps = it.getIntExtra(KEY_STEPS, mTodaysSteps)
                mCurrentDate = it.getLongExtra(KEY_DATE, mCurrentDate)
                mLastSteps = -1 // reset step counter to avoid incorrect delta calculations
                AppPreferences.steps = mTodaysSteps
                AppPreferences.date = mCurrentDate
                handleStepUpdate(manualStepCountChange = true)
            }

            sendUpdate()
        }

        return START_STICKY
    }

    private fun startService() {
        val pauseIntent = Intent(this, MotionService::class.java).apply {
            action = ACTION_PAUSE_COUNTING
        }

        val pausePendingIntent = PendingIntent.getService(
            this,
            1,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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
            .addAction(
                R.drawable.ic_notification,
                getString(R.string.action_pause),
                pausePendingIntent
            )
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
        if (mNotificationManager.getNotificationChannel(pauseChannelId) == null) {
            val pauseNotificationChannel = NotificationChannel(
                pauseChannelId,
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