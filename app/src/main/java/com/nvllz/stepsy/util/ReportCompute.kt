package com.nvllz.stepsy.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

object ReportCompute {
    private const val MIN_RECORD_WEEKS_EVER = 8
    private const val MIN_RECORD_WEEKS_YEAR = 4
    private const val MIN_RECORD_MONTHS = 3
    private const val GAP_DAYS = 14
    private const val MIN_STREAK = 3
    private const val MAX_INSIGHTS = 3

    fun bucketFor(p: ReportPeriod) = when (p.kind) {
        ReportKind.WEEK -> ReportBucket.DAY
        ReportKind.MONTH -> ReportBucket.WEEK
        ReportKind.YEAR -> ReportBucket.MONTH
        ReportKind.CUSTOM -> when {
            p.days <= 14 -> ReportBucket.DAY
            p.days <= 120 -> ReportBucket.WEEK
            else -> ReportBucket.MONTH
        }
    }

    fun build(s: DaySeries, p: ReportPeriod, today: LocalDate, goal: Int, firstDow: DayOfWeek): PeriodReport {
        val inProgress = !p.start.isAfter(today) && !p.end.isBefore(today)
        val total = s.total(p.start, p.end)
        val logged = s.logged(p.start, p.end)
        val bucket = bucketFor(p)
        val spanEnd = if (p.end.isAfter(today)) today else p.end
        val firstDay = if (s.isEmpty) p.start else maxOf(p.start, s.firstDay)
        val expected = if (spanEnd.isBefore(firstDay)) 0 else (ChronoUnit.DAYS.between(firstDay, spanEnd) + 1).toInt()

        var best: Pair<LocalDate, Int>? = null
        var goalDays = 0
        var d = p.start
        while (!d.isAfter(spanEnd)) {
            val v = s.stepsOn(d)
            if (v >= 0) {
                if (best == null || v > best.second) best = d to v
                if (goal > 0 && v >= goal) goalDays++
            }
            d = d.plusDays(1)
        }

        val runs = goalRuns(s, goal)
        val longestInPeriod = runs.filter { it.end >= p.start && it.start <= spanEnd }
            .maxOfOrNull { ChronoUnit.DAYS.between(maxOf(it.start, p.start), minOf(it.end, spanEnd)).toInt() + 1 } ?: 0

        return PeriodReport(
            period = p, bucket = bucket, inProgress = inProgress,
            totalSteps = total, loggedDays = logged, expectedDays = expected,
            avgPerDay = if (logged > 0) (total / logged).toInt() else 0,
            goal = goal, goalDays = goalDays, bestDay = best, longestGoalRun = longestInPeriod,
            bars = bars(s, p, bucket, today, firstDow),
            insights = insights(s, p, today, goal, firstDow, inProgress, goalDays, logged, runs),
        )
    }

    fun milestones(s: DaySeries, p: ReportPeriod): List<MilestoneRecord> {
        if (s.isEmpty) return emptyList()
        val targets = Util.MILESTONES
        val out = ArrayList<MilestoneRecord>()
        var next = 0
        var total = 0L
        for (i in s.steps.indices) {
            total += s.steps[i].coerceAtLeast(0)
            val d = s.firstDay.plusDays(i.toLong())
            while (next < targets.size && total >= targets[next]) {
                if (d in p) out.add(MilestoneRecord(targets[next].toInt(), d.toString()))
                next++
            }
            if (next >= targets.size) break
        }
        return out
    }

    private fun bars(s: DaySeries, p: ReportPeriod, bucket: ReportBucket, today: LocalDate, firstDow: DayOfWeek): List<ReportBar> {
        val out = ArrayList<ReportBar>()
        val weekEndDow = firstDow.minus(1)
        var a = p.start
        while (!a.isAfter(p.end)) {
            val b = when (bucket) {
                ReportBucket.DAY -> a
                ReportBucket.WEEK -> minOf(p.end, a.with(TemporalAdjusters.nextOrSame(weekEndDow)))
                ReportBucket.MONTH -> minOf(p.end, YearMonth.from(a).atEndOfMonth())
            }
            val t = s.total(a, b)
            val l = s.logged(a, b)
            out.add(ReportBar(a, b, t, l, if (l > 0) (t / l).toInt() else 0, !a.isAfter(today) && !b.isBefore(today)))
            a = b.plusDays(1)
        }
        return out
    }

    fun delta(s: DaySeries, p: ReportPeriod, today: LocalDate): PeriodDelta? {
        val prev = p.previous()
        val inProgress = !p.start.isAfter(today) && !p.end.isBefore(today)
        val curEnd: LocalDate
        val prevEnd: LocalDate
        if (inProgress) {
            val n = ChronoUnit.DAYS.between(p.start, today).toInt()
            if (n < 2) return null
            curEnd = p.start.plusDays(n - 1L)
            prevEnd = minOf(prev.end, prev.start.plusDays(n - 1L))
        } else {
            curEnd = p.end
            prevEnd = prev.end
        }
        val curLogged = s.logged(p.start, curEnd)
        val prevLogged = s.logged(prev.start, prevEnd)
        val prevSpan = (ChronoUnit.DAYS.between(prev.start, prevEnd) + 1).toInt()
        if (curLogged == 0 || prevLogged < ceil(prevSpan * 0.5).toInt().coerceAtLeast(1)) return null
        val cur = s.total(p.start, curEnd).toDouble() / curLogged
        val old = s.total(prev.start, prevEnd).toDouble() / prevLogged
        if (old <= 0.0) return null
        val pct = ((cur - old) / old * 100).roundToInt()
        if (abs(pct) >= 1000) return null
        return PeriodDelta(pct, prev, inProgress)
    }

    class GoalRun(val start: LocalDate, val end: LocalDate, val length: Int, val isRecord: Boolean)

    fun goalRuns(s: DaySeries, goal: Int): List<GoalRun> {
        if (goal <= 0 || s.isEmpty) return emptyList()
        val runs = ArrayList<GoalRun>()
        var max = 0
        var runStart = -1
        fun close(endIdx: Int) {
            if (runStart < 0) return
            val len = endIdx - runStart + 1
            runs.add(GoalRun(s.firstDay.plusDays(runStart.toLong()), s.firstDay.plusDays(endIdx.toLong()), len, len > max && len >= MIN_STREAK))
            if (len > max) max = len
            runStart = -1
        }
        for (i in s.steps.indices) {
            if (s.steps[i] >= goal) { if (runStart < 0) runStart = i } else close(i - 1)
        }
        close(s.steps.lastIndex)
        return runs
    }

    private class Bucketed(val start: LocalDate, val total: Long)

    private fun completeWeeks(s: DaySeries, today: LocalDate, firstDow: DayOfWeek): List<Bucketed> {
        if (s.isEmpty) return emptyList()
        val out = ArrayList<Bucketed>()
        var w = s.firstDay.with(TemporalAdjusters.nextOrSame(firstDow))
        while (!w.plusDays(6).isAfter(today.minusDays(1))) {
            out.add(Bucketed(w, s.total(w, w.plusDays(6))))
            w = w.plusDays(7)
        }
        return out
    }

    private fun completeMonths(s: DaySeries, today: LocalDate): List<Bucketed> {
        if (s.isEmpty) return emptyList()
        val out = ArrayList<Bucketed>()
        var m = YearMonth.from(s.firstDay).let { if (s.firstDay.dayOfMonth == 1) it else it.plusMonths(1) }
        while (!m.atEndOfMonth().isAfter(today.minusDays(1))) {
            out.add(Bucketed(m.atDay(1), s.total(m.atDay(1), m.atEndOfMonth())))
            m = m.plusMonths(1)
        }
        return out
    }

    private fun completeYears(s: DaySeries, today: LocalDate): List<Bucketed> {
        if (s.isEmpty) return emptyList()
        val out = ArrayList<Bucketed>()
        var y = if (s.firstDay.dayOfYear == 1) s.firstDay.year else s.firstDay.year + 1
        while (LocalDate.of(y, 12, 31).isBefore(today)) {
            out.add(Bucketed(LocalDate.of(y, 1, 1), s.total(LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31))))
            y++
        }
        return out
    }

    private fun isTop(list: List<Bucketed>, start: LocalDate, inYearOnly: Boolean): Pair<Boolean, Int> {
        val pool = if (inYearOnly) list.filter { it.start.year == start.year } else list
        val me = pool.firstOrNull { it.start == start } ?: return false to pool.size
        return (pool.none { it.total > me.total } && me.total > 0) to pool.size
    }

    private fun mostActiveWeekday(s: DaySeries, p: ReportPeriod, today: LocalDate): Insight.ActiveWeekday? {
        val spanEnd = if (p.end.isAfter(today)) today else p.end
        val sums = LongArray(7)
        val counts = IntArray(7)
        var loggedTotal = 0
        var d = p.start
        while (!d.isAfter(spanEnd)) {
            val v = s.stepsOn(d)
            if (v > 0) {
                sums[d.dayOfWeek.value - 1] += v.toLong()
                counts[d.dayOfWeek.value - 1]++
                loggedTotal++
            }
            d = d.plusDays(1)
        }
        if (loggedTotal < 6) return null
        val ranked = (0 until 7).filter { counts[it] >= 2 }.sortedByDescending { sums[it] }
        if (ranked.size < 2) return null
        val best = ranked[0]
        if (sums[best] * 100 < sums[ranked[1]] * 115) return null
        return Insight.ActiveWeekday(DayOfWeek.of(best + 1))
    }

    private fun insights(
        s: DaySeries, p: ReportPeriod, today: LocalDate, goal: Int, firstDow: DayOfWeek,
        inProgress: Boolean, goalDays: Int, logged: Int, runs: List<GoalRun>,
    ): List<Insight> {
        val out = ArrayList<Insight>()
        delta(s, p, today)?.let { d ->
            out.add(if (d.percent == 0 || abs(d.percent) < 2) Insight.SameAsPrevious(d.previous, d.paceBased) else Insight.VsPrevious(d))
        }

        val records = ArrayList<Insight>()
        if (!inProgress && p.kind != ReportKind.CUSTOM) {
            when (p.kind) {
                ReportKind.YEAR -> {
                    val years = ArrayList(completeYears(s, today))
                    val yStart = LocalDate.of(today.year, 1, 1)
                    val cur = s.total(yStart, today)
                    if (cur > 0) years.add(Bucketed(yStart, cur))
                    if (years.size >= 2 && isTop(years, p.start, false).first) records.add(Insight.BestYear(p.start.year))
                }
                ReportKind.MONTH -> {
                    val months = ArrayList(completeMonths(s, today))
                    val mStart = today.withDayOfMonth(1)
                    val cur = s.total(mStart, today)
                    if (cur > 0) months.add(Bucketed(mStart, cur))
                    val ever = isTop(months, p.start, false)
                    val yr = isTop(months, p.start, true)
                    if (ever.first && ever.second >= MIN_RECORD_MONTHS) records.add(Insight.BestMonth(RecordScope.EVER))
                    else if (yr.first && yr.second >= MIN_RECORD_MONTHS) records.add(Insight.BestMonth(RecordScope.YEAR))
                }
                ReportKind.WEEK -> {
                    val weeks = ArrayList(completeWeeks(s, today, firstDow))
                    val wStart = today.with(TemporalAdjusters.previousOrSame(firstDow))
                    val cur = s.total(wStart, today)
                    if (cur > 0) weeks.add(Bucketed(wStart, cur))
                    val ever = isTop(weeks, p.start, false)
                    val yr = isTop(weeks, p.start, true)
                    if (ever.first && ever.second >= MIN_RECORD_WEEKS_EVER) records.add(Insight.BestWeek(RecordScope.EVER))
                    else if (yr.first && yr.second >= MIN_RECORD_WEEKS_YEAR) records.add(Insight.BestWeek(RecordScope.YEAR))
                }
                else -> Unit
            }
        }

        val spanEnd = if (p.end.isAfter(today)) today else p.end
        runs.filter { it.isRecord && it.end >= p.start && it.start <= spanEnd }.maxByOrNull { it.length }?.let {
            records.add(Insight.NewLongestStreak(it.length, !it.end.isBefore(today.minusDays(1))))
        }

        val top3 = ArrayList<Pair<LocalDate, Int>>()
        for (i in s.steps.indices) {
            val v = s.steps[i]
            if (v <= 0) continue
            top3.add(s.firstDay.plusDays(i.toLong()) to v)
            top3.sortByDescending { it.second }
            if (top3.size > 3) top3.removeAt(3)
        }
        top3.withIndex().firstOrNull { (_, e) -> e.first in p }?.let { (i, e) ->
            records.add(Insight.TopDay(e.first, e.second, i + 1))
        }

        if (goal > 0) {
            var lastGoalIdx = -1
            var found: Insight.GoalAfterGap? = null
            for (i in s.steps.indices) {
                if (s.steps[i] < goal) continue
                val date = s.firstDay.plusDays(i.toLong())
                if (date in p) {
                    val gap = if (lastGoalIdx < 0) null else i - lastGoalIdx
                    val historyBefore = s.logged(s.firstDay, date.minusDays(1))
                    if (gap == null && historyBefore >= 7 || gap != null && gap >= GAP_DAYS) found = Insight.GoalAfterGap(date, gap)
                }
                lastGoalIdx = i
            }
            found?.let { records.add(it) }
        }

        mostActiveWeekday(s, p, today)?.let { records.add(it) }

        val rank = { i: Insight ->
            when (i) {
                is Insight.BestYear -> 0
                is Insight.BestMonth -> 1
                is Insight.BestWeek -> if (i.scope == RecordScope.EVER) 2 else 4
                is Insight.NewLongestStreak -> 3
                is Insight.TopDay -> 5
                is Insight.GoalAfterGap -> 6
                is Insight.ActiveWeekday -> 7
                else -> 9
            }
        }
        records.sortBy(rank)
        for (r in records) if (out.size < MAX_INSIGHTS) out.add(r)
        if (out.size < 2 && goal > 0 && logged > 0) out.add(Insight.GoalDays(goalDays, logged))
        return out
    }

    fun dayParts(
        p: ReportPeriod, today: LocalDate,
        rowOf: (LocalDate) -> IntArray?, totalOf: (LocalDate) -> Int,
    ): DayPartStats {
        val sums = LongArray(4)
        var included = 0
        var rows = 0
        var d = p.start
        val end = if (p.end.isAfter(today)) today else p.end
        while (!d.isAfter(end)) {
            val total = totalOf(d)
            if (total >= 0) {
                rows++
                val r = rowOf(d)
                if (r != null && r[4] > 0 && r[4].toLong() * 100 >= total.toLong() * 80) {
                    included++
                    for (i in 0..3) sums[i] += r[i].toLong()
                }
            }
            d = d.plusDays(1)
        }
        return DayPartStats(sums, included, rows)
    }
}
