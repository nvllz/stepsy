/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.motionmate.ui

import android.content.Intent
import android.os.Bundle
import android.os.ResultReceiver
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.ItemTouchHelper
import android.view.View
import android.widget.CalendarView
import android.widget.TextView
import com.tiefensuche.motionmate.R
import com.tiefensuche.motionmate.service.MotionService
import com.tiefensuche.motionmate.ui.cards.MotionActivityTextItem
import com.tiefensuche.motionmate.ui.cards.MotionStatisticsTextItem
import com.tiefensuche.motionmate.ui.cards.MotionTextItem
import com.tiefensuche.motionmate.util.Database
import com.tiefensuche.motionmate.util.Util
import java.util.*
import kotlin.collections.ArrayList

/**
 * The main activity for the UI of the step counter.
 */
internal class MainActivity : AppCompatActivity() {
    private lateinit var mTextViewSteps: TextView
    private lateinit var mTextViewMeters: TextView
    private lateinit var mTextViewCalendarContent: TextView
    private lateinit var mCalendarView: CalendarView
    private lateinit var mChart: Chart
    private lateinit var mTextViewChart: TextView
    private var mAdapter: TextItemAdapter = TextItemAdapter()
    private var mCurrentSteps: Int = 0
    private var mSelectedWeek: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        mTextViewSteps = findViewById(R.id.textViewSteps)
        mTextViewMeters = findViewById(R.id.textViewMeters)
        val mRecyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        mTextViewCalendarContent = findViewById(R.id.textViewCalendarContent)
        mChart = findViewById(R.id.chart)
        mTextViewChart = findViewById(R.id.textViewChart)
        mCalendarView = findViewById(R.id.calendar)
        mCalendarView.minDate = Database.getInstance(this).firstEntry
        mCalendarView.maxDate = Util.calendar.timeInMillis
        mCalendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val cal = Util.calendar
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, month)
            cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)

            setDataForWeek(cal)
        }

        val mLayoutManager = LinearLayoutManager(this)
        mRecyclerView.layoutManager = mLayoutManager
        mRecyclerView.adapter = mAdapter

        // setup swipeable cards and remove them on swiped, used for activities
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView,
                                viewHolder: RecyclerView.ViewHolder,
                                target: RecyclerView.ViewHolder): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val item = mAdapter[viewHolder.adapterPosition]
                if (item is MotionActivityTextItem) {
                    val i = Intent(this@MainActivity, MotionService::class.java)
                    i.action = MotionService.ACTION_STOP_ACTIVITY
                    i.putExtra(MotionService.KEY_ID, item.id)
                    startService(i)
                    mAdapter.remove(viewHolder.adapterPosition)
                }
            }

            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                val swipeFlags = ItemTouchHelper.START or ItemTouchHelper.END
                return if (mAdapter[viewHolder.adapterPosition].isSwipeable) {
                    ItemTouchHelper.Callback.makeMovementFlags(0, swipeFlags)
                } else 0
            }

        }).attachToRecyclerView(mRecyclerView)

        // Floating action button to start new activity
        findViewById<View>(R.id.fab).setOnClickListener {
            val i = Intent(this@MainActivity, MotionService::class.java)
            i.action = MotionService.ACTION_START_ACTIVITY
            startService(i)
        }

        // initial update of the diagram
        setDataForWeek(Util.calendar)

        // Add some cards with statistics
        setupCards()

        // Start the motion service
        subscribeService()
    }

    private fun setupCards() {
        val startOfMonth = Calendar.getInstance()
        startOfMonth.set(Calendar.DAY_OF_MONTH, 1)

        // A card that displays sum of steps in the current month
        val stepsThisMonth = Database.getInstance(this).getSumSteps(startOfMonth.timeInMillis)
        mAdapter.add(MotionStatisticsTextItem(this, R.string.steps_month, stepsThisMonth))

        // A card that displays average distance in a day
        val item = MotionTextItem(this, R.string.avg_distance)
        item.setContent(Database.getInstance(this).avgSteps)
        mAdapter.add(item)

        // A card that displays overall sum of steps
        val overallSteps = Database.getInstance(this).getSumSteps(0)
        mAdapter.add(MotionStatisticsTextItem(this, R.string.overall_distance, overallSteps))
    }

    private fun subscribeService() {
        // start the service and pass a result receiver that is used by the service to update the UI
        val i = Intent(this, MotionService::class.java)
        i.action = MotionService.ACTION_SUBSCRIBE
        i.putExtra(RECEIVER_TAG, object : ResultReceiver(null) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle) {
                if (resultCode == 0) {
                    runOnUiThread { updateView(resultData.getInt(MotionService.KEY_STEPS), resultData.getParcelableArrayList(MotionService.KEY_ACTIVITIES) ?: ArrayList()) }
                }
            }
        })
        startService(i)
    }

    private fun updateView(steps: Int, activities: MutableList<Bundle>) {
        // update current today's steps in the header
        mCurrentSteps = steps
        mTextViewMeters.text = String.format(getString(R.string.meters_today), Util.stepsToMeters(steps))
        mTextViewSteps.text = resources.getQuantityString(R.plurals.steps_text, steps, steps)

        // update calendar max date for the case that new day started
        mCalendarView.maxDate = Util.calendar.timeInMillis

        // update the cards
        for (i in 0 until mAdapter.itemCount) {
            val item = mAdapter[i]
            if (item is MotionStatisticsTextItem) {
                item.updateSteps(steps)
            } else if (item is MotionActivityTextItem) {
                for (activity in activities) {
                    if (activity.getInt(MotionService.KEY_ID) == item.id) {
                        item.updateSteps(activity.getInt(MotionService.KEY_STEPS))
                        activities.remove(activity)
                        break
                    }
                }
            }
        }

        // initialize dynamic cards, e.g. activities, that are not yet added
        for (activity in activities) {
            val id = activity.getInt(MotionService.KEY_ID)
            val item = MotionActivityTextItem(this, id, View.OnClickListener {
                val i = Intent(this@MainActivity, MotionService::class.java)
                i.action = MotionService.ACTION_TOGGLE_ACTIVITY
                i.putExtra(MotionService.KEY_ID, id)
                startService(i)
            })
            item.updateSteps(activity.getInt(MotionService.KEY_STEPS))
            item.setActive(activity.getBoolean(MotionService.KEY_ACTIVE))
            mAdapter.addTop(item)
        }

        // If selected week is the current week, update the diagram with today's steps
        if (mSelectedWeek == Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)) {
            mChart.setCurrentSteps(steps)
            mChart.update()
        }
    }

    private fun setDataForWeek(selected: Calendar) {
        mSelectedWeek = selected.get(Calendar.WEEK_OF_YEAR)

        val min = Calendar.getInstance()
        min.timeInMillis = selected.timeInMillis

        // Jump to the first day of the week
        min.set(Calendar.DAY_OF_WEEK, Calendar.getInstance().firstDayOfWeek)

        val max = Calendar.getInstance()
        max.timeInMillis = min.timeInMillis

        // Jump to the last day of the week
        max.add(Calendar.DAY_OF_YEAR, 6)

        mChart.clearDiagram()
        mTextViewChart.text = String.format(Locale.getDefault(), getString(R.string.week_display_format), min.get(Calendar.WEEK_OF_YEAR), min.timeInMillis, max.timeInMillis)

        // Get the records of the selected week between the min and max timestamps
        val entries = Database.getInstance(this).getEntries(min.timeInMillis, max.timeInMillis)

        mTextViewCalendarContent.text = String.format(getString(R.string.no_entry), selected.timeInMillis)
        for (entry in entries) {
            mChart.setDiagramEntry(entry)

            val cal = Calendar.getInstance()
            cal.timeInMillis = entry.timestamp

            // Update the description text with the selected date
            if (cal.get(Calendar.DAY_OF_WEEK) == selected.get(Calendar.DAY_OF_WEEK)) {
                mTextViewCalendarContent.text = String.format(Locale.getDefault(), getString(R.string.steps_day_display), cal.timeInMillis, Util.stepsToMeters(entry.steps), entry.steps)
            }
        }

        // If selected week is the current week, update the diagram with today's steps
        if (mSelectedWeek == Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)) {
            mChart.setCurrentSteps(mCurrentSteps)
        }
        mChart.update()
    }

    companion object {

        const val RECEIVER_TAG = "RECEIVER_TAG"
    }
}
