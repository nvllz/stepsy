package com.nvllz.stepsy.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.nvllz.stepsy.R
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.*

object Util {
    enum class UnitSystem {
        METRIC, IMPERIAL
    }

    internal const val CM_PER_INCH = 2.54f
    internal const val LB_PER_KG = 2.20462f
    internal const val MILES_PER_KM = 0.621371f

    private lateinit var appContext: Context

    private class DayStartRule(val hour: Int, val prevHour: Int, val switchMs: Long)

    @Volatile
    private var dayStartRule = DayStartRule(0, 0, 0)

    internal val DAY_START_PRESETS = listOf(22, 23, 0, 1, 2, 3, 4)

    internal fun refreshDayStartCache() {
        val prefs = runBlocking { AppPreferences.dataStore.data.first() }
        dayStartRule = DayStartRule(
            prefs[AppPreferences.PreferenceKeys.DAY_START_HOUR] ?: 0,
            prefs[AppPreferences.PreferenceKeys.DAY_START_PREV_HOUR] ?: 0,
            prefs[AppPreferences.PreferenceKeys.DAY_START_SWITCH_MS] ?: 0L,
        )
    }

    internal fun shiftOf(hour: Int): Int = if (hour >= 12) hour - 24 else hour

    private fun shiftedWallClock(hour: Int, ms: Long, zone: ZoneId): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), zone)
            .minusHours(shiftOf(hour).toLong())

    internal fun labelWith(hour: Int, ms: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val dt = shiftedWallClock(hour, ms, zone)
        return "%04d-%02d-%02d".format(Locale.ROOT, dt.year, dt.monthValue, dt.dayOfMonth)
    }

    internal fun label(ms: Long): String {
        val rule = dayStartRule
        return (if (ms >= rule.switchMs) labelWith(rule.hour, ms) else labelWith(rule.prevHour, ms))
    }

    private fun hourBoundaries(hours: IntArray, fromMs: Long, zone: ZoneId): List<Long> {
        val start = LocalDateTime.ofInstant(Instant.ofEpochMilli(fromMs), zone).toLocalDate()
        val candidates = ArrayList<Long>()
        for (offset in 0..3) {
            val date = start.plusDays(offset.toLong())
            for (hour in hours) {
                val ms = LocalDateTime.of(date, LocalTime.of(hour, 0)).atZone(zone).toInstant().toEpochMilli()
                if (ms > fromMs) candidates.add(ms)
            }
        }
        return candidates.sorted()
    }

    internal fun earliestSwitchMs(prevHour: Int, newHour: Int, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (labelWith(prevHour, nowMs, zone) == labelWith(newHour, nowMs, zone)) return nowMs
        for (t in hourBoundaries(intArrayOf(prevHour, newHour), nowMs, zone)) {
            if (labelWith(prevHour, t, zone) == labelWith(newHour, t, zone)) return t
        }
        return null
    }

    internal fun nextLabelChangeMs(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val rule = dayStartRule
        val current = if (nowMs >= rule.switchMs) labelWith(rule.hour, nowMs, zone)
        else labelWith(rule.prevHour, nowMs, zone)
        val candidates = (hourBoundaries(intArrayOf(rule.prevHour, rule.hour), nowMs, zone) + rule.switchMs).sorted()
        for (t in candidates) {
            if (t <= nowMs) continue
            val label = if (t >= rule.switchMs) labelWith(rule.hour, t, zone) else labelWith(rule.prevHour, t, zone)
            if (label != current) return t
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zone)
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
    }

    internal fun init(context: Context) {
        if (!::appContext.isInitialized) appContext = context.applicationContext
    }

    internal val calendar: Calendar
        get() {
            val rule = dayStartRule
            val now = System.currentTimeMillis()
            val hour = if (now >= rule.switchMs) rule.hour else rule.prevHour
            val dt = shiftedWallClock(hour, now, ZoneId.systemDefault())
            val calendar = Calendar.getInstance()
            calendar.firstDayOfWeek = AppPreferences.firstDayOfWeek
            calendar.set(Calendar.YEAR, dt.year)
            calendar.set(Calendar.MONTH, dt.monthValue - 1)
            calendar.set(Calendar.DAY_OF_MONTH, dt.dayOfMonth)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar
        }

    internal val MILESTONES = listOf(
        10_000L, 50_000L, 100_000L, 500_000L, 750_000L,
        1_000_000L, 1_500_000L, 2_000_000L, 3_000_000L,
        4_000_000L, 5_000_000L, 6_000_000L, 7_000_000L,
        8_000_000L, 9_000_000L, 10_000_000L, 12_500_000L,
        15_000_000L, 17_500_000L, 20_000_000L
    )

    internal val GOAL_PRESETS = listOf(0, 5_000, 7_000, 10_000)

    internal fun formatSteps(steps: Int): String = if (steps >= 10_000) {
        NumberFormat.getIntegerInstance().format(steps)
    } else {
        steps.toString()
    }

    internal fun formatShortSteps(steps: Long, context: Context): String = when {
        steps >= 1_000_000L -> {
            val millions = steps / 1_000_000.0
            if (millions == millions.toInt().toDouble()) context.getString(R.string.milestone_short_millions, millions.toInt())
            else context.getString(R.string.milestone_short_millions_decimal, millions)
        }
        steps >= 1_000L -> context.getString(R.string.milestone_short_thousands, (steps / 1_000).toInt())
        else -> NumberFormat.getIntegerInstance().format(steps)
    }

    internal fun formatShortStepsDecimal(steps: Long, context: Context): String = when {
        steps >= 1_000_000L -> context.getString(R.string.milestone_short_millions_decimal, steps / 1_000_000.0)
        steps >= 1_000L -> {
            val fractional = NumberFormat.getNumberInstance().apply {
                minimumFractionDigits = 1
                maximumFractionDigits = 1
            }
            fractional.format(steps / 1_000.0) + "k"
        }
        else -> NumberFormat.getIntegerInstance().format(steps)
    }

    internal fun estimatedStepLengthCm(heightCm: Int): Float = heightCm * 0.415f

    internal fun milestoneBadge(milestoneSteps: Int): String = when {
        milestoneSteps >= 20_000_000 -> "♾️"
        milestoneSteps >= 17_500_000 -> "🛸"
        milestoneSteps >= 15_000_000 -> "👽"
        milestoneSteps >= 12_500_000L -> "👑"
        milestoneSteps >=  10_000_000L -> "🦄"
        milestoneSteps >=  9_000_000L -> "💀"
        milestoneSteps >=  8_000_000L -> "🐉"
        milestoneSteps >=  7_000_000L -> "💫"
        milestoneSteps >=  6_000_000 -> "🏆"
        milestoneSteps >=  5_000_000 -> "💎"
        milestoneSteps >=  4_000_000 -> "👻"
        milestoneSteps >=  3_000_000 -> "🚀"
        milestoneSteps >=  2_000_000 -> "🥇"
        milestoneSteps >=  1_500_000 -> "🧭"
        milestoneSteps >=  1_000_000 -> "🗿"
        milestoneSteps >=    750_000 -> "🍀"
        milestoneSteps >=    500_000 -> "🌟"
        milestoneSteps >=    100_000 -> "🔥"
        milestoneSteps >=     50_000 -> "💪"
        milestoneSteps >=     10_000 -> "🎯"
        else                         -> "🎯"
    }

    internal fun todayDateString(): String = label(System.currentTimeMillis())

    internal fun dateAndMinuteFor(millis: Long): Pair<String, Int> {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return calendarToDateString(cal) to cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    private var timeLabelFormat: DateTimeFormatter? = null
    private var timeLabelIs24: Boolean? = null
    private val timeLabelLock = Any()

    internal fun timeLabel(context: Context, minuteOfDay: Int): String {
        val is24 = android.text.format.DateFormat.is24HourFormat(context)
        val minute = ((minuteOfDay % (24 * 60)) + 24 * 60) % (24 * 60)
        synchronized(timeLabelLock) {
            if (timeLabelFormat == null || timeLabelIs24 != is24) {
                timeLabelFormat = DateTimeFormatter.ofPattern(
                    if (is24) "HH:mm" else "h:mm a",
                    Locale.getDefault(),
                )
                timeLabelIs24 = is24
            }
            return timeLabelFormat!!.format(LocalTime.of(minute / 60, minute % 60))
        }
    }

    internal fun logicalToday(): LocalDate = LocalDate.parse(label(System.currentTimeMillis()))

    internal fun dateStringToCalendarMillis(date: String): Long {
        return try {
            val parts = date.split("-")
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, parts[0].toInt())
                set(Calendar.MONTH, parts[1].toInt() - 1)
                set(Calendar.DAY_OF_MONTH, parts[2].toInt())
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            cal.timeInMillis
        } catch (_: Exception) {
            0L
        }
    }

    internal fun millisToDateString(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return "%04d-%02d-%02d".format(
            Locale.ROOT,
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    internal fun calendarToDateString(cal: Calendar): String {
        return "%04d-%02d-%02d".format(
            Locale.ROOT,
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    internal fun distanceUnit(): String =
        if (AppPreferences.unitSystem == UnitSystem.IMPERIAL) appContext.getString(R.string.unit_mi)
        else appContext.getString(R.string.unit_km)

    internal fun distanceFor(steps: Int, stepLengthCm: Float, system: UnitSystem): Float {
        val km = (steps * stepLengthCm) / 100000
        return when (system) {
            UnitSystem.METRIC   -> km
            UnitSystem.IMPERIAL -> km * MILES_PER_KM
        }
    }

    internal fun caloriesFor(steps: Int, weightKg: Int): Int = (steps * weightKg * 0.0005).toInt()

    fun stepsToDistance(steps: Number): Float =
        distanceFor(steps.toInt(), AppPreferences.stepLength, AppPreferences.unitSystem)

    internal fun stepsToCalories(steps: Number): Int =
        caloriesFor(steps.toInt(), AppPreferences.weight)

    internal fun applyTheme(theme: String) {
        when (theme) {
            "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            "light"  -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark"   -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }
}