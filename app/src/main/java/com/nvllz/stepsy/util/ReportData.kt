package com.nvllz.stepsy.util

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object ReportData {
    private const val MIN_DATE = "2000-01-01"
    private const val MAX_DAYS = 40_000

    private class Snapshot(val stamp: Long, val today: String, val series: DaySeries)
    private class PartsSnapshot(val stamp: Long, val today: String, val first: LocalDate, val rows: Array<IntArray?>)
    private data class HeatKey(val stamp: Long, val today: String, val period: ReportPeriod, val goal: Int)

    private val lock = Any()
    @Volatile private var snapshot: Snapshot? = null
    @Volatile private var parts: PartsSnapshot? = null
    private val heatCache = LinkedHashMap<HeatKey, Map<LocalDate, Float>>(16, 0.75f, true)

    fun invalidate() { snapshot = null; parts = null; synchronized(heatCache) { heatCache.clear() } }

    private fun snapshot(db: Database): Snapshot {
        val stamp = db.changeStamp
        val today = Util.todayDateString()
        snapshot?.takeIf { it.stamp == stamp && it.today == today }?.let { return it }
        synchronized(lock) {
            snapshot?.takeIf { it.stamp == stamp && it.today == today }?.let { return it }
            val rows = db.loadDaySteps(MIN_DATE, today).mapNotNull { (d, v) ->
                runCatching { LocalDate.parse(d) }.getOrNull()?.let { it to v }
            }
            val series = if (rows.isEmpty()) DaySeries(LocalDate.parse(today), IntArray(0)) else {
                val first = rows.first().first
                val size = (ChronoUnit.DAYS.between(first, rows.last().first) + 1).toInt()
                if (size > MAX_DAYS) DaySeries(LocalDate.parse(today), IntArray(0)) else {
                    val arr = IntArray(size) { -1 }
                    for ((d, v) in rows) arr[ChronoUnit.DAYS.between(first, d).toInt()] = v
                    DaySeries(first, arr)
                }
            }
            return Snapshot(stamp, today, series).also { snapshot = it }
        }
    }

    private fun todaySteps(db: Database, today: String, live: Int?): Int =
        live ?: db.getEntries(today, today).firstOrNull()?.steps ?: -1

    fun bounds(ctx: Context): ReportBounds {
        val db = Database.getInstance(ctx)
        val today = LocalDate.parse(Util.todayDateString())
        val first = db.firstEntry?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return ReportBounds(first, today)
    }

    fun load(ctx: Context, period: ReportPeriod, goal: Int, firstDow: DayOfWeek, liveToday: Int? = null): PeriodReport {
        val db = Database.getInstance(ctx)
        val snap = snapshot(db)
        val today = LocalDate.parse(snap.today)
        val series = snap.series.extendedWith(today, todaySteps(db, snap.today, liveToday))
        return ReportCompute.build(series, period, today, goal, firstDow)
    }

    fun loadMilestones(ctx: Context, period: ReportPeriod, liveToday: Int? = null): List<MilestoneRecord> {
        val db = Database.getInstance(ctx)
        val snap = snapshot(db)
        val today = LocalDate.parse(snap.today)
        val live = liveToday ?: if (AppPreferences.date == snap.today) AppPreferences.steps else null
        val series = snap.series.extendedWith(today, todaySteps(db, snap.today, live))
        return ReportCompute.milestones(series, period)
    }

    fun loadHeat(ctx: Context, period: ReportPeriod, goal: Int): Map<LocalDate, Float> {
        if (goal <= 0) return emptyMap()
        val db = Database.getInstance(ctx)
        val snap = snapshot(db)
        val today = LocalDate.parse(snap.today)
        val live = if (AppPreferences.date == snap.today) AppPreferences.steps else null
        val heat = HashMap<LocalDate, Float>()
        var d = period.start
        val end = minOf(period.end, today)
        while (!d.isAfter(end)) {
            val v = if (d == today) (live ?: snap.series.stepsOn(d)) else snap.series.stepsOn(d)
            if (v >= 0) heat[d] = v.toFloat() / goal
            d = d.plusDays(1)
        }
        synchronized(heatCache) {
            heatCache[HeatKey(snap.stamp, snap.today, period, goal)] = heat
            while (heatCache.size > 24) heatCache.remove(heatCache.keys.first())
        }
        return heat
    }

    fun peekHeat(ctx: Context, period: ReportPeriod, goal: Int): Map<LocalDate, Float>? {
        if (goal <= 0) return emptyMap()
        val key = HeatKey(Database.getInstance(ctx).changeStamp, Util.todayDateString(), period, goal)
        return synchronized(heatCache) { heatCache[key] }
    }

    fun loadDayParts(ctx: Context, period: ReportPeriod): DayPartStats {
        val db = Database.getInstance(ctx)
        val snap = snapshot(db)
        val today = LocalDate.parse(snap.today)
        val table = partsTable(db, snap)
        val todayRow = if (period.end >= today) db.loadDayParts(snap.today, snap.today).firstOrNull()?.parts else null
        val todayDbTotal = if (period.end >= today) db.getEntries(snap.today, snap.today).firstOrNull()?.steps ?: -1 else -1
        return ReportCompute.dayParts(
            period, today,
            rowOf = { d ->
                if (d == today) todayRow
                else table?.let { t -> val i = ChronoUnit.DAYS.between(t.first, d).toInt(); t.rows.getOrNull(i) }
            },
            totalOf = { d -> if (d == today) todayDbTotal else snap.series.stepsOn(d) },
        )
    }

    private fun partsTable(db: Database, snap: Snapshot): PartsSnapshot? {
        if (snap.series.isEmpty) return null
        parts?.takeIf { it.stamp == snap.stamp && it.today == snap.today }?.let { return it }
        synchronized(lock) {
            parts?.takeIf { it.stamp == snap.stamp && it.today == snap.today }?.let { return it }
            val first = snap.series.firstDay
            val rows = arrayOfNulls<IntArray>(snap.series.steps.size)
            for (r in db.loadDayParts(first.toString(), snap.series.lastDay.toString())) {
                val d = runCatching { LocalDate.parse(r.date) }.getOrNull() ?: continue
                val i = ChronoUnit.DAYS.between(first, d).toInt()
                if (i in rows.indices) rows[i] = r.parts
            }
            return PartsSnapshot(snap.stamp, snap.today, first, rows).also { parts = it }
        }
    }
}
