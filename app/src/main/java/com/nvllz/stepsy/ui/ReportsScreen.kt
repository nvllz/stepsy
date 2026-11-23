package com.nvllz.stepsy.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nvllz.stepsy.R
import com.nvllz.stepsy.ui.components.CountText
import com.nvllz.stepsy.ui.components.DayPartsPanel
import com.nvllz.stepsy.ui.components.Eyebrow
import com.nvllz.stepsy.ui.components.MonthCalendar
import com.nvllz.stepsy.ui.components.Panel
import com.nvllz.stepsy.ui.components.PillSelector
import com.nvllz.stepsy.ui.components.ScreenHeader
import com.nvllz.stepsy.ui.components.SectionLabel
import com.nvllz.stepsy.ui.components.StatPill
import com.nvllz.stepsy.ui.components.StepsBarChart
import com.nvllz.stepsy.ui.components.StepsyDialog
import com.nvllz.stepsy.ui.components.pillColor
import com.nvllz.stepsy.ui.theme.Chivo
import com.nvllz.stepsy.ui.theme.StepsyTheme
import com.nvllz.stepsy.util.AppPreferences
import com.nvllz.stepsy.util.DayPartStats
import com.nvllz.stepsy.util.Insight
import com.nvllz.stepsy.util.MilestoneRecord
import com.nvllz.stepsy.util.PeriodReport
import com.nvllz.stepsy.util.ReportBucket
import com.nvllz.stepsy.util.ReportBounds
import com.nvllz.stepsy.util.ReportData
import com.nvllz.stepsy.util.ReportKind
import com.nvllz.stepsy.util.ReportPeriod
import com.nvllz.stepsy.util.RecordScope
import com.nvllz.stepsy.util.Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun ReportsScreen(
    onBack: () -> Unit,
    boot: ReportBoot,
    bootKind: ReportKind? = null,
    bootStartIso: String? = null,
    bootEndIso: String? = null,
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var firstDow by remember { mutableStateOf(boot.firstDow) }
    var goal by remember { mutableIntStateOf(boot.goal) }
    var dateFmt by remember { mutableStateOf(boot.dateFmt) }
    var hourlyOn by remember { mutableStateOf(boot.hourlyOn) }
    var bounds by remember { mutableStateOf<ReportBounds?>(boot.bounds) }
    var tick by remember { mutableIntStateOf(0) }
    var resumedSincePause by remember { mutableStateOf(false) }

    suspend fun refreshPrefs() = withContext(Dispatchers.IO) {
        firstDow = calendarDayToDayOfWeek(AppPreferences.firstDayOfWeek)
        goal = AppPreferences.dailyGoalTarget
        dateFmt = AppPreferences.dateFormatString
        hourlyOn = AppPreferences.hourlyBreakdown
        bounds = ReportData.bounds(app)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> resumedSincePause = true
                Lifecycle.Event.ON_RESUME -> if (resumedSincePause) {
                    resumedSincePause = false
                    tick++
                    scope.launch { refreshPrefs() }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val seedFromBootRange = bootKind == null || bootKind == ReportKind.CUSTOM
    var kind by rememberSaveable { mutableStateOf(bootKind ?: boot.kind) }
    var startIso by rememberSaveable {
        mutableStateOf(bootStartIso ?: if (seedFromBootRange) boot.startIso else boot.todayIso)
    }
    var endIso by rememberSaveable {
        mutableStateOf(bootEndIso ?: if (seedFromBootRange) boot.endIso else boot.todayIso)
    }
    var openPicker by remember { mutableStateOf(false) }
    var partsHelpOpen by remember { mutableStateOf(false) }
    var animateSections by remember { mutableStateOf(true) }

    LaunchedEffect(bounds, firstDow) {
        val b = bounds ?: return@LaunchedEffect
        if (startIso.isEmpty()) {
            val p = ReportPeriod.containing(ReportKind.CUSTOM, b.today, firstDow)
            kind = ReportKind.CUSTOM
            startIso = p.start.toString()
            endIso = p.end.toString()
        }
    }

    val period = remember(kind, startIso, endIso, firstDow) {
        if (startIso.isEmpty()) null
        else if (kind == ReportKind.CUSTOM) ReportPeriod(ReportKind.CUSTOM, LocalDate.parse(startIso), LocalDate.parse(endIso))
        else ReportPeriod.containing(kind, LocalDate.parse(startIso), firstDow)
    }

    val bootMatches = kind == boot.kind && startIso == boot.startIso && endIso == boot.endIso
    val skipFirstRun = remember { mutableStateOf(bootMatches) }
    val content by produceState(
        if (bootMatches) boot.content else ReportContent(null, emptyList(), null),
        listOf(period, goal, firstDow, tick),
    ) {
        if (skipFirstRun.value) {
            skipFirstRun.value = false
            return@produceState
        }
        val p = period ?: return@produceState
        value = withContext(Dispatchers.IO) {
            val today = Util.todayDateString()
            val live = if (AppPreferences.date == today) AppPreferences.steps else null
            val r = ReportData.load(app, p, goal, firstDow, live)
            ReportContent(r, ReportData.loadMilestones(app, p, live), ReportData.loadDayParts(app, p))
        }
    }
    val report = content.report
    val currentParts = content.parts
    val inProgress by rememberUpdatedState(report?.inProgress == true)

    LaunchedEffect(Unit) {
        AppPreferences.stepsFlow().drop(1).collectLatest { s ->
            if (s > 0 && inProgress) {
                delay(5_000)
                tick++
            }
        }
    }

    val fmt = remember(dateFmt) { SimpleDateFormat(dateFmt, Locale.getDefault()) }
    val percentFmt = remember { NumberFormat.getPercentInstance() }

    fun switchKind(k: ReportKind) {
        val b = bounds ?: return
        val p = ReportPeriod.containing(k, b.today, firstDow)
        animateSections = false
        if (k == ReportKind.MONTH) {
            scope.launch {
                withContext(Dispatchers.IO) { ReportData.loadHeat(app, p, goal) }
                kind = k
                startIso = p.start.toString()
                endIso = p.end.toString()
            }
        } else {
            kind = k
            startIso = p.start.toString()
            endIso = p.end.toString()
        }
    }

    fun shiftPeriod(dir: Int) {
        val p = period ?: return
        val np = if (dir < 0) p.previous() else p.next()
        animateSections = true
        if (np.kind == ReportKind.MONTH) {
            scope.launch {
                withContext(Dispatchers.IO) { ReportData.loadHeat(app, np, goal) }
                startIso = np.start.toString()
                endIso = np.end.toString()
            }
        } else {
            startIso = np.start.toString()
            endIso = np.end.toString()
        }
    }

    Box(Modifier.fillMaxSize().background(StepsyTheme.colors.background).statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(stringResource(R.string.reports_title), onBack)

            val b = bounds
            when {
                b == null -> Column(Modifier.padding(horizontal = 16.dp)) { SkeletonLoading() }
                b.firstDate == null -> Column(Modifier.padding(horizontal = 16.dp)) {
                    Panel(Modifier.padding(top = 24.dp)) {
                        MutedMessage(stringResource(R.string.reports_empty_all))
                    }
                }
                else -> {
                    val p = period
                    if (p == null || report == null) {
                        val skeletonScroll = rememberScrollState()
                        Column(
                            Modifier.weight(1f).verticalScroll(skeletonScroll).padding(horizontal = 16.dp),
                        ) {
                            SkeletonLoading()
                        }
                    } else {
                        AnimatedContent(
                            targetState = content,
                            contentKey = { it.report?.period?.kind },
                            modifier = Modifier.weight(1f).clipToBounds(),
                            contentAlignment = Alignment.TopCenter,
                            transitionSpec = {
                                val dir =
                                    if (filterOrder(targetState.report?.period?.kind) >= filterOrder(initialState.report?.period?.kind)) 1 else -1
                                (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { dir * it } + fadeIn(tween(200)))
                                    .togetherWith(
                                        slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -dir * it } + fadeOut(tween(180)),
                                    )
                                    .using(SizeTransform(clip = false) { _, _ -> snap() })
                            },
                            label = "reportsPage",
                        ) { c ->
                            val pageScroll = rememberScrollState()
                            Column(
                                Modifier.fillMaxSize()
                                    .verticalScroll(pageScroll)
                                    .padding(horizontal = 16.dp),
                            ) {
                                val r = c.report
                                if (r != null && r.isEmpty) {
                                    Panel(Modifier.padding(top = 24.dp)) {
                                        MutedMessage(stringResource(R.string.reports_empty_period))
                                    }
                                }
                                if (r != null && !r.isEmpty) {
                                    val singleDay = r.period.kind == ReportKind.CUSTOM && r.period.days == 1
                                    val insights = if (singleDay) r.insights.filterNot { it is Insight.GoalDays } else r.insights
                                    HeroSection(r, singleDay, animateSections)
                                    AnimatedSection(visible = insights.isNotEmpty()) {
                                        Panel(
                                            Modifier.padding(top = 16.dp)
                                                .then(if (animateSections) Modifier.animateContentSize() else Modifier),
                                        ) {
                                            insights.forEach { ins ->
                                                Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.Top) {
                                                    Text(
                                                        ">",
                                                        fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold,
                                                        color = StepsyTheme.colors.accent,
                                                    )
                                                    Spacer(Modifier.width(10.dp))
                                                    Text(
                                                        insightText(ctx, ins, fmt, percentFmt),
                                                        fontSize = 16.sp, lineHeight = 21.sp,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    AnimatedSection(visible = !singleDay) {
                                        ChartSection(r, goal, firstDow, b.today, fmt, tick, animateSections)
                                    }

                                    val partsHidden = !hourlyOn && c.parts?.included == 0
                                    AnimatedSection(visible = !partsHidden && !singleDay) {
                                        SectionLabel(stringResource(R.string.reports_time_of_day))
                                        val enough = c.parts != null &&
                                            c.parts.included >= 3 && c.parts.included * 2 >= c.parts.rowsInRange
                                        DayPartsPanel(
                                            stats = c.parts,
                                            labels = listOf(
                                                stringResource(R.string.reports_part_night),
                                                stringResource(R.string.reports_part_morning),
                                                stringResource(R.string.reports_part_afternoon),
                                                stringResource(R.string.reports_part_evening),
                                            ),
                                            icons = listOf(
                                                R.drawable.ic_night,
                                                R.drawable.ic_morning,
                                                R.drawable.ic_afternoon,
                                                R.drawable.ic_evening,
                                            ),
                                            headline = if (enough && c.parts != null) rhythmHeadline(ctx, c.parts) else null,
                                            detail = if (enough && c.parts != null) rhythmCoverage(ctx, c.parts) else null,
                                            onHelp = { partsHelpOpen = true },
                                        )
                                    }
                                    AnimatedVisibility(
                                        visible = c.milestones.isNotEmpty(),
                                        enter = if (animateSections) fadeIn(tween(200)) + expandVertically(tween(220)) else fadeIn(snap()) + expandVertically(snap()),
                                        exit = if (animateSections) fadeOut(tween(120)) + shrinkVertically(tween(180)) else fadeOut(snap()) + shrinkVertically(snap()),
                                    ) {
                                        Column {
                                            SectionLabel(stringResource(R.string.reports_milestones))
                                            MilestoneCards(c.milestones)
                                        }
                                    }
                                }
                                Spacer(Modifier.navigationBarsPadding().height(144.dp))
                            }
                        }
                    }
                }
            }
        }

        bounds?.takeIf { it.firstDate != null }?.let { b ->
            FloatingControls(
                period = report?.period ?: period,
                kind = report?.period?.kind ?: kind,
                firstDate = b.firstDate ?: b.today,
                today = b.today,
                fmt = fmt,
                onSelect = { k -> if (k == ReportKind.CUSTOM) openPicker = true else switchKind(k) },
                onPickCustom = { openPicker = true },
                onShift = ::shiftPeriod,
            )
        }
    }

    bounds?.takeIf { it.firstDate != null }?.let { b ->
        if (openPicker) {
            CustomRangeDialog(
                minDate = b.firstDate ?: b.today,
                today = b.today,
                firstDow = firstDow,
                onDismiss = { openPicker = false },
                onConfirm = { s, e ->
                    kind = ReportKind.CUSTOM
                    startIso = s.toString()
                    endIso = e.toString()
                    openPicker = false
                    animateSections = false
                },
            )
        }
    }

    if (partsHelpOpen) {
        StepsyDialog(
            onDismiss = { partsHelpOpen = false },
            confirmText = stringResource(android.R.string.ok),
            onConfirm = { partsHelpOpen = false },
            dismissText = null,
        ) {
            val included = currentParts?.included ?: 0
            val rowsInRange = currentParts?.rowsInRange ?: 0
            if (included == 0) {
                Text(
                    stringResource(
                        R.string.hourly_average_empty_message,
                        ctx.resources.getQuantityString(R.plurals.days_with_count, rowsInRange, rowsInRange),
                    ),
                    fontSize = 14.sp, lineHeight = 18.sp,
                )
            } else {
                Text(
                    stringResource(R.string.hourly_average_message, included, rowsInRange),
                    fontSize = 14.sp, lineHeight = 18.sp,
                )
            }
        }
    }
}

internal data class ReportContent(
    val report: PeriodReport?,
    val milestones: List<MilestoneRecord>,
    val parts: DayPartStats?,
)

internal class ReportBoot private constructor(
    val firstDow: DayOfWeek,
    val goal: Int,
    val dateFmt: String,
    val hourlyOn: Boolean,
    val bounds: ReportBounds,
    val kind: ReportKind,
    val todayIso: String,
    val startIso: String,
    val endIso: String,
    val content: ReportContent,
) {
    companion object {
        suspend fun load(ctx: Context): ReportBoot = withContext(Dispatchers.IO) {
            val firstDow = calendarDayToDayOfWeek(AppPreferences.firstDayOfWeek)
            val goal = AppPreferences.dailyGoalTarget
            val dateFmt = AppPreferences.dateFormatString
            val hourlyOn = AppPreferences.hourlyBreakdown
            val bounds = ReportData.bounds(ctx)
            val today = bounds.today
            val p = ReportPeriod.containing(ReportKind.CUSTOM, today, firstDow)
            val live = if (AppPreferences.date == bounds.today.toString()) AppPreferences.steps else null
            ReportBoot(
                firstDow, goal, dateFmt, hourlyOn, bounds,
                ReportKind.CUSTOM, today.toString(),
                p.start.toString(), p.end.toString(),
                ReportContent(
                    ReportData.load(ctx, p, goal, firstDow, live),
                    ReportData.loadMilestones(ctx, p, live),
                    ReportData.loadDayParts(ctx, p),
                ),
            )
        }
    }
}

private const val MAX_CUSTOM_RANGE_DAYS = 366

private fun filterOrder(k: ReportKind?): Int = when (k) {
    ReportKind.WEEK -> 0
    ReportKind.MONTH -> 1
    ReportKind.YEAR -> 2
    ReportKind.CUSTOM -> 3
    null -> 0
}

@Composable
private fun AnimatedSection(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    if (visible) Column { content() }
}

@Composable
private fun BoxScope.FloatingControls(
    period: ReportPeriod?,
    kind: ReportKind,
    firstDate: LocalDate,
    today: LocalDate,
    fmt: SimpleDateFormat,
    onSelect: (ReportKind) -> Unit,
    onPickCustom: () -> Unit,
    onShift: (Int) -> Unit,
) {
    val ctx = LocalContext.current
    val customInteraction = remember { MutableInteractionSource() }
    Column(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .shadow(6.dp, CircleShape)
                .shadow(20.dp, CircleShape)
                .background(StepsyTheme.colors.dialogSurface, CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f), CircleShape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavChevron(
                back = true, enabled = period == null || period.start > firstDate,
                description = stringResource(R.string.reports_previous),
            ) { onShift(-1) }
            Box(
                Modifier.weight(1f).pointerInput(period, today, firstDate) {
                    var totalX = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalX = 0f },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            totalX += amount
                        },
                        onDragEnd = {
                            val p = period
                            when {
                                totalX <= -56.dp.toPx() && p != null && !p.contains(today) -> onShift(1)
                                totalX >= 56.dp.toPx() && p != null && p.start > firstDate -> onShift(-1)
                            }
                        },
                    )
                },
                contentAlignment = Alignment.Center,
            ) {
                val navLabel = period?.let { p ->
                    when (p.kind) {
                        ReportKind.MONTH -> monthTitle(p.start.year, p.start.monthValue)
                        ReportKind.YEAR -> ctx.getString(R.string.header_year, p.start.year)
                        else -> ctx.getString(
                            R.string.week_display_range,
                            dateLabel(ctx, fmt, p.start), dateLabel(ctx, fmt, p.end),
                        )
                    }
                } ?: ""
                Eyebrow(
                    navLabel,
                    (if (period?.kind == ReportKind.CUSTOM) {
                        Modifier.clickable(
                            interactionSource = customInteraction,
                            indication = null,
                            onClick = onPickCustom,
                        )
                    } else Modifier).padding(vertical = 10.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                )
            }
            NavChevron(
                back = false, enabled = period != null && !period.contains(today),
                description = stringResource(R.string.reports_next),
            ) { onShift(1) }
        }
        Spacer(Modifier.height(4.dp))
        PillSelector(
            options = listOf(
                ReportKind.WEEK to stringResource(R.string.label_week),
                ReportKind.MONTH to stringResource(R.string.label_month),
                ReportKind.YEAR to stringResource(R.string.reports_year),
                ReportKind.CUSTOM to stringResource(R.string.goal_preset_custom),
            ),
            selected = kind,
            onSelect = onSelect,
            containerColor = StepsyTheme.colors.dialogSurface,
            elevated = true,
        )
    }
}

@Composable
private fun NavChevron(back: Boolean, enabled: Boolean, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_chevron_right), description,
            Modifier.size(18.dp).rotate(if (back) 180f else 0f)
                .graphicsLayer(alpha = if (enabled) 1f else 0.3f),
        )
    }
}

@Composable
private fun HeroSection(report: PeriodReport, singleDay: Boolean, animate: Boolean) {
    val ctx = LocalContext.current
    val enterLine = if (animate) fadeIn(tween(200)) + expandVertically(tween(220)) else fadeIn(snap()) + expandVertically(snap())
    val exitLine = if (animate) fadeOut(tween(120)) + shrinkVertically(tween(180)) else fadeOut(snap()) + shrinkVertically(snap())
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (report.inProgress) stringResource(R.string.reports_so_far).uppercase() else "",
            fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold,
            color = StepsyTheme.colors.accent,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        CountText(
            report.totalSteps.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            Util::formatSteps,
            TextStyle(
                fontSize = 48.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold,
                fontFamily = Chivo, fontFeatureSettings = "tnum",
                color = MaterialTheme.colorScheme.onSurface,
            ),
        )
        Text(
            ctx.resources.getQuantityString(
                R.plurals.steps_word,
                report.totalSteps.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            ).uppercase(),
            fontSize = 12.sp, letterSpacing = 2.sp,
            fontWeight = FontWeight.SemiBold, color = StepsyTheme.colors.accent,
            modifier = Modifier.offset(y = (-2).dp),
        )
        AnimatedVisibility(
            visible = !singleDay && report.loggedDays < report.expectedDays,
            enter = enterLine, exit = exitLine,
        ) {
            Text(
                ctx.getString(R.string.reports_days_logged, report.loggedDays, report.expectedDays),
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatPill(
                R.drawable.ic_distance, report.totalSteps.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                { st -> ctx.getString(R.string.distance_today, Util.stepsToDistance(st), Util.distanceUnit()) },
                pillColor(colorResource(R.color.colorDistance), 1f),
            )
            StatPill(
                R.drawable.ic_calories, report.totalSteps.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                { st -> ctx.getString(R.string.calories, Util.formatKcal(Util.stepsToCalories(st), ctx)) },
                pillColor(colorResource(R.color.colorFlame), 1f),
            )
        }
    }
}

@Composable
private fun MilestoneCards(items: List<MilestoneRecord>) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(4).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { m ->
                    Column(
                        Modifier.weight(1f).height(80.dp)
                            .clip(shape)
                            .background(cs.onSurface.copy(alpha = 0.06f))
                            .padding(top = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(Util.milestoneBadge(m.milestone), fontSize = 28.sp)
                        Text(
                            Util.formatShortSteps(m.milestone.toLong(), ctx),
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ChartSection(
    report: PeriodReport,
    goal: Int,
    firstDow: DayOfWeek,
    today: LocalDate,
    fmt: SimpleDateFormat,
    tick: Int,
    animate: Boolean,
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext
    when (report.period.kind) {
        ReportKind.WEEK -> {
            var selected by remember(report.period) { mutableIntStateOf(-1) }
            Panel(Modifier.padding(top = 16.dp)) {
                StatsBlock(report, fmt, animate)
            }
            SectionLabel(stringResource(R.string.reports_daily_steps))
            Panel {
                StepsBarChart(
                    values = report.bars.map { it.avgPerDay },
                    labels = weekdayShortLabels(firstDow),
                    goal = goal,
                    selectedIndex = selected.takeIf { it >= 0 },
                    boldIndex = report.barIndexOfToday,
                    onBarClick = { i -> selected = if (selected == i) -1 else i },
                    revealInstant = true,
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                )
            }
            AnimatedContent(
                targetState = selected.takeIf { it >= 0 }?.let { report.bars.getOrNull(it) },
                contentKey = { it?.start },
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier.clipToBounds(),
                transitionSpec = {
                    if (!animate) {
                        (slideInVertically(snap()) { -it } + fadeIn(snap()))
                            .togetherWith(
                                slideOutVertically(snap()) { -it } + fadeOut(snap()),
                            )
                            .using(SizeTransform(clip = false) { _, _ -> snap() })
                    } else if (targetState != null) {
                        (slideInVertically(tween(220, easing = FastOutSlowInEasing)) { -it } + fadeIn(tween(200)))
                            .togetherWith(
                                slideOutVertically(tween(180, easing = FastOutSlowInEasing)) { -it } + fadeOut(tween(120)),
                            )
                            .using(SizeTransform(clip = false) { _, _ -> snap() })
                    } else {
                        (slideInVertically(snap()) { -it } + fadeIn(snap()))
                            .togetherWith(
                                slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { -it } + fadeOut(tween(160)),
                            )
                            .using(SizeTransform(clip = false) { _, _ -> tween(200, easing = FastOutSlowInEasing) })
                    }
                },
                label = "weekBarCaption",
            ) { bar ->
                if (bar == null) {
                    Spacer(Modifier.fillMaxWidth())
                } else {
                    Text(
                        dateLabel(ctx, fmt, bar.start) + " · " + ctx.resources.getQuantityString(
                            R.plurals.steps_formatted, bar.steps.toInt(), Util.formatSteps(bar.steps.toInt()),
                        ),
                        fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        }
        ReportKind.MONTH -> {
            val heatState = remember(report.period, goal, tick) {
                mutableStateOf(ReportData.peekHeat(app, report.period, goal) ?: emptyMap())
            }
            LaunchedEffect(report.period, goal, tick) {
                val p = report.period
                heatState.value = withContext(Dispatchers.IO) { ReportData.loadHeat(app, p, goal) }
            }
            Panel(Modifier.padding(top = 16.dp)) {
                MonthCalendar(
                    selected = minOf(report.period.end, today),
                    minDate = report.period.start,
                    maxDate = minOf(report.period.end, today),
                    firstDay = firstDow,
                    heat = heatState.value,
                    laps = heatState.value.filterValues { it >= 1f },
                    onSelect = {},
                    outline = report.period.contains(today),
                    showHeader = false,
                )
            }
            Panel(Modifier.padding(top = 16.dp)) {
                StatsBlock(report, fmt, animate)
            }
            SectionLabel(stringResource(R.string.reports_by_week))
            Panel {
                StepsBarChart(
                    values = report.bars.map { it.avgPerDay },
                    labels = report.bars.map { weekLabel(it.start, it.end) },
                    goal = goal, selectedIndex = null, boldIndex = report.barIndexOfToday,
                    onBarClick = {}, revealInstant = true,
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                )
            }
        }
        ReportKind.YEAR -> {
            Panel(Modifier.padding(top = 16.dp)) {
                StatsBlock(report, fmt, animate)
            }
            SectionLabel(stringResource(R.string.reports_by_month))
            Panel {
                StepsBarChart(
                    values = report.bars.map { it.avgPerDay },
                    labels = report.bars.map { it.start.month.getDisplayName(DateTextStyle.NARROW, Locale.getDefault()) },
                    goal = goal, selectedIndex = null, boldIndex = report.barIndexOfToday,
                    onBarClick = {}, revealInstant = true,
                    valueLabel = { Util.formatShortStepsDecimal(it.toLong(), ctx) },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                )
            }
        }
        ReportKind.CUSTOM -> {
            Panel(Modifier.padding(top = 16.dp)) {
                StatsBlock(report, fmt, animate)
            }
            SectionLabel(
                stringResource(
                    when (report.bucket) {
                        ReportBucket.DAY -> R.string.reports_daily_steps
                        ReportBucket.WEEK -> R.string.reports_by_week
                        ReportBucket.MONTH -> R.string.reports_by_month
                    }
                )
            )
            Panel {
                StepsBarChart(
                    values = report.bars.map { it.avgPerDay },
                    labels = report.bars.map { bar ->
                        when (report.bucket) {
                            ReportBucket.MONTH -> bar.start.month.getDisplayName(DateTextStyle.NARROW, Locale.getDefault())
                            ReportBucket.WEEK -> weekLabel(bar.start, bar.end)
                            else -> if (bar.start.dayOfMonth == 1) {
                                bar.start.dayOfMonth.toString() + " " +
                                    bar.start.month.getDisplayName(DateTextStyle.SHORT, Locale.getDefault())
                            } else bar.start.dayOfMonth.toString()
                        }
                    },
                    goal = goal, selectedIndex = null, boldIndex = report.barIndexOfToday,
                    onBarClick = {}, revealInstant = true,
                    showValues = report.bars.size <= 14,
                    valueLabel = { Util.formatShortStepsDecimal(it.toLong(), ctx) },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                )
            }
        }
    }
}

@Composable
private fun StatsBlock(report: PeriodReport, fmt: SimpleDateFormat, animate: Boolean) {
    val ctx = LocalContext.current
    val divider = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val rowEnter = if (animate) fadeIn(tween(200)) + expandVertically(tween(220)) else fadeIn(snap()) + expandVertically(snap())
    val rowExit = if (animate) fadeOut(tween(120)) + shrinkVertically(tween(180)) else fadeOut(snap()) + shrinkVertically(snap())
    val bestDay = report.bestDay
    val allTimeBest = bestDay != null &&
        report.insights.any { it is Insight.TopDay && it.rank == 1 && it.date == bestDay.first }
    val star: (@Composable () -> Unit)? = if (allTimeBest) {
        {
            Icon(
                painterResource(R.drawable.ic_star_filled), null,
                tint = colorResource(R.color.colorSpecial),
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
    } else null
    StatRow(
        stringResource(R.string.reports_stat_best_day),
        bestDay?.let { (d, v) -> dateLabel(ctx, fmt, d) + " · " + Util.formatSteps(v) } ?: "—",
        valueLeading = star,
    )
    AnimatedVisibility(visible = report.goal > 0, enter = rowEnter, exit = rowExit) {
        Column {
            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(1.dp).background(divider))
            StatRow(
                stringResource(R.string.records_goal_reached),
                ctx.getString(R.string.reports_of, report.goalDays, report.loggedDays),
            )
            AnimatedVisibility(visible = report.longestGoalRun >= 2, enter = rowEnter, exit = rowExit) {
                Column {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(1.dp).background(divider))
                    StatRow(
                        stringResource(R.string.reports_stat_goal_run),
                        ctx.resources.getQuantityString(R.plurals.days_with_count, report.longestGoalRun, report.longestGoalRun),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, valueLeading: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp, color = StepsyTheme.colors.accent)
        Row(verticalAlignment = Alignment.CenterVertically) {
            valueLeading?.invoke()
            Text(
                value, fontSize = 15.sp, fontFamily = Chivo,
                color = MaterialTheme.colorScheme.onSurface,
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
        }
    }
}

@Composable
private fun RangePresetPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
        color = if (selected) StepsyTheme.colors.background else StepsyTheme.colors.accent,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.onSurface else StepsyTheme.colors.muted)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    )
}

@Composable
private fun CustomRangeDialog(
    minDate: LocalDate,
    today: LocalDate,
    firstDow: DayOfWeek,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
) {
    var anchor by remember { mutableStateOf<LocalDate?>(null) }
    var selectedRange by remember { mutableStateOf<Pair<LocalDate, LocalDate>?>(null) }

    val picked: Pair<LocalDate, LocalDate>? = selectedRange ?: anchor?.let { it to it }
    val spanDays = picked?.let { (s, e) -> ChronoUnit.DAYS.between(s, e).toInt() + 1 } ?: 0
    val valid = spanDays in 1..MAX_CUSTOM_RANGE_DAYS
    val applyPreset: (Int) -> Unit = { days ->
        if (spanDays == days) {
            anchor = null
            selectedRange = null
        } else {
            val start = today.minusDays((days - 1).toLong())
            anchor = start
            selectedRange = start to today
        }
    }

    StepsyDialog(
        title = stringResource(R.string.reports_custom_pick),
        onDismiss = onDismiss,
        confirmText = stringResource(android.R.string.ok),
        confirmEnabled = valid,
        onConfirm = { picked?.let { (s, e) -> onConfirm(s, e) } },
    ) {
        Column {
            val heat = remember(selectedRange, anchor) {
                val span = selectedRange ?: anchor?.let { it to it }
                buildMap {
                    if (span != null) {
                        var d = span.first
                        while (!d.isAfter(span.second)) {
                            put(d, 1f)
                            d = d.plusDays(1)
                        }
                    }
                }
            }
            MonthCalendar(
                selected = today,
                minDate = minDate,
                maxDate = today,
                firstDay = firstDow,
                heat = heat,
                outline = false,
                onSelect = { d ->
                    val a = anchor
                    when {
                        selectedRange != null -> {
                            anchor = d
                            selectedRange = null
                        }
                        a == null -> anchor = d
                        a == d -> Unit
                        else -> {
                            anchor = minOf(a, d)
                            selectedRange = minOf(a, d) to maxOf(a, d)
                        }
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                RangePresetPill(stringResource(R.string.label_7d), spanDays == 7) { applyPreset(7) }
                RangePresetPill(stringResource(R.string.label_30d), spanDays == 30) { applyPreset(30) }
                RangePresetPill(stringResource(R.string.label_90d), spanDays == 90) { applyPreset(90) }
            }
            if (!valid && picked != null) {
                Text(
                    stringResource(R.string.reports_custom_max, MAX_CUSTOM_RANGE_DAYS),
                    fontSize = 13.sp, color = StepsyTheme.colors.accent,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SkeletonLoading() {
    val bed = StepsyTheme.colors.dialogSurface
    Column(
        Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(120.dp).clip(CircleShape).background(bed))
        Spacer(Modifier.height(20.dp))
        Box(Modifier.width(140.dp).height(22.dp).clip(RoundedCornerShape(11.dp)).background(bed))
        Spacer(Modifier.height(12.dp))
        Box(Modifier.width(96.dp).height(14.dp).clip(RoundedCornerShape(7.dp)).background(bed))
        Spacer(Modifier.height(28.dp))
        Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(24.dp)).background(bed))
    }
}

@Composable
private fun MutedMessage(text: String) {
    Text(
        text, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center,
        color = StepsyTheme.colors.accent,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        style = LocalTextStyle.current.copy(
            lineBreak = LineBreak(
                strictness = LineBreak.Strictness.Default,
                wordBreak = LineBreak.WordBreak.Default,
                strategy = LineBreak.Strategy.Balanced,
            ),
        ),
    )
}

private fun rhythmHeadline(ctx: Context, stats: DayPartStats): String {
    val leader = stats.leader
    if (leader == null || stats.share(leader) < 0.35f) return ctx.getString(R.string.reports_rhythm_balanced)
    return ctx.getString(
        when (leader) {
            0 -> R.string.reports_rhythm_night
            1 -> R.string.reports_rhythm_morning
            2 -> R.string.reports_rhythm_afternoon
            else -> R.string.reports_rhythm_evening
        }
    )
}

private fun rhythmCoverage(ctx: Context, stats: DayPartStats): String? =
    if (stats.included < stats.rowsInRange) {
        ctx.getString(R.string.reports_hourly_coverage, stats.included, stats.rowsInRange)
    } else null

private fun dateLabel(ctx: Context, fmt: SimpleDateFormat, date: LocalDate): String =
    fmt.format(Date(Util.dateStringToCalendarMillis(date.toString())))

private fun weekLabel(start: LocalDate, end: LocalDate): String =
    if (start == end) start.dayOfMonth.toString()
    else "${start.dayOfMonth}–${end.dayOfMonth}"

private fun weekdayShortLabels(firstDow: DayOfWeek): List<String> {
    val calDay = (firstDow.value % 7) + 1
    return Calendar.getInstance().let { c ->
        (0 until 7).map { i ->
            c.set(Calendar.DAY_OF_WEEK, ((calDay - 1 + i) % 7) + 1)
            c.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()).orEmpty()
        }
    }
}

private fun insightText(ctx: Context, insight: Insight, fmt: SimpleDateFormat, percentFmt: NumberFormat): String {
    fun prevName(p: ReportPeriod): String = when (p.kind) {
        ReportKind.WEEK -> ctx.getString(R.string.reports_prev_week)
        ReportKind.MONTH -> ctx.getString(R.string.reports_prev_month)
        ReportKind.YEAR -> ctx.getString(R.string.reports_prev_year)
        ReportKind.CUSTOM -> ctx.resources.getQuantityString(R.plurals.reports_prev_custom, p.days, p.days)
    }
    fun gapText(days: Int): String = when {
        days >= 14 -> {
            val weeks = (days.toDouble() / 7).roundToInt()
            ctx.resources.getQuantityString(R.plurals.reports_weeks_count, weeks, weeks)
        }
        else -> ctx.resources.getQuantityString(R.plurals.days_with_count, days, days)
    }
    return when (insight) {
        is Insight.VsPrevious -> {
            val pct = insight.delta.percent
            val pctText = if (pct > 0) "+" + percentFmt.format(pct / 100.0) else percentFmt.format(pct / 100.0)
            ctx.getString(
                if (insight.delta.paceBased) R.string.reports_vs_diff_pace else R.string.reports_vs_diff,
                pctText, prevName(insight.delta.previous),
            )
        }
        is Insight.SameAsPrevious -> ctx.getString(
            if (insight.paceBased) R.string.reports_vs_same_pace else R.string.reports_vs_same,
            prevName(insight.previous),
        )
        is Insight.BestYear -> ctx.getString(R.string.reports_best_year)
        is Insight.BestMonth -> ctx.getString(
            if (insight.scope == RecordScope.EVER) R.string.reports_best_month_ever else R.string.reports_best_month_year,
        )
        is Insight.BestWeek -> ctx.getString(
            if (insight.scope == RecordScope.EVER) R.string.reports_best_week_ever else R.string.reports_best_week_year,
        )
        is Insight.NewLongestStreak -> {
            val streak = ctx.resources.getQuantityString(R.plurals.days_with_count, insight.days, insight.days)
            if (insight.ongoing) ctx.getString(R.string.reports_streak_record_ongoing, streak)
            else ctx.getString(R.string.reports_streak_record, streak)
        }
        is Insight.TopDay -> ctx.getString(R.string.reports_top_day, dateLabel(ctx, fmt, insight.date), insight.rank)
        is Insight.ActiveWeekday -> ctx.getString(
            R.string.reports_active_weekday,
            insight.day.getDisplayName(DateTextStyle.FULL, Locale.getDefault()),
        )
        is Insight.GoalAfterGap -> if (insight.gapDays == null) ctx.getString(R.string.reports_goal_first_ever)
        else ctx.getString(R.string.reports_goal_after_gap, gapText(insight.gapDays))
        is Insight.GoalDays -> ctx.getString(R.string.reports_goal_days_line, insight.met, insight.logged)
    }
}
