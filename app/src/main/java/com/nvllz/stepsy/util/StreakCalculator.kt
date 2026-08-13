package com.nvllz.stepsy.util

import android.content.Context
import com.nvllz.stepsy.R
import java.text.NumberFormat
import java.util.*

internal object StreakCalculator {

    internal fun calculateGoalStreak(
        context: Context,
        database: Database,
        dailyGoalTarget: Int
    ): Pair<Int, String>? {
        if (dailyGoalTarget <= 0) return null

        val streak = calculateCurrentStreak(database, dailyGoalTarget)

        if (streak <= 1) return null

        val formattedGoal = formatNumber(dailyGoalTarget)
        val stepsText = getStepsText(context, dailyGoalTarget, formattedGoal)
        val daysText = getDaysText(context, streak)

        val streakText = context.getString(
            R.string.goal_streak_line,
            stepsText,
            streak.toString(),
            daysText
        )

        return Pair(streak, streakText)
    }

    private fun calculateCurrentStreak(
        database: Database,
        dailyGoalTarget: Int
    ): Int {
        val calendar = Calendar.getInstance()
        var streakCount = 0

        try {
            val today = Util.calendarToDateString(calendar)
            val todaySteps = database.getSumSteps(today, today)

            if (todaySteps >= dailyGoalTarget) {
                streakCount++
            }

            calendar.add(Calendar.DAY_OF_YEAR, -1)

            while (true) {
                val dateStr = Util.calendarToDateString(calendar)
                val daySteps = database.getSumSteps(dateStr, dateStr)

                if (daySteps >= dailyGoalTarget) {
                    streakCount++
                    calendar.add(Calendar.DAY_OF_YEAR, -1)
                } else {
                    break
                }

                if (streakCount > 10000) break
            }

        } catch (_: Exception) {
            return 0
        }

        return streakCount
    }

    private fun formatNumber(number: Int): String {
        return if (number >= 10_000) {
            NumberFormat.getIntegerInstance(Locale.getDefault()).format(number)
        } else {
            number.toString()
        }
    }

    private fun getStepsText(context: Context, steps: Int, formattedSteps: String): String {
        return context.resources.getQuantityString(
            R.plurals.steps_formatted,
            steps,
            formattedSteps
        )
    }

    private fun getDaysText(context: Context, days: Int): String {
        return context.resources.getQuantityString(
            R.plurals.days_word_only,
            days
        )
    }
}