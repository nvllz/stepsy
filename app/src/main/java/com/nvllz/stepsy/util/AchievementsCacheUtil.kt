package com.nvllz.stepsy.util

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

data class DayRecord(val date: String, val steps: Int)
data class RangeRecord(val steps: Int, val startDate: String, val endDate: String)
data class MonthRecord(val yearMonth: String, val steps: Int)
data class StreakRecord(val days: Int, val startDate: String, val endDate: String)
data class MilestoneRecord(val milestone: Int, val date: String)

data class AchievementResults(
    val dailyGoal: Int,
    val firstDayOfWeek: Int,
    val firstDate: String?,
    val avgStepsPerDay: Int,
    val top3Days: List<DayRecord>,
    val bestWeek: RangeRecord?,
    val bestMonth: MonthRecord?,
    val longestStreak: StreakRecord?,
    val milestones: List<MilestoneRecord>
) {
    val hasData: Boolean get() = firstDate != null

    companion object {
        fun empty(goal: Int, firstDow: Int) = AchievementResults(
            goal, firstDow, null, 0, emptyList(), null, null, null, emptyList()
        )
    }
}

object AchievementsCacheUtil {
    private const val TAG = "AchievementsCache"
    private const val PREF_NAME = "achievements_cache"
    private const val LEGACY_PREF_NAME = "goals_cache"
    private const val KEY_PAYLOAD = "payload"
    private const val SCHEMA_VERSION = 3
    private const val KEY_SEEN = "seen_milestones"

    private val ISO_DATE = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
    private val ISO_MONTH = Regex("[0-9]{4}-[0-9]{2}")

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /** Returns valid cached results, or null (and wipes the cache) if missing/corrupt/outdated. */
    fun load(context: Context): AchievementResults? {
        val raw = prefs(context).getString(KEY_PAYLOAD, null) ?: return null
        return try {
            fromJson(JSONObject(raw))
        } catch (e: Exception) {
            Log.w(TAG, "Discarding invalid achievements cache", e)
            clear(context)
            null
        }
    }

    fun save(context: Context, results: AchievementResults) {
        try {
            val json = toJson(results).toString()
            prefs(context).edit { putString(KEY_PAYLOAD, json) }

            val legacy = context.applicationContext.getSharedPreferences(LEGACY_PREF_NAME, Context.MODE_PRIVATE)
            if (legacy.all.isNotEmpty()) legacy.edit { clear() }
        } catch (e: Exception) {
            Log.w(TAG, "Could not save achievements cache", e)
        }
    }

    fun clear(context: Context) {
        prefs(context).edit { remove(KEY_PAYLOAD) }
    }

    /** null = never stored (first launch after update) */
    fun loadSeenMilestones(context: Context): Set<Int>? =
        prefs(context).getStringSet(KEY_SEEN, null)?.mapNotNull { it.toIntOrNull() }?.toSet()

    fun saveSeenMilestones(context: Context, seen: Set<Int>) {
        prefs(context).edit { putStringSet(KEY_SEEN, seen.map { it.toString() }.toSet()) }
    }

    private fun toJson(r: AchievementResults): JSONObject {
        val o = JSONObject()
        o.put("schema", SCHEMA_VERSION)
        o.put("dailyGoal", r.dailyGoal)
        o.put("firstDayOfWeek", r.firstDayOfWeek)
        o.putOpt("firstDate", r.firstDate)
        o.put("avg", r.avgStepsPerDay)

        val top3 = JSONArray()
        r.top3Days.forEach { top3.put(JSONObject().put("date", it.date).put("steps", it.steps)) }
        o.put("top3", top3)

        r.bestWeek?.let {
            o.put("bestWeek", JSONObject().put("steps", it.steps).put("start", it.startDate).put("end", it.endDate))
        }
        r.bestMonth?.let {
            o.put("bestMonth", JSONObject().put("ym", it.yearMonth).put("steps", it.steps))
        }
        r.longestStreak?.let {
            o.put("streak", JSONObject().put("days", it.days).put("start", it.startDate).put("end", it.endDate))
        }

        val ms = JSONArray()
        r.milestones.forEach { ms.put(JSONObject().put("m", it.milestone).put("date", it.date)) }
        o.put("milestones", ms)
        return o
    }

    /** Strict: throws on anything unexpected, so the caller discards the cache. */
    private fun fromJson(o: JSONObject): AchievementResults {
        check(o.getInt("schema") == SCHEMA_VERSION) { "schema mismatch" }
        return AchievementResults(
            dailyGoal = o.getInt("dailyGoal"),
            firstDayOfWeek = o.getInt("firstDayOfWeek"),
            firstDate = o.isoDate("firstDate"),
            avgStepsPerDay = o.nonNegInt("avg"),
            top3Days = o.getJSONArray("top3").mapObjects { DayRecord(it.isoDate("date"), it.nonNegInt("steps")) },
            bestWeek = o.optObject("bestWeek")?.let {
                RangeRecord(it.nonNegInt("steps"), it.isoDate("start"), it.isoDate("end"))
            },
            bestMonth = o.optObject("bestMonth")?.let {
                MonthRecord(it.yearMonth("ym"), it.nonNegInt("steps"))
            },
            longestStreak = o.optObject("streak")?.let {
                StreakRecord(it.nonNegInt("days"), it.isoDate("start"), it.isoDate("end"))
            },
            milestones = o.getJSONArray("milestones").mapObjects {
                MilestoneRecord(it.nonNegInt("m"), it.isoDate("date"))
            }
        )
    }

    private fun JSONObject.optObject(name: String): JSONObject? =
        if (isNull(name)) null else getJSONObject(name)

    private fun JSONObject.nonNegInt(name: String): Int =
        getInt(name).also { require(it >= 0) { "negative $name" } }

    private fun JSONObject.isoDate(name: String): String =
        getString(name).also { require(ISO_DATE.matches(it)) { "bad date '$it'" } }

    private fun JSONObject.yearMonth(name: String): String =
        getString(name).also { require(ISO_MONTH.matches(it)) { "bad month '$it'" } }

    private fun <T> JSONArray.mapObjects(f: (JSONObject) -> T): List<T> =
        (0 until length()).map { f(getJSONObject(it)) }
}