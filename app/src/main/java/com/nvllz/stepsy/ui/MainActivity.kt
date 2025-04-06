/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.nvllz.stepsy.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import android.text.format.DateUtils
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.View
import android.widget.CalendarView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.preference.PreferenceManager
import com.nvllz.stepsy.R
import com.nvllz.stepsy.service.MotionService
import com.nvllz.stepsy.ui.cards.MotionStatisticsTextItem
import com.nvllz.stepsy.ui.cards.MotionTextItem
import com.nvllz.stepsy.util.Database
import com.nvllz.stepsy.util.Util
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.edit

/**
 * The main activity for the UI of the step counter.
 */
internal class MainActivity : AppCompatActivity() {
    private lateinit var mTextViewMeters: TextView
    private lateinit var mTextViewSteps: TextView
    private lateinit var mTextViewCalories: TextView
    private lateinit var mTextViewCalendarContent: TextView
    private lateinit var mCalendarView: CalendarView
    private lateinit var mChart: Chart
    private lateinit var mTextViewChart: TextView
    private var mAdapter: TextItemAdapter = TextItemAdapter()
    private lateinit var mMonthlyStepsCard: MotionStatisticsTextItem
    private lateinit var mAverageStepsCard: MotionTextItem
    private lateinit var mOverallStepsCard: MotionStatisticsTextItem
    private var mCurrentSteps: Int = 0
    private var mSelectedMonth = Util.calendar
    private var isPaused = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Util.applyTheme(PreferenceManager.getDefaultSharedPreferences(this).getString("theme", "system")!!)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        Util.height = PreferenceManager.getDefaultSharedPreferences(this).getInt("height_cm", 180)
        Util.weight = PreferenceManager.getDefaultSharedPreferences(this).getInt("weight_kg", 70)

        mTextViewMeters = findViewById(R.id.textViewMeters)
        mTextViewSteps = findViewById(R.id.textViewSteps)
        mTextViewCalories = findViewById(R.id.textViewCalories)
        isPaused = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("isPaused", false)

        val fab = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab)
        if (isPaused) {
            fab.setImageResource(android.R.drawable.ic_media_play)
        } else {
            fab.setImageResource(android.R.drawable.ic_media_pause)
        }

        val mRecyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        mTextViewCalendarContent = findViewById(R.id.textViewCalendarContent)
        mChart = findViewById(R.id.chart)
        mTextViewChart = findViewById(R.id.textViewChart)
        mCalendarView = findViewById(R.id.calendar)
        mCalendarView.minDate = Database.getInstance(this).firstEntry.let {
            if (it == 0L)
                Util.calendar.timeInMillis
            else
                it
        }
        mCalendarView.maxDate = Util.calendar.timeInMillis
        mCalendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            mSelectedMonth.set(Calendar.YEAR, year)
            mSelectedMonth.set(Calendar.MONTH, month)
            mSelectedMonth.set(Calendar.DAY_OF_MONTH, dayOfMonth)

            updateChart()
            updateCards()
        }

        val mLayoutManager = LinearLayoutManager(this)
        mRecyclerView.layoutManager = mLayoutManager
        mRecyclerView.adapter = mAdapter

        findViewById<View>(R.id.fab).let {
            it.setOnClickListener {
                val fab = it as com.google.android.material.floatingactionbutton.FloatingActionButton

                if (isPaused) {
                    val intent = Intent(this, MotionService::class.java)
                    intent.action = MotionService.ACTION_RESUME_COUNTING
                    startService(intent)
                    fab.setImageResource(android.R.drawable.ic_media_pause)
                    getSharedPreferences("settings", MODE_PRIVATE).edit {
                        putBoolean(
                            "isPaused",
                            false
                        )
                    }
                } else {
                    val intent = Intent(this, MotionService::class.java)
                    intent.action = MotionService.ACTION_PAUSE_COUNTING
                    startService(intent)
                    fab.setImageResource(android.R.drawable.ic_media_play)
                    getSharedPreferences("settings", MODE_PRIVATE).edit {
                        putBoolean(
                            "isPaused",
                            true
                        )
                    }
                }
                isPaused = !isPaused
            }
        }

        mTextViewSteps.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        updateChart()

        setupCards()

        checkPermissions()
    }

    override fun onResume() {
        super.onResume()

        subscribeService()

        val intent = Intent(this, MotionService::class.java).apply {
            putExtra("FORCE_UPDATE", true)
        }
        startService(intent)
    }

    private fun formatToSelectedDateFormat(dateInMillis: Long): String {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val dateFormatString = sharedPreferences.getString("date_format", "yyyy-MM-dd") ?: "yyyy-MM-dd"

        val sdf = SimpleDateFormat(dateFormatString, Locale.getDefault())
        return sdf.format(Date(dateInMillis))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        AppCompatDelegate.setDefaultNightMode(
            if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES)
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else AppCompatDelegate.MODE_NIGHT_YES)
        return super.onOptionsItemSelected(item)
    }

    private fun setupCards() {
        mMonthlyStepsCard = MotionStatisticsTextItem(this, R.string.steps_month, 0)
        mAdapter.add(mMonthlyStepsCard)

        mAverageStepsCard = MotionTextItem(this, R.string.avg_distance)
        mAdapter.add(mAverageStepsCard)

        val overallSteps = Database.getInstance(this).getSumSteps(Database.getInstance(this).firstEntry, Database.getInstance(this).lastEntry)
        mOverallStepsCard = MotionStatisticsTextItem(this, R.string.total_distance, overallSteps)
        mAdapter.add(mOverallStepsCard)

        updateCards()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER) &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), 0)

        } else {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 0) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                subscribeService()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                }
            }
        }
    }


    private fun updateView(steps: Int) {
        // update current today's steps in the header
        mCurrentSteps = steps
        mTextViewMeters.text = String.format(getString(R.string.distance_today), Util.stepsToMeters(steps))
        mTextViewSteps.text = resources.getQuantityString(R.plurals.steps_text, steps, steps)
        mTextViewCalories.text = String.format(getString(R.string.calories), Util.stepsToCalories(steps))

        // update calendar max date for the case that new day started
        if (!DateUtils.isToday(mCalendarView.maxDate))
            mCalendarView.maxDate = Util.calendar.timeInMillis

        // update the cards
        mOverallStepsCard.updateSteps(steps)

        // If selected week is the current week, update the diagram and cards with today's stepsy
        if (mSelectedMonth.get(Calendar.WEEK_OF_YEAR) == Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)) {
            mChart.setCurrentSteps(steps)
            mChart.update()
            mMonthlyStepsCard.updateSteps(steps)
        }
    }

    private fun updateChart() {
        val min = Calendar.getInstance()
        min.timeInMillis = mSelectedMonth.timeInMillis

        min.set(Calendar.DAY_OF_WEEK, Calendar.getInstance().firstDayOfWeek)

        val max = Calendar.getInstance()
        max.timeInMillis = min.timeInMillis

        max.add(Calendar.DAY_OF_YEAR, 6)

        mChart.clearDiagram()

        val startDateFormatted = formatToSelectedDateFormat(min.timeInMillis)
        val endDateFormatted = formatToSelectedDateFormat(max.timeInMillis)

        mTextViewChart.text = String.format(
            Locale.getDefault(),
            getString(R.string.week_display_format),
            min.get(Calendar.WEEK_OF_YEAR), startDateFormatted, endDateFormatted
        )

        val entries = Database.getInstance(this).getEntries(min.timeInMillis, max.timeInMillis)

        val selectedMonthDateFormatted = formatToSelectedDateFormat(mSelectedMonth.timeInMillis)
        mTextViewCalendarContent.text = String.format(getString(R.string.no_entry), selectedMonthDateFormatted)
        for (entry in entries) {
            mChart.setDiagramEntry(entry)

            val cal = Calendar.getInstance()
            cal.timeInMillis = entry.timestamp

            val calDateFormatted = formatToSelectedDateFormat(cal.timeInMillis)

            if (cal.get(Calendar.DAY_OF_WEEK) == mSelectedMonth.get(Calendar.DAY_OF_WEEK)) {
                mTextViewCalendarContent.text = String.format(
                    Locale.getDefault(),
                    getString(R.string.steps_day_display),
                    calDateFormatted, Util.stepsToMeters(entry.steps), entry.steps, Util.stepsToCalories(entry.steps)
                )
            }
        }

        if (mSelectedMonth.get(Calendar.WEEK_OF_YEAR) == Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)) {
            mChart.setCurrentSteps(mCurrentSteps)
        }
        mChart.update()
    }

    private fun updateCards() {
        val cal = Calendar.getInstance().apply {
            timeInMillis = Util.calendar.timeInMillis
            set(Calendar.DAY_OF_MONTH, 1)
        }

        val startOfMonth = Calendar.getInstance()
        startOfMonth.timeInMillis = mSelectedMonth.timeInMillis
        startOfMonth.set(Calendar.DAY_OF_MONTH, 1)
        val currentMonth = cal == startOfMonth

        val endOfMonth = Calendar.getInstance()
        endOfMonth.timeInMillis = startOfMonth.timeInMillis
        endOfMonth.add(Calendar.MONTH, 1)

        val stepsThisMonth = Database.getInstance(this).getSumSteps(startOfMonth.timeInMillis, endOfMonth.timeInMillis)
        mMonthlyStepsCard.initialSteps = stepsThisMonth
        mMonthlyStepsCard.updateSteps(if (currentMonth) mCurrentSteps else 0)
        mAverageStepsCard.setContent(Database.getInstance(this).avgSteps(startOfMonth.timeInMillis, endOfMonth.timeInMillis))
    }

    companion object {

        const val RECEIVER_TAG = "RECEIVER_TAG"
    }
}
