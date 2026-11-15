package com.nvllz.stepsy.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class ReportKind { WEEK, MONTH, YEAR, CUSTOM }
enum class ReportBucket { DAY, WEEK, MONTH }

data class ReportPeriod(val kind: ReportKind, val start: LocalDate, val end: LocalDate) {
    val days: Int get() = (ChronoUnit.DAYS.between(start, end) + 1).toInt()
    operator fun contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)

    fun previous(): ReportPeriod = when (kind) {
        ReportKind.WEEK -> week(start.minusDays(7))
        ReportKind.MONTH -> month(YearMonth.from(start).minusMonths(1))
        ReportKind.YEAR -> year(start.year - 1)
        ReportKind.CUSTOM -> ReportPeriod(kind, start.minusDays(days.toLong()), start.minusDays(1))
    }

    fun next(): ReportPeriod = when (kind) {
        ReportKind.WEEK -> week(start.plusDays(7))
        ReportKind.MONTH -> month(YearMonth.from(start).plusMonths(1))
        ReportKind.YEAR -> year(start.year + 1)
        ReportKind.CUSTOM -> ReportPeriod(kind, end.plusDays(1), end.plusDays(days.toLong()))
    }

    companion object {
        fun week(start: LocalDate) = ReportPeriod(ReportKind.WEEK, start, start.plusDays(6))
        fun month(ym: YearMonth) = ReportPeriod(ReportKind.MONTH, ym.atDay(1), ym.atEndOfMonth())
        fun year(y: Int) = ReportPeriod(ReportKind.YEAR, LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31))

        fun containing(kind: ReportKind, date: LocalDate, firstDow: DayOfWeek) = when (kind) {
            ReportKind.WEEK -> week(date.with(TemporalAdjusters.previousOrSame(firstDow)))
            ReportKind.MONTH -> month(YearMonth.from(date))
            ReportKind.YEAR -> year(date.year)
            ReportKind.CUSTOM -> ReportPeriod(kind, date.minusDays(6), date)
        }
    }
}

class DaySeries(val firstDay: LocalDate, val steps: IntArray) {
    val lastDay: LocalDate get() = firstDay.plusDays((steps.size - 1).toLong())
    private val prefixTotal = LongArray(steps.size + 1)
    private val prefixLogged = IntArray(steps.size + 1)

    init {
        for (i in steps.indices) {
            prefixTotal[i + 1] = prefixTotal[i] + steps[i].coerceAtLeast(0)
            prefixLogged[i + 1] = prefixLogged[i] + if (steps[i] >= 0) 1 else 0
        }
    }

    val isEmpty get() = steps.isEmpty()
    private fun idx(d: LocalDate) = ChronoUnit.DAYS.between(firstDay, d).toInt()
    fun stepsOn(d: LocalDate): Int { val i = idx(d); return if (i in steps.indices) steps[i] else -1 }

    private fun clamp(from: LocalDate, to: LocalDate): Pair<Int, Int>? {
        if (isEmpty) return null
        val a = idx(from).coerceAtLeast(0)
        val b = idx(to).coerceAtMost(steps.size - 1)
        return if (a > b) null else a to b
    }

    fun total(from: LocalDate, to: LocalDate): Long =
        clamp(from, to)?.let { (a, b) -> prefixTotal[b + 1] - prefixTotal[a] } ?: 0L

    fun logged(from: LocalDate, to: LocalDate): Int =
        clamp(from, to)?.let { (a, b) -> prefixLogged[b + 1] - prefixLogged[a] } ?: 0

    fun extendedWith(day: LocalDate, todaySteps: Int): DaySeries {
        if (isEmpty) return if (todaySteps >= 0) DaySeries(day, intArrayOf(todaySteps)) else this
        if (!day.isAfter(lastDay)) return this
        val gap = ChronoUnit.DAYS.between(lastDay, day).toInt()
        val out = IntArray(steps.size + gap) { -1 }
        steps.copyInto(out)
        out[out.lastIndex] = todaySteps
        return DaySeries(firstDay, out)
    }
}

data class ReportBar(
    val start: LocalDate, val end: LocalDate,
    val steps: Long, val loggedDays: Int, val avgPerDay: Int, val inProgress: Boolean,
)

data class PeriodDelta(val percent: Int, val previous: ReportPeriod, val paceBased: Boolean)

enum class RecordScope { YEAR, EVER }

sealed interface Insight {
    data class VsPrevious(val delta: PeriodDelta) : Insight
    data class SameAsPrevious(val previous: ReportPeriod, val paceBased: Boolean) : Insight
    data class BestYear(val year: Int) : Insight
    data class BestMonth(val scope: RecordScope) : Insight
    data class BestWeek(val scope: RecordScope) : Insight
    data class NewLongestStreak(val days: Int, val ongoing: Boolean) : Insight
    data class TopDay(val date: LocalDate, val steps: Int, val rank: Int) : Insight
    data class ActiveWeekday(val day: DayOfWeek) : Insight
    data class GoalAfterGap(val date: LocalDate, val gapDays: Int?) : Insight
    data class GoalDays(val met: Int, val logged: Int) : Insight
}

data class PeriodReport(
    val period: ReportPeriod,
    val bucket: ReportBucket,
    val inProgress: Boolean,
    val totalSteps: Long,
    val loggedDays: Int,
    val expectedDays: Int,
    val avgPerDay: Int,
    val goal: Int,
    val goalDays: Int,
    val bestDay: Pair<LocalDate, Int>?,
    val longestGoalRun: Int,
    val bars: List<ReportBar>,
    val insights: List<Insight>,
) {
    val isEmpty: Boolean get() = loggedDays == 0 || totalSteps == 0L
    val barIndexOfToday: Int? get() = bars.indexOfFirst { it.inProgress }.takeIf { it >= 0 }
}

class DayPartStats(val steps: LongArray, val included: Int, val rowsInRange: Int) {
    val total: Long get() = steps.sum()
    fun share(part: Int): Float = if (total == 0L) 0f else steps[part].toFloat() / total
    val leader: Int? get() = if (total == 0L) null else steps.indices.maxByOrNull { steps[it] }
}

data class ReportBounds(val firstDate: LocalDate?, val today: LocalDate)
