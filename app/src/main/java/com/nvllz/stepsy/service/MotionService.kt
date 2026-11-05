package com.nvllz.stepsy.service

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.ResultReceiver
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.nvllz.stepsy.R
import com.nvllz.stepsy.ui.MainActivity
import com.nvllz.stepsy.util.Database
import com.nvllz.stepsy.util.Util
import java.util.*
import com.nvllz.stepsy.util.AppPreferences
import com.nvllz.stepsy.util.GoalNotificationWorker
import com.nvllz.stepsy.util.TimedPauseManager
import com.nvllz.stepsy.util.Util.distanceUnit
import com.nvllz.stepsy.util.WidgetManager

internal class MotionService : Service() {
    private var mTodaysSteps: Int = 0
    private var mLastSteps = -1
    private var mCurrentDate: String = ""
    private var mCachedDailyTarget: Int = 0
    private var mCachedShowProgressbar: Boolean = false
    private var receiver: ResultReceiver? = null
    private lateinit var mListener: SensorEventListener
    private lateinit var mNotificationManager: NotificationManager
    private var isCountingPaused = false
    private var goalReachedToday = false
    private lateinit var activityRecognitionManager: ActivityRecognitionManager
    private var mBaseTotal: Long = 0L
    private var mLastNotifiedMilestone: Long = 0L
    private var mCachedVehicleFilter: Boolean = false
    private var mCachedEncouraging: Boolean = false
    private var mCachedHourly: Boolean? = null
    private val mSlots = IntArray(Database.SLOT_COUNT)
    private var mSlotDirty = 0L
    private var mSlotWindowStart = 0L
    private var mSlotWindowEnd = 0L
    private var mSlotWindowSlot = -1
    private var mSlotWindowDate = ""
    private var mSensorManager: SensorManager? = null

    private val pauseChannelId = "com.nvllz.stepsy.PAUSE_CHANNEL_ID"
    private val pauseNotificationId = 3844
    private val notificationUpdateInterval: Long
        get() = if (isBatterySavingEnabled(this)) 5_000L else 2_500L
    private var lastNotificationUpdateTime: Long = 0

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        Log.d(TAG, "Creating MotionService")

        serviceThread = HandlerThread(TAG)
        serviceThread.start()
        handler = Handler(serviceThread.getLooper())

        startService()

        handler.post {
            Util.refreshDayStartCache()
            MidnightResetReceiver.scheduleNextMidnightAlarm(this@MotionService)
            checkForExistingTimedPause()

            mCurrentDate = AppPreferences.date
            mTodaysSteps = AppPreferences.steps
            mCachedShowProgressbar = AppPreferences.dailyGoalNotificationProgressbar
            isCountingPaused = AppPreferences.isPaused

            if (mCurrentDate.isEmpty()) {
                mCurrentDate = Util.todayDateString()
                mTodaysSteps = Database.getInstance(this@MotionService).getSumSteps(mCurrentDate, mCurrentDate)
                AppPreferences.date = mCurrentDate
            }

            refreshCachedSettings(force = true)
            seedSlots()
            recalcBaseTotal()
            mLastNotifiedMilestone = AppPreferences.lastNotifiedMilestone
            if (mLastNotifiedMilestone <= 0L) {
                mLastNotifiedMilestone = Util.MILESTONES.lastOrNull { it <= mBaseTotal + mTodaysSteps } ?: 0L
                AppPreferences.lastNotifiedMilestone = mLastNotifiedMilestone
            }

            goalReachedToday = AppPreferences.dailyGoalNotification
                    && AppPreferences.dailyGoalTarget > 0
                    && mTodaysSteps >= AppPreferences.dailyGoalTarget

            val sensorManager = getSystemService(SENSOR_SERVICE) as? SensorManager
                ?: throw IllegalStateException("Could not get sensor service")

            if (packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER) &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || ContextCompat.checkSelfPermission(this@MotionService, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED)
            ) {
                Log.d(TAG, "Using step counter sensor")
                val mStepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

                if (mStepSensor == null) {
                    toastOnMain(R.string.no_sensor)
                    stopSelf()
                    return@post
                }

                mSensorManager = sensorManager
                mListener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        handleEvent(event.values[0].toInt(), event.timestamp)
                    }

                    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
                }

                sensorManager.registerListener(mListener, mStepSensor, SensorManager.SENSOR_DELAY_UI, 1000000, handler)
            } else {
                stopSelf()
                return@post
            }

            activityRecognitionManager = ActivityRecognitionManager(this@MotionService)
            activityRecognitionManager.start()
        }
    }

    private lateinit var serviceThread: HandlerThread
    private lateinit var handler: Handler
    private val delayedWriteRunnable = Runnable {
        handleStepUpdate(delayedTrigger = true)
    }

    private fun handleEvent(value: Int, eventTimestamp: Long) {
        if (!isCountingPaused) {

            refreshCachedSettings()
            val delta = deltaFresh(value)
            if (mCachedVehicleFilter && activityRecognitionManager.isInVehicle) return

            mTodaysSteps += delta
            if (delta > 0 && mCachedHourly == true) recordSlot(eventTimestamp, delta)
            checkMilestones()

            val target = mCachedDailyTarget
            if (target > 0 && mTodaysSteps >= target && !goalReachedToday) {
                goalReachedToday = true
                GoalNotificationWorker.showDailyGoalNotification(this, target)
            }

            if (mCachedEncouraging && !goalReachedToday && target > 0) {
                GoalNotificationWorker.showEncouragingNotification(this, target, mTodaysSteps)
            }

            handleStepUpdate()

            handler.removeCallbacks(delayedWriteRunnable)
            handler.postDelayed(delayedWriteRunnable, dbWriteInterval)
        } else {
            mLastSteps = value
        }
    }

    private var lastPrefReadTime: Long = 0
    private fun refreshCachedSettings(force: Boolean = false) {
        val currentTime = System.currentTimeMillis()
        if (force || currentTime - lastPrefReadTime >= dataStoreWriteInterval) {
            mCachedVehicleFilter = AppPreferences.vehicleFilterEnabled
            mCachedDailyTarget = AppPreferences.dailyGoalTarget
            mCachedEncouraging = AppPreferences.encouragingNotifications
            mCachedHourly = AppPreferences.hourlyBreakdown
            lastPrefReadTime = currentTime
        }
    }

    private fun deltaFresh(value: Int): Int {
        if (mLastSteps == -1 || value < mLastSteps) {
            mLastSteps = value
            return 0
        }
        val delta = value - mLastSteps
        mLastSteps = value
        return delta
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
        val todayStr = Util.todayDateString()

        if (todayStr != mCurrentDate) {
            Database.getInstance(this).addEntry(mCurrentDate, mTodaysSteps)
            flushSlots()

            lastDbWriteTime = currentTime

            val existingSteps = Database.getInstance(this).getSumSteps(todayStr, todayStr)
            val isNewDay = existingSteps == 0

            mTodaysSteps = existingSteps
            mLastSteps = -1

            if (todayStr > mCurrentDate) {
                if (isNewDay) {
                    goalReachedToday = false
                    GoalNotificationWorker.resetEncouragingNotificationFlags()
                } else {
                    val target = AppPreferences.dailyGoalTarget
                    goalReachedToday = target > 0 && mTodaysSteps >= target
                }
            } else {
                val target = AppPreferences.dailyGoalTarget
                goalReachedToday = target > 0 && mTodaysSteps >= target
            }

            mCurrentDate = todayStr
            seedSlots()
            AppPreferences.date = mCurrentDate
            AppPreferences.steps = mTodaysSteps

            recalcBaseTotal()
            checkMilestones()
            lastSharedPrefsWriteTime = currentTime.also { lastDbWriteTime = it }
        }

        if (currentTime - lastSharedPrefsWriteTime >= dataStoreWriteInterval && !manualStepCountChange) {
            AppPreferences.steps = mTodaysSteps
            lastSharedPrefsWriteTime = currentTime
        }

        if (currentTime - lastDbWriteTime >= dbWriteInterval || manualStepCountChange) {
            Database.getInstance(this).addEntry(mCurrentDate, mTodaysSteps)
            flushSlots()
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
        sendBroadcast(Intent("com.nvllz.stepsy.STATE_UPDATE").setPackage(packageName))

        if (isCountingPaused) {
            if (TimedPauseManager.shouldResumeCounting(this)) {
                resumeCountingAutomatically()
                return
            }
            sendPauseNotification()
            sendBundleUpdate(true)
            return
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastNotificationUpdateTime >= notificationUpdateInterval) {
            val builder = createStepsNotification(mCachedShowProgressbar, mCachedDailyTarget)
            startForeground(FOREGROUND_ID, builder.build())
            lastNotificationUpdateTime = currentTime
        }

        sendBundleUpdate(false)
    }

    private fun formatString(context: Context, @StringRes id: Int, vararg args: Any?): String =
        runCatching { context.getString(id).format(Locale.getDefault(), *args) }.getOrElse {
            val en = context.createConfigurationContext(
                Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) }
            ).getString(id)
            runCatching { en.format(Locale.getDefault(), *args) }.getOrDefault("")
        }

    private fun createStepsNotification(
        showProgressbar: Boolean = AppPreferences.dailyGoalNotificationProgressbar,
        dailyTarget: Int = AppPreferences.dailyGoalTarget
    ): NotificationCompat.Builder {

        val formattedSteps = Util.formatSteps(mTodaysSteps)
        val formattedTarget = Util.formatSteps(dailyTarget)

        val stepsPlural = resources.getQuantityString(R.plurals.steps_formatted, mTodaysSteps, formattedSteps)
        val stepsWord = resources.getQuantityString(R.plurals.steps_word, mTodaysSteps)
        val stepGoalPercentage = (mTodaysSteps.toFloat() / dailyTarget * 100).toInt()

        val notificationTextProgress = formatString(this, R.string.notification_step_goal_progress,
            Util.stepsToDistance(mTodaysSteps), distanceUnit(), Util.stepsToCalories(mTodaysSteps))

        val notificationTitleRaw = formatString(this, R.string.notification_step_count_raw,
            stepsPlural, Util.stepsToDistance(mTodaysSteps), distanceUnit())

        val notificationTitleProgress = formatString(this, R.string.notification_step_goal_progress_title,
            formattedSteps, formattedTarget, stepsWord, stepGoalPercentage)

        val pausePendingIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MotionService::class.java).apply { action = ACTION_PAUSE_COUNTING },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationPendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, STEP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(notificationPendingIntent)
            .setAutoCancel(false)
            .setGroup("com.nvllz.stepsy.STEP_GROUP")
            .addAction(R.drawable.ic_notification, getString(R.string.action_pause), pausePendingIntent)
            .apply {
                if (showProgressbar && dailyTarget > 0) {
                    val progress = stepGoalPercentage.coerceIn(0, 100)
                    if (progress < 100) {
                        setContentTitle(notificationTitleProgress)
                        setContentText(notificationTextProgress)
                        setProgress(100, progress, false)
                    } else {
                        setContentTitle(notificationTitleRaw)
                        setContentText(getString(R.string.notification_step_goal_completed))
                    }
                } else {
                    setContentText(notificationTitleRaw)
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
        val pauseNotification = createPauseNotificationBuilder().build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(pauseNotificationId, pauseNotification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(pauseNotificationId, pauseNotification)
        }
    }

    fun isBatterySavingEnabled(context: Context): Boolean {
        val powerManager = context.getSystemService(POWER_SERVICE) as PowerManager
        return powerManager.isPowerSaveMode
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Received start id $startId: $intent")
        handler.post { handleStartCommand(intent) }
        return START_STICKY
    }

    private fun toastOnMain(resId: Int) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleStartCommand(intent: Intent?) {
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

                    val isTimedPause = it.getBooleanExtra("TIMED_PAUSE", false)
                    if (isTimedPause) {
                        val endTime = it.getLongExtra("END_TIME", 0L)
                        val durationMinutes = it.getIntExtra("DURATION_MINUTES", 0)

                        if (endTime > System.currentTimeMillis()) {
                            TimedPauseManager.setPauseEndTime(this, endTime, durationMinutes)
                            startTimedPauseMonitoring()
                        } else {
                            stopTimedPauseMonitoring()
                            TimedPauseManager.clearPauseEndTime(this)
                        }
                    } else {
                        stopTimedPauseMonitoring()
                        TimedPauseManager.clearPauseEndTime(this)
                        if (!it.getBooleanExtra(EXTRA_SUPPRESS_TOAST, false)) {
                            toastOnMain(R.string.step_counting_paused)
                        }
                    }
                }
                ACTION_RESUME_COUNTING -> {
                    isCountingPaused = false
                    stopTimedPauseMonitoring()
                    TimedPauseManager.clearPauseEndTime(this)
                    if (!it.getBooleanExtra(EXTRA_SUPPRESS_TOAST, false)) {
                        toastOnMain(R.string.step_counting_resumed)
                    }
                }
                ACTION_TIMED_PAUSE_END -> {
                    resumeCountingAutomatically()
                    return
                }
                "UPDATE_NOTIFICATION" -> {
                    Util.refreshDayStartCache()
                    MidnightResetReceiver.scheduleNextMidnightAlarm(this@MotionService)
                    refreshCachedSettings(force = true)
                    mCachedShowProgressbar = intent.getBooleanExtra("show_progressbar", mCachedShowProgressbar)
                    mCachedDailyTarget = intent.getIntExtra("daily_target", mCachedDailyTarget)

                    if (intent.getBooleanExtra("clear_hourly", false)) {
                        mSlots.fill(0)
                        mSlotDirty = 0L
                        invalidateSlotWindow()
                        runCatching { Database.getInstance(this).clearAllHourly() }
                            .onFailure { Log.w(TAG, "Failed to clear hourly data", it) }
                    }

                    if (!isCountingPaused) {
                        val builder = createStepsNotification(mCachedShowProgressbar, mCachedDailyTarget)
                        startForeground(FOREGROUND_ID, builder.build())
                    }
                    return
                }
            }

            AppPreferences.isPaused = isCountingPaused

            if (it.hasExtra("FORCE_UPDATE")) {
                mTodaysSteps = it.getIntExtra(KEY_STEPS, mTodaysSteps)
                val dateExtra = it.getStringExtra(KEY_DATE)
                if (!dateExtra.isNullOrEmpty()) mCurrentDate = dateExtra
                mLastSteps = -1
                AppPreferences.steps = mTodaysSteps
                AppPreferences.date = mCurrentDate
                if (it.hasExtra(KEY_STEPS)) seedSlots()
                handleStepUpdate()
                recalcBaseTotal()
                checkMilestones()
            }

            if (it.hasExtra("MANUAL_STEP_COUNT_CHANGE")) {
                val newSteps = it.getIntExtra(KEY_STEPS, mTodaysSteps)
                val diff = newSteps - mTodaysSteps
                if (diff != 0 && mCachedHourly == true) {
                    val minute = Util.dateAndMinuteFor(System.currentTimeMillis()).second
                    runCatching { Database.getInstance(this).addAdjust(mCurrentDate, minute, diff) }
                        .onFailure { Log.w(TAG, "Failed to record hourly adjustment", it) }
                }
                mTodaysSteps = newSteps
                mLastSteps = -1
                AppPreferences.steps = mTodaysSteps
                checkMilestones()
                handleStepUpdate(manualStepCountChange = true)
            }

            sendUpdate()
        }
    }

    private fun startService() {
        mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
            ?: throw IllegalStateException("Could not get notification service")

        createStepNotificationChannel()
        createPauseNotificationChannel()

        if (isCountingPaused) {
            val pauseNotification = createPauseNotificationBuilder().build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(pauseNotificationId, pauseNotification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            } else {
                startForeground(pauseNotificationId, pauseNotification)
            }
        } else {
            val stepNotification = createStepsNotification(mCachedShowProgressbar, mCachedDailyTarget).build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(FOREGROUND_ID, stepNotification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            } else {
                startForeground(FOREGROUND_ID, stepNotification)
            }
        }
    }

    private fun createPauseNotificationBuilder(): NotificationCompat.Builder {
        val resumeIntent = Intent(this, MotionService::class.java).apply {
            action = ACTION_RESUME_COUNTING
        }

        val resumePendingIntent = PendingIntent.getService(
            this, 0, resumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notificationText = if (TimedPauseManager.isTimedPauseActive(this)) {
            TimedPauseManager.getRemainingTimeText(this) ?: getString(R.string.notification_step_counting_paused)
        } else {
            getString(R.string.notification_step_counting_paused)
        }

        return NotificationCompat.Builder(this, pauseChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentText(notificationText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_notification, getString(R.string.action_resume), resumePendingIntent)
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

    private fun startTimedPauseMonitoring() {
        stopTimedPauseMonitoring()

        val endTime = TimedPauseManager.getPauseEndTime(this)
        val now = System.currentTimeMillis()

        if (endTime <= now) {
            resumeCountingAutomatically()
            return
        }

        val pi = PendingIntent.getBroadcast(
            this, PAUSE_ALARM_REQ,
            Intent(this, TimedPauseReceiver::class.java)
                .setAction(ACTION_TIMED_PAUSE_END),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val am = getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endTime, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endTime, pi)
        }
    }

    private fun resumeCountingAutomatically() {
        TimedPauseManager.clearPauseEndTime(this@MotionService)
        isCountingPaused = false

        AppPreferences.isPaused = false

        mLastSteps = -1

        mNotificationManager.cancel(pauseNotificationId)

        sendUpdate()
        toastOnMain(R.string.step_counting_resumed_auto)

        stopTimedPauseMonitoring()
    }

    private fun stopTimedPauseMonitoring() {
        val pi = PendingIntent.getBroadcast(
            this, PAUSE_ALARM_REQ,
            Intent(this, TimedPauseReceiver::class.java)
                .setAction(ACTION_TIMED_PAUSE_END),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        getSystemService(AlarmManager::class.java).cancel(pi)
    }

    private fun checkForExistingTimedPause() {
        if (TimedPauseManager.isTimedPauseActive(this)) {
            isCountingPaused = true
            startTimedPauseMonitoring()
        } else if (TimedPauseManager.shouldResumeCounting(this)) {
            resumeCountingAutomatically()
        }
    }

    private fun recordSlot(eventTimestampNanos: Long, delta: Int) {
        val now = System.currentTimeMillis()
        val age = (SystemClock.elapsedRealtimeNanos() - eventTimestampNanos) / 1_000_000L
        val eventMs = if (age < 0 || age > MAX_EVENT_AGE_MS) now else now - age
        val slot = slotFor(eventMs)
        mSlots[slot] += delta
        mSlotDirty = mSlotDirty or (1L shl slot)
    }

    private fun slotFor(eventMs: Long): Int {
        if (mSlotWindowDate == mCurrentDate && eventMs >= mSlotWindowStart && eventMs < mSlotWindowEnd) {
            return mSlotWindowSlot
        }
        val start = Calendar.getInstance().apply {
            timeInMillis = eventMs
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.HOUR_OF_DAY, 1) }
        val (date, minute) = Util.dateAndMinuteFor(eventMs)
        mSlotWindowStart = start.timeInMillis
        mSlotWindowEnd = end.timeInMillis
        mSlotWindowDate = mCurrentDate
        mSlotWindowSlot = when {
            date == mCurrentDate -> (minute / Database.SLOT_MINUTES).coerceIn(0, Database.SLOT_COUNT - 1)
            date > mCurrentDate   -> Database.SLOT_COUNT - 1
            else                  -> 0
        }
        return mSlotWindowSlot
    }

    private fun invalidateSlotWindow() {
        mSlotWindowStart = 0L
        mSlotWindowEnd = 0L
        mSlotWindowSlot = -1
        mSlotWindowDate = ""
    }

    private fun flushSlots() {
        if (mSlotDirty == 0L || mCachedHourly != true) return
        val dirty = mSlotDirty
        runCatching { Database.getInstance(this).saveSlots(mCurrentDate, mSlots, dirty) }
            .onSuccess { mSlotDirty = mSlotDirty and dirty.inv() }
            .onFailure { Log.w(TAG, "Failed to save hourly slots", it) }
    }

    private fun seedSlots() {
        val loaded = runCatching { Database.getInstance(this).loadSlots(mCurrentDate) }
            .onFailure { Log.w(TAG, "Failed to load hourly slots", it) }
            .getOrDefault(IntArray(Database.SLOT_COUNT))
        loaded.copyInto(mSlots)
        mSlotDirty = 0L
        invalidateSlotWindow()
    }

    private fun recalcBaseTotal() {
        val tracked = mCurrentDate.ifEmpty { Util.todayDateString() }
        mBaseTotal = Database.getInstance(this).totalStepsExcluding(tracked)
        AppPreferences.baseTotalSteps = mBaseTotal
    }

    private fun checkMilestones() {
        val total = mBaseTotal + mTodaysSteps
        val crossed = Util.MILESTONES.lastOrNull { it > mLastNotifiedMilestone && it <= total } ?: return

        mLastNotifiedMilestone = crossed
        AppPreferences.lastNotifiedMilestone = crossed
        if (AppPreferences.milestoneNotificationsEnabled) {
            GoalNotificationWorker.showMilestoneNotification(this, crossed)
        }
    }

    override fun onDestroy() {
        stopTimedPauseMonitoring()
        MidnightResetReceiver.cancelMidnightAlarm(this)
        if (::mListener.isInitialized) mSensorManager?.unregisterListener(mListener)
        if (::activityRecognitionManager.isInitialized) activityRecognitionManager.stop()
        if (::handler.isInitialized) handler.removeCallbacksAndMessages(null)
        if (::serviceThread.isInitialized) serviceThread.quitSafely()
        super.onDestroy()
    }

    companion object {
        private val TAG = MotionService::class.java.simpleName
        internal const val ACTION_SUBSCRIBE = "ACTION_SUBSCRIBE"
        internal const val KEY_STEPS = "STEPS"
        internal const val KEY_DATE = "DATE"
        internal const val KEY_IS_PAUSED = "IS_PAUSED"
        internal const val ACTION_PAUSE_COUNTING = "com.nvllz.stepsy.action.PAUSE_COUNTING"
        internal const val ACTION_RESUME_COUNTING = "com.nvllz.stepsy.action.RESUME_COUNTING"
        internal const val ACTION_TIMED_PAUSE_END = "com.nvllz.stepsy.action.TIMED_PAUSE_END"
        internal const val EXTRA_SUPPRESS_TOAST = "SUPPRESS_TOAST"
        private const val PAUSE_ALARM_REQ = 4201
        private const val FOREGROUND_ID = 3843
        private const val MAX_EVENT_AGE_MS = 12L * 60L * 60L * 1000L
        internal const val STEP_CHANNEL_ID = "com.nvllz.stepsy.STEP_CHANNEL_ID"
    }
}