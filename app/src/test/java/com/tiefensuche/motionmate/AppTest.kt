/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.nvllz.stepsy

import android.text.format.DateUtils
import android.util.SparseArray
import com.nvllz.stepsy.service.MotionService
import com.nvllz.stepsy.util.Database
import com.nvllz.stepsy.util.Util
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowSystemClock
import java.util.*
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
internal class AppTest {

    @Before
    internal fun setup() {
        ShadowLog.stream = System.out
    }

    @Test
    @LooperMode(LooperMode.Mode.LEGACY)
    @Throws(Exception::class)
    internal fun testService() {
        val mCurrentDate = Util.calendar.timeInMillis - DateUtils.DAY_IN_MILLIS // -24h, previous day
        var mCurrentSteps = Random().nextInt(10000) // random stepsy between 0-10000

        ShadowSystemClock.setNanoTime(TimeUnit.NANOSECONDS.convert(mCurrentDate, TimeUnit.MILLISECONDS))

        // create service instance
        val service = Robolectric.setupService(MotionService::class.java)

        val database = Database.getInstance(service)

        // Generate some database entries
        val random = Random()
        val cal = Util.calendar
        cal.add(Calendar.DAY_OF_MONTH, -101)
        for (i in 0..98) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
            database.addEntry(cal.timeInMillis, random.nextInt(8000))
        }

        Assert.assertEquals(99, database.getEntries(database.firstEntry, database.lastEntry).size)

        // get private field mCurrentDate and change it to the previous day
        val dateField = MotionService::class.java.getDeclaredField("mCurrentDate")
        dateField.isAccessible = true
        dateField.setLong(service, mCurrentDate)

        // check if date was set properly
        Assert.assertEquals(mCurrentDate, dateField.getLong(service))
        println(String.format("%tD", dateField.getLong(service)))

        // get private field mCurrentSteps, it should be initially zero
        val stepsField = MotionService::class.java.getDeclaredField("mCurrentSteps")
        stepsField.isAccessible = true
        Assert.assertEquals(0, stepsField.getInt(service))
        println("mCurrentSteps=" + stepsField.getInt(service))

        // start new activities
        val activitiesField = MotionService::class.java.getDeclaredField("motionActivities")
        activitiesField.isAccessible = true
        val activities = activitiesField.get(service) as SparseArray<MotionActivity>
        val motionActivityFirst = MotionActivity(0, 0)
        activities.put(0, motionActivityFirst)
        val motionActivitySecond = MotionActivity(1, 0)
        motionActivitySecond.toggle()
        activities.put(1, motionActivitySecond)

        // handle sensor event and update stepsy, check for new day
        val m = MotionService::class.java.getDeclaredMethod("handleEvent", Int::class.javaPrimitiveType!!)
        m.isAccessible = true
        m.invoke(service, 0) // first event initializes current sensor value, here zero

        // another sensor event
        m.invoke(service, mCurrentSteps)

        // get private field mTodaysSteps, it should be updated from sensor event
        val todaysStepsField = MotionService::class.java.getDeclaredField("mTodaysSteps")
        todaysStepsField.isAccessible = true
        Assert.assertEquals(mCurrentSteps, todaysStepsField.getInt(service))
        println("mTodaysSteps=" + todaysStepsField.getInt(service))

        // check if activities where updated
        Assert.assertEquals(mCurrentSteps, motionActivityFirst.steps)
        Assert.assertEquals(0, motionActivitySecond.steps)

        // enable second activity
        motionActivitySecond.toggle()

        // simulate sensor event
        m.invoke(service, ++mCurrentSteps)

        // check if activities where updated
        Assert.assertEquals(mCurrentSteps, motionActivityFirst.steps)
        Assert.assertEquals(1, motionActivitySecond.steps)

        // change clock to the next day
        ShadowSystemClock.setNanoTime(TimeUnit.NANOSECONDS.convert(Util.calendar.timeInMillis, TimeUnit.MILLISECONDS))

        // simulate sensor event
        m.invoke(service, mCurrentSteps)

        // check if new day was triggered and date updated
        println(String.format("%tD", dateField.getLong(service)))
        Assert.assertTrue(DateUtils.isToday(dateField.getLong(service)))

        // check if stepsy were reset
        println("mTodaysSteps=" + todaysStepsField.getInt(service))
        Assert.assertEquals(0, todaysStepsField.getInt(service))

        // check if new record was created in database
        println(String.format("%tD", database.lastEntry))
        val entries = database.getEntries(database.lastEntry, database.lastEntry)
        println("entries=" + entries.size)
        println("stepsy=" + entries[0].steps)

        Assert.assertEquals(100, database.getEntries(database.firstEntry, database.lastEntry).size)
        Assert.assertEquals(mCurrentDate, database.lastEntry)
        Assert.assertEquals(mCurrentSteps, database.getEntries(database.lastEntry, database.lastEntry)[0].steps)

        // trigger another sensor event
        m.invoke(service, ++mCurrentSteps)

        // check if activities where properly updated
        Assert.assertEquals(mCurrentSteps, motionActivityFirst.steps)
        Assert.assertEquals(2, motionActivitySecond.steps)
    }
}
