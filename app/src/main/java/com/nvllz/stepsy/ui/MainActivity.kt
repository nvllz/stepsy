/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.nvllz.stepsy.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.ResultReceiver
import android.provider.Settings
import android.text.format.DateUtils
import android.view.View
import android.view.ViewGroup
import android.widget.CalendarView
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.transition.AutoTransition
import androidx.transition.Transition
import androidx.transition.TransitionManager
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.button.MaterialButton
import com.nvllz.stepsy.R
import com.nvllz.stepsy.service.MotionService
import com.nvllz.stepsy.util.AppPreferences
import com.nvllz.stepsy.util.Database
import com.nvllz.stepsy.util.Util
import java.text.SimpleDateFormat
import java.util.*

/**
 * The main activity for the UI of the step counter.
 */
internal class MainActivity : AppCompatActivity() {
    private lateinit var mTextViewMeters: TextView
    private lateinit var mTextViewSteps: TextView
    private lateinit var mTextViewCalories: TextView
    private lateinit var mCalendarView: CalendarView
    private lateinit var mChart: Chart
    private lateinit var mTextViewChartHeader: TextView
    private lateinit var mTextViewChartWeekRange: TextView
    private var mCurrentSteps: Int = 0
    private var mSelectedMonth = Util.calendar
    private var isPaused = false
    private var currentSelectedButton: MaterialButton? = null
    private var isTodaySelected = true

    private lateinit var mTextViewDayHeader: TextView
    private lateinit var mTextViewDayDetails: TextView
    private lateinit var mTextViewMonthTotal: TextView
    private lateinit var mTextViewMonthAverage: TextView
    private lateinit var mTextViewTopHeader: TextView
    private lateinit var mTextAvgPerDayHeader: TextView
    private lateinit var mTextAvgPerDayValue: TextView

    private lateinit var mRangeStaticBox: FlexboxLayout
    private lateinit var mRangeDynamicBox: FlexboxLayout
    private lateinit var mExpandButton: ImageButton
    private var isExpanded = false
    private var currentSelectedYearButton: MaterialButton? = null

    override fun onCreate(savedInstanceState: Bundle?) {
//        Util.init(applicationContext)
        Util.applyTheme(AppPreferences.theme)

        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        mRangeStaticBox = findViewById(R.id.rangeStaticBox)
        mRangeDynamicBox = findViewById(R.id.rangeDynamicBox)
        mExpandButton = findViewById(R.id.expandButton)

        AppPreferences.welcomeDialog(this)

        mExpandButton.setOnClickListener {
            isExpanded = !isExpanded

            if (isExpanded) {
                mRangeStaticBox.visibility = View.VISIBLE
                mRangeDynamicBox.visibility = View.VISIBLE
            }

            TransitionManager.beginDelayedTransition(
                mRangeStaticBox.parent as ViewGroup,
                AutoTransition().apply {
                    duration = 300
                    addListener(object : Transition.TransitionListener {
                        override fun onTransitionEnd(transition: Transition) {
                            if (!isExpanded) {
                                mRangeStaticBox.visibility = View.GONE
                                mRangeDynamicBox.visibility = View.GONE
                            }
                        }
                        override fun onTransitionStart(transition: Transition) {}
                        override fun onTransitionCancel(transition: Transition) {}
                        override fun onTransitionPause(transition: Transition) {}
                        override fun onTransitionResume(transition: Transition) {}
                    })
                }
            )

            mRangeStaticBox.visibility = if (isExpanded) View.VISIBLE else View.INVISIBLE
            mRangeDynamicBox.visibility = if (isExpanded) View.VISIBLE else View.INVISIBLE

            mExpandButton.setImageResource(
                if (isExpanded) R.drawable.ic_expand_less
                else R.drawable.ic_expand_more
            )
        }

        loadYearButtons()

        val todayButton = findViewById<MaterialButton>(R.id.button_today)
        setSelectedButton(todayButton)

        mTextViewMeters = findViewById(R.id.textViewMeters)
        mTextViewSteps = findViewById(R.id.textViewSteps)
        mTextViewCalories = findViewById(R.id.textViewCalories)
        isPaused = getSharedPreferences("StepsyPrefs", MODE_PRIVATE).getBoolean(MotionService.KEY_IS_PAUSED, false)
        mTextViewDayHeader = findViewById(R.id.textViewDayHeader)
        mTextViewDayDetails = findViewById(R.id.textViewDayDetails)
        mTextViewMonthTotal = findViewById(R.id.textViewMonthTotal)
        mTextViewMonthAverage = findViewById(R.id.textViewMonthAverage)
        mTextViewTopHeader = findViewById(R.id.textViewTopHeader)
        mTextAvgPerDayHeader = findViewById(R.id.textAvgPerDayHeader)
        mTextAvgPerDayValue = findViewById(R.id.textAvgPerDayValue)

        val gearButton = findViewById<ImageButton>(R.id.gearButton)
        gearButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        val fab = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab)
        if (isPaused) {
            fab.setImageResource(android.R.drawable.ic_media_play)
            fab.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.colorAccent))
        } else {
            fab.setImageResource(android.R.drawable.ic_media_pause)
            fab.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.colorPrimary))
        }

        mChart = findViewById(R.id.chart)
        mTextViewChartHeader = findViewById(R.id.textViewChartHeader)
        mTextViewChartWeekRange = findViewById(R.id.textViewChartWeekRange)
        mCalendarView = findViewById(R.id.calendar)
        mCalendarView.minDate = Database.getInstance(this).firstEntry.let {
            if (it == 0L)
                Util.calendar.timeInMillis
            else
                it
        }
        mCalendarView.maxDate = Util.calendar.timeInMillis
        mCalendarView.firstDayOfWeek = AppPreferences.firstDayOfWeek
        mCalendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            mSelectedMonth.set(Calendar.YEAR, year)
            mSelectedMonth.set(Calendar.MONTH, month)
            mSelectedMonth.set(Calendar.DAY_OF_MONTH, dayOfMonth)

            updateChart()
        }

        findViewById<MaterialButton>(R.id.button_today).setOnClickListener {
            handleTimeRangeSelection("TODAY", it as MaterialButton)
        }

        findViewById<MaterialButton>(R.id.button_this_week).setOnClickListener {
            handleTimeRangeSelection("WEEK", it as MaterialButton)
        }

        findViewById<MaterialButton>(R.id.button_this_month).setOnClickListener {
            handleTimeRangeSelection("MONTH", it as MaterialButton)
        }

        findViewById<MaterialButton>(R.id.button_7days).setOnClickListener {
            handleTimeRangeSelection("7 DAYS", it as MaterialButton)
        }

        findViewById<MaterialButton>(R.id.button_30days).setOnClickListener {
            handleTimeRangeSelection("30 DAYS", it as MaterialButton)
        }

        findViewById<MaterialButton>(R.id.button_alltime).setOnClickListener {
            handleTimeRangeSelection("ALL TIME", it as MaterialButton)
        }

        findViewById<View>(R.id.fab).let {
            it.setOnClickListener {
                val fab = it as com.google.android.material.floatingactionbutton.FloatingActionButton

                if (isPaused) {
                    val intent = Intent(this, MotionService::class.java)
                    intent.action = MotionService.ACTION_RESUME_COUNTING
                    startService(intent)
                    fab.setImageResource(android.R.drawable.ic_media_pause)
                    fab.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.colorPrimary))
                    getSharedPreferences("StepsyPrefs", MODE_PRIVATE).edit { putBoolean(MotionService.KEY_IS_PAUSED, false) }
                } else {
                    val intent = Intent(this, MotionService::class.java)
                    intent.action = MotionService.ACTION_PAUSE_COUNTING
                    startService(intent)
                    fab.setImageResource(android.R.drawable.ic_media_play)
                    fab.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.colorAccent))
                    getSharedPreferences("StepsyPrefs", MODE_PRIVATE).edit { putBoolean(MotionService.KEY_IS_PAUSED, true) }
                }
                isPaused = !isPaused
            }
        }

        restoreSelectionState()

        updateChart()

        checkPermissions()
    }

    override fun onResume() {
        super.onResume()

        if (isActivityPermissionGranted()) {
            subscribeService()
            startService(Intent(this, MotionService::class.java).apply {
                putExtra("FORCE_UPDATE", true)
            })
        }
    }

    private fun setSelectedButton(button: MaterialButton, isYearButton: Boolean = false) {
        if (isYearButton) {
            currentSelectedYearButton?.let {
                it.strokeWidth = 2
                it.setTextColor(ContextCompat.getColor(this, R.color.colorAccent))
                it.setTypeface(null, Typeface.NORMAL)
            }
            currentSelectedYearButton = button
            currentSelectedButton?.let {
                it.strokeWidth = 2
                it.setTextColor(ContextCompat.getColor(this, R.color.colorAccent))
                it.setTypeface(null, Typeface.NORMAL)
            }
            currentSelectedButton = null
        } else {
            currentSelectedButton?.let {
                it.strokeWidth = 2
                it.setTextColor(ContextCompat.getColor(this, R.color.colorAccent))
                it.setTypeface(null, Typeface.NORMAL)
            }
            currentSelectedButton = button
            currentSelectedYearButton?.let {
                it.strokeWidth = 2
                it.setTextColor(ContextCompat.getColor(this, R.color.colorAccent))
                it.setTypeface(null, Typeface.NORMAL)
            }
            currentSelectedYearButton = null
        }

        button.setTypeface(null, Typeface.BOLD)
        button.strokeWidth = 6
        button.setTextColor(ContextCompat.getColor(this, R.color.colorOnSurface))
    }

    private fun loadYearButtons() {
        val db = Database.getInstance(this)
        val firstYear = Calendar.getInstance().apply {
            timeInMillis = db.firstEntry
        }.get(Calendar.YEAR)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        mRangeDynamicBox.removeAllViews()

        for (year in firstYear..currentYear) {
            val startOfYear = Calendar.getInstance(getDeviceTimeZone()).apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, Calendar.JANUARY)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }.timeInMillis

            val endOfYear = Calendar.getInstance(getDeviceTimeZone()).apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, Calendar.DECEMBER)
                set(Calendar.DAY_OF_MONTH, 31)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
            }.timeInMillis

            val hasData = db.getSumSteps(startOfYear, endOfYear) > 0

            if (hasData) {
                val button = layoutInflater.inflate(R.layout.year_button, mRangeDynamicBox, false) as MaterialButton
                button.text = year.toString()
                button.setOnClickListener {
                    setSelectedButton(button, true)
                    updateYearSummaryView(year)
                }
                mRangeDynamicBox.addView(button)
            }
        }
    }

    private fun handleTimeRangeSelection(range: String, button: MaterialButton) {
        setSelectedButton(button)
        isTodaySelected = range == "TODAY"
        saveSelectedRange(range)

        if (isTodaySelected) {
            mTextViewTopHeader.text = getString(R.string.header_today)
            updateView(mCurrentSteps)
            return
        }

        val timeZone = getDeviceTimeZone()
        val calendar = Calendar.getInstance(timeZone)
        val db = Database.getInstance(this)

        val (startTime, endTime) = when (range) {
            "WEEK" -> {
                mTextViewTopHeader.text = getString(R.string.header_week)
                calendar.firstDayOfWeek = AppPreferences.firstDayOfWeek
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                calendar.add(Calendar.DAY_OF_YEAR, 6)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                Pair(start, calendar.timeInMillis)
            }
            "MONTH" -> {
                mTextViewTopHeader.text = getString(R.string.header_month)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                calendar.set(Calendar.HOUR_OF_DAY, 1)
                Pair(start, calendar.timeInMillis)
            }
            "7 DAYS" -> {
                mTextViewTopHeader.text = getString(R.string.header_7d)
                calendar.add(Calendar.DAY_OF_YEAR, -6)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                calendar.add(Calendar.DAY_OF_YEAR, 6)
                calendar.set(Calendar.HOUR_OF_DAY, 1)
                Pair(start, calendar.timeInMillis)
            }
            "30 DAYS" -> {
                mTextViewTopHeader.text = getString(R.string.header_30d)
                calendar.add(Calendar.DAY_OF_YEAR, -29)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                calendar.add(Calendar.DAY_OF_YEAR, 29)
                calendar.set(Calendar.HOUR_OF_DAY, 1)
                Pair(start, calendar.timeInMillis)
            }
            "ALL TIME" -> {
                mTextViewTopHeader.text = getString(R.string.header_all_time)
                // Include the very first and last moments
                Pair(db.firstEntry, db.lastEntry)
            }
            else -> return
        }

        val totalSteps = db.getSumSteps(startTime, endTime)
        val avgSteps = db.avgSteps(startTime, endTime)

        mTextViewSteps.text = resources.getQuantityString(R.plurals.steps_text, totalSteps, totalSteps)
        mTextViewMeters.text = String.format(getString(R.string.distance_today),
            Util.stepsToDistance(totalSteps),
            Util.getDistanceUnitString())

        mTextViewCalories.text = ""
        mTextAvgPerDayHeader.visibility = View.VISIBLE
        mTextAvgPerDayValue.visibility = View.VISIBLE
        mTextAvgPerDayHeader.text = getString(R.string.avg_distance)
        mTextAvgPerDayValue.text = String.format(
            getString(R.string.steps_format),
            avgSteps.toString(),
            Util.stepsToDistance(avgSteps),
            Util.getDistanceUnitString()
        )
    }

    private fun restoreSelectionState() {
        if (isYearSelected()) {
            val year = loadSelectedYear()
            if (year != -1) {
                for (i in 0 until mRangeDynamicBox.childCount) {
                    val button = mRangeDynamicBox.getChildAt(i) as? MaterialButton
                    if (button?.text == year.toString()) {
                        setSelectedButton(button, true)
                        updateYearSummaryView(year)
                        break
                    }
                }
            }
        } else {
            val range = loadSelectedRange()
            if (range != null) {
                val buttonId = when (range) {
                    "TODAY" -> R.id.button_today
                    "WEEK" -> R.id.button_this_week
                    "MONTH" -> R.id.button_this_month
                    "7 DAYS" -> R.id.button_7days
                    "30 DAYS" -> R.id.button_30days
                    "ALL TIME" -> R.id.button_alltime
                    else -> null
                }

                buttonId?.let {
                    val button = findViewById<MaterialButton>(it)
                    setSelectedButton(button)
                    handleTimeRangeSelection(range, button)
                }
            }
        }
    }

    private fun updateYearSummaryView(year: Int) {
        saveSelectedYear(year)
        currentSelectedButton = null

        val timeZone = getDeviceTimeZone()
        val startOfYear = Calendar.getInstance(timeZone).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val endOfYear = Calendar.getInstance(timeZone).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, Calendar.DECEMBER)
            set(Calendar.DAY_OF_MONTH, 31)
            set(Calendar.HOUR_OF_DAY, 1)
        }.timeInMillis

        mTextViewTopHeader.text = String.format(getString(R.string.header_year),
            year)

        val yearSteps = Database.getInstance(this).getSumSteps(startOfYear, endOfYear)
        val avgSteps = Database.getInstance(this).avgSteps(startOfYear, endOfYear)

        mTextViewSteps.text = resources.getQuantityString(R.plurals.steps_text, yearSteps, yearSteps)
        mTextViewMeters.text = String.format(getString(R.string.distance_today),
            Util.stepsToDistance(yearSteps),
            Util.getDistanceUnitString())

        mTextViewCalories.text = ""
        mTextAvgPerDayHeader.visibility = View.VISIBLE
        mTextAvgPerDayValue.visibility = View.VISIBLE
        mTextAvgPerDayHeader.text = getString(R.string.avg_distance)
        mTextAvgPerDayValue.text = String.format(
            getString(R.string.steps_format),
            avgSteps.toString(),
            Util.stepsToDistance(avgSteps),
            Util.getDistanceUnitString()
        )
    }

    private fun formatToSelectedDateFormat(dateInMillis: Long): String {
        val sdf = SimpleDateFormat(AppPreferences.dateFormatString, Locale.getDefault())
        return sdf.format(Date(dateInMillis))
    }

    private fun subscribeService() {
        val i = Intent(this, MotionService::class.java)
        i.action = MotionService.ACTION_SUBSCRIBE
        i.putExtra(RECEIVER_TAG, object : ResultReceiver(null) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle) {
                if (resultCode == 0) {
                    isPaused = resultData.getBoolean(MotionService.KEY_IS_PAUSED, false)
                    runOnUiThread {
                        updateView(resultData.getInt(MotionService.KEY_STEPS))
                        val fab = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab)
                        fab.setImageResource(if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause)
                    }
                }
            }
        })
        startService(i)
    }

    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER) &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && // API 34, Android 14
            ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_HEALTH) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.FOREGROUND_SERVICE_HEALTH)
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 100)
        }
        requestIgnoreBatteryOptimization()
    }

    private fun requestIgnoreBatteryOptimization() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val packageName = packageName

        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent().apply {
                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = "package:$packageName".toUri()
            }
            startActivity(intent)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (isActivityPermissionGranted()) {
                subscribeService()
                startService(Intent(this, MotionService::class.java).apply {
                    putExtra("FORCE_UPDATE", true)
                })
            }
        }
    }

    private fun isActivityPermissionGranted(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER) &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
            return true
        }

        return false
    }

    private fun updateView(steps: Int) {
        mCurrentSteps = steps
        if (isTodaySelected) {
            // Only update today's steps if "Today" is selected

            mTextViewMeters.text = String.format(getString(R.string.distance_today),
                Util.stepsToDistance(steps),
                Util.getDistanceUnitString())
            mTextViewSteps.text = resources.getQuantityString(R.plurals.steps_text, steps, steps)
            mTextViewCalories.text = String.format(getString(R.string.calories),
                Util.stepsToCalories(steps))
            mTextAvgPerDayHeader.visibility = View.GONE
            mTextAvgPerDayValue.visibility = View.GONE
            mTextAvgPerDayHeader.text = ""
            mTextAvgPerDayValue.text = ""
        }

        // Update calendar max date for the case that new day started
        if (!DateUtils.isToday(mCalendarView.maxDate)) {
            mCalendarView.maxDate = Util.calendar.timeInMillis
        }

        // If a year is selected, refresh its data to get latest steps
        currentSelectedYearButton?.let { button ->
            val year = button.text.toString().toInt()
            updateYearSummaryView(year)
        }

        // If selected week is the current week, update the diagram and cards with today's stepsy
        if (mSelectedMonth.get(Calendar.WEEK_OF_YEAR) == Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)) {
            mChart.setCurrentSteps(steps)
            mChart.update()
        }

        // If a time range is selected (other than Today), refresh its data
        currentSelectedButton?.let { button ->
            when (button.id) {
                R.id.button_this_week -> handleTimeRangeSelection("WEEK", button)
                R.id.button_this_month -> handleTimeRangeSelection("MONTH", button)
                R.id.button_7days -> handleTimeRangeSelection("7 DAYS", button)
                R.id.button_30days -> handleTimeRangeSelection("30 DAYS", button)
                R.id.button_alltime -> handleTimeRangeSelection("ALL TIME", button)
            }
        }
    }

    private fun getDeviceTimeZone(): TimeZone {
        return TimeZone.getDefault()
    }

    private fun getDayEntry(timestamp: Long): Database.Entry? {
        val calendar = Calendar.getInstance(getDeviceTimeZone()).apply {
            timeInMillis = timestamp
        }
        // Get start and end of day in local timezone
        val startOfDay = calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val endOfDay = calendar.apply {
            set(Calendar.HOUR_OF_DAY, 23)
        }.timeInMillis

        val entries = Database.getInstance(this).getEntries(startOfDay, endOfDay)
        return entries.firstOrNull()
    }

    private fun updateChart() {
        val timeZone = getDeviceTimeZone()

        mTextViewDayHeader.text = formatToSelectedDateFormat(mSelectedMonth.timeInMillis)

        val dayEntry = getDayEntry(mSelectedMonth.timeInMillis)

        val stepsPlural = dayEntry?.let {
            resources.getQuantityString(
                R.plurals.steps_text,
                it.steps,
                it.steps
            )
        }

        if (dayEntry != null) {
            mTextViewDayDetails.text = String.format(
                getString(R.string.steps_day_display),
                stepsPlural,
                Util.stepsToDistance(dayEntry.steps),
                Util.getDistanceUnitString(),
                Util.stepsToCalories(dayEntry.steps)
            )
        } else {
            mTextViewDayDetails.text = String.format(
                getString(R.string.steps_day_display),
                resources.getQuantityString(R.plurals.steps_text,0,0),
                0.0,
                Util.getDistanceUnitString(),
                0
            )
        }

        val startOfMonth = Calendar.getInstance(timeZone).apply {
            timeInMillis = mSelectedMonth.timeInMillis
            set(Calendar.DAY_OF_MONTH, 1)
        }

        val endOfMonth = Calendar.getInstance(timeZone).apply {
            timeInMillis = startOfMonth.timeInMillis
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 1)
        }

        val monthSteps = Database.getInstance(this).getSumSteps(startOfMonth.timeInMillis, endOfMonth.timeInMillis)
        val avgSteps = Database.getInstance(this).avgSteps(startOfMonth.timeInMillis, endOfMonth.timeInMillis)

        mTextViewMonthTotal.text = String.format(
            getString(R.string.steps_format),
            monthSteps.toString(),
            Util.stepsToDistance(monthSteps),
            Util.getDistanceUnitString()
        )

        mTextViewMonthAverage.text = String.format(
            getString(R.string.steps_format),
            avgSteps.toString(),
            Util.stepsToDistance(avgSteps),
            Util.getDistanceUnitString()
        )

        val isToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis == Calendar.getInstance().apply {
            timeInMillis = mSelectedMonth.timeInMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val min: Calendar
        val max: Calendar

        if (isToday) {
            // Show past 7 days
            min = Calendar.getInstance(timeZone).apply {
                add(Calendar.DAY_OF_YEAR, -6) // Go back 6 days to include today as the 7th day
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            max = Calendar.getInstance(timeZone).apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
        } else {
            // Show the week containing the selected date
            min = Calendar.getInstance().apply {
                timeInMillis = mSelectedMonth.timeInMillis
                firstDayOfWeek = AppPreferences.firstDayOfWeek
                set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            max = Calendar.getInstance().apply {
                timeInMillis = min.timeInMillis
                add(Calendar.DAY_OF_YEAR, 6)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
        }

        mChart.clearDiagram()
        mChart.setPast7DaysMode(isToday, min.timeInMillis)

        val startDateFormatted = formatToSelectedDateFormat(min.timeInMillis)
        val endDateFormatted = formatToSelectedDateFormat(max.timeInMillis)

        if (isToday) {
            mTextViewChartHeader.text = String.format(
                Locale.getDefault(),
                getString(R.string.header_7d)
            ).uppercase()
            mTextViewChartWeekRange.text = String.format(
                Locale.getDefault(),
                getString(R.string.week_display_range),
                startDateFormatted, endDateFormatted
            )
        } else {
            mTextViewChartHeader.text = String.format(
                Locale.getDefault(),
                getString(R.string.week_display_format),
                min.get(Calendar.WEEK_OF_YEAR)
            ).uppercase()
            mTextViewChartWeekRange.text = String.format(
                Locale.getDefault(),
                getString(R.string.week_display_range),
                startDateFormatted, endDateFormatted
            )
        }

        val entries = Database.getInstance(this).getEntries(min.timeInMillis, max.timeInMillis)
        for (entry in entries) {
            mChart.setDiagramEntry(entry)
        }

        if (isToday) {
            mChart.setCurrentSteps(mCurrentSteps)
        }
        mChart.update()
    }

    private fun saveSelectedRange(range: String) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
            putString(KEY_SELECTED_RANGE, range)
            putBoolean(KEY_IS_YEAR_SELECTED, false)
            apply()
        }
    }

    private fun saveSelectedYear(year: Int) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
            putInt(KEY_SELECTED_YEAR, year)
            putBoolean(KEY_IS_YEAR_SELECTED, true)
            apply()
        }
    }

    private fun loadSelectedRange(): String? {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_SELECTED_RANGE, null)
    }

    private fun loadSelectedYear(): Int {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(KEY_SELECTED_YEAR, -1)
    }

    private fun isYearSelected(): Boolean {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_IS_YEAR_SELECTED, false)
    }

    companion object {
        const val RECEIVER_TAG = "RECEIVER_TAG"
        private const val PREFS_NAME = "RangePrefs"
        private const val KEY_SELECTED_RANGE = "selected_range"
        private const val KEY_SELECTED_YEAR = "selected_year"
        private const val KEY_IS_YEAR_SELECTED = "is_year_selected"
    }
}
