package com.nvllz.stepsy.util

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nvllz.stepsy.ui.AchievementsActivity
import androidx.core.content.edit

object AchievementsCacheUtil {
    private const val PREF_NAME = "goals_cache"
    private const val KEY_RESULTS = "cached_results"
    private val gson = Gson()

    fun saveCachedResults(context: Context, results: AchievementsActivity.ComputedResults) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit() { putString(KEY_RESULTS, gson.toJson(results)) }
    }

    fun loadCachedResults(context: Context): AchievementsActivity.ComputedResults? {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_RESULTS, null) ?: return null
        return try {
            val type = object : TypeToken<AchievementsActivity.ComputedResults>() {}.type
            gson.fromJson<AchievementsActivity.ComputedResults>(json, type)
        } catch (_: Exception) {
            null
        }
    }
}
