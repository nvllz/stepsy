package com.nvllz.stepsy.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nvllz.stepsy.R
import com.nvllz.stepsy.ui.components.CountText
import com.nvllz.stepsy.ui.components.HeroBar
import com.nvllz.stepsy.ui.components.LocalToast
import com.nvllz.stepsy.ui.components.Panel
import com.nvllz.stepsy.ui.components.SectionLabel
import com.nvllz.stepsy.ui.components.StatPill
import com.nvllz.stepsy.ui.components.StepsyDialog
import com.nvllz.stepsy.ui.components.ToastKind
import com.nvllz.stepsy.ui.components.pillColor
import com.nvllz.stepsy.ui.theme.Chivo
import com.nvllz.stepsy.ui.theme.StepsyTheme
import com.nvllz.stepsy.util.AchievementsCacheUtil
import com.nvllz.stepsy.util.AppPreferences
import com.nvllz.stepsy.util.Database
import com.nvllz.stepsy.util.NotificationChannels
import com.nvllz.stepsy.util.Util
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

data class MilestoneAchievement(val milestone: Int, val date: String, val isNew: Boolean)

private enum class Status { LOADING, READY, ERROR }

private class RecordStat(
    val label: String,
    val value: Int?,
    val unit: String = "",
    val detail: String? = null,
    val note: String = "",
)

private val milestoneRowInset = 8.dp

private val tileRadius = 18.dp

@Composable
internal fun AchievementsScreen(
    initialMilestone: Int?,
    targetShelf: Boolean = false,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val goal by AppPreferences.dailyGoalTargetFlow().collectAsStateWithLifecycle(AppPreferences.dailyGoalTarget)
    val firstDow = remember { AppPreferences.firstDayOfWeek }

    val app = ctx.applicationContext
    val restored = remember {
        val seen = runCatching { AchievementsCacheUtil.loadSeenMilestones(app) }.getOrNull() ?: emptySet()
        seen to runCatching { AchievementsCacheUtil.load(app) }.getOrNull()
    }
    var results by remember { mutableStateOf(restored.second) }
    var status by remember { mutableStateOf(if (restored.second != null) Status.READY else Status.LOADING) }
    var seenAtOpen by remember { mutableStateOf(restored.first) }
    var pending by rememberSaveable { mutableStateOf(initialMilestone) }
    var openId by rememberSaveable { mutableStateOf<Int?>(null) }
    var jumpToMilestones by rememberSaveable { mutableStateOf(initialMilestone != null || targetShelf) }
    var notify by remember { mutableStateOf(AppPreferences.milestoneNotificationsEnabled) }
    var blocked by remember { mutableStateOf(NotificationChannels.isBlocked(app, NotificationChannels.MILESTONE)) }
    var settled by remember { mutableStateOf(false) }
    val pageScroll = rememberScrollState()
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                blocked = NotificationChannels.isBlocked(app, NotificationChannels.MILESTONE)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        val g = AppPreferences.dailyGoalTarget
        val dow = AppPreferences.firstDayOfWeek
        val seen = seenAtOpen
        val cached = restored.second
        if (cached != null) {
            withContext(Dispatchers.IO) {
                AchievementsCacheUtil.saveSeenMilestones(app, seen + cached.milestones.map { it.milestone })
            }
        }
        try {
            val fresh = withContext(Dispatchers.IO) { computeResults(Database.getInstance(app), g, dow) }
            if (fresh != cached) {
                results = fresh
                status = Status.READY
                withContext(Dispatchers.IO) {
                    AchievementsCacheUtil.saveSeenMilestones(app, seen + fresh.milestones.map { it.milestone })
                    if (fresh.hasData) AchievementsCacheUtil.save(app, fresh) else AchievementsCacheUtil.clear(app)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (cached == null) status = Status.ERROR
        }
        settled = true
    }

    val r = results
    val dateFormat = remember { SimpleDateFormat(AppPreferences.dateFormatString, Locale.getDefault()) }
    val monthFormat = remember { SimpleDateFormat("LLLL yyyy", Locale.getDefault()) }
    fun date(iso: String) = dateFormat.format(Date(Util.dateStringToCalendarMillis(iso)))

    val milestones = remember(r, seenAtOpen) {
        r?.milestones.orEmpty().sortedByDescending { it.milestone }
            .map { MilestoneAchievement(it.milestone, it.date, it.milestone !in seenAtOpen) }
    }
    LaunchedEffect(milestones) {
        pending?.let { t -> if (milestones.any { it.milestone == t }) { openId = t; pending = null } }
    }
    LaunchedEffect(jumpToMilestones) {
        if (!jumpToMilestones) return@LaunchedEffect
        snapshotFlow { pageScroll.maxValue to settled }
            .first { (maxValue, ready) -> ready && maxValue > 0 }
        var previous = pageScroll.maxValue
        while (!pageScroll.isScrollInProgress) {
            withFrameNanos {  }
            val current = pageScroll.maxValue
            if (current > 0 && current == previous) break
            previous = current
        }
        delay(500)
        val target = pageScroll.maxValue
        if (target > pageScroll.value) {
            val duration = ((target - pageScroll.value) / density.density * 0.9f).roundToInt().coerceIn(500, 1400)
            pageScroll.animateScrollTo(target, tween(duration, easing = FastOutSlowInEasing))
        }
        jumpToMilestones = false
    }

    val loading = stringResource(R.string.loading_data).trim()
    val noData = stringResource(R.string.no_data_available).trim()
    val error = stringResource(R.string.error_loading_data).trim()
    val unit = Util.distanceUnit()

    val msg = when {
        r == null -> if (status == Status.ERROR) error else loading
        !r.hasData -> noData
        else -> null
    }
    val labels = listOf(
        stringResource(R.string.best_week),
        stringResource(R.string.most_walked_month),
        stringResource(R.string.streak_record, NumberFormat.getIntegerInstance().format(goal)),
        stringResource(R.string.records_goal_reached),
    )
    fun daysWord(n: Int) = ctx.resources.getQuantityString(R.plurals.days_word_only, n)
    fun stepsWord(n: Int) = ctx.resources.getQuantityString(R.plurals.steps_word, n)
    fun range(a: String, b: String) = ctx.getString(R.string.week_display_range, date(a), date(b))
    val stats: List<RecordStat> = if (r == null || msg != null) {
        emptyList()
    } else {
        val days = r.longestStreak?.days ?: 0
        val week = r.bestWeek.takeIf { r.firstDayOfWeek == firstDow }
        val goalKnown = r.dailyGoal == goal
        val goalPercent = if (r.daysTracked > 0) (r.goalReachedDays * 100f / r.daysTracked).roundToInt() else 0
        buildList {
            add(
                RecordStat(
                    labels[0], week?.steps, stepsWord(week?.steps ?: 0),
                    week?.let { range(it.startDate, it.endDate) },
                    note = if (r.firstDayOfWeek != firstDow) loading else noData,
                ),
            )
            add(
                RecordStat(
                    labels[1], r.bestMonth?.steps, r.bestMonth?.let { stepsWord(it.steps) } ?: "",
                    r.bestMonth?.let {
                        monthFormat.format(Date(Util.dateStringToCalendarMillis("${it.yearMonth}-01")))
                            .replaceFirstChar { c -> c.titlecase(Locale.getDefault()) }
                    },
                    note = noData,
                ),
            )
            if (goal > 0) {
                add(
                    RecordStat(
                        labels[2], days.takeIf { goalKnown }, daysWord(days),
                        r.longestStreak?.let {
                            if (it.days > 1) range(it.startDate, it.endDate) else date(it.endDate)
                        },
                        note = loading,
                    ),
                )
                add(
                    RecordStat(
                        labels[3], r.goalReachedDays.takeIf { goalKnown }, daysWord(r.goalReachedDays),
                        if (!goalKnown) null
                        else stringResource(R.string.records_percent_of_days, goalPercent),
                        note = loading,
                    ),
                )
            }
        }
    }

    Column(Modifier.fillMaxSize().background(StepsyTheme.colors.background).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleButton(onBack) {
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                    Modifier.rotate(180f),
                )
            }
            Text(
                stringResource(R.string.achievements_title),
                fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        Column(Modifier.weight(1f).verticalScroll(pageScroll).padding(horizontal = 16.dp)) {
            val top3 = r?.top3Days.orEmpty()
            if (stats.isEmpty()) {
                Panel {
                    Text(
                        msg ?: noData,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = StepsyTheme.colors.accent, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    )
                }
            } else {
                val res = r!!
                RecordsHero(res.totalSteps, res.avgStepsPerDay, res.firstDate, ::date, unit)
                if (top3.isNotEmpty()) {
                    SectionLabel(stringResource(R.string.top_3_days), topPadding = 16.dp)
                    Panel {
                        top3.forEachIndexed { i, d ->
                            if (i > 0) Divider()
                            TopDayRow(
                                index = i, steps = d.steps,
                                distance = stringResource(R.string.distance_today, Util.stepsToDistance(d.steps), unit),
                                date = date(d.date),
                                fraction = d.steps.toFloat() / top3.first().steps.coerceAtLeast(1).toFloat(),
                            )
                        }
                    }
                }
                SectionLabel(stringResource(R.string.personal_records))
                Panel {
                    stats.forEachIndexed { i, s ->
                        if (i > 0) Divider()
                        RecordRow(s)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(
                    "${stringResource(R.string.milestone_achievements)}: " +
                            stringResource(R.string.milestone_progress, milestones.size, Util.MILESTONES.size),
                    Modifier.weight(1f),
                )
                BellButton(notify, blocked, { new ->
                    notify = new
                    AppPreferences.milestoneNotificationsEnabled = new
                    AppPreferences.lastNotifiedMilestone =
                        Util.MILESTONES.lastOrNull { it < AppPreferences.baseTotalSteps } ?: 0L
                }, Modifier.padding(top = 14.dp))
            }
            Panel {
                Column(Modifier.padding(vertical = 12.dp)) {
                    r?.let { res ->
                        val next = Util.MILESTONES.firstOrNull { it > res.totalSteps }
                        if (next != null) {
                            val prev = Util.MILESTONES.lastOrNull { it <= res.totalSteps } ?: 0L
                            NextMilestone(res.totalSteps, next, prev, Util.formatShortSteps(next, ctx))
                            if (milestones.isNotEmpty()) {
                                Spacer(Modifier.height(14.dp)); Divider(); Spacer(Modifier.height(14.dp))
                            }
                        }
                    }
                    if (milestones.isEmpty()) {
                        if (r != null) {
                            Text(
                                stringResource(R.string.no_milestones_reached),
                                fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                color = StepsyTheme.colors.accent, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                            )
                        }
                    } else {
                        MilestoneShelf(milestones, { Util.formatShortSteps(it, ctx) }) { openId = it }
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }

    milestones.firstOrNull { it.milestone == openId }?.let { m ->
        MilestoneDialog(m, r?.firstDate, ::date) { openId = null }
    }
}

@Composable
private fun CircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.size(40.dp).clip(CircleShape).background(StepsyTheme.colors.accentOpaque).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun Caption(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    Text(
        text.uppercase(), fontSize = 12.sp, lineHeight = 14.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold,
        color = StepsyTheme.colors.accent, modifier = modifier, textAlign = textAlign,
    )
}

@Composable
private fun Divider(startInset: Dp = milestoneRowInset) {
    Box(
        Modifier.fillMaxWidth().padding(start = startInset, end = milestoneRowInset).height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    )
}

@Composable
private fun TopDayRow(index: Int, steps: Int, distance: String, date: String, fraction: Float) {
    val c = StepsyTheme.colors
    val cs = MaterialTheme.colorScheme
    val first = index == 0
    val rank = remember(index) { NumberFormat.getIntegerInstance().format(index + 1) }
    val rankBackground = when (index) {
        0 -> cs.onSurface
        1 -> cs.onSurface.copy(alpha = 0.13f)
        else -> cs.onSurface.copy(alpha = 0.08f)
    }
    val rankColor = if (first) c.background else c.accent
    val track = cs.onSurface.copy(alpha = 0.08f)
    val grow = remember { Animatable(0f) }
    LaunchedEffect(fraction) {
        grow.animateTo(fraction, tween(700, delayMillis = index * 120, easing = EaseOutCubic))
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(rankBackground),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    rank,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = Chivo,
                    color = rankColor,
                )
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(date, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(distance, fontSize = 13.sp, lineHeight = 18.sp, color = c.accent)
            }
            CountText(steps, Util::formatSteps, TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = Chivo))
        }
        Box(
            Modifier.padding(start = 40.dp, top = 10.dp).fillMaxWidth().height(4.dp).drawBehind {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(track, cornerRadius = r)
                drawRoundRect(
                    if (first) c.goal else c.goal.copy(alpha = 0.55f),
                    size = Size(size.width * grow.value, size.height), cornerRadius = r,
                )
            },
        )
    }
}

@Composable
private fun RecordsHero(total: Int, avg: Int, firstDate: String?, date: (String) -> String, unit: String) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Caption(
            buildString {
                append(stringResource(R.string.label_all_time))
                firstDate?.let { append(" · ").append(stringResource(R.string.since_date, date(it))) }
            },
            Modifier.padding(bottom = 10.dp),
            textAlign = TextAlign.Center,
        )
        CountText(
            total, Util::formatSteps,
            TextStyle(
                fontSize = 48.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold, fontFamily = Chivo,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            duration = 900,
        )
        Text(
            ctx.resources.getQuantityString(R.plurals.steps_word, total, total).uppercase(),
            fontSize = 13.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold,
            color = StepsyTheme.colors.accent,
            modifier = Modifier.offset(y = (-2).dp),
        )
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val steel = colorResource(R.color.colorDistance)
            val sage = colorResource(R.color.colorStepsDay)
            StatPill(
                R.drawable.ic_distance, total,
                { st -> ctx.getString(R.string.distance_today, Util.stepsToDistance(st), unit) },
                pillColor(steel, 1f),
            )
            StatPill(
                R.drawable.ic_steps, avg,
                { st -> ctx.getString(R.string.avg_per_day_pill, Util.formatSteps(st)) },
                pillColor(sage, 1f),
            )
        }
    }
}

@Composable
private fun RecordRow(stat: RecordStat) {
    val c = StepsyTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stat.label, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = LocalTextStyle.current.copy(
                    lineBreak = LineBreak(
                        strictness = LineBreak.Strictness.Default,
                        wordBreak = LineBreak.WordBreak.Default,
                        strategy = LineBreak.Strategy.Balanced,
                    ),
                ),
            )
            stat.detail?.let {
                Text(
                    it, fontSize = 12.sp, lineHeight = 16.sp, color = c.accent, maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = LocalTextStyle.current.copy(
                        lineBreak = LineBreak(
                            strictness = LineBreak.Strictness.Default,
                            wordBreak = LineBreak.WordBreak.Default,
                            strategy = LineBreak.Strategy.Balanced,
                        ),
                    ),
                )
            }
        }
        Column(
            Modifier.padding(start = 12.dp).widthIn(min = 92.dp),
            horizontalAlignment = Alignment.End,
        ) {
            val v = stat.value
            if (v != null) {
                CountText(
                    v, Util::formatSteps,
                    TextStyle(fontSize = 20.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = Chivo),
                )
                Text(stat.unit, fontSize = 12.sp, lineHeight = 16.sp, color = c.accent)
            } else {
                Text(stat.note, fontSize = 14.sp, color = c.accent)
            }
        }
    }
}

@Composable
private fun rememberNewPulse(): State<Float> =
    rememberInfiniteTransition(label = "newGlow").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "newGlow",
    )

private fun Modifier.newHalo(color: Color, pulse: State<Float>, radius: Dp): Modifier = drawBehind {
    val breath = 0.5f - 0.5f * kotlin.math.cos(pulse.value * 2f * kotlin.math.PI.toFloat())
    val g = 1.5.dp.toPx() + 3.dp.toPx() * breath
    drawRoundRect(
        color.copy(alpha = 0.03f + 0.13f * breath),
        Offset(-g, -g),
        Size(size.width + 2 * g, size.height + 2 * g), CornerRadius(radius.toPx() + g),
    )
}

@Composable
private fun MilestoneTile(
    m: MilestoneAchievement, label: String, index: Int, reveal: () -> Float,
    modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    val c = StepsyTheme.colors
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(tileRadius)
    Box(
        modifier.height(80.dp).graphicsLayer {
            val t = ((reveal() - minOf(index, 7) * 0.07f) / 0.51f).coerceIn(0f, 1f)
            val e = EaseOutCubic.transform(t)
            alpha = e
            scaleX = 0.88f + 0.12f * e
            scaleY = scaleX
        },
    ) {
        Column(
            Modifier.fillMaxSize()
                .clip(shape)
                .background(cs.onSurface.copy(alpha = 0.06f))
                .then(if (m.isNew) Modifier.border(2.dp, cs.primary, shape) else Modifier)
                .clickable(onClick = onClick)
                .padding(top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(Util.milestoneBadge(m.milestone), fontSize = 28.sp)
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
        }
        if (m.isNew) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp)
                    .size(13.5.dp).clip(CircleShape).background(c.special)
                    .border(1.5.dp, cs.onSurface.copy(alpha = 0.06f), CircleShape),
            )
        }
    }
}

@Composable
private fun MilestoneShelf(items: List<MilestoneAchievement>, label: (Long) -> String, onOpen: (Int) -> Unit) {
    val rows = remember(items) { items.chunked(4) }
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(700, easing = LinearEasing)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEachIndexed { r, cells ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cells.forEachIndexed { col, m ->
                    MilestoneTile(m, label(m.milestone.toLong()), r * 4 + col, { reveal.value }, Modifier.weight(1f)) {
                        onOpen(m.milestone)
                    }
                }
                repeat(4 - cells.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun NextMilestone(total: Int, next: Long, prev: Long, targetLabel: String) {
    val c = StepsyTheme.colors
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { armed = true }
    val left = (next - total).toInt()
    val progress = ((total - prev).toFloat() / (next - prev)).coerceIn(0f, 1f)
    val dash = cs.onSurface.copy(alpha = 0.3f)

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Caption(stringResource(R.string.milestone_next))
            Text(
                ctx.getString(
                    R.string.milestone_to_go,
                    ctx.resources.getQuantityString(R.plurals.steps_formatted, left, Util.formatSteps(left)),
                ),
                fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp),
            )
            HeroBar(if (armed) progress else 0f, Modifier.padding(top = 12.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(
            Modifier.size(width = 62.dp, height = 72.dp)
                .drawWithCache {
                    val rr = tileRadius.toPx()
                    val perimeter = 2f * (size.width + size.height) - 8f * rr + 2f * kotlin.math.PI.toFloat() * rr
                    val seg = 6.dp.toPx()
                    val count = kotlin.math.round(perimeter / (seg + 5.dp.toPx())).coerceAtLeast(2f)
                    val gap = (perimeter / count) - seg
                    val stroke = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(seg, gap)))
                    val r = CornerRadius(rr)
                    onDrawBehind { drawRoundRect(dash, cornerRadius = r, style = stroke) }
                }
                .padding(top = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_award_star), null,
                Modifier.size(26.dp), tint = cs.onSurface.copy(alpha = 0.4f),
            )
            Text(
                targetLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.accent,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun DialogBadge(m: MilestoneAchievement, modifier: Modifier = Modifier) {
    val c = StepsyTheme.colors
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    val pulse = if (m.isNew) rememberNewPulse() else null
    Box(modifier.size(116.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(88.dp)
                .then(if (pulse != null) Modifier.newHalo(c.goal, pulse, 28.dp) else Modifier)
                .clip(shape).background(cs.onSurface.copy(alpha = 0.06f))
                .then(if (m.isNew) Modifier.border(2.dp, cs.primary, shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) { Text(Util.milestoneBadge(m.milestone), fontSize = 48.sp) }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun BellButton(enabled: Boolean, blocked: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val tip = rememberTooltipState()
    val swing = remember { Animatable(0f) }
    val active = enabled && !blocked
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(stringResource(R.string.milestone_notifications_tooltip), fontSize = 14.sp) } },
        state = tip,
        modifier = modifier,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).combinedClickable(
                role = Role.Button,
                onClick = {
                    if (blocked) {
                        NotificationChannels.open(ctx, NotificationChannels.MILESTONE)
                    } else {
                        val next = !enabled
                        onToggle(next)
                        if (next) scope.launch {
                            swing.animateTo(18f, tween(90))
                            swing.animateTo(-12f, tween(100))
                            swing.animateTo(6f, tween(90))
                            swing.animateTo(0f, tween(120))
                        }
                        toast.show(
                            ctx.getString(
                                if (next) R.string.milestone_notifications_enabled else R.string.milestone_notifications_disabled,
                            ),
                            if (next) ToastKind.SUCCESS else ToastKind.INFO,
                        )
                    }
                },
                onLongClick = { scope.launch { tip.show() } },
            ),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(
                targetState = active,
                animationSpec = tween(durationMillis = 250),
                label = "bellState",
            ) { state ->
                val scale by animateFloatAsState(
                    targetValue = if (state) 1f else 0.9f,
                    animationSpec = tween(durationMillis = 250),
                    label = "bellScale",
                )
                Icon(
                    painterResource(if (state) R.drawable.ic_notifications_active else R.drawable.ic_notifications_off),
                    if (state) stringResource(R.string.milestone_notifications_toggle) else null,
                    Modifier
                        .size(26.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            rotationZ = if (state) swing.value else 0f
                        }
                        .alpha(if (state) 1f else 0.35f),
                )
            }
        }
    }
}

@Composable
private fun MilestoneDialog(m: MilestoneAchievement, firstDate: String?, date: (String) -> String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val c = StepsyTheme.colors
    val days = remember(m, firstDate) {
        firstDate?.let {
            runCatching { ChronoUnit.DAYS.between(LocalDate.parse(it), LocalDate.parse(m.date)).toInt() + 1 }.getOrNull()
        }
    }
    var armed by remember { mutableStateOf(!m.isNew) }
    LaunchedEffect(Unit) { if (m.isNew) armed = true }
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }
    StepsyDialog(
        onDismiss = onDismiss,
        confirmText = stringResource(android.R.string.ok),
        onConfirm = onDismiss,
        dismissText = null,
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            DialogBadge(
                m,
                modifier = Modifier.graphicsLayer {
                    val s = 0.6f + 0.4f * pop.value
                    scaleX = s
                    scaleY = s
                    alpha = (0.4f + 0.6f * pop.value).coerceAtMost(1f)
                },
            )
            CountText(
                if (armed) m.milestone else 0, Util::formatSteps,
                TextStyle(
                    fontSize = 38.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, fontFamily = Chivo,
                    fontFeatureSettings = "tnum",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                duration = 650, easing = EaseOutCubic,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            Text(
                ctx.resources.getQuantityString(R.plurals.steps_word, m.milestone, m.milestone).uppercase(),
                fontSize = 14.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold,
                color = c.accent, modifier = Modifier.offset(y = (-2).dp),
            )
            Row(
                Modifier.padding(top = 16.dp).fillMaxWidth()
                    .layout { measurable, constraints ->
                        val extra = 48.dp.roundToPx()
                        val placeable = measurable.measure(
                            constraints.copy(minWidth = 0, maxWidth = constraints.maxWidth + extra),
                        )
                        layout(constraints.maxWidth, placeable.height) {
                            placeable.placeRelative(x = -extra / 2, y = 0)
                        }
                    }
                    .padding(vertical = 14.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Stat(
                    stringResource(R.string.milestone_label_distance),
                    "%.0f %s".format(Util.stepsToDistance(m.milestone), Util.distanceUnit()),
                    Modifier.weight(1f),
                )
                StatSeparator()
                Stat(stringResource(R.string.milestone_label_date), date(m.date), Modifier.weight(1.2f))
                days?.let {
                    StatSeparator()
                    Stat(
                        stringResource(R.string.milestone_label_took),
                        ctx.resources.getQuantityString(R.plurals.milestone_reached_in_days, it, it),
                        Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatSeparator() {
    Box(
        Modifier.width(1.dp).height(34.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    )
}

@Composable
private fun Stat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            fontSize = 14.5.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = Chivo,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 12.5.sp,
                maxFontSize = 15.5.sp,
                stepSize = 0.01.sp,
            ),
        )

        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            lineHeight = 12.sp,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = StepsyTheme.colors.accent,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = true,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 8.sp,
                maxFontSize = 10.sp,
                stepSize = 0.01.sp,
            ),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
