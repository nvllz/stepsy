package com.nvllz.stepsy.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nvllz.stepsy.R
import com.nvllz.stepsy.service.MotionService
import com.nvllz.stepsy.ui.components.ChoiceDialog
import com.nvllz.stepsy.ui.components.CountText
import com.nvllz.stepsy.ui.components.Panel
import com.nvllz.stepsy.ui.components.PillSelector
import com.nvllz.stepsy.ui.components.ScreenHeader
import com.nvllz.stepsy.ui.components.SectionLabel
import com.nvllz.stepsy.ui.components.SettingDivider
import com.nvllz.stepsy.ui.components.SettingGroup
import com.nvllz.stepsy.ui.components.SettingRow
import com.nvllz.stepsy.ui.components.StepsyDialog
import com.nvllz.stepsy.ui.components.SwitchSetting
import com.nvllz.stepsy.ui.components.fieldColors
import com.nvllz.stepsy.ui.theme.Chivo
import com.nvllz.stepsy.ui.theme.StepsyTheme
import com.nvllz.stepsy.util.AppPreferences
import com.nvllz.stepsy.util.GoalNotificationWorker
import com.nvllz.stepsy.util.NotificationChannels
import com.nvllz.stepsy.util.Util
import com.nvllz.stepsy.util.WidgetManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.text.NumberFormat
import kotlin.math.roundToInt

@Composable
fun DailyGoalsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val goal by prefsState({ AppPreferences.dailyGoalTargetFlow() }, { AppPreferences.dailyGoalTarget })
    val steps by prefsState({ AppPreferences.stepsFlow() }, { AppPreferences.steps })
    val notify by prefsState({ AppPreferences.dailyGoalNotificationFlow() }, { AppPreferences.dailyGoalNotification })
    val progress by prefsState(
        { AppPreferences.dailyGoalNotificationProgressbarFlow() },
        { AppPreferences.dailyGoalNotificationProgressbar },
    )
    val encouraging by prefsState(
        { AppPreferences.encouragingNotificationsFlow() },
        { AppPreferences.encouragingNotifications },
    )
    val live by prefsState({ AppPreferences.liveNotificationFlow() }, { AppPreferences.liveNotification })

    var blocked by remember { mutableStateOf(blockedChannels(ctx)) }
    val liveOff = blocked[MotionService.STEP_CHANNEL_ID] == true
    val liveSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA
    val liveMode = liveSupported && live
    var promotionOff by remember { mutableStateOf(liveSupported && !NotificationChannels.canPromote(ctx)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                blocked = blockedChannels(ctx)
                promotionOff = liveSupported && !NotificationChannels.canPromote(ctx)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var editingGoal by rememberSaveable { mutableStateOf(false) }
    var choosingProgress by rememberSaveable { mutableStateOf(false) }
    var soundAlerts by rememberSaveable { mutableStateOf(false) }
    val percentFormat = remember { NumberFormat.getPercentInstance() }

    val initial = remember {
        val stored = AppPreferences.encouragingThresholds.sorted().map { it.coerceIn(10, 95) }
        val two = stored.size >= 2
        val first = (stored.getOrNull(0) ?: 50).coerceIn(10, if (two) 90 else 95)
        val second = if (two) stored.getOrNull(1)!!.coerceIn(first + 5, 95) else first
        two to listOf(first, second)
    }
    var twoReminders by rememberSaveable { mutableStateOf(initial.first) }
    var low by rememberSaveable { mutableStateOf(initial.second[0] / 100f) }
    var high by rememberSaveable { mutableStateOf(initial.second[1] / 100f) }

    fun pushNotification(showProgress: Boolean, target: Int) {
        ctx.startService(Intent(ctx, MotionService::class.java).apply {
            action = "UPDATE_NOTIFICATION"
            putExtra("show_progressbar", showProgress)
            putExtra("daily_target", target)
        })
    }

    fun openChannel(channel: String) {
        NotificationChannels.ensureAll(ctx)
        NotificationChannels.open(ctx, channel)
    }

    fun setGoal(value: Int) {
        scope.launch {
            AppPreferences.dailyGoalTarget = value
            pushNotification(progress, value)
            WidgetManager.updateAllWidgets(ctx)
        }
    }

    fun commitReminders(lowValue: Float, highValue: Float, two: Boolean) {
        val values = if (two) {
            listOf(lowValue.toPercent(), highValue.toPercent())
        } else {
            listOf(lowValue.toPercent())
        }
        scope.launch {
            AppPreferences.encouragingThresholds = values.toSet()
            GoalNotificationWorker.invalidateEncouragingSettings()
        }
    }

    Column(Modifier.fillMaxSize().background(StepsyTheme.colors.background).statusBarsPadding()) {
        ScreenHeader(stringResource(R.string.daily_goals), onBack)

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            GoalHero(goal = goal, onEdit = { editingGoal = true }, onPick = ::setGoal)

            AnimatedVisibility(
                visible = goal > 0,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column {
                    SectionLabel(stringResource(R.string.section_goal_alerts))
                    SettingGroup {
                        SwitchSetting(
                            title = stringResource(R.string.daily_goal_notification),
                            summary = if (blocked[NotificationChannels.GOAL] == true) {
                                stringResource(R.string.channel_blocked)
                            } else {
                                null
                            },
                            icon = R.drawable.ic_speakerphone,
                            checked = notify,
                            onCheckedChange = { on ->
                                if (on && blocked[NotificationChannels.GOAL] == true) {
                                    openChannel(NotificationChannels.GOAL)
                                } else {
                                    scope.launch { AppPreferences.dailyGoalNotification = on }
                                }
                            },
                        )
                        SettingDivider()
                        SwitchSetting(
                            title = stringResource(R.string.encouraging_notifications),
                            summary = stringResource(
                                if (blocked[NotificationChannels.REMINDERS] == true) R.string.channel_blocked
                                else R.string.encouraging_notifications_summary,
                            ),
                            icon = R.drawable.ic_notify,
                            checked = encouraging,
                            onCheckedChange = { on ->
                                if (on && blocked[NotificationChannels.REMINDERS] == true) {
                                    openChannel(NotificationChannels.REMINDERS)
                                } else {
                                    scope.launch { AppPreferences.encouragingNotifications = on }
                                }
                            },
                        )
                        AnimatedVisibility(
                            visible = encouraging,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Column {
                                Column(
                                    Modifier.padding(start = 56.dp, end = 20.dp, top = 14.dp, bottom = 10.dp),
                                ) {
                                    PillSelector(
                                        options = listOf(
                                            1 to stringResource(R.string.reminders_one),
                                            2 to stringResource(R.string.reminders_two),
                                        ),
                                        selected = if (twoReminders) 2 else 1,
                                        onSelect = { count ->
                                            val toTwo = count == 2
                                            if (toTwo != twoReminders) {
                                                twoReminders = toTwo
                                                if (toTwo) {
                                                    low = 0.50f
                                                    high = 0.85f
                                                    commitReminders(low, high, true)
                                                } else {
                                                    low = 0.85f
                                                    high = 0.85f
                                                    commitReminders(low, high, false)
                                                }
                                            }
                                        },
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                    AnimatedContent(
                                        targetState = twoReminders,
                                        modifier = Modifier.padding(top = 12.dp),
                                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                                        label = "reminderCount",
                                    ) { two ->
                                        val track = MaterialTheme.colorScheme.onSurface
                                        if (two) {
                                            RangeSlider(
                                                value = low..high,
                                                onValueChange = { range ->
                                                    if (range.start != low) {
                                                        low = range.start.coerceAtMost(high - 0.05f)
                                                    } else {
                                                        high = range.endInclusive.coerceAtLeast(low + 0.05f)
                                                    }
                                                },
                                                onValueChangeFinished = {
                                                    commitReminders(low, high, true)
                                                },
                                                valueRange = 0.10f..0.95f,
                                                steps = 16,
                                                colors = SliderDefaults.colors(
                                                    thumbColor = track,
                                                    activeTrackColor = track.copy(alpha = 0.30f),
                                                    inactiveTrackColor = track.copy(alpha = 0.10f),
                                                    activeTickColor = track.copy(alpha = 0.30f),
                                                    inactiveTickColor = track.copy(alpha = 0.10f),
                                                ),
                                            )
                                        } else {
                                            Slider(
                                                value = low,
                                                onValueChange = { low = it },
                                                onValueChangeFinished = {
                                                    commitReminders(low, high, false)
                                                },
                                                valueRange = 0.10f..0.95f,
                                                steps = 16,
                                                colors = SliderDefaults.colors(
                                                    thumbColor = track,
                                                    activeTrackColor = track,
                                                    inactiveTrackColor = track.copy(alpha = 0.12f),
                                                    activeTickColor = track.copy(alpha = 0.30f),
                                                    inactiveTickColor = track.copy(alpha = 0.12f),
                                                ),
                                            )
                                        }
                                    }
                                    Text(
                                        if (twoReminders) {
                                            stringResource(
                                                R.string.reminder_double_summary,
                                                percentFormat.format(low.toDouble()),
                                                percentFormat.format(high.toDouble()),
                                            )
                                        } else {
                                            stringResource(
                                                R.string.reminder_single_summary,
                                                percentFormat.format(low.toDouble()),
                                            )
                                        },
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        color = StepsyTheme.colors.accent.copy(alpha = 0.7f),
                                        textAlign = TextAlign.Center,
                                        style = TextStyle(
                                            lineBreak = LineBreak(
                                                strictness = LineBreak.Strictness.Default,
                                                wordBreak = LineBreak.WordBreak.Default,
                                                strategy = LineBreak.Strategy.Balanced,
                                            ),
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                    )
                                }
                                SettingDivider()
                                SettingRow(
                                    title = stringResource(R.string.encouraging_sample),
                                    icon = R.drawable.ic_notifications_active,
                                    onClick = {
                                        if (blocked[NotificationChannels.REMINDERS] == true) {
                                            openChannel(NotificationChannels.REMINDERS)
                                        } else {
                                            GoalNotificationWorker.showEncouragingNotification(
                                                ctx.applicationContext, goal, steps, demo = true,
                                            )
                                        }
                                    },
                                    trailing = {},
                                )
                            }
                        }
                    }
                }
            }

            SectionLabel(stringResource(R.string.section_notifications))
            SettingGroup {
                SettingRow(
                    title = stringResource(R.string.step_notification_title),
                    summary = stringResource(
                        when {
                            liveOff -> R.string.live_notification_off
                            liveMode && promotionOff -> R.string.channel_blocked
                            liveMode && blocked[MotionService.LIVE_UPDATE_CHANNEL_ID] == true -> R.string.channel_blocked
                            liveMode -> R.string.live_walk_description
                            progress -> R.string.live_steps_progress
                            else -> R.string.live_steps_only
                        },
                    ),
                    icon = R.drawable.ic_progress,
                    onClick = { choosingProgress = true },
                )
                SettingDivider()
                SettingRow(
                    title = stringResource(R.string.notification_settings_row),
                    summary = stringResource(R.string.notification_settings_summary),
                    icon = R.drawable.ic_settings,
                    onClick = { soundAlerts = true },
                )
            }

            if (soundAlerts) {
                StepsyDialog(
                    title = stringResource(R.string.notification_settings_row),
                    onDismiss = { soundAlerts = false },
                    confirmText = null,
                    dismissText = null,
                ) {
                    SettingGroup {
                        val channels = buildList {
                            add(NotificationChannels.REMINDERS to R.string.channel_reminders_name)
                            add(MotionService.STEP_CHANNEL_ID to R.string.notification_category_steps_day)
                            add(NotificationChannels.GOAL to R.string.records_goal_reached)
                            if (liveMode) {
                                add(NotificationChannels.LIVE_UPDATE to R.string.notification_category_live_update)
                            }
                        }
                        channels.forEachIndexed { index, (channel, name) ->
                            if (index > 0) SettingDivider()
                            SettingRow(
                                title = stringResource(name),
                                summary = if (blocked[channel] == true) {
                                    stringResource(R.string.channel_blocked)
                                } else {
                                    null
                                },
                                onClick = { openChannel(channel) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }

    if (editingGoal) {
        GoalDialog(
            initial = goal,
            onSave = { value ->
                editingGoal = false
                setGoal(value)
            },
            onDismiss = { editingGoal = false },
        )
    }

    if (choosingProgress) {
        val off = stringResource(R.string.live_notification_off)
        val stepsOnly = stringResource(R.string.live_steps_only)
        val withProgress = stringResource(R.string.live_steps_progress)
        val liveLabel = stringResource(R.string.live_notification_title)
        ChoiceDialog(
            title = stringResource(R.string.step_notification_title),
            options = buildList {
                add(0 to off)
                add(1 to stepsOnly)
                add(2 to withProgress)
                if (liveSupported) add(3 to liveLabel)
            },
            selected = when {
                liveOff -> 0
                liveMode -> 3
                progress -> 2
                else -> 1
            },
            onSelect = { choice ->
                choosingProgress = false
                when (choice) {
                    0 -> openChannel(MotionService.STEP_CHANNEL_ID)
                    1, 2 -> {
                        val showProgress = choice == 2
                        scope.launch {
                            AppPreferences.liveNotification = false
                            AppPreferences.dailyGoalNotificationProgressbar = showProgress
                            pushNotification(showProgress, AppPreferences.dailyGoalTarget)
                        }
                        if (liveOff) openChannel(MotionService.STEP_CHANNEL_ID)
                    }
                    3 -> {
                        scope.launch {
                            AppPreferences.liveNotification = true
                            AppPreferences.dailyGoalNotificationProgressbar = false
                            pushNotification(false, AppPreferences.dailyGoalTarget)
                        }
                        when {
                            liveOff -> openChannel(MotionService.STEP_CHANNEL_ID)
                            blocked[MotionService.LIVE_UPDATE_CHANNEL_ID] == true ->
                                openChannel(MotionService.LIVE_UPDATE_CHANNEL_ID)
                            promotionOff -> NotificationChannels.openPromotion(ctx)
                        }
                    }
                }
            },
            onDismiss = { choosingProgress = false },
        )
    }
}

@Composable
private fun <T> prefsState(flow: () -> Flow<T>, initial: () -> T): State<T> {
    val remembered = remember { flow() }
    return remembered.collectAsStateWithLifecycle(initialValue = remember { initial() })
}

private fun blockedChannels(ctx: Context): Map<String, Boolean> =
    listOf(
        NotificationChannels.GOAL,
        NotificationChannels.REMINDERS,
        MotionService.STEP_CHANNEL_ID,
        MotionService.LIVE_UPDATE_CHANNEL_ID,
    ).associateWith { NotificationChannels.isBlocked(ctx, it) }

private fun Float.toPercent(): Int = (this * 100).roundToInt()

@Composable
private fun GoalHero(goal: Int, onEdit: () -> Unit, onPick: (Int) -> Unit) {
    val ctx = LocalContext.current
    val off = stringResource(R.string.goal_preset_off)
    val custom = stringResource(R.string.goal_preset_custom)
    val presets = Util.GOAL_PRESETS.zip(
        listOf(off) + Util.GOAL_PRESETS.drop(1).map { Util.formatShortSteps(it.toLong(), ctx) }
    )
    val isPreset = presets.any { it.first == goal }
    val selected = if (isPreset) goal else -1
    val options = presets + (-1 to custom)

    Panel(
        Modifier.clip(RoundedCornerShape(24.dp)).clickable(
            onClickLabel = stringResource(R.string.daily_goal_target),
            role = Role.Button,
            onClick = onEdit,
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            var heroShownGoal by remember { mutableIntStateOf(goal) }
            if (goal > 0) heroShownGoal = goal
            AnimatedContent(
                targetState = goal > 0,
                transitionSpec = {
                    (fadeIn() + expandVertically()) togetherWith (fadeOut() + shrinkVertically())
                },
                label = "goalState",
            ) { hasGoal ->
                Column(
                    Modifier.semantics(mergeDescendants = true) {},
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (hasGoal) {
                        CountText(
                            heroShownGoal, Util::formatSteps,
                            TextStyle(
                                fontSize = 48.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold,
                                fontFamily = Chivo,
                                fontFeatureSettings = "tnum",
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                        Text(
                            pluralStringResource(R.plurals.steps_word, heroShownGoal).uppercase(),
                            fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold,
                            color = StepsyTheme.colors.accent, modifier = Modifier.offset(y = (-2).dp),
                        )
                    } else {
                        Text(
                            stringResource(R.string.daily_goal_none),
                            fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = Chivo,
                        )
                    }
                }
            }
            PillSelector(
                options = options,
                selected = selected,
                onSelect = { value -> if (value < 0) onEdit() else onPick(value) },
                chipPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun GoalDialog(initial: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var value by remember {
        val start = if (initial > 0) initial.toString() else ""
        mutableStateOf(TextFieldValue(start, TextRange(start.length)))
    }
    val parsed = value.text.filter(Char::isDigit).take(6).toIntOrNull()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    StepsyDialog(
        title = stringResource(R.string.daily_goal_target),
        onDismiss = onDismiss,
        confirmText = stringResource(android.R.string.ok),
        onConfirm = { parsed?.let(onSave) },
        confirmEnabled = parsed != null,
    ) {
        TextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors(),
            placeholder = { Text(stringResource(R.string.daily_goal_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
    }
}
