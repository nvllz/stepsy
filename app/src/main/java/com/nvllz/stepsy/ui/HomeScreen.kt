package com.nvllz.stepsy.ui

import android.app.Activity
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nvllz.stepsy.R
import com.nvllz.stepsy.ui.components.*
import com.nvllz.stepsy.ui.theme.Chivo
import com.nvllz.stepsy.ui.theme.StepsyTheme
import com.nvllz.stepsy.util.AppPreferences
import com.nvllz.stepsy.util.Database
import com.nvllz.stepsy.util.Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private fun calToDow(cal: Int): DayOfWeek = DayOfWeek.of((cal + 5) % 7 + 1)

private fun monthIndex(s: Selection): Int? =
    (s as? Selection.Month)?.let { (y, m) -> y * 12 + m }

@Composable
private fun <T> rememberRefreshed(vararg keys: Any?, compute: () -> T): T {
    val latest by rememberUpdatedState(compute)
    val state = remember { mutableStateOf(compute()) }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(*keys) {
        if (first[0]) { first[0] = false; return@LaunchedEffect }
        state.value = withContext(Dispatchers.IO) { latest() }
    }
    return state.value
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeScreen(
    steps: Int,
    paused: Boolean,
    onTogglePause: () -> Unit,
    onPauseOptions: () -> Unit,
    onUpdateSteps: (Int) -> Unit,
    stepsBlocked: Boolean = false,
    onFixSteps: () -> Unit = {},
    onOpen: (Class<out Activity>) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    var filterRowHeight by remember { mutableIntStateOf(0) }
    var filterRowApplied by remember { mutableStateOf(false) }
    val filterRowHidden by remember {
        derivedStateOf { filterRowHeight > 0 && scroll.value >= filterRowHeight }
    }

    val fabShown = remember { mutableStateOf(true) }
    LaunchedEffect(scroll, filterRowHeight) {
        var concealedNow = false
        if (!filterRowApplied && filterRowHeight > 0) {
            scroll.scrollTo(filterRowHeight)
            filterRowApplied = true
            concealedNow = true
        }

        var prev = scroll.value
        if (concealedNow) fabShown.value = true
        snapshotFlow { scroll.value }.collect { value ->
            val delta = value - prev
            prev = value
            when {
                value <= 0 -> fabShown.value = true
                delta > 2 -> fabShown.value = false
                delta < -2 -> fabShown.value = true
            }
        }
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        if (filterRowHeight == 0) filterRowApplied = true
    }
    val today = Util.logicalToday()

    var filters by remember { mutableStateOf(loadFilterVisibility(ctx)) }
    var selection by remember {
        val sel = loadSelection(ctx)
        val hidden = (sel is Selection.Range && sel.range !in filters.ranges) ||
            (sel is Selection.Year && !filters.years) ||
            sel is Selection.Month
        mutableStateOf(if (hidden) Selection.Range(StepRange.TODAY) else sel)
    }
    var selectedDate by remember { mutableStateOf(today) }
    var pickedDay by remember { mutableStateOf<LocalDate?>(null) }
    val effDay = pickedDay?.takeIf { it != today }
    val viewSel = if (pickedDay == today) Selection.Range(StepRange.TODAY) else selection
    val isTodayView = pickedDay == today ||
        (pickedDay == null && selection == Selection.Range(StepRange.TODAY))
    var editSteps by remember { mutableStateOf(false) }
    var confirmLower by remember { mutableStateOf<Int?>(null) }
    var showFilterPicker by remember { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) }
    val jumpKey = remember { mutableIntStateOf(0) }
    val editable = pickedDay != null || selection == Selection.Range(StepRange.TODAY)

    val goal by AppPreferences.dailyGoalTargetFlow().collectAsStateWithLifecycle(AppPreferences.dailyGoalTarget)

    val dayTotals = rememberRefreshed(version) { stepsByDay(ctx) }
    val years = remember(dayTotals) {
        dayTotals.filterValues { it > 0 }.keys.map { it.year }.distinct().sorted()
    }

    val goalMet = goal > 0 && steps >= goal
    val summaryBase = rememberRefreshed(selection, pickedDay, goalMet, version) {
        loadSummary(ctx, viewSel, effDay, steps)
    }
    val summary = if (isTodayView) summaryBase.copy(steps = steps) else summaryBase
    val chartBase = rememberRefreshed(pickedDay, selectedDate, version) {
        loadChart(ctx, effDay == null, selectedDate, 0)
    }
    val chart = remember(chartBase, steps) {
        val i = chartBase.dates.indexOf(today)
        if (i < 0) chartBase
        else chartBase.copy(values = chartBase.values.toMutableList().also { it[i] = steps })
    }
    val minDate = remember { firstEntryDate(ctx) }
    val todayPct = if (goal > 0) (steps.toLong() * 100 / goal).toInt() else 0
    val baseHeat = remember(dayTotals, goal) {
        dayTotals.mapValues { (_, v) -> if (goal > 0) (v.toFloat() / goal).coerceAtMost(1f) else 0f }
    }
    val heat = remember(baseHeat, todayPct) {
        baseHeat + (today to (minOf(todayPct, 100) / 100f))
    }
    val baseLaps = remember(dayTotals, goal) {
        if (goal <= 0) emptyMap<LocalDate, Float>()
        else dayTotals.mapValues { it.value.toFloat() / goal }.filterValues { it >= 1f }
    }
    val lapDays = remember(baseLaps, todayPct) {
        if (todayPct >= 100) baseLaps + (today to todayPct / 100f) else baseLaps
    }
    val streakDays: Set<LocalDate> = remember(heat) {
        var d = if ((heat[today] ?: 0f) >= 1f) today else today.minusDays(1)
        val set = mutableSetOf<LocalDate>()
        while ((heat[d] ?: 0f) >= 1f) { set += d; d = d.minusDays(1) }
        if (set.size > 1) set else emptySet()
    }
    val streakCount = streakDays.size
    var lastStreak by remember { mutableStateOf(0) }
    LaunchedEffect(streakCount) { if (streakCount > 0) lastStreak = streakCount }

    var newMilestones by remember { mutableStateOf(hasUnseenMilestones(ctx)) }
    val baseTotal = remember { AppPreferences.baseTotalSteps }
    var crossingSeen by remember {
        mutableLongStateOf(Util.MILESTONES.lastOrNull { it <= baseTotal + steps } ?: 0L)
    }
    LaunchedEffect(steps, version) {
        val crossed = Util.MILESTONES.lastOrNull { it <= baseTotal + steps } ?: 0L
        if (crossed > crossingSeen) {
            crossingSeen = crossed
            newMilestones = hasUnseenMilestones(ctx)
        }
    }
    LifecycleResumeEffect(Unit) {
        newMilestones = hasUnseenMilestones(ctx)
        onPauseOrDispose { }
    }
    var lastGoal by remember { mutableStateOf(goal) }
    LaunchedEffect(goal) { if (goal > 0) lastGoal = goal }
    val dayView = pickedDay != null || selection == Selection.Range(StepRange.TODAY)
    val s = summary
    val ringBase = when {
        isTodayView -> steps
        pickedDay != null -> s.steps
        else -> s.avg ?: 0
    }
    val progress = if (goal > 0) ringBase.toFloat() / goal else 0f
    val heroReached = goal > 0 && progress >= 1f
    val pillProgress = if (goal > 0) progress else 1f
    val heroAnim = remember { Animatable(progress.coerceIn(0f, 10f)) }
    LaunchedEffect(progress) {
        heroAnim.animateTo(
            progress.coerceIn(0f, 10f),
            spring(dampingRatio = 1f, stiffness = Spring.StiffnessLow),
        )
    }
    val dayHeaderFormat = remember { SimpleDateFormat(AppPreferences.dateFormatString, Locale.getDefault()) }
    fun dayHeader(d: LocalDate): String =
        dayHeaderFormat.format(Date(Util.dateStringToCalendarMillis(d.toString())))
    val ranges = StepRange.entries.filter { it in filters.ranges }.map<StepRange, Pair<Selection?, String>> {
        Selection.Range(it) to stringResource(it.label)
    } + (if (filters.years) years.map { Selection.Year(it) to it.toString() } else emptyList())

    val rangesLive by rememberUpdatedState(ranges)

    val toast = LocalToast.current

    fun applyEdit(n: Int) {
        val day = effDay
        if (day == null) onUpdateSteps(n)
        else scope.launch {
            withContext(Dispatchers.IO) { Database.getInstance(ctx).addEntry(day.toString(), n) }
            version++
        }
        toast.show(ctx.getString(R.string.steps_updated), ToastKind.SUCCESS)
    }

    fun scrollToFold() {
        scope.launch {
            scroll.animateScrollTo(
                filterRowHeight,
                tween(850, easing = FastOutSlowInEasing)
            )
        }
    }

    fun jumpToToday() {
        selection = Selection.Range(StepRange.TODAY)
        saveSelection(ctx, selection)
        pickedDay = null
        selectedDate = today
        jumpKey.intValue++
        scrollToFold()
    }

    fun selectFilter(sel: Selection) {
        selection = sel
        pickedDay = null
        selectedDate = today
        saveSelection(ctx, sel)
    }

    fun moveFilter(dir: Int) {
        val list = rangesLive
        val i = list.indexOfFirst { it.first == selection }
        val sel = if (i >= 0) list[(i + dir).coerceIn(0, list.size - 1)].first
        else (list.firstOrNull { it.first == loadSavedRange(ctx) } ?: list.first()).first
        selectFilter(sel ?: return)
    }

    var swipeStepped by remember { mutableStateOf(false) }
    var countAccepted by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val countKey = pickedDay?.toString() ?: selection.toString()
    val accepted = countAccepted
    val countLanded = accepted?.first == countKey && accepted.second != s.steps
    if (accepted == null || accepted.first != countKey || accepted.second != s.steps) {
        countAccepted = countKey to s.steps
    }
    SideEffect { if (countLanded) swipeStepped = false }

    fun stepDay(dir: Int) {
        val cur = pickedDay ?: return
        val t = Util.logicalToday()
        val lower = minOf(minDate, t)
        if (lower > t) return
        val next = cur.plusDays(dir.toLong()).coerceIn(lower, t)
        if (next == cur) return
        pickedDay = next
        selectedDate = next
        swipeStepped = true
    }

    fun stepMonth(dir: Int) {
        val cur = selection as? Selection.Month ?: return
        val (y, m) = cur
        val t = Util.logicalToday()
        val next = YearMonth.of(y, m).plusMonths(dir.toLong())
        val todayMonth = YearMonth.from(t)
        if (next.isAfter(todayMonth) || next.isBefore(YearMonth.from(minOf(minDate, t)))) return
        selection = Selection.Month(next.year, next.monthValue)
        selectedDate = next.atDay(1)
        swipeStepped = true
    }

    fun moveSelection(dir: Int) = when {
        pickedDay != null -> stepDay(dir)
        selection is Selection.Month -> stepMonth(dir)
        else -> moveFilter(dir)
    }

    Box(Modifier.fillMaxSize().background(StepsyTheme.colors.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopBar(
                onOpen = onOpen,
                showToday = pickedDay != null || selection != Selection.Range(StepRange.TODAY),
                onToday = ::jumpToToday,
                newMilestones = newMilestones,
            )

            AnimatedVisibility(
                visible = stepsBlocked,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) { StepsOffStrip(onFixSteps) }

            Column(
                Modifier
                    .weight(1f)
                    .graphicsLayer { alpha = if (filterRowApplied) 1f else 0f }
                    .verticalScroll(scroll)
                    .padding(horizontal = 16.dp)
            ) {
                PillSelector(
                    options = ranges, selected = if (pickedDay == null) selection else null,
                    onLongClick = { showFilterPicker = true },
                    onSelect = { sel -> if (sel != null) selectFilter(sel) },
                    modifier = Modifier.onSizeChanged { filterRowHeight = it.height },
                )

                Column(
                    Modifier.fillMaxWidth()
                        .padding(bottom = 20.dp)
                        .pointerInput(Unit) {
                            var dragX = 0f
                            awaitEachGesture {
                                dragX = 0f
                                var claimed = false
                                awaitFirstDown(requireUnconsumed = false)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    val dx = change.position.x - change.previousPosition.x
                                    if (claimed) {
                                        change.consume()
                                        dragX += dx
                                    } else if (!change.isConsumed) {
                                        val dy = change.position.y - change.previousPosition.y
                                        if (abs(dx) > viewConfiguration.touchSlop && abs(dx) > abs(dy)) {
                                            claimed = true
                                            change.consume()
                                            dragX += dx
                                        }
                                    }
                                    if (!change.pressed) break
                                }
                                if (claimed) {
                                    when {
                                        dragX <= -72.dp.toPx() -> moveSelection(+1)
                                        dragX >= 72.dp.toPx() -> moveSelection(-1)
                                    }
                                }
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.fillMaxWidth().height(30.dp), contentAlignment = Alignment.Center) {
                        FilterBarCaret(
                            visible = filterRowHidden,
                            onClick = {
                                scope.launch {
                                    scroll.animateScrollTo(
                                        0,
                                        tween(850, easing = FastOutSlowInEasing)
                                    )
                                }
                            },
                        )
                    }

                    AnimatedContent(
                        targetState = selection,
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter,
                        transitionSpec = {
                            val from = rangesLive.indexOfFirst { it.first == initialState }
                            val to = rangesLive.indexOfFirst { it.first == targetState }
                            val mi = monthIndex(initialState); val mt = monthIndex(targetState)
                            val dir = when {
                                mi != null && mt != null -> if (mt < mi) -1 else 1
                                from >= 0 && to >= 0 && to < from -> -1
                                else -> 1
                            }
                            (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { dir * it } + fadeIn(tween(200)))
                                .togetherWith(
                                    slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -dir * it } + fadeOut(tween(180)),
                                ) using SizeTransform(clip = false)
                        },
                        label = "heroPage",
                    ) { sel ->
                        val mult = goalMultiplier(ringBase, goal)
                        val showGoalDays = !dayView
                        val live = sel == selection
                        var goalDaysLive by remember { mutableStateOf(showGoalDays) }
                        if (live) goalDaysLive = showGoalDays
                        var goalDaysShown by remember { mutableStateOf(s.goalDays) }
                        if (goalDaysLive) goalDaysShown = s.goalDays
                        var multShown by remember { mutableStateOf("") }
                        if (mult != null) multShown = mult
                        var multLive by remember { mutableStateOf(mult != null) }
                        if (live) multLive = mult != null
                        var lapShown by remember {
                            mutableStateOf(if (goal > 0) (ringBase.toFloat() / goal).toInt().coerceAtLeast(0) else 0)
                        }
                        if (live && goal > 0) lapShown = (ringBase.toFloat() / goal).toInt().coerceAtLeast(0)
                        var headerShown by remember { mutableStateOf("") }
                        val day = pickedDay
                        if (live) headerShown = if (day != null) dayHeader(day) else s.header

                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                headerShown.uppercase(),
                                modifier = Modifier.padding(bottom = 6.dp),
                                fontSize = 13.sp,
                                letterSpacing = 2.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = StepsyTheme.colors.accent
                            )
                            CountText(
                                s.steps,
                                Util::formatSteps,
                                TextStyle(
                                    fontSize = 56.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = Chivo,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = editable) { editSteps = true },
                                duration = if (swipeStepped) 0 else CountTextDefaultDuration,
                            )
                            Text(
                                ctx.resources.getQuantityString(R.plurals.steps_word, s.steps).uppercase(),
                                fontSize = 13.sp, letterSpacing = 2.sp,
                                fontWeight = FontWeight.SemiBold, color = StepsyTheme.colors.accent,
                                modifier = Modifier.offset(y = (-2).dp),
                            )
                            if (goal > 0) {
                                HeroBar(heroAnim.asState(), Modifier.padding(top = 18.dp, bottom = 8.dp))
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f)) {
                                        androidx.compose.animation.AnimatedVisibility(goalDaysLive, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                                            Text(ctx.resources.getQuantityString(R.plurals.goal_reached_times, goalDaysShown, goalDaysShown),
                                                fontSize = 14.sp, color = StepsyTheme.colors.accent)
                                        }
                                    }
                                    androidx.compose.animation.AnimatedVisibility(multLive, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                                        val lapColor = lapOf(lapPalette(), lapShown)
                                        Text(multShown, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = lapColor,
                                            modifier = Modifier.clip(CircleShape).background(lapColor.copy(alpha = 0.15f))
                                                .padding(horizontal = 10.dp, vertical = 1.dp))
                                    }
                                }
                            }
                            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                val flame = colorResource(R.color.colorFlame)
                                val steel = colorResource(R.color.colorDistance)
                                val sage = colorResource(R.color.colorStepsDay)
                                StatPill(
                                    R.drawable.ic_distance, s.steps,
                                    { st -> ctx.getString(R.string.distance_today, Util.stepsToDistance(st), Util.distanceUnit()) },
                                    pillColor(steel, pillProgress), glow = heroReached,
                                )
                                AnimatedContent(
                                    dayView,
                                    transitionSpec = {
                                        (fadeIn(tween(180)) togetherWith fadeOut(tween(180))) using SizeTransform(clip = false)
                                    },
                                    contentAlignment = Alignment.Center,
                                    label = "pill2",
                                ) { day ->
                                    if (day) StatPill(
                                        R.drawable.ic_calories, s.steps,
                                        { st -> ctx.getString(R.string.calories, Util.stepsToCalories(st)) },
                                        pillColor(flame, pillProgress), glow = heroReached,
                                    )
                                    else StatPill(
                                        R.drawable.ic_steps, s.avg ?: 0,
                                        { st -> ctx.getString(R.string.avg_per_day_pill, Util.formatSteps(st)) },
                                        pillColor(sage, pillProgress), glow = heroReached,
                                    )
                                }
                            }
                            val showStreak = goal > 0 && isTodayView && streakCount > 1
                            val accent = StepsyTheme.colors.accent
                            val streakColor = pillColor(StepsyTheme.colors.special, pillProgress)
                            val shownGoal = if (goal > 0) goal else lastGoal
                            AnimatedVisibility(
                                visible = goal > 0,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically(),
                            ) {
                                Row(
                                    Modifier
                                        .padding(top = 14.dp)
                                        .clip(CircleShape)
                                        .background(StepsyTheme.colors.accentOpaque)
                                        .clickable(role = Role.Button) {
                                            ctx.startActivity(Intent(ctx, DailyGoalsActivity::class.java))
                                        }
                                        .padding(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        Modifier.padding(start = 10.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            painterResource(R.drawable.ic_target), null, tint = accent,
                                            modifier = Modifier.padding(end = 8.dp).size(20.dp),
                                        )
                                        Text(
                                            pluralStringResource(
                                                R.plurals.steps_formatted, shownGoal, Util.formatSteps(shownGoal),
                                            ),
                                            fontSize = 16.sp, fontWeight = FontWeight.Medium, color = accent,
                                        )
                                        Icon(
                                            painterResource(R.drawable.ic_chevron_right), null,
                                            tint = accent.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(start = 2.dp).size(16.dp),
                                        )
                                    }

                                    AnimatedVisibility(
                                        visible = showStreak,
                                        enter = scaleIn(initialScale = 0.5f) +
                                                expandHorizontally(expandFrom = Alignment.Start),
                                        exit = scaleOut(targetScale = 0.5f) +
                                                shrinkHorizontally(shrinkTowards = Alignment.Start),
                                    ) {
                                        val days = if (streakCount > 1) streakCount else lastStreak
                                        val description = pluralStringResource(
                                            R.plurals.streak_segment_description, days, days,
                                        )
                                        Row(
                                            Modifier
                                                .clearAndSetSemantics { contentDescription = description }
                                                .clip(CircleShape)
                                                .background(streakColor.copy(alpha = 0.15f))
                                                .padding(start = 8.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            AnimatedContent(
                                                targetState = heroReached,
                                                modifier = Modifier.padding(end = 5.dp),
                                                transitionSpec = {
                                                    (fadeIn() + scaleIn(initialScale = 0.4f)) togetherWith
                                                            (fadeOut() + scaleOut(targetScale = 0.4f))
                                                },
                                                contentAlignment = Alignment.Center,
                                                label = "streakStar",
                                            ) { reached ->
                                                Icon(
                                                    painterResource(
                                                        if (reached) R.drawable.ic_star_filled else R.drawable.ic_star,
                                                    ),
                                                    null,
                                                    tint = streakColor,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                            Text(
                                                pluralStringResource(R.plurals.streak_record_count, days, days),
                                                fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = streakColor,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Panel(Modifier.height(260.dp)) {
                    var chartLabelShown by remember { mutableStateOf("") }
                    if (chart.past7 == (effDay == null)) {
                        chartLabelShown = if (effDay == null) chart.header else "${chart.header} · ${chart.range}"
                    }
                    Text(
                        text = chartLabelShown,
                        fontSize = 13.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
                    )
                    StepsBarChart(
                        values = chart.values, labels = chart.labels,
                        goal = if (goal > 0) goal else 0,
                        selectedIndex = pickedDay?.let { chart.dates.indexOf(it) }?.takeIf { it >= 0 },
                        boldIndex = if (dayView) chart.dates.indexOf(pickedDay ?: today).takeIf { it >= 0 } else null,
                        onBarClick = { i ->
                            val d = chart.dates[i]
                            if (!d.isAfter(today)) {
                                selectedDate = d; pickedDay = d
                                scrollToFold()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }

                Panel(Modifier.padding(top = 16.dp)) {
                    MonthCalendar(
                        selected = selectedDate, minDate = minDate, maxDate = today,
                        firstDay = calToDow(AppPreferences.firstDayOfWeek), heat = heat, laps = lapDays,
                        streak = streakDays,
                        onSelect = {
                            selectedDate = it
                            pickedDay = it
                            scrollToFold()
                        },
                        onMonthClick = { y, m ->
                            selection = Selection.Month(y, m)
                            pickedDay = null
                            selectedDate = LocalDate.of(y, m, 1)
                            scrollToFold()
                        },
                    )
                }
                Spacer(Modifier.navigationBarsPadding().height(12.dp))
            }
        }

        AnimatedVisibility(
            fabShown.value,
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp),
            enter = scaleIn(initialScale = 0.5f) + fadeIn(),
            exit = scaleOut(targetScale = 0.5f) + fadeOut(),
        ) {
            PauseFab(paused, onTogglePause, onPauseOptions)
        }
    }

    if (editSteps) {
        val dialogDate = if (selectedDate == today) {
            stringResource(R.string.label_today)
        } else {
            selectedDate.toString()
        }
        NumberInputDialog(
            stringResource(R.string.edit_step_count_for_date, stringResource(R.string.edit_step_count), dialogDate),
            s.steps.toString(),
            onConfirm = { input ->
                editSteps = false
                val n = input.toIntOrNull()
                when { n == null -> toast.show(ctx.getString(R.string.invalid_step_count), ToastKind.ERROR); n < s.steps -> confirmLower = n; else -> applyEdit(n) }
            }, onDismiss = { editSteps = false })
    }
    confirmLower?.let { n ->
        MessageDialog(
            stringResource(R.string.confirm_decrease_title),
            stringResource(R.string.confirm_decrease_message),
            onConfirm = { confirmLower = null; applyEdit(n) },
            onDismiss = { confirmLower = null },
        )
    }

    if (showFilterPicker) {
        FilterPickerDialog(
            filters,
            onApply = { nf ->
                showFilterPicker = false
                if (nf != filters) {
                    filters = nf
                    saveFilterVisibility(ctx, nf)
                    val sel = selection
                    val hidden = (sel is Selection.Year && !nf.years) ||
                        (sel is Selection.Range && sel.range !in nf.ranges)
                    if (hidden) {
                        selection = Selection.Range(StepRange.TODAY)
                        saveSelection(ctx, selection)
                    }
                }
            },
            onDismiss = { showFilterPicker = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterPickerDialog(
    filters: FilterVisibility,
    onApply: (FilterVisibility) -> Unit,
    onDismiss: () -> Unit,
) {
    var yearsOn by remember { mutableStateOf(filters.years) }
    var rangesOn by remember { mutableStateOf(filters.ranges) }
    StepsyDialog(
        title = stringResource(R.string.filters_title),
        onDismiss = onDismiss,
        confirmText = stringResource(android.R.string.ok),
        onConfirm = { onApply(FilterVisibility(yearsOn, rangesOn)) },
    ) {
        FlowRow(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChipLabel(stringResource(R.string.label_today), on = true, onClick = null)
            StepRange.entries.filter { it != StepRange.TODAY }.forEach { r ->
                FilterChipLabel(stringResource(r.label), on = r in rangesOn) {
                    rangesOn = if (r in rangesOn) rangesOn - r else rangesOn + r
                }
            }
            FilterChipLabel(stringResource(R.string.filters_years), on = yearsOn) { yearsOn = !yearsOn }
        }
    }
}

@Composable
private fun FilterChipLabel(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = StepsyTheme.colors
    val bg by animateColorAsState(if (on) MaterialTheme.colorScheme.onSurface else c.accentOpaque, tween(200), label = "chipBg")
    val ink by animateColorAsState(if (on) c.background else c.accent, tween(200), label = "chipInk")
    Text(
        label, fontSize = 14.sp, maxLines = 1,
        fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
        color = ink,
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 7.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    onOpen: (Class<out Activity>) -> Unit,
    showToday: Boolean,
    onToday: () -> Unit,
    newMilestones: Boolean,
) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).heightIn(min = 40.dp)) {
        AnimatedVisibility(showToday, Modifier.align(Alignment.Center), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {

            Row(
                Modifier
                    .clip(CircleShape)
                    .background(StepsyTheme.colors.accentOpaque)
                    .border(1.dp, StepsyTheme.colors.accent.copy(alpha = 0.3f), CircleShape)
                    .clickable(onClick = onToday)
                    .padding(start = 10.dp, end = 15.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    stringResource(R.string.label_today),
                    Modifier
                        .rotate(180f)
                        .padding(start = 10.dp)
                        .size(16.dp)
                )
                Text(
                    stringResource(R.string.label_today).uppercase(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Row(
            Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(CircleShape)
                        .background(StepsyTheme.colors.accentOpaque)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = LocalIndication.current,
                            onClick = { onOpen(AchievementsActivity::class.java) },
                        ),
                )
                Box {
                    Icon(
                        painter = painterResource(
                            if (newMilestones) R.drawable.ic_trophy_filled else R.drawable.ic_trophy
                        ),
                        contentDescription = stringResource(R.string.achievements_title),
                        modifier = Modifier.size(24.dp),
                    )
                    if (newMilestones) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 6.dp, y = (-6).dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(StepsyTheme.colors.special)
                                .border(1.dp, StepsyTheme.colors.accentOpaque, CircleShape),
                        )
                    }
                }
            }

            Box {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(StepsyTheme.colors.accentOpaque)
                        .clickable { open = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(
                            androidx.appcompat.R.drawable.abc_ic_menu_overflow_material
                        ),
                        contentDescription = stringResource(R.string.settings),
                    )
                }

                DropdownMenu(
                    expanded = open,
                    onDismissRequest = { open = false },
                    containerColor = StepsyTheme.colors.dialogSurface,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    listOf(
                        R.drawable.ic_target to (R.string.daily_goals to DailyGoalsActivity::class.java),
                        R.drawable.ic_settings to (R.string.settings to SettingsActivity::class.java),
                        R.drawable.ic_info to (R.string.about_stepsy to AboutActivity::class.java),
                    ).forEach { (icon, item) ->
                        val (label, cls) = item

                        DropdownMenuItem(
                            text = { Text(stringResource(label), fontSize = 17.sp) },
                            leadingIcon = {
                                Icon(
                                    painterResource(icon),
                                    null,
                                    Modifier.size(24.dp),
                                )
                            },
                            onClick = {
                                open = false
                                onOpen(cls)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterBarCaret(visible: Boolean, onClick: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.5f),
        exit = fadeOut() + scaleOut(targetScale = 0.5f),
    ) {
        Box(
            Modifier
                .width(48.dp)
                .height(30.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_chevron_up),
                stringResource(R.string.home_show_filter_bar),
                tint = StepsyTheme.colors.accent.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PauseFab(paused: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val container by animateColorAsState(
        if (paused) StepsyTheme.colors.special else MaterialTheme.colorScheme.onSurface,
        label = "fabColor",
    )
    val label = stringResource(if (paused) R.string.action_resume else R.string.action_pause)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .shadow(10.dp, CircleShape)
            .clip(CircleShape).background(container)
            .combinedClickable(onClickLabel = label, onClick = onClick, onLongClick = { if (!paused) onLongClick() }, role = Role.Button)
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
            .height(56.dp).padding(horizontal = 16.dp),
    ) {
        AnimatedContent(
            targetState = paused,
            transitionSpec = {
                (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), initialScale = 0.4f) + fadeIn())
                    .togetherWith(scaleOut(targetScale = 0.4f) + fadeOut())
            },
            label = "fabIcon",
        ) { isPaused ->
            Icon(
                painterResource(if (isPaused) R.drawable.ic_play else R.drawable.ic_pause),
                label, tint = StepsyTheme.colors.background,
                modifier = Modifier.size(24.dp),
            )
        }
        if (paused) {
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                color = StepsyTheme.colors.background, modifier = Modifier.padding(start = 8.dp, end = 4.dp))
        }
    }
}
