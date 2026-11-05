package com.nvllz.stepsy.util

import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.edit
import com.nvllz.stepsy.service.MotionService
import com.nvllz.stepsy.service.MotionService.Companion.KEY_DATE
import com.nvllz.stepsy.service.MotionService.Companion.KEY_STEPS
import kotlinx.coroutines.runBlocking
import java.util.Calendar
import java.util.Locale
import kotlin.math.min
import kotlin.random.Random

internal enum class MockFocus {
    BALANCED, NIGHT, MORNING, AFTERNOON, EVENING,
}

internal enum class MockEffort {
    BEGINNER, AVERAGE, HEAVY,
}

internal object MockData {

    internal fun generateAndStore(ctx: Context, days: Int, hourly: Boolean, effort: MockEffort, focus: MockFocus): Int {
        val db = Database.getInstance(ctx)
        val today = Util.todayDateString()
        val todayCal = Calendar.getInstance()
        val nowMinute = todayCal.get(Calendar.HOUR_OF_DAY) * 60 + todayCal.get(Calendar.MINUTE)
        val cal = (todayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -days) }

        val history = mutableListOf<Pair<String, Int>>()
        val slots = mutableMapOf<String, IntArray>()
        val adjustments = mutableListOf<Triple<String, Int, Int>>()
        var todaySteps = 0
        var factor = 1f

        while (cal.timeInMillis <= todayCal.timeInMillis) {
            val date = dateString(cal)
            val isToday = date == today
            val weekend = cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                    cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
            val total = (daySteps(effort, weekend) * factor).toInt()
            factor = (factor * (0.9f + Random.nextFloat() * 0.25f)).coerceIn(0.6f, 1.5f)

            if (!hourly) {
                history.add(date to total)
                if (isToday) todaySteps = total
                cal.add(Calendar.DAY_OF_YEAR, 1)
                continue
            }

            val daySlots = IntArray(Database.SLOT_COUNT)
            val sleepMinute = when (focus) {
                MockFocus.MORNING -> Random.nextInt(1320, 1381)
                MockFocus.AFTERNOON -> Random.nextInt(1320, 1421)
                MockFocus.EVENING -> Random.nextInt(1360, 1440)
                else -> Random.nextInt(1290, 1440)
            }
            val startMinute: Int
            var untimed = 0
            val wakeMinute: Int = when (focus) {
                MockFocus.NIGHT -> 0
                MockFocus.MORNING -> Random.nextInt(4 * 60 + 30, 6 * 60 + 1)
                MockFocus.AFTERNOON -> Random.nextInt(7 * 60 + 30, 10 * 60 + 1)
                MockFocus.EVENING -> Random.nextInt(8 * 60, 9 * 60 + 31)
                MockFocus.BALANCED -> Random.nextInt(5 * 60 + 30, 8 * 60 + 1)
            }
            if (focus == MockFocus.NIGHT) {
                startMinute = 0
            } else {
                val lateStart = !isToday && Random.nextInt(100) < 12
                if (lateStart) {
                    startMinute = Random.nextInt(9 * 60, 11 * 60 + 1)
                    untimed = total * Random.nextInt(8, 16) / 100
                } else if (isToday) {
                    startMinute = 0
                } else {
                    startMinute = wakeMinute
                }
            }
            val tracked = total - untimed
            val endMinute = if (isToday) nowMinute else sleepMinute
            distribute(tracked, startMinute / Database.SLOT_MINUTES, endMinute / Database.SLOT_MINUTES, daySlots, focus)

            var dayAdjust = 0
            if (!isToday && total >= 1500 && Random.nextInt(100) < 8) {
                val magnitude = min(Random.nextInt(100, 1501), total / 7)
                val delta = if (Random.nextBoolean()) -magnitude else magnitude
                val minute = if (Random.nextBoolean()) 1440 else Random.nextInt(wakeMinute, sleepMinute)
                adjustments.add(Triple(date, minute, delta))
                dayAdjust += delta
            }

            val storedTotal = if (isToday) daySlots.sum() else total + dayAdjust
            history.add(date to storedTotal)
            if (isToday) todaySteps = storedTotal
            if (daySlots.any { it > 0 }) slots[date] = daySlots
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        db.clearAllAndInsert(history, slots, adjustments)

        runBlocking {
            AppPreferences.dataStore.edit { prefs ->
                prefs[AppPreferences.PreferenceKeys.STEPS] = todaySteps
                prefs[AppPreferences.PreferenceKeys.DATE] = today

                val total = db.getSumSteps("2000-01-01", "9999-12-31").toLong()
                prefs[AppPreferences.PreferenceKeys.LAST_NOTIFIED_MILESTONE] =
                    Util.MILESTONES.lastOrNull { it <= total } ?: 0L
                prefs[AppPreferences.PreferenceKeys.BASE_TOTAL_STEPS] =
                    (total - todaySteps).coerceAtLeast(0L)
            }
            ctx.startService(
                Intent(ctx, MotionService::class.java).apply {
                    putExtra("FORCE_UPDATE", true)
                    putExtra(KEY_STEPS, todaySteps)
                    putExtra(KEY_DATE, today)
                }
            )
        }
        return todaySteps
    }

    private fun daySteps(effort: MockEffort, weekend: Boolean): Int {
        val roll = Random.nextInt(100)
        return when (effort) {
            MockEffort.BEGINNER -> when {
                roll < if (weekend) 10 else 20 -> Random.nextInt(100, 901)
                roll < 93 -> Random.nextInt(2000, 6001)
                roll < 99 -> Random.nextInt(5000, 9001)
                else -> Random.nextInt(10000, 14001)
            }
            MockEffort.AVERAGE -> when {
                roll < if (weekend) 8 else 15 -> Random.nextInt(100, 901)
                roll < if (weekend) 55 else 90 -> Random.nextInt(3000, 9501)
                roll < if (weekend) 97 else 98 -> Random.nextInt(8000, 12001)
                else -> Random.nextInt(12000, 18001)
            }
            MockEffort.HEAVY -> when {
                roll < if (weekend) 6 else 10 -> Random.nextInt(100, 901)
                roll < if (weekend) 45 else 75 -> Random.nextInt(6000, 11001)
                roll < if (weekend) 95 else 98 -> Random.nextInt(10000, 16001)
                else -> Random.nextInt(16000, 28001)
            }
        }
    }

    private fun distribute(tracked: Int, startSlot: Int, endSlot: Int, slots: IntArray, focus: MockFocus) {
        if (endSlot < startSlot) return
        val count = endSlot - startSlot + 1
        val weights = IntArray(count) { slotWeight(startSlot + it, focus) }
        if (weights.sum() == 0) weights[count - 1] = 1
        val weightSum = weights.sum()
        var previous = 0
        var cumulative = 0
        for (i in 0 until count) {
            cumulative += weights[i]
            val bound = if (weightSum == 0) 0 else (tracked.toLong() * cumulative / weightSum).toInt()
            slots[startSlot + i] = bound - previous
            previous = bound
        }
        slots[endSlot] += tracked - previous
    }

    private fun slotWeight(slot: Int, focus: MockFocus): Int = when (focus) {
        MockFocus.BALANCED -> when (Random.nextInt(12)) {
            0 -> Random.nextInt(20, 61)
            1 -> 0
            else -> Random.nextInt(1, 17)
        }
        MockFocus.NIGHT -> when {
            slot <= 4 || slot >= 22 -> Random.nextInt(25, 65)
            slot <= 5 || slot == 21 -> Random.nextInt(8, 21)
            else -> Random.nextInt(1, 7)
        }
        MockFocus.MORNING -> when {
            slot in 5..12 -> Random.nextInt(25, 65)
            slot <= 4 || slot == 13 -> Random.nextInt(8, 21)
            else -> Random.nextInt(1, 7)
        }
        MockFocus.AFTERNOON -> when {
            slot in 12..18 -> Random.nextInt(25, 65)
            slot == 11 || slot == 19 -> Random.nextInt(8, 21)
            else -> Random.nextInt(1, 7)
        }
        MockFocus.EVENING -> when {
            slot in 18..23 -> Random.nextInt(25, 65)
            slot in 16..17 -> Random.nextInt(8, 21)
            else -> Random.nextInt(1, 7)
        }
    }

    private fun dateString(cal: Calendar): String =
        "%04d-%02d-%02d".format(
            Locale.ROOT,
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )
}

internal object DebugTools {

    private val CLEARABLE_SETTINGS = setOf(
        AppPreferences.PreferenceKeys.THEME,
        AppPreferences.PreferenceKeys.HEIGHT,
        AppPreferences.PreferenceKeys.WEIGHT,
        AppPreferences.PreferenceKeys.STEP_LENGTH,
        AppPreferences.PreferenceKeys.UNIT_SYSTEM,
        AppPreferences.PreferenceKeys.DATE_FORMAT,
        AppPreferences.PreferenceKeys.FIRST_DAY_OF_WEEK,
        AppPreferences.PreferenceKeys.VEHICLE_FILTER_ENABLED,
        AppPreferences.PreferenceKeys.HOURLY_BREAKDOWN,
        AppPreferences.PreferenceKeys.BACKUP_LOCATION_URI,
        AppPreferences.PreferenceKeys.BACKUP_FREQUENCY,
        AppPreferences.PreferenceKeys.BACKUP_RETENTION_COUNT,
        AppPreferences.PreferenceKeys.DAILY_GOAL_NOTIFICATION,
        AppPreferences.PreferenceKeys.DAILY_GOAL_TARGET,
        AppPreferences.PreferenceKeys.DAILY_GOAL_NOTIFICATION_PROGRESSBAR,
        AppPreferences.PreferenceKeys.ENCOURAGING_NOTIFICATIONS,
        AppPreferences.PreferenceKeys.ENCOURAGING_THRESHOLDS,
        AppPreferences.PreferenceKeys.ENCOURAGING_STATE,
        AppPreferences.PreferenceKeys.MILESTONE_NOTIFICATIONS_ENABLED,
        AppPreferences.PreferenceKeys.STAR_PROMPT_HANDLED,
        AppPreferences.PreferenceKeys.SETUP_CARD_DISMISSED,
    )

    internal fun clearAllSteps(ctx: Context) {
        val db = Database.getInstance(ctx)
        db.clearAllAndInsert(emptyList(), emptyMap(), emptyList())
        val today = Util.todayDateString()
        runBlocking {
            AppPreferences.dataStore.edit { prefs ->
                prefs[AppPreferences.PreferenceKeys.STEPS] = 0
                prefs[AppPreferences.PreferenceKeys.DATE] = today
                prefs[AppPreferences.PreferenceKeys.LAST_NOTIFIED_MILESTONE] = 0L
                prefs[AppPreferences.PreferenceKeys.BASE_TOTAL_STEPS] = 0L
            }
            ctx.startService(
                Intent(ctx, MotionService::class.java).apply {
                    putExtra("FORCE_UPDATE", true)
                    putExtra(KEY_STEPS, 0)
                    putExtra(KEY_DATE, today)
                }
            )
        }
    }

    internal fun clearSettings(ctx: Context) {
        runBlocking {
            AppPreferences.dataStore.edit { prefs ->
                for (key in CLEARABLE_SETTINGS) prefs.remove(key)
            }
        }
        ctx.startService(
            Intent(ctx, MotionService::class.java).apply { action = "UPDATE_NOTIFICATION" }
        )
    }
}
