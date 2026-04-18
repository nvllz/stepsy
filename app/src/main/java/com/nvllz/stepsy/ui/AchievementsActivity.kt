package com.nvllz.stepsy.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nvllz.stepsy.R
import com.nvllz.stepsy.util.AppPreferences
import com.nvllz.stepsy.util.Database
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import com.nvllz.stepsy.util.AchievementsCacheUtil
import com.nvllz.stepsy.util.Util
import com.nvllz.stepsy.util.Util.UnitSystem
import java.util.concurrent.TimeUnit

class AchievementsActivity : AppCompatActivity() {
    private lateinit var database: Database
    private lateinit var dateFormat: DateFormat
    private lateinit var monthFormat: DateFormat
    private lateinit var displayFormat: DateFormat
    private lateinit var milestonesAdapter: MilestonesAdapter

    data class MilestoneAchievement(val milestone: Int, val timestamp: Long)

    data class Top3DayEntry(val steps: Int, val timestamp: Long)

    data class ComputedResults(
        val top3Days: List<Top3DayEntry>,
        val bestWeek: String,
        val bestMonth: String,
        val streakRecord: String,
        val avgStepsPerDay: String,
        val milestones: List<MilestoneAchievement>
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_achievements)

        supportActionBar?.apply {
            title = getString(R.string.achievements_title)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            setBackgroundDrawable(ContextCompat.getColor(this@AchievementsActivity, R.color.colorBackground).toDrawable())
            elevation = 0f
        }

        database = Database.getInstance(this)

        lifecycleScope.launch {
            dateFormat = SimpleDateFormat(AppPreferences.dateFormatString, Locale.getDefault())
            monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            displayFormat = SimpleDateFormat("LLLL yyyy", Locale.getDefault())

            setupViews()
            loadCachedResultsIfAny()
            updateAchievements()
        }
    }

    private fun setupViews() {
        milestonesAdapter = MilestonesAdapter()
        findViewById<RecyclerView>(R.id.milestones_recycler_view).apply {
            adapter = milestonesAdapter
            layoutManager = LinearLayoutManager(this@AchievementsActivity)
            isNestedScrollingEnabled = false
        }

        updateStreakRecordTitle()
    }

    private fun updateStreakRecordTitle() {
        val streakRecordContainer = findViewById<View>(R.id.streak_record_container)
        val streakRecordTitle = streakRecordContainer?.findViewById<TextView>(R.id.streak_record_title)

        if (streakRecordTitle != null) {
            val goalTarget = AppPreferences.dailyGoalTarget
            val formattedGoal = NumberFormat.getIntegerInstance().format(goalTarget)
            streakRecordTitle.text = getString(R.string.streak_record, formattedGoal)
        }
    }

    private fun loadCachedResultsIfAny() {
        val cached = AchievementsCacheUtil.loadCachedResults(this)
        if (cached != null && cached.top3Days != null) {
            updateTop3DaysUI(cached.top3Days)
            updatePersonalRecord(R.id.best_week_value, cached.bestWeek)
            updatePersonalRecord(R.id.best_month_value, cached.bestMonth ?: getString(R.string.no_data_available))
            updatePersonalRecord(R.id.streak_record_value, cached.streakRecord)
            updatePersonalRecord(R.id.avg_steps_per_day_value, cached.avgStepsPerDay)

            if (cached.milestones.isNotEmpty()) {
                showMilestones(cached.milestones)
            } else {
                showNoMilestones()
            }
        } else {
            showNoMilestones()
        }
    }

    private fun updateTop3DaysUI(top3: List<Top3DayEntry>?) {
        val safeList = top3 ?: emptyList()
        val dayIds = listOf(
            Pair(R.id.top_day_1_value, R.id.top_day_1_date),
            Pair(R.id.top_day_2_value, R.id.top_day_2_date),
            Pair(R.id.top_day_3_value, R.id.top_day_3_date)
        )
        val noData = getString(R.string.no_data_available)
        for (i in dayIds.indices) {
            val entry = safeList.getOrNull(i)
            val valueView = findViewById<TextView>(dayIds[i].first)
            val dateView = findViewById<TextView>(dayIds[i].second)
            if (entry != null) {
                valueView.text = formatStepsWithDistance(entry.steps)
                dateView.text = dateFormat.format(Date(entry.timestamp))
            } else {
                valueView.text = noData
                dateView.text = ""
            }
        }
    }

    private suspend fun updateAchievements() {
        try {
            val (firstEntry, lastEntry) = withContext(Dispatchers.IO) {
                database.firstEntry to database.lastEntry
            }

            if (firstEntry == "" || lastEntry == "") {
                updateTop3DaysUI(emptyList())
                updatePersonalRecord(R.id.best_week_value, getString(R.string.no_data_available))
                updatePersonalRecord(R.id.best_month_value, getString(R.string.no_data_available))
                updatePersonalRecord(R.id.streak_record_value, getString(R.string.error_loading_data))
                updatePersonalRecord(R.id.avg_steps_per_day_value, getString(R.string.no_data_available))
                showNoMilestones()
                return
            }

            val results = withContext(Dispatchers.Default) {
                computeAllResults(firstEntry, lastEntry)
            }

            updateTop3DaysUI(results.top3Days)
            updatePersonalRecord(R.id.best_week_value, results.bestWeek)
            updatePersonalRecord(R.id.best_month_value, results.bestMonth)
            updatePersonalRecord(R.id.streak_record_value, results.streakRecord)
            updatePersonalRecord(R.id.avg_steps_per_day_value, results.avgStepsPerDay)

            val orderedMilestones = results.milestones
                .sortedByDescending { it.timestamp }

            AchievementsCacheUtil.saveCachedResults(
                this@AchievementsActivity,
                results.copy(milestones = orderedMilestones)
            )

            if (orderedMilestones.isNotEmpty()) {
                showMilestones(orderedMilestones)
            } else {
                showNoMilestones()
            }

        } catch (_: Exception) {
            updateTop3DaysUI(emptyList())
            updatePersonalRecord(R.id.best_week_value, getString(R.string.error_loading_data))
            updatePersonalRecord(R.id.best_month_value, getString(R.string.error_loading_data))
            updatePersonalRecord(R.id.streak_record_value, getString(R.string.error_loading_data))
            updatePersonalRecord(R.id.avg_steps_per_day_value, getString(R.string.error_loading_data))
            showNoMilestones()
        }
    }

    private fun computeAllResults(firstEntry: String?, lastEntry: String?): ComputedResults {
        val entries = database.getEntries(firstEntry, lastEntry)

        if (entries.isEmpty()) {
            val noData = getString(R.string.no_data_available)
            return ComputedResults(emptyList(), noData, noData, noData, noData, emptyList())
        }

        val firstEntryTimestamp = entries.minOf { it.timestamp }
        var totalSteps = 0
        val milestones = calculateMilestoneAchievementsOptimized(entries)
        val (longestStreak, streakRange) = calculateLongestStreak(entries)

        val sortedByStepsDesc = entries.sortedByDescending { it.steps }
        val top3Days = sortedByStepsDesc.take(3).map { Top3DayEntry(it.steps, it.timestamp) }

        val firstDayOfWeek = AppPreferences.firstDayOfWeek
        val weeklySteps = mutableMapOf<Long, Int>()
        val weeklyRange = mutableMapOf<Long, Pair<Long, Long>>()
        val monthlySteps = mutableMapOf<String, Int>()

        for (entry in entries) {
            totalSteps += entry.steps

            val cal = Calendar.getInstance().apply {
                timeInMillis = entry.timestamp
                this.firstDayOfWeek = firstDayOfWeek
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            while (cal.get(Calendar.DAY_OF_WEEK) != firstDayOfWeek) {
                cal.add(Calendar.DAY_OF_YEAR, -1)
            }
            val weekKey = cal.timeInMillis
            weeklySteps[weekKey] = (weeklySteps[weekKey] ?: 0) + entry.steps
            val existing = weeklyRange[weekKey]
            weeklyRange[weekKey] = if (existing == null) {
                Pair(entry.timestamp, entry.timestamp)
            } else {
                Pair(minOf(existing.first, entry.timestamp), maxOf(existing.second, entry.timestamp))
            }

            val monthKey = monthFormat.format(Date(entry.timestamp))
            monthlySteps[monthKey] = (monthlySteps[monthKey] ?: 0) + entry.steps
        }

        val bestWeekEntry = weeklySteps.maxByOrNull { it.value }
        val bestWeek = if (bestWeekEntry != null) {
            val range = weeklyRange[bestWeekEntry.key]
            val startStr = dateFormat.format(Date(range!!.first))
            val endStr = dateFormat.format(Date(range.second))
            "${formatStepsWithDistance(bestWeekEntry.value)}\n$startStr — $endStr"
        } else {
            getString(R.string.no_data_available)
        }

        val maxMonth = monthlySteps.maxByOrNull { it.value }
        val bestMonth = if (maxMonth != null) {
            val date = monthFormat.parse(maxMonth.key) ?: Date()
            "${formatStepsWithDistance(maxMonth.value)}\n${displayFormat.format(date)}"
        } else {
            getString(R.string.no_data_available)
        }

        val numberOfDays = entries.size
        val avgSteps = if (numberOfDays > 0) totalSteps / numberOfDays else 0
        val avgStepsPerDay = "${formatStepsWithDistance(avgSteps)}\n" +
                getString(R.string.since_date, dateFormat.format(Date(firstEntryTimestamp)))

        val streakRecord = if (longestStreak > 0 && streakRange != null) {
            val dateText = if (longestStreak == 1) {
                "${dateFormat.format(Date(streakRange.second))}"
            } else {
                "${dateFormat.format(Date(streakRange.first))} — ${dateFormat.format(Date(streakRange.second))}"
            }
            resources.getQuantityString(R.plurals.streak_record_count, longestStreak, longestStreak) + "\n$dateText"
        } else {
            resources.getQuantityString(R.plurals.streak_record_count, 0, 0)
        }

        return ComputedResults(top3Days, bestWeek, bestMonth, streakRecord, avgStepsPerDay, milestones)
    }

    private fun calculateMilestoneAchievementsOptimized(entries: List<Database.Entry>): List<MilestoneAchievement> {
        if (entries.isEmpty()) return emptyList()

        val milestoneTargets = listOf(
            10_000, 50_000, 100_000, 500_000, 750_000, 1_000_000, 1_500_000, 2_000_000, 3_000_000,
            4_000_000, 5_000_000, 6_000_000, 7_000_000, 8_000_000, 9_000_000, 10_000_000, 12_500_000,
            15_000_000, 20_000_000
        ).sorted()

        val achievements = mutableListOf<MilestoneAchievement>()
        var cumulativeSteps = 0
        var nextMilestoneIndex = 0
        val sortedEntries = entries.sortedBy { it.timestamp }

        for (entry in sortedEntries) {
            cumulativeSteps += entry.steps

            while (nextMilestoneIndex < milestoneTargets.size &&
                cumulativeSteps >= milestoneTargets[nextMilestoneIndex]) {

                achievements.add(MilestoneAchievement(milestoneTargets[nextMilestoneIndex], entry.timestamp))
                nextMilestoneIndex++
            }

            if (nextMilestoneIndex >= milestoneTargets.size) break
        }

        return achievements.sortedByDescending { it.milestone }
    }

    private fun calculateLongestStreak(entries: List<Database.Entry>): Pair<Int, Pair<Long, Long>?> {
        if (entries.isEmpty()) return Pair(0, null)

        val sortedEntries = entries.sortedBy { it.timestamp }
        var currentStreak = 0
        var longestStreak = 0
        var streakStart: Long? = null
        var longestStreakRange: Pair<Long, Long>? = null
        val dailyGoal = AppPreferences.dailyGoalTarget

        var prevDate: Calendar? = null

        for (entry in sortedEntries) {
            val entryDate = Calendar.getInstance().apply {
                timeInMillis = entry.timestamp
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }

            val isConsecutive = prevDate?.let {
                val diff = entryDate.timeInMillis - it.timeInMillis
                diff == TimeUnit.DAYS.toMillis(1)
            } ?: true

            if (entry.steps >= dailyGoal && isConsecutive) {
                currentStreak++
                if (streakStart == null) streakStart = entry.timestamp
                if (currentStreak > longestStreak) {
                    longestStreak = currentStreak
                    longestStreakRange = Pair(streakStart, entry.timestamp)
                }
            } else {
                currentStreak = if (entry.steps >= dailyGoal) 1 else 0
                streakStart = if (entry.steps >= dailyGoal) entry.timestamp else null
            }
            prevDate = entryDate
        }

        return Pair(longestStreak, longestStreakRange)
    }

    private fun formatStepsWithDistance(steps: Int): String {
        val distanceKm = steps * AppPreferences.stepLength / 100000f
        val distanceUnit = Util.distanceUnit()
        val formattedSteps = if (steps >= 10_000) {
            NumberFormat.getIntegerInstance().format(steps)
        } else {
            steps.toString()
        }

        return if (AppPreferences.unitSystem == UnitSystem.METRIC) {
            "$formattedSteps • %.2f $distanceUnit".format(distanceKm)
        } else {
            val distanceMiles = distanceKm * 0.621371f
            "$formattedSteps • %.2f $distanceUnit".format(distanceMiles)
        }
    }

    private fun updatePersonalRecord(viewId: Int, value: String) {
        findViewById<TextView>(viewId).text = value
    }

    private fun showMilestones(milestones: List<MilestoneAchievement>) {
        findViewById<RecyclerView>(R.id.milestones_recycler_view).visibility = View.VISIBLE
        findViewById<TextView>(R.id.no_milestones_text).visibility = View.GONE
        milestonesAdapter.updateMilestones(milestones)
    }

    private fun showNoMilestones() {
        findViewById<RecyclerView>(R.id.milestones_recycler_view).visibility = View.GONE
        findViewById<TextView>(R.id.no_milestones_text).visibility = View.VISIBLE
    }
}

class MilestonesAdapter : ListAdapter<AchievementsActivity.MilestoneAchievement,
        MilestonesAdapter.MilestoneViewHolder>(DIFF_CALLBACK) {
    private var dateFormat: DateFormat = SimpleDateFormat(AppPreferences.dateFormatString, Locale.getDefault())

    fun updateMilestones(newMilestones: List<AchievementsActivity.MilestoneAchievement>) {
        submitList(newMilestones)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): MilestoneViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_milestone_achievement_alt, parent, false)
        return MilestoneViewHolder(view)
    }

    override fun onBindViewHolder(holder: MilestoneViewHolder, position: Int) {
        holder.bind(getItem(position), isLast = position == itemCount - 1)
    }

    inner class MilestoneViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val badge: TextView    = itemView.findViewById(R.id.achievement_badge)
        private val title: TextView    = itemView.findViewById(R.id.achievement_title)
        private val date: TextView     = itemView.findViewById(R.id.achievement_date)
        private val divider: View      = itemView.findViewById(R.id.milestone_divider)

        fun bind(milestone: AchievementsActivity.MilestoneAchievement, isLast: Boolean = false) {
            divider.visibility = if (isLast) View.GONE else View.VISIBLE

            badge.text = when {
                milestone.milestone >= 20_000_000 -> "🏁"
                milestone.milestone >= 15_000_000 -> "♾️"
                milestone.milestone >= 12_500_000 -> "🪬"
                milestone.milestone >= 10_000_000 -> "👑"
                milestone.milestone >=  9_000_000 -> "🦄"
                milestone.milestone >=  8_000_000 -> "🐉"
                milestone.milestone >=  7_000_000 -> "💫"
                milestone.milestone >=  6_000_000 -> "🏆"
                milestone.milestone >=  5_000_000 -> "💎"
                milestone.milestone >=  4_000_000 -> "🪐"
                milestone.milestone >=  3_000_000 -> "🚀"
                milestone.milestone >=  2_000_000 -> "🥇"
                milestone.milestone >=  1_500_000 -> "⚡"
                milestone.milestone >=  1_000_000 -> "🗿"
                milestone.milestone >=    750_000 -> "⛳"
                milestone.milestone >=    500_000 -> "🌟"
                milestone.milestone >=    100_000 -> "🔥"
                milestone.milestone >=     50_000 -> "💪"
                milestone.milestone >=     10_000 -> "🎯"
                else                              -> "🎯"
            }

            title.text = formatMilestoneTitle(milestone.milestone)
            date.text  = dateFormat.format(Date(milestone.timestamp))
        }

        private fun formatMilestoneTitle(steps: Int): String {
            val distanceKm = steps * AppPreferences.stepLength / 100000f
            val distanceUnit = Util.distanceUnit()
            val distancePart = if (AppPreferences.unitSystem == UnitSystem.METRIC) {
                "%.2f $distanceUnit"
                    .format(distanceKm)
            } else {
                "%.2f $distanceUnit"
                    .format(distanceKm * 0.621371f)
            }

            return when {
                steps >= 1_000_000 -> {
                    val millions = steps / 1_000_000.0
                    if (millions == millions.toInt().toDouble()) {
                        itemView.context.getString(R.string.million_steps_with_distance, millions.toInt(), distancePart)
                    } else {
                        itemView.context.getString(R.string.million_steps_decimal_with_distance, millions, distancePart)
                    }
                }
                steps >= 1_000 -> {
                    val thousands = steps / 1_000
                    itemView.context.getString(R.string.thousand_steps_with_distance, thousands, distancePart)
                }
                else -> itemView.context.getString(R.string.steps_count_with_distance, steps, distancePart)
            }
        }
    }

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AchievementsActivity.MilestoneAchievement>() {
            override fun areItemsTheSame(a: AchievementsActivity.MilestoneAchievement,
                                         b: AchievementsActivity.MilestoneAchievement) =
                a.milestone == b.milestone
            override fun areContentsTheSame(a: AchievementsActivity.MilestoneAchievement,
                                            b: AchievementsActivity.MilestoneAchievement) =
                a == b
        }
    }
}