package com.nvllz.stepsy.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.core.content.edit
import com.nvllz.stepsy.R
import com.nvllz.stepsy.util.AchievementsCacheUtil
import com.nvllz.stepsy.util.AppPreferences
import com.nvllz.stepsy.util.Database
import com.nvllz.stepsy.util.Util
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class StepRange(val key: String, @StringRes val label: Int, @StringRes val header: Int) {
    TODAY("TODAY", R.string.label_today, R.string.label_today),
    WEEK("WEEK", R.string.label_week, R.string.header_week),
    MONTH("MONTH", R.string.label_month, R.string.header_month),
    SEVEN("7 DAYS", R.string.label_7d, R.string.header_7d),
    THIRTY("30 DAYS", R.string.label_30d, R.string.header_30d),
    ALL("ALL TIME", R.string.label_all_time, R.string.header_all_time),
}

sealed interface Selection {
    data class Range(val range: StepRange) : Selection
    data class Year(val year: Int) : Selection
    data class Month(val year: Int, val month: Int) : Selection
}

fun monthTitle(year: Int, month: Int): String {
    val locale = Locale.getDefault()
    val pattern = DateFormat.getBestDateTimePattern(locale, "yMMMM")
    val date = Calendar.getInstance().apply { clear(); set(year, month - 1, 1) }.time
    return SimpleDateFormat(pattern, locale).format(date)
        .replaceFirstChar { it.titlecase(locale) }
}

data class Summary(val header: String, val steps: Int, val avg: Int?, val distance: String, val kcal: String, val goalDays: Int = 0)
data class ChartData(val header: String, val range: String, val values: List<Int>, val labels: List<String>, val dates: List<LocalDate>, val past7: Boolean, val weekdayAverages: Boolean = false)
data class HourlyPoint(val minute: Int, val value: Int)
data class HourlyAdjustment(val minute: Int, val delta: Int)
data class HourlyDay(
    val date: String,
    val slots: IntArray,
    val adjustments: List<HourlyAdjustment>,
    val points: List<HourlyPoint>,
    val untimed: Int,
    val partial: Boolean,
    val hasData: Boolean,
    val startMinute: Int,
    val endMinute: Int,
    val total: Int,
    val dbTotal: Int,
    val beforeEra: Boolean = false,
)
data class HourlyAverage(val points: List<HourlyPoint>, val included: Int, val days: Int, val excluded: Int)

private const val PREFS = "RangePrefs"
private const val KEY_RANGE = "selected_range"
private const val KEY_HOURLY = "chart_hourly"
private const val KEY_CHART_LINE = "chart_line"

private fun rangeFromKey(key: String?) = Selection.Range(StepRange.entries.firstOrNull { it.key == key } ?: StepRange.TODAY)

fun loadSelection(ctx: Context): Selection {
    val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val monthOfYear = p.getInt("selected_month", -1)
    if (p.getBoolean("is_month_selected", false) && monthOfYear != -1)
        return Selection.Month(monthOfYear / 12, monthOfYear % 12 + 1)
    val year = p.getInt("selected_year", -1)
    if (p.getBoolean("is_year_selected", false) && year != -1) return Selection.Year(year)
    return rangeFromKey(p.getString(KEY_RANGE, null))
}

fun loadSavedRange(ctx: Context) = rangeFromKey(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_RANGE, null))

fun loadHourlyView(ctx: Context): Boolean =
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_HOURLY, false)

fun saveHourlyView(ctx: Context, hourly: Boolean) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
    putBoolean(KEY_HOURLY, hourly)
}

fun loadChartLine(ctx: Context): Boolean =
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_CHART_LINE, false)

fun saveChartLine(ctx: Context, line: Boolean) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
    putBoolean(KEY_CHART_LINE, line)
}

fun saveSelection(ctx: Context, s: Selection) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
    when (s) {
        is Selection.Range -> putString("selected_range", s.range.key)
        is Selection.Year -> putInt("selected_year", s.year)
        is Selection.Month -> putInt("selected_month", s.year * 12 + (s.month - 1))
    }
    putBoolean("is_month_selected", s is Selection.Month)
    putBoolean("is_year_selected", s is Selection.Year)
}

fun unseenMilestones(context: Context): List<Long> {
    val ceiling = AppPreferences.lastNotifiedMilestone
    if (ceiling <= 0L) return emptyList()
    val seen = runCatching {
        AchievementsCacheUtil.loadSeenMilestones(context.applicationContext)
    }.getOrNull() ?: emptySet()
    return Util.MILESTONES.filter { it <= ceiling && it.toInt() !in seen }
}

fun hasUnseenMilestones(context: Context): Boolean = unseenMilestones(context).isNotEmpty()

fun singleUnseenMilestone(context: Context): Long? = unseenMilestones(context).singleOrNull()

private fun dateFormat() = SimpleDateFormat(AppPreferences.dateFormatString, Locale.getDefault())
private fun dist(c: Context, steps: Int) = c.getString(R.string.distance_today, Util.stepsToDistance(steps), Util.distanceUnit())
private fun kcal(c: Context, steps: Int) = c.getString(R.string.calories, Util.stepsToCalories(steps))

data class FilterVisibility(val years: Boolean = true, val ranges: Set<StepRange> = StepRange.entries.toSet())

fun loadFilterVisibility(ctx: Context): FilterVisibility {
    val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("visible_filters", null)
        ?: return FilterVisibility()
    val keys = raw.split(",").filter { it.isNotEmpty() }.toSet()
    return FilterVisibility(
        years = "YEARS" in keys,
        ranges = StepRange.entries.filter { it.key in keys }.toSet(),
    )
}

fun saveFilterVisibility(ctx: Context, v: FilterVisibility) {
    val keys = v.ranges.map { it.key } + (if (v.years) listOf("YEARS") else emptyList())
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
        putString("visible_filters", keys.joinToString(","))
        remove("filter_order")
    }
}

fun firstEntryDate(ctx: Context): LocalDate =
    Database.getInstance(ctx).firstEntry?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: Util.logicalToday()

fun stepsByDay(ctx: Context): Map<LocalDate, Int> {
    val db = Database.getInstance(ctx)
    val first = db.firstEntry ?: return emptyMap()
    val last = db.lastEntry ?: return emptyMap()
    return db.getEntries(first, last).mapNotNull { e ->
        runCatching { LocalDate.parse(e.date) }.getOrNull()?.let { it to e.steps }
    }.toMap()
}

private fun rangeDates(range: StepRange, db: Database): Pair<String, String> {
    val c = Util.calendar
    fun back(days: Int): Pair<String, String> {
        val end = Util.calendarToDateString(c)
        c.add(Calendar.DAY_OF_YEAR, -days)
        return Util.calendarToDateString(c) to end
    }
    return when (range) {
        StepRange.WEEK -> {
            c.set(Calendar.DAY_OF_WEEK, c.firstDayOfWeek)
            val s = Util.calendarToDateString(c); c.add(Calendar.DAY_OF_YEAR, 6); s to Util.calendarToDateString(c)
        }
        StepRange.MONTH -> {
            c.set(Calendar.DAY_OF_MONTH, 1)
            val s = Util.calendarToDateString(c)
            c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH)); s to Util.calendarToDateString(c)
        }
        StepRange.SEVEN -> back(6)
        StepRange.THIRTY -> back(29)
        else -> { val t = Util.todayDateString(); (db.firstEntry ?: t) to (db.lastEntry ?: t) }
    }
}

private fun selectionDateRange(sel: Selection, db: Database): Pair<String, String> = when (sel) {
    is Selection.Month -> "%04d-%02d-01".format(sel.year, sel.month) to
            "%04d-%02d-%02d".format(sel.year, sel.month, YearMonth.of(sel.year, sel.month).lengthOfMonth())
    is Selection.Year -> "%04d-01-01".format(sel.year) to "%04d-12-31".format(sel.year)
    is Selection.Range -> if (sel.range == StepRange.TODAY) {
        val t = Util.todayDateString(); t to t
    } else rangeDates(sel.range, db)
}

fun loadSummary(ctx: Context, sel: Selection, picked: LocalDate?, live: Int): Summary {
    val db = Database.getInstance(ctx)
    val df = dateFormat()
    if (picked != null) {
        val s = db.getSumSteps(picked.toString(), picked.toString())
        return Summary(df.format(Date(Util.dateStringToCalendarMillis(picked.toString()))), s, null, dist(ctx, s), kcal(ctx, s))
    }
    if (sel == Selection.Range(StepRange.TODAY))
        return Summary(ctx.getString(R.string.label_today), live, null, dist(ctx, live), kcal(ctx, live))

    val dates = selectionDateRange(sel, db)
    val header = when (sel) {
        is Selection.Month -> monthTitle(sel.year, sel.month)
        is Selection.Year -> ctx.getString(R.string.header_year, sel.year)
        is Selection.Range -> if (sel.range == StepRange.ALL)
            ctx.getString(R.string.since_date, df.format(Date(Util.dateStringToCalendarMillis(dates.first))))
        else ctx.getString(sel.range.header)
    }
    val total = db.getSumSteps(dates.first, dates.second)
    val gt = AppPreferences.dailyGoalTarget
    val todayStr = Util.todayDateString()
    val goalDays = if (gt > 0) db.getEntries(dates.first, dates.second)
        .count { (if (it.date == todayStr) live else it.steps) >= gt } else 0
    return Summary(header, total, db.avgSteps(dates.first, dates.second), dist(ctx, total), kcal(ctx, total), goalDays)
}

fun loadChart(ctx: Context, past7: Boolean, selected: LocalDate, live: Int): ChartData {
    val start = Util.calendar.apply {
        if (past7) add(Calendar.DAY_OF_YEAR, -6) else {
            timeInMillis = Util.dateStringToCalendarMillis(selected.toString())
            firstDayOfWeek = AppPreferences.firstDayOfWeek
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        }
    }
    val days = (0 until 7).map { (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, it) } }
    val dates = days.map(Util::calendarToDateString)
    val byDate = Database.getInstance(ctx).getEntries(dates.first(), dates.last()).associate { it.date to it.steps }
    val today = Util.todayDateString()
    val df = dateFormat()
    return ChartData(
        header = if (past7) ctx.getString(R.string.header_7d) else ctx.getString(R.string.week_display_format, start.get(Calendar.WEEK_OF_YEAR)),
        range = ctx.getString(R.string.week_display_range, df.format(days.first().time), df.format(days.last().time)),
        values = dates.map { if (it == today) live else byDate[it] ?: 0 },
        labels = days.map { it.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()).orEmpty() },
        dates = dates.map(LocalDate::parse),
        past7 = past7,
    )
}

fun loadWeekdayAverages(ctx: Context, sel: Selection): ChartData {
    val db = Database.getInstance(ctx)
    val dates = selectionDateRange(sel, db)
    val header = when (sel) {
        is Selection.Month -> monthTitle(sel.year, sel.month)
        is Selection.Year -> ctx.getString(R.string.daily_average_title)
        is Selection.Range -> if (
            sel.range == StepRange.ALL ||
            sel.range == StepRange.MONTH ||
            sel.range == StepRange.THIRTY
        ) ctx.getString(R.string.daily_average_title) else ctx.getString(sel.range.header)
    }
    val firstDow = AppPreferences.firstDayOfWeek
    val sums = LongArray(7)
    val counts = IntArray(7)
    var dayCount = 0
    db.getEntries(dates.first, dates.second).forEach { entry ->
        val date = LocalDate.parse(entry.date)
        val dowIso = date.dayOfWeek.value
        val offset = (dowIso - (firstDow - 1) + 7) % 7
        if (offset in 0..6) {
            sums[offset] += entry.steps
            counts[offset]++
        }
        dayCount++
    }
    val values = (0 until 7).map { i ->
        if (counts[i] > 0) (sums[i] / counts[i]).toInt() else 0
    }
    val labels = Calendar.getInstance().let { c ->
        (0 until 7).map { i ->
            c.set(Calendar.DAY_OF_WEEK, ((firstDow - 1 + i) % 7) + 1)
            c.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()).orEmpty()
        }
    }
    return ChartData(
        header = header,
        range = ctx.resources.getQuantityString(R.plurals.days_with_count, dayCount, dayCount),
        values = values,
        labels = labels,
        dates = emptyList(),
        past7 = false,
        weekdayAverages = true,
    )
}

fun loadHourlyDay(ctx: Context, date: LocalDate, liveSteps: Int): HourlyDay {
    val db = Database.getInstance(ctx)
    val dateStr = date.toString()
    val slots = db.loadSlots(dateStr)
    val adjustments = db.loadAdjust(dateStr).map { HourlyAdjustment(it.minute, it.delta) }
    val dbTotal = db.getSumSteps(dateStr, dateStr)
    val nowMinute = Util.dateAndMinuteFor(System.currentTimeMillis()).second
    val isToday = dateStr == Util.todayDateString()
    var total = dbTotal
    if (isToday && liveSteps > dbTotal) {
        slots[(nowMinute / Database.SLOT_MINUTES).coerceIn(0, Database.SLOT_COUNT - 1)] += liveSteps - dbTotal
        total = liveSteps
    }
    val endMinute = if (isToday) nowMinute else 24 * 60
    val shiftMinutes = AppPreferences.dayStartHour * Database.SLOT_MINUTES
    val firstHourlyDate = if (isToday) null else db.firstHourlyDate()
    return buildHourlyDay(
        dateStr,
        rotateSlots(slots, shiftMinutes),
        adjustments.map { HourlyAdjustment(
            if (it.minute >= 24 * 60) 24 * 60 else ((it.minute - shiftMinutes) % (24 * 60) + 24 * 60) % (24 * 60),
            it.delta,
        ) }
            .sortedBy { it.minute },
        total,
        dbTotal,
        if (isToday) ((endMinute - shiftMinutes) % (24 * 60) + 24 * 60) % (24 * 60) else 24 * 60,
        isToday,
        beforeEra = !isToday && (firstHourlyDate == null || dateStr < firstHourlyDate),
    )
}

fun applyHourlyLive(day: HourlyDay, liveSteps: Int, shiftMinutes: Int): HourlyDay {
    if (liveSteps <= day.dbTotal) return day
    val slots = day.slots.copyOf()
    val nowMinute = Util.dateAndMinuteFor(System.currentTimeMillis()).second
    val nowDisplay = ((nowMinute - shiftMinutes) % (24 * 60) + 24 * 60) % (24 * 60)
    slots[(nowDisplay / Database.SLOT_MINUTES).coerceIn(0, Database.SLOT_COUNT - 1)] += liveSteps - day.dbTotal
    return buildHourlyDay(day.date, slots, day.adjustments, liveSteps, day.dbTotal, nowDisplay, isToday = true)
}

private fun buildHourlyDay(
    date: String,
    slots: IntArray,
    adjustments: List<HourlyAdjustment>,
    total: Int,
    dbTotal: Int,
    endMinute: Int,
    isToday: Boolean,
    beforeEra: Boolean = false,
): HourlyDay {
    val sumSlots = slots.sum()
    val sumAdjust = adjustments.sumOf { it.delta }
    val untimed = (total - sumSlots - sumAdjust).coerceAtLeast(0)
    val hasData = slots.any { it > 0 }
    val firstSlot = slots.indexOfFirst { it > 0 }
    val partial = hasData && firstSlot > 0 && untimed >= 100
    val startMinute = if (partial) firstSlot * Database.SLOT_MINUTES else 0
    val clampedEnd = endMinute.coerceIn(0, 24 * 60)
    val adjustToEnd = adjustments.filter { it.minute <= clampedEnd }.sumOf { it.delta }
    val finalValue = (total - adjustToEnd).coerceAtLeast(0)
    val points = mutableListOf<HourlyPoint>()
    var prefix = 0
    var minute = startMinute
    while (minute <= clampedEnd) {
        points.add(HourlyPoint(minute, (untimed + prefix).coerceAtLeast(0)))
        val slot = minute / Database.SLOT_MINUTES
        if (minute % Database.SLOT_MINUTES == 0 && slot < slots.size) prefix += slots[slot]
        minute += Database.SLOT_MINUTES
    }
    if (points.isEmpty() || points.last().minute < clampedEnd) {
        points.add(HourlyPoint(clampedEnd, finalValue))
    } else if (isToday && points.last().value != finalValue) {
        points[points.lastIndex] = HourlyPoint(clampedEnd, finalValue)
    }
    return HourlyDay(
        date = date,
        slots = slots,
        adjustments = adjustments,
        points = points,
        untimed = untimed,
        partial = partial,
        hasData = hasData,
        startMinute = startMinute,
        endMinute = clampedEnd,
        total = total,
        dbTotal = dbTotal,
        beforeEra = beforeEra,
    )
}

fun loadHourlyAverage(ctx: Context, sel: Selection): HourlyAverage {
    val db = Database.getInstance(ctx)
    val dates = selectionDateRange(sel, db)
    val dayTotals = db.getEntries(dates.first, dates.second)
        .associate { it.date to it.steps }
    val shiftMinutes = AppPreferences.dayStartHour * Database.SLOT_MINUTES
    val prefixes = db.loadSlotsRange(dates.first, dates.second)
        .mapNotNull { day ->
            val total = dayTotals[day.date] ?: return@mapNotNull null
            val sum = day.slots.sum()
            if (day.slots.none { it > 0 } ||
                sum.toLong() * 100 < total.toLong() * 80
            ) null else prefixSums(rotateSlots(day.slots, shiftMinutes))
        }
    val included = prefixes.size
    val points = if (included == 0) emptyList() else (0..Database.SLOT_COUNT).map { i ->
        HourlyPoint(i * Database.SLOT_MINUTES, (prefixes.sumOf { it[i] } / included).toInt())
    }
    return HourlyAverage(points, included, dayTotals.size, (dayTotals.size - included).coerceAtLeast(0))
}

private fun rotateSlots(slots: IntArray, shiftMinutes: Int): IntArray {
    if (shiftMinutes % (24 * 60) == 0) return slots
    val hours = shiftMinutes / Database.SLOT_MINUTES
    return IntArray(Database.SLOT_COUNT) { j -> slots[(j + hours) % Database.SLOT_COUNT] }
}

private fun prefixSums(slots: IntArray): LongArray {
    val prefix = LongArray(slots.size + 1)
    for (i in slots.indices) prefix[i + 1] = prefix[i] + slots[i]
    return prefix
}