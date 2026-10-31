package com.nvllz.stepsy.ui.onboarding

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
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
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nvllz.stepsy.R
import com.nvllz.stepsy.ui.BodyHeightImperialDialog
import com.nvllz.stepsy.ui.bodyHeightText
import com.nvllz.stepsy.ui.bodyStepLengthText
import com.nvllz.stepsy.ui.bodyWeightDialogInitial
import com.nvllz.stepsy.ui.bodyWeightText
import com.nvllz.stepsy.ui.bodyWeightToKg
import com.nvllz.stepsy.ui.components.NumberFieldDialog
import com.nvllz.stepsy.ui.components.PillSelector
import com.nvllz.stepsy.ui.components.PrimaryButton
import com.nvllz.stepsy.ui.components.SecondaryButton
import com.nvllz.stepsy.ui.components.SettingDivider
import com.nvllz.stepsy.ui.components.SettingGroup
import com.nvllz.stepsy.ui.components.SettingRow
import com.nvllz.stepsy.ui.components.StepsyDialog
import com.nvllz.stepsy.ui.components.fieldColors
import com.nvllz.stepsy.ui.theme.Chivo
import com.nvllz.stepsy.ui.theme.StepsyTheme
import com.nvllz.stepsy.util.AppPreferences
import com.nvllz.stepsy.util.TrackingAccess.Ask
import com.nvllz.stepsy.util.Util
import com.nvllz.stepsy.util.Util.UnitSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.text.NumberFormat

@Composable
internal fun PageScaffold(
    body: @Composable ColumnScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            content = body,
        )
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = actions,
        )
    }
}

@Composable
internal fun PageText(label: String, title: String, body: String) {
    Text(
        label.uppercase(),
        fontSize = 13.sp, letterSpacing = 2.sp,
        fontWeight = FontWeight.SemiBold, color = StepsyTheme.colors.accent,
        modifier = Modifier.padding(top = 16.dp),
    )
    Text(
        title,
        fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 6.dp),
    )
    Text(
        body,
        fontSize = 15.sp, lineHeight = 21.sp,
        fontWeight = FontWeight.Light,
        modifier = Modifier.padding(top = 10.dp, bottom = 16.dp),
    )
}

@Composable
internal fun PageNote(text: String, center: Boolean = false) {
    Text(
        text,
        fontSize = 13.sp, lineHeight = 18.sp, color = StepsyTheme.colors.accent,
        textAlign = if (center) TextAlign.Center else null,
        style = TextStyle(
            lineBreak = LineBreak(
                strictness = LineBreak.Strictness.Default,
                wordBreak = LineBreak.WordBreak.Default,
                strategy = LineBreak.Strategy.Balanced,
            ),
        ),
        modifier = Modifier
            .then(if (center) Modifier.fillMaxWidth() else Modifier)
            .padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 8.dp),
    )
}

@Composable
internal fun FoldSwap(state: Int, content: @Composable ColumnScope.(Int) -> Unit) {
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            (fadeIn(tween(200)) + expandVertically(tween(280, easing = FastOutSlowInEasing)))
                .togetherWith(
                    fadeOut(tween(120)) + shrinkVertically(tween(280, easing = FastOutSlowInEasing)),
                )
        },
        label = "foldSwap",
    ) { s ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content(s)
        }
    }
}

@Composable
internal fun StatusMark(on: Boolean) {
    val ink = MaterialTheme.colorScheme.onSurface
    val description = stringResource(if (on) R.string.onboarding_status_on else R.string.onboarding_status_off)
    val fill by animateColorAsState(
        if (on) ink else ink.copy(alpha = 0.08f),
        tween(250),
        label = "statusFill",
    )
    val check by animateFloatAsState(if (on) 1f else 0f, tween(250), label = "statusCheck")
    Box(
        Modifier.size(28.dp).clip(CircleShape)
            .background(fill)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_check), null,
            tint = StepsyTheme.colors.background,
            modifier = Modifier.size(16.dp).graphicsLayer { alpha = check },
        )
    }
}

@Composable
private fun <T> prefsState(flow: () -> Flow<T>, initial: () -> T): State<T> {
    val remembered = remember { flow() }
    return remembered.collectAsStateWithLifecycle(initialValue = remember { initial() })
}

private const val RESTORE_BUSY = 4

@Composable
internal fun WelcomePage(
    hasSensor: Boolean,
    restoreState: Int,
    restoredRows: Int,
    busy: Boolean,
    onRestore: () -> Unit,
    onNext: () -> Unit,
) {
    val ctx = LocalContext.current
    val compact = LocalConfiguration.current.screenHeightDp < 600
    val restoreCode = if (busy) RESTORE_BUSY else restoreState

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            WelcomeHeadline(compact)
            Spacer(Modifier.weight(1f))
            FeatureBullets(compact)
            Spacer(Modifier.weight(1f))
        }
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!hasSensor) PageNote(stringResource(R.string.onboarding_no_sensor))

            FoldSwap(restoreCode) { s ->
                val summary = when (s) {
                    RESTORE_BUSY -> stringResource(R.string.onboarding_restore_busy)
                    RESTORE_DONE -> ctx.resources.getQuantityString(
                        R.plurals.onboarding_restore_result, restoredRows, restoredRows,
                    )
                    RESTORE_INVALID -> stringResource(R.string.import_invalid_file)
                    RESTORE_FAILED -> stringResource(R.string.cannot_open_file)
                    else -> stringResource(R.string.onboarding_restore_summary)
                }
                SettingGroup {
                    if (s == RESTORE_DONE) {
                        SettingRow(
                            title = stringResource(R.string.onboarding_restore_done_title),
                            summary = summary,
                            icon = R.drawable.ic_import,
                            trailing = { StatusMark(true) },
                        )
                    } else {
                        SettingRow(
                            title = stringResource(R.string.onboarding_restore_title),
                            summary = summary,
                            icon = R.drawable.ic_import,
                            enabled = s != RESTORE_BUSY,
                            onClick = onRestore,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                stringResource(R.string.onboarding_get_started), onNext,
                Modifier.fillMaxWidth(), enabled = !busy,
            )
        }
    }
}

@Composable
private fun WelcomeHeadline(compact: Boolean) {
    val nameSize = if (compact) 32.sp else 40.sp
    val nameLine = if (compact) 38.sp else 46.sp
    val subSize = if (compact) 19.sp else 24.sp
    val subLine = if (compact) 24.sp else 30.sp
    Column(
        Modifier.fillMaxWidth().padding(top = if (compact) 12.dp else 20.dp)
            .semantics(mergeDescendants = true) { heading() },
    ) {
        Text(
            stringResource(R.string.app_name),
            style = TextStyle(
                fontFamily = Chivo, fontWeight = FontWeight.Bold, fontSize = nameSize,
                lineHeight = nameLine,
                color = MaterialTheme.colorScheme.onSurface,
            ),
        )
        Text(
            stringResource(R.string.onboarding_welcome_headline_b),
            style = TextStyle(
                fontFamily = Chivo, fontWeight = FontWeight.SemiBold, fontSize = subSize,
                lineHeight = subLine,
                color = StepsyTheme.colors.accent,
            ),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun FeatureBullets(compact: Boolean) {
    val gap = if (compact) 8.dp else 12.dp
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            FeatureBullet(
                R.drawable.ic_offline,
                stringResource(R.string.onboarding_welcome_point_offline),
                compact, Modifier.weight(1f).fillMaxHeight(),
            )
            FeatureBullet(
                R.drawable.ic_battery,
                stringResource(R.string.onboarding_welcome_point_battery),
                compact, Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            FeatureBullet(
                R.drawable.ic_auto_backups,
                stringResource(R.string.onboarding_welcome_point_backups),
                compact, Modifier.weight(1f).fillMaxHeight(),
            )
            FeatureBullet(
                R.drawable.ic_timeline,
                stringResource(R.string.onboarding_welcome_point_pressure),
                compact, Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun FeatureBullet(
    icon: Int,
    label: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(StepsyTheme.colors.accent.copy(alpha = 0.07f))
            .semantics(mergeDescendants = true) {}
            .padding(if (compact) 12.dp else 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.onSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(icon), null,
                tint = StepsyTheme.colors.background,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            label,
            minLines = 2,
            style = TextStyle(
                fontFamily = Chivo, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                lineHeight = 20.sp,
                lineBreak = LineBreak(
                    strictness = LineBreak.Strictness.Default,
                    wordBreak = LineBreak.WordBreak.Default,
                    strategy = LineBreak.Strategy.Balanced,
                ),
            ),
            modifier = Modifier.padding(top = if (compact) 16.dp else 24.dp),
        )
    }
}

@Composable
internal fun StepsPage(
    steps: Int,
    granted: Boolean,
    ask: Int,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onNext: () -> Unit,
) {
    val compact = LocalConfiguration.current.screenHeightDp < 600
    val blocked = ask == Ask.BLOCKED.ordinal
    val denied = ask == Ask.DENIED.ordinal
    PageScaffold(
        body = {
            PageText(
                stringResource(R.string.onboarding_steps_label),
                stringResource(R.string.onboarding_steps_title),
                stringResource(R.string.onboarding_steps_body),
            )
            Spacer(Modifier.height(8.dp))
            SettingGroup {
                SettingRow(
                    title = stringResource(R.string.onboarding_steps_row),
                    summary = stringResource(
                        when {
                            granted -> R.string.onboarding_steps_on
                            blocked -> R.string.onboarding_steps_blocked
                            denied -> R.string.onboarding_steps_denied
                            else -> R.string.onboarding_steps_idle
                        },
                    ),
                    icon = R.drawable.ic_activity,
                    onClick = when {
                        granted -> null
                        blocked -> onOpenSettings
                        else -> onAllow
                    },
                    trailing = { StatusMark(granted) },
                )
            }
            Spacer(Modifier.height(32.dp))
            AnimatedVisibility(
                visible = granted,
                enter = fadeIn(tween(200)) + expandVertically(tween(280, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(280, easing = FastOutSlowInEasing)),
            ) {
                OnboardingHero(steps, compact)
            }
            FoldSwap(
                when {
                    granted -> 1
                    blocked -> 2
                    denied -> 3
                    else -> 0
                },
            ) { s ->
                when (s) {
                    1 -> PageNote(stringResource(R.string.onboarding_steps_note_on), center = true)
                    2 -> PageNote(stringResource(R.string.onboarding_steps_note_blocked), center = true)
                    3 -> PageNote(stringResource(R.string.onboarding_steps_note_denied), center = true)
                }
            }
        },
        actions = {
            FoldSwap(
                when {
                    granted -> 0
                    blocked -> 1
                    denied -> 2
                    else -> 3
                },
            ) { s ->
                when (s) {
                    0 -> PrimaryButton(stringResource(R.string.onboarding_continue), onNext, Modifier.fillMaxWidth())
                    1 -> {
                        PrimaryButton(stringResource(R.string.onboarding_continue), onNext, Modifier.fillMaxWidth())
                        SecondaryButton(stringResource(R.string.onboarding_open_settings), onOpenSettings, Modifier.fillMaxWidth())
                    }
                    2 -> {
                        PrimaryButton(stringResource(R.string.onboarding_continue), onNext, Modifier.fillMaxWidth())
                        SecondaryButton(stringResource(R.string.onboarding_ask_again), onAllow, Modifier.fillMaxWidth())
                    }
                    else -> {
                        PrimaryButton(stringResource(R.string.onboarding_allow), onAllow, Modifier.fillMaxWidth())
                        SecondaryButton(stringResource(R.string.onboarding_not_now), onNext, Modifier.fillMaxWidth())
                    }
                }
            }
        },
    )
}

@Composable
internal fun PocketPage(
    exempt: Boolean,
    tried: Boolean,
    route: Int,
    onAllow: () -> Unit,
    onNext: () -> Unit,
) {
    PageScaffold(
        body = {
            PageText(
                stringResource(R.string.onboarding_pocket_label),
                stringResource(R.string.onboarding_pocket_title),
                stringResource(R.string.onboarding_pocket_body),
            )
            Spacer(Modifier.height(8.dp))
            SettingGroup {
                SettingRow(
                    title = stringResource(R.string.onboarding_pocket_row),
                    summary = stringResource(
                        if (exempt) R.string.onboarding_pocket_on else R.string.onboarding_pocket_off,
                    ),
                    icon = R.drawable.ic_battery,
                    onClick = if (exempt) null else onAllow,
                    trailing = { StatusMark(exempt) },
                )
            }
            Spacer(Modifier.height(24.dp))
            FoldSwap(
                when {
                    exempt -> 1
                    tried && route < 0 -> 2
                    tried && route > 0 -> 3
                    tried -> 4
                    else -> 0
                },
            ) { s ->
                when (s) {
                    1 -> PageNote(stringResource(R.string.onboarding_pocket_note_on), center = true)
                    2 -> PageNote(stringResource(R.string.onboarding_pocket_note_none), center = true)
                    3 -> PageNote(stringResource(R.string.onboarding_pocket_note_manual), center = true)
                    4 -> PageNote(stringResource(R.string.onboarding_pocket_note_tried), center = true)
                }
            }
        },
        actions = {
            FoldSwap(
                when {
                    exempt -> 0
                    tried -> 1
                    else -> 2
                },
            ) { s ->
                when (s) {
                    0 -> PrimaryButton(stringResource(R.string.onboarding_continue), onNext, Modifier.fillMaxWidth())
                    1 -> {
                        PrimaryButton(stringResource(R.string.onboarding_continue), onNext, Modifier.fillMaxWidth())
                        SecondaryButton(stringResource(R.string.onboarding_ask_again), onAllow, Modifier.fillMaxWidth())
                    }
                    else -> {
                        PrimaryButton(stringResource(R.string.onboarding_allow), onAllow, Modifier.fillMaxWidth())
                        SecondaryButton(stringResource(R.string.onboarding_not_now), onNext, Modifier.fillMaxWidth())
                    }
                }
            }
        },
    )
}

@Composable
internal fun BodyPage(onNext: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val height by prefsState({ AppPreferences.heightFlow() }, { AppPreferences.height })
    val weight by prefsState({ AppPreferences.weightFlow() }, { AppPreferences.weight })
    val system by prefsState({ AppPreferences.unitSystemFlow() }, { AppPreferences.unitSystem })
    val imperial = system == UnitSystem.IMPERIAL
    var dialog by rememberSaveable { mutableStateOf(0) }

    val heightUnit = if (imperial) "${stringResource(R.string.unit_ft)}/${stringResource(R.string.unit_in)}"
    else stringResource(R.string.unit_cm)
    val stepLengthUnit = if (imperial) stringResource(R.string.unit_in) else stringResource(R.string.unit_cm)
    val weightUnit = if (imperial) stringResource(R.string.unit_lbs) else stringResource(R.string.unit_kg)
    val unitOptions = remember(ctx) {
        ctx.resources.getStringArray(R.array.unit_system_values)
            .zip(ctx.resources.getStringArray(R.array.unit_system_entries)) { value, label -> value to label }
    }

    PageScaffold(
        body = {
            PageText(
                stringResource(R.string.onboarding_body_label),
                stringResource(R.string.onboarding_body_title),
                stringResource(R.string.onboarding_body_body),
            )
            Spacer(Modifier.height(8.dp))
            PillSelector(
                options = unitOptions,
                selected = if (imperial) "imperial" else "metric",
                onSelect = { chosen ->
                    scope.launch {
                        AppPreferences.dataStore.edit { prefs ->
                            prefs[AppPreferences.PreferenceKeys.UNIT_SYSTEM] = chosen
                        }
                    }
                },
                modifier = Modifier.padding(bottom = 12.dp),
            )
            SettingGroup {
                SettingRow(
                    title = stringResource(R.string.pref_height),
                    summary = bodyHeightText(height, imperial, heightUnit),
                    icon = R.drawable.ic_height,
                    onClick = { dialog = 1 },
                )
                SettingDivider()
                SettingRow(
                    title = stringResource(R.string.pref_weight),
                    summary = bodyWeightText(weight, imperial, weightUnit),
                    icon = R.drawable.ic_weight,
                    onClick = { dialog = 2 },
                )
                SettingDivider()
                SettingRow(
                    title = stringResource(R.string.pref_step_length),
                    summary = "~" + bodyStepLengthText(Util.estimatedStepLengthCm(height), imperial, stepLengthUnit),
                    icon = R.drawable.ic_step_length,
                )
            }
            PageNote(stringResource(R.string.onboarding_body_note), center = true)
        },
        actions = {
            PrimaryButton(stringResource(R.string.onboarding_continue), onNext, Modifier.fillMaxWidth())
        },
    )

    when (dialog) {
        1 -> if (imperial) {
            BodyHeightImperialDialog(
                initialCm = height,
                onConfirm = { cm ->
                    dialog = 0
                    scope.launch {
                        AppPreferences.dataStore.edit { prefs ->
                            prefs[AppPreferences.PreferenceKeys.HEIGHT] = cm.toString()
                        }
                    }
                },
                onDismiss = { dialog = 0 },
            )
        } else {
            NumberFieldDialog(
                title = stringResource(R.string.pref_height),
                initial = height.toString(),
                suffix = heightUnit,
                isValid = { (it.toIntOrNull() ?: 0) in 1..250 },
                onConfirm = { input ->
                    dialog = 0
                    scope.launch {
                        AppPreferences.dataStore.edit { prefs ->
                            prefs[AppPreferences.PreferenceKeys.HEIGHT] = input.toInt().toString()
                        }
                    }
                },
                onDismiss = { dialog = 0 },
            )
        }
        2 -> NumberFieldDialog(
            title = stringResource(R.string.pref_weight),
            initial = bodyWeightDialogInitial(weight, imperial),
            suffix = weightUnit,
            isValid = {
                val v = it.toIntOrNull() ?: return@NumberFieldDialog false
                if (imperial) v in 1..1100 else v in 1..500
            },
            onConfirm = { input ->
                dialog = 0
                val kg = bodyWeightToKg(input, imperial)
                scope.launch {
                    AppPreferences.dataStore.edit { prefs ->
                        prefs[AppPreferences.PreferenceKeys.WEIGHT] = kg.toString()
                    }
                }
            },
            onDismiss = { dialog = 0 },
        )
    }
}

@Composable
internal fun GoalPage(
    notificationsOn: Boolean,
    notificationsBlocked: Boolean,
    onAllowNotifications: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val goal by prefsState({ AppPreferences.dailyGoalTargetFlow() }, { AppPreferences.dailyGoalTarget })
    val stepLength by prefsState({ AppPreferences.stepLengthFlow() }, { AppPreferences.stepLength })
    val weight by prefsState({ AppPreferences.weightFlow() }, { AppPreferences.weight })
    val system by prefsState({ AppPreferences.unitSystemFlow() }, { AppPreferences.unitSystem })
    var customOpen by rememberSaveable { mutableStateOf(false) }

    val ctx = LocalContext.current
    val off = stringResource(R.string.goal_preset_off)
    val custom = stringResource(R.string.goal_preset_custom)
    val presets = Util.GOAL_PRESETS.zip(
        listOf(off) + Util.GOAL_PRESETS.drop(1).map { Util.formatShortSteps(it.toLong(), ctx) }
    )
    val isPreset = presets.any { it.first == goal }
    val selected = if (isPreset) goal else -1
    val options = presets + (-1 to custom)

    val preview = remember(goal, stepLength, weight, system) {
        val km = Util.distanceFor(goal, stepLength, system)
        NumberFormat.getNumberInstance().apply {
            maximumFractionDigits = 1
            minimumFractionDigits = 1
        }.format(km.toDouble()) to Util.caloriesFor(goal, weight)
    }
    val distanceUnit = stringResource(if (system == UnitSystem.IMPERIAL) R.string.unit_mi else R.string.unit_km)

    fun writeGoal(value: Int) {
        scope.launch {
            AppPreferences.dataStore.edit { prefs ->
                prefs[AppPreferences.PreferenceKeys.DAILY_GOAL_TARGET] = value
            }
        }
    }

    PageScaffold(
        body = {
            PageText(
                stringResource(R.string.onboarding_goal_label),
                stringResource(R.string.onboarding_goal_title),
                stringResource(R.string.onboarding_goal_body),
            )
            Spacer(Modifier.height(8.dp))
            PillSelector(
                options = options,
                selected = selected,
                onSelect = { chosen -> if (chosen < 0) customOpen = true else writeGoal(chosen) },
            )
            if (goal > 0) {
                PageNote(
                    stringResource(R.string.onboarding_goal_preview, preview.first, distanceUnit, preview.second),
                    center = true,
                )
            } else {
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(16.dp))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                SettingGroup {
                    SettingRow(
                        title = stringResource(R.string.onboarding_reminders_title),
                        summary = stringResource(
                            when {
                                notificationsOn -> R.string.onboarding_reminders_on
                                notificationsBlocked -> R.string.onboarding_reminders_blocked
                                else -> R.string.onboarding_reminders_summary
                            },
                        ),
                        icon = R.drawable.ic_notifications_active,
                        onClick = when {
                            notificationsBlocked -> onOpenNotificationSettings
                            notificationsOn -> null
                            else -> onAllowNotifications
                        },
                        trailing = { StatusMark(notificationsOn) },
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        },
        actions = {
            PrimaryButton(stringResource(R.string.onboarding_done), onDone, Modifier.fillMaxWidth())
        },
    )

    if (customOpen) {
        OnboardingGoalDialog(
            initial = goal,
            onSave = { value ->
                customOpen = false
                writeGoal(value)
            },
            onDismiss = { customOpen = false },
        )
    }
}

@Composable
private fun OnboardingGoalDialog(initial: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
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
