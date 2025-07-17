package com.nvllz.stepsy.util

import android.content.Context
import com.nvllz.stepsy.R
import java.text.NumberFormat
import java.util.*

internal object StreakCalculator {

    /**
     * Calculates the current goal streak for the user
     * @param context Context for resources
     * @param database Database instance
     * @param dailyGoalTarget Target steps per day
     * @return Pair of (streak count, formatted string) or null if no streak
     */

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

    private fun calculateCurrentStreak(database: Database, dailyGoalTarget: Int): Int {
        val calendar = Calendar.getInstance()
        var streakCount = 0
        var includedToday = false

        val todayStart = getDayStart(calendar)
        val todayEnd = getDayEnd(calendar)

        try {
            val todaySteps = database.getSumSteps(todayStart, todayEnd)
            if (todaySteps >= dailyGoalTarget) {
                streakCount++
                includedToday = true
            }
        } catch (_: Exception) {
        }

        calendar.add(Calendar.DAY_OF_YEAR, -1)

        while (true) {
            val dayStart = getDayStart(calendar)
            val dayEnd = getDayEnd(calendar)

            try {
                val daySteps = database.getSumSteps(dayStart, dayEnd)

                if (daySteps >= dailyGoalTarget) {
                    streakCount++
                    calendar.add(Calendar.DAY_OF_YEAR, -1)
                } else {
                    break
                }
            } catch (_: Exception) {
                break
            }

            // Safety check to avoid infinite loops
            if (streakCount > 10000) break
        }

        return streakCount
    }

    private fun getDayStart(calendar: Calendar): Long {
        val cal = calendar.clone() as Calendar
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun getDayEnd(calendar: Calendar): Long {
        val cal = calendar.clone() as Calendar
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
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