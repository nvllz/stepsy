package com.nvllz.stepsy.ui

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nvllz.stepsy.R
import com.nvllz.stepsy.util.AchievementResults
import com.nvllz.stepsy.util.AchievementsCacheUtil
import com.nvllz.stepsy.util.AppPreferences
import com.nvllz.stepsy.util.Database
import com.nvllz.stepsy.util.DayRecord
import com.nvllz.stepsy.util.MilestoneRecord
import com.nvllz.stepsy.util.MonthRecord
import com.nvllz.stepsy.util.RangeRecord
import com.nvllz.stepsy.util.StreakRecord
import com.nvllz.stepsy.util.Util
import com.nvllz.stepsy.util.Util.UnitSystem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters
import java.util.*

class AchievementsActivity : AppCompatActivity() {
    private lateinit var database: Database
    private lateinit var dateFormat: DateFormat
    private lateinit var displayFormat: DateFormat
    private lateinit var milestonesAdapter: MilestonesAdapter
    private var seenAtOpen: Set<Int> = emptySet()
    private var pendingOpenMilestone: Int? = null

    data class MilestoneAchievement(val milestone: Int, val timestamp: Long, val isNew: Boolean = false)

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
        dateFormat = SimpleDateFormat(AppPreferences.dateFormatString, Locale.getDefault())
        displayFormat = SimpleDateFormat("LLLL yyyy", Locale.getDefault())

        setupMilestoneNotificationToggle()
        setupViews()

        pendingOpenMilestone = if (savedInstanceState == null)
            intent?.getIntExtra(EXTRA_MILESTONE, -1)?.takeIf { it > 0 }
        else null

        lifecycleScope.launch { loadAndRefresh() }
    }

    private suspend fun loadAndRefresh() {
        val goal = AppPreferences.dailyGoalTarget
        val firstDow = AppPreferences.firstDayOfWeek

        seenAtOpen = withContext(Dispatchers.IO) {
            AchievementsCacheUtil.loadSeenMilestones(applicationContext)
        } ?: emptySet()

        val cached = withContext(Dispatchers.IO) { AchievementsCacheUtil.load(applicationContext) }
        if (cached != null) {
            render(cached, goal, firstDow)
            markSeen(cached)
        } else showLoading()

        try {
            val fresh = withContext(Dispatchers.IO) { computeResults(goal, firstDow) }
            if (fresh == cached) return

            render(fresh, goal, firstDow)
            markSeen(fresh)
            withContext(Dispatchers.IO) {
                if (fresh.hasData) AchievementsCacheUtil.save(applicationContext, fresh)
                else AchievementsCacheUtil.clear(applicationContext)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (cached == null) showError()
        }
    }

    private suspend fun markSeen(r: AchievementResults) {
        val all = seenAtOpen + r.milestones.map { it.milestone }
        withContext(Dispatchers.IO) { AchievementsCacheUtil.saveSeenMilestones(applicationContext, all) }
    }

    private fun setupViews() {
        milestonesAdapter = MilestonesAdapter { achievement -> showMilestoneDetail(achievement) }
        findViewById<RecyclerView>(R.id.milestones_recycler_view).apply {
            adapter = milestonesAdapter
            layoutManager = LinearLayoutManager(this@AchievementsActivity)
            isNestedScrollingEnabled = false
        }
        updateStreakRecordTitle()
    }

    private fun setupMilestoneNotificationToggle() {
        val btn = findViewById<ImageButton>(R.id.btn_milestone_notifications)
        updateNotificationButtonAppearance(btn, AppPreferences.milestoneNotificationsEnabled)

        TooltipCompat.setTooltipText(btn, getString(R.string.milestone_notifications_tooltip))

        btn.setOnClickListener {
            val newValue = !AppPreferences.milestoneNotificationsEnabled
            AppPreferences.milestoneNotificationsEnabled = newValue
            AppPreferences.lastNotifiedMilestone =
                Util.MILESTONES.lastOrNull { it < AppPreferences.baseTotalSteps } ?: 0L
            updateNotificationButtonAppearance(btn, newValue)
        }
    }

    private fun updateNotificationButtonAppearance(btn: ImageButton, enabled: Boolean) {
        if (enabled) {
            btn.setImageResource(R.drawable.ic_notifications_active)
            btn.alpha = 1.0f
        } else {
            btn.setImageResource(R.drawable.ic_notifications_off)
            btn.alpha = 0.35f
        }
    }

    private fun updateStreakRecordTitle() {
        val streakRecordTitle = findViewById<TextView>(R.id.streak_record_title) ?: return
        val formattedGoal = NumberFormat.getIntegerInstance().format(AppPreferences.dailyGoalTarget)
        streakRecordTitle.text = getString(R.string.streak_record, formattedGoal)
    }

    private fun computeResults(goal: Int, firstDow: Int): AchievementResults {
        val first = database.firstEntry
        val last = database.lastEntry
        if (first.isNullOrEmpty() || last.isNullOrEmpty()) return AchievementResults.empty(goal, firstDow)
        return buildResults(database.getEntries(first, last), goal, firstDow)
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
        val targets = Util.MILESTONES.map { it }

        val top3 = ArrayList<DayRecord>(4)
        val weeks = LinkedHashMap<LocalDate, WeekAcc>()
        val months = LinkedHashMap<YearMonth, Int>()
        val milestones = ArrayList<MilestoneRecord>()

        var total = 0L
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
                val consecutive = prevDate != null && date == prevDate.plusDays(1)
                if (streak > 0 && consecutive) {
                    streak++
                } else {
                    streak = 1
                    streakStart = date
                }
                if (streak > longest) {
                    longest = streak
                    bestStart = streakStart
                    bestEnd = date
                }
            } else {
                streak = 0
                streakStart = null
            }
            prevDate = date
        }

        val bestWeek = weeks.values.maxByOrNull { it.steps }?.let {
            RangeRecord(it.steps, it.first.toString(), it.last.toString())
        }
        val bestMonth = months.entries.maxByOrNull { it.value }?.let {
            MonthRecord(it.key.toString(), it.value)
        }
        val s = bestStart
        val e = bestEnd
        val streakRecord = if (longest > 0 && s != null && e != null) {
            StreakRecord(longest, s.toString(), e.toString())
        } else null

        return AchievementResults(
            dailyGoal = goal,
            firstDayOfWeek = calendarFirstDow,
            firstDate = days.firstKey().toString(),
            avgStepsPerDay = (total / days.size).toInt(),
            top3Days = top3,
            bestWeek = bestWeek,
            bestMonth = bestMonth,
            longestStreak = streakRecord,
            milestones = milestones
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

    private fun render(r: AchievementResults, currentGoal: Int, currentFirstDow: Int) {
        if (!r.hasData) { renderEmpty(); return }

        val weekValid = r.firstDayOfWeek == currentFirstDow
        val streakValid = r.dailyGoal == currentGoal
        val loading = getString(R.string.loading_data)
        val noData = getString(R.string.no_data_available)

        updateTop3DaysUI(r.top3Days)

        when {
            !weekValid -> updateBestWeekUI(loading, null, null)
            r.bestWeek != null -> updateBestWeekUI(
                formatStepsWithDistance(r.bestWeek.steps, "•"),
                formatDate(r.bestWeek.startDate),
                formatDate(r.bestWeek.endDate)
            )
            else -> updateBestWeekUI(noData, null, null)
        }

        updateBestMonthUI(
            r.bestMonth?.let { formatStepsWithDistance(it.steps, "•") } ?: noData,
            r.bestMonth?.let { formatMonth(it.yearMonth) }
        )

        updateAvgStepsUI(
            if (r.firstDate != null) formatStepsWithDistance(r.avgStepsPerDay, "•") else noData,
            r.firstDate?.let { getString(R.string.since_date, formatDate(it)) }
        )

        if (!streakValid) {
            updateStreakRecordUI(loading, null, null)
        } else {
            val st = r.longestStreak
            val days = st?.days ?: 0
            updateStreakRecordUI(
                resources.getQuantityString(R.plurals.streak_record_count, days, days),
                if (st != null && st.days > 1) formatDate(st.startDate) else null,
                st?.let { formatDate(it.endDate) }
            )
        }

        val milestones = r.milestones
            .sortedByDescending { it.milestone }
            .map {
                MilestoneAchievement(
                    it.milestone,
                    Util.dateStringToCalendarMillis(it.date),
                    isNew = it.milestone !in seenAtOpen
                )
            }
        if (milestones.isEmpty()) showNoMilestones() else showMilestones(milestones)

        pendingOpenMilestone?.let { target ->
            milestones.firstOrNull { it.milestone == target }?.let {
                pendingOpenMilestone = null
                showMilestoneDetail(it)
            }
        }
    }

    private fun renderEmpty() {
        val noData = getString(R.string.no_data_available)
        updateTop3DaysUI(emptyList())
        updateBestWeekUI(noData, null, null)
        updateBestMonthUI(noData, null)
        updateStreakRecordUI(noData, null, null)
        updateAvgStepsUI(noData, null)
        showNoMilestones()
    }

    private fun showLoading() {
        val loading = getString(R.string.loading_data)
        updateTop3DaysUI(emptyList())
        updateBestWeekUI(loading, null, null)
        updateBestMonthUI(loading, null)
        updateStreakRecordUI(loading, null, null)
        updateAvgStepsUI(loading, null)
        findViewById<RecyclerView>(R.id.milestones_recycler_view).visibility = View.GONE
        findViewById<TextView>(R.id.no_milestones_text).visibility = View.GONE
    }

    private fun showError() {
        val error = getString(R.string.error_loading_data)
        updateTop3DaysUI(emptyList())
        updateBestWeekUI(error, null, null)
        updateBestMonthUI(error, null)
        updateStreakRecordUI(error, null, null)
        updateAvgStepsUI(error, null)
        showNoMilestones()
    }

    private fun updateTop3DaysUI(top3: List<DayRecord>) {
        val cards = listOf(
            R.id.top_day_1_card,
            R.id.top_day_2_card,
            R.id.top_day_3_card
        )

        val values = listOf(
            Triple(R.id.top_day_1_value, R.id.top_day_1_distance, R.id.top_day_1_date),
            Triple(R.id.top_day_2_value, R.id.top_day_2_distance, R.id.top_day_2_date),
            Triple(R.id.top_day_3_value, R.id.top_day_3_distance, R.id.top_day_3_date)
        )

        val backgroundAlphas = listOf(
            0.8f, // 1st
            0.4f, // 2nd
            0.2f  // 3rd
        )

        for (i in cards.indices) {
            val entry = top3.getOrNull(i)
            val card = findViewById<View>(cards[i])

            if (entry != null) {
                card.visibility = View.VISIBLE

                card.background?.alpha = (backgroundAlphas[i] * 255).toInt()

                val (valueId, distanceId, dateId) = values[i]

                findViewById<TextView>(valueId).text = formatSteps(entry.steps)
                findViewById<TextView>(distanceId).text = formatDistance(entry.steps)
                findViewById<TextView>(dateId).text = formatDate(entry.date)
            } else {
                card.visibility = View.GONE
            }
        }
    }

    private fun formatDate(iso: String): String =
        dateFormat.format(Date(Util.dateStringToCalendarMillis(iso)))

    private fun formatMonth(yearMonth: String): String =
        displayFormat.format(Date(Util.dateStringToCalendarMillis("$yearMonth-01")))

    private fun formatSteps(steps: Int): String =
        if (steps >= 10_000) NumberFormat.getIntegerInstance().format(steps) else steps.toString()

    private fun formatDistance(steps: Int): String =
        "%.2f %s".format(Util.stepsToDistance(steps), Util.distanceUnit())

    private fun formatStepsWithDistance(steps: Int, divider: String = ""): String =
        "${formatSteps(steps)} $divider ${formatDistance(steps)}"

    private fun updateBestWeekUI(value: String, startDate: String?, endDate: String?) {
        updatePersonalRecord(R.id.best_week_value, value)
        val startView = findViewById<TextView>(R.id.best_week_start_date)
        val endView = findViewById<TextView>(R.id.best_week_end_date)
        val hasRange = !startDate.isNullOrEmpty() && !endDate.isNullOrEmpty()
        startView.text = startDate ?: ""
        endView.text = endDate ?: ""
        startView.visibility = if (hasRange) View.VISIBLE else View.GONE
        endView.visibility = if (hasRange) View.VISIBLE else View.GONE
    }

    private fun updateStreakRecordUI(value: String, startDate: String?, endDate: String?) {
        updatePersonalRecord(R.id.streak_record_value, value)
        val startView = findViewById<TextView>(R.id.streak_record_start_date)
        val endView = findViewById<TextView>(R.id.streak_record_end_date)
        startView.text = startDate ?: ""
        endView.text = endDate ?: ""
        startView.visibility = if (!startDate.isNullOrEmpty()) View.VISIBLE else View.GONE
        endView.visibility = if (!endDate.isNullOrEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateBestMonthUI(value: String, date: String?) {
        updatePersonalRecord(R.id.best_month_value, value)
        val dateView = findViewById<TextView>(R.id.best_month_date)
        dateView.text = date ?: ""
        dateView.visibility = if (!date.isNullOrEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateAvgStepsUI(value: String, date: String?) {
        updatePersonalRecord(R.id.avg_steps_per_day_value, value)
        val dateView = findViewById<TextView>(R.id.avg_steps_per_day_date)
        dateView.text = date ?: ""
        dateView.visibility = if (!date.isNullOrEmpty()) View.VISIBLE else View.GONE
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


    private fun showMilestoneDetail(achievement: MilestoneAchievement) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_milestone_detail, null)

        val badge       = dialogView.findViewById<TextView>(R.id.milestone_detail_badge)
        val stepsValue  = dialogView.findViewById<TextView>(R.id.milestone_detail_steps_value)
        val distanceVal = dialogView.findViewById<TextView>(R.id.milestone_detail_distance_value)
        val dateVal     = dialogView.findViewById<TextView>(R.id.milestone_detail_date_value)
        val daysVal     = dialogView.findViewById<TextView>(R.id.milestone_detail_days_value)
        val daysCell    = dialogView.findViewById<View>(R.id.milestone_detail_days_cell)

        val numberFormat = NumberFormat.getIntegerInstance()
        val fullSteps = numberFormat.format(achievement.milestone)
        stepsValue.text = resources.getQuantityString(
            R.plurals.steps_formatted, achievement.milestone, fullSteps
        )

        val distanceKm = achievement.milestone * AppPreferences.stepLength / 100000f
        val distanceUnit = Util.distanceUnit()
        distanceVal.text = if (AppPreferences.unitSystem == UnitSystem.METRIC) {
            "%.0f $distanceUnit".format(distanceKm)
        } else {
            "%.0f $distanceUnit".format(distanceKm * 0.621371f)
        }

        dateVal.text = dateFormat.format(Date(achievement.timestamp))
        badge.text = Util.milestoneBadge(achievement.milestone)

        val newVis = if (achievement.isNew) View.VISIBLE else View.GONE
        dialogView.findViewById<View>(R.id.milestone_detail_new)?.visibility = newVis
        dialogView.findViewById<View>(R.id.milestone_detail_new_ring)?.visibility = newVis

        val dialog = android.app.Dialog(this, R.style.ThemeOverlay_stepsy_MilestoneDialog)
        dialog.setContentView(dialogView)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setCancelable(true)

        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0.75f)
            attributes = attributes.apply {
                windowAnimations = R.style.MilestoneDialogAnimation
                width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            }
            val dm = resources.displayMetrics
            val minPx = (dm.density * 280).toInt()
            val maxPx = (dm.density * 360).toInt()
            val desired = (dm.widthPixels * 0.88f).toInt().coerceIn(minPx, maxPx)
            attributes = attributes.apply { width = desired }
        }

        lifecycleScope.launch {
            val firstEntry = withContext(Dispatchers.IO) { database.firstEntry }
            val firstDate = if (!firstEntry.isNullOrEmpty()) {
                runCatching { LocalDate.parse(firstEntry) }.getOrNull()
            } else null

            val milestoneDate = java.time.Instant.ofEpochMilli(achievement.timestamp)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()

            if (firstDate != null) {
                val days = java.time.temporal.ChronoUnit.DAYS
                    .between(firstDate, milestoneDate).toInt() + 1

                daysVal.text = resources.getQuantityString(
                        R.plurals.milestone_reached_in_days, days, days
                    )
            } else {
                daysCell.visibility = View.GONE
            }
        }

        dialog.show()
    }

    companion object {
        const val EXTRA_MILESTONE = "extra_milestone"
    }
}

class MilestonesAdapter(
    private val onMilestoneClick: (AchievementsActivity.MilestoneAchievement) -> Unit = {}
) : ListAdapter<AchievementsActivity.MilestoneAchievement,
        MilestonesAdapter.MilestoneViewHolder>(DIFF_CALLBACK) {

    private val dateFormat: DateFormat =
        SimpleDateFormat(AppPreferences.dateFormatString, Locale.getDefault())

    fun updateMilestones(newMilestones: List<AchievementsActivity.MilestoneAchievement>) {
        submitList(newMilestones)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): MilestoneViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_milestone_achievement, parent, false)
        return MilestoneViewHolder(view)
    }

    override fun onBindViewHolder(holder: MilestoneViewHolder, position: Int) {
        holder.bind(
            getItem(position),
            isLast = position == itemCount - 1,
            onClick = onMilestoneClick
        )
    }

    inner class MilestoneViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val badge: TextView   = itemView.findViewById(R.id.achievement_badge)
        private val title: TextView   = itemView.findViewById(R.id.achievement_title)
        private val date: TextView    = itemView.findViewById(R.id.achievement_date)
        private val divider: View     = itemView.findViewById(R.id.milestone_divider)
        private val newBadge: View? = itemView.findViewById(R.id.achievement_new)
        private val newRing: View? = itemView.findViewById(R.id.achievement_new_ring)

        fun bind(
            milestone: AchievementsActivity.MilestoneAchievement,
            isLast: Boolean = false,
            onClick: (AchievementsActivity.MilestoneAchievement) -> Unit = {}
        ) {
            divider.visibility = if (isLast) View.GONE else View.VISIBLE
            val vis = if (milestone.isNew) View.VISIBLE else View.GONE
            newBadge?.visibility = vis
            newRing?.visibility = vis

            badge.text = Util.milestoneBadge(milestone.milestone)
            title.text = formatMilestoneTitle(milestone.milestone)
            date.text  = dateFormat.format(Date(milestone.timestamp))

            itemView.setOnClickListener { onClick(milestone) }
        }

        private fun formatMilestoneTitle(steps: Int): String {
            val distanceKm = steps * AppPreferences.stepLength / 100000f
            val distanceUnit = Util.distanceUnit()
            val distancePart = if (AppPreferences.unitSystem == UnitSystem.METRIC) {
                "%.2f $distanceUnit".format(distanceKm)
            } else {
                "%.2f $distanceUnit".format(distanceKm * 0.621371f)
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

