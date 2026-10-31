package com.nvllz.stepsy.ui

import com.nvllz.stepsy.util.AchievementResults
import com.nvllz.stepsy.util.Database
import com.nvllz.stepsy.util.DayRecord
import com.nvllz.stepsy.util.MilestoneRecord
import com.nvllz.stepsy.util.MonthRecord
import com.nvllz.stepsy.util.RangeRecord
import com.nvllz.stepsy.util.StreakRecord
import com.nvllz.stepsy.util.Util
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters
import java.util.LinkedHashMap
import java.util.TreeMap

internal fun computeResults(db: Database, goal: Int, firstDow: Int): AchievementResults {
    val first = db.firstEntry
    val last = db.lastEntry
    if (first.isNullOrEmpty() || last.isNullOrEmpty()) return AchievementResults.empty(goal, firstDow)
    return buildResults(db.getEntries(first, last), goal, firstDow)
}

private class WeekAcc(var steps: Int, val first: LocalDate, var last: LocalDate)

private fun buildResults(entries: List<Database.Entry>, goal: Int, calendarFirstDow: Int): AchievementResults {
    val days = TreeMap<LocalDate, Int>()
    for (e in entries) {
        val date = parseIsoDate(e.date) ?: continue
        val prev = days[date]
        if (prev == null || e.steps > prev) days[date] = e.steps
    }
    if (days.isEmpty()) return AchievementResults.empty(goal, calendarFirstDow)

    val weekStartDay = calendarDayToDayOfWeek(calendarFirstDow)
    val targets = Util.MILESTONES

    val top3 = ArrayList<DayRecord>(4)
    val weeks = LinkedHashMap<LocalDate, WeekAcc>()
    val months = LinkedHashMap<YearMonth, Int>()
    val milestones = ArrayList<MilestoneRecord>()

    var total = 0L
    var goalDays = 0
    var nextMilestone = 0
    var streak = 0
    var longest = 0
    var streakStart: LocalDate? = null
    var bestStart: LocalDate? = null
    var bestEnd: LocalDate? = null
    var prevDate: LocalDate? = null

    for ((date, steps) in days) {
        total += steps

        top3.add(DayRecord(date.toString(), steps))
        top3.sortByDescending { it.steps }
        if (top3.size > 3) top3.removeAt(3)

        val weekStart = date.with(TemporalAdjusters.previousOrSame(weekStartDay))
        val w = weeks[weekStart]
        if (w == null) weeks[weekStart] = WeekAcc(steps, date, date)
        else { w.steps += steps; w.last = date }

        val ym = YearMonth.from(date)
        months[ym] = (months[ym] ?: 0) + steps

        while (nextMilestone < targets.size && total >= targets[nextMilestone]) {
            milestones.add(MilestoneRecord(targets[nextMilestone].toInt(), date.toString()))
            nextMilestone++
        }

        if (steps >= goal) {
            goalDays++
            val consecutive = prevDate != null && date == prevDate.plusDays(1)
            if (streak > 0 && consecutive) streak++ else { streak = 1; streakStart = date }
            if (streak > longest) { longest = streak; bestStart = streakStart; bestEnd = date }
        } else {
            streak = 0
            streakStart = null
        }
        prevDate = date
    }

    val bestWeek = weeks.values.maxByOrNull { it.steps }?.let {
        RangeRecord(it.steps, it.first.toString(), it.last.toString())
    }
    val bestMonth = months.entries.maxByOrNull { it.value }?.let { MonthRecord(it.key.toString(), it.value) }
    val s = bestStart
    val e = bestEnd
    val streakRecord = if (longest > 0 && s != null && e != null) StreakRecord(longest, s.toString(), e.toString()) else null

    return AchievementResults(
        dailyGoal = goal,
        firstDayOfWeek = calendarFirstDow,
        firstDate = days.firstKey().toString(),
        avgStepsPerDay = (total / days.size).toInt(),
        totalSteps = total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        goalReachedDays = goalDays,
        daysTracked = days.size,
        top3Days = top3,
        bestWeek = bestWeek,
        bestMonth = bestMonth,
        longestStreak = streakRecord,
        milestones = milestones,
    )
}

private fun parseIsoDate(raw: String): LocalDate? = try {
    LocalDate.parse(normalizeDigits(raw.trim()))
} catch (_: DateTimeParseException) {
    null
}

private fun normalizeDigits(s: String): String = buildString(s.length) {
    for (ch in s) {
        val d = Character.digit(ch, 10)
        append(if (d >= 0) '0' + d else ch)
    }
}

private fun calendarDayToDayOfWeek(calDay: Int): DayOfWeek = DayOfWeek.of((calDay + 5) % 7 + 1)