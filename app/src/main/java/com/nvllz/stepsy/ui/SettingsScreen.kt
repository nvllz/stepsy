package com.nvllz.stepsy.ui

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.nvllz.stepsy.BuildConfig
import com.nvllz.stepsy.R
import com.nvllz.stepsy.service.MotionService
import com.nvllz.stepsy.service.isPlayServicesAvailable
import com.nvllz.stepsy.ui.components.ChoiceDialog
import com.nvllz.stepsy.ui.components.LocalToast
import com.nvllz.stepsy.ui.components.NumberFieldDialog
import com.nvllz.stepsy.ui.components.PillSelector
import com.nvllz.stepsy.ui.components.ScreenHeader
import com.nvllz.stepsy.ui.components.SectionLabel
import com.nvllz.stepsy.ui.components.SettingDivider
import com.nvllz.stepsy.ui.components.SettingGroup
import com.nvllz.stepsy.ui.components.SettingRow
import com.nvllz.stepsy.ui.components.StepsyDialog
import com.nvllz.stepsy.ui.components.SwitchSetting
import com.nvllz.stepsy.ui.components.ToastKind
import com.nvllz.stepsy.ui.components.ToastState
import com.nvllz.stepsy.ui.theme.StepsyTheme
import com.nvllz.stepsy.util.AppPreferences
import com.nvllz.stepsy.util.DebugTools
import com.nvllz.stepsy.util.MockData
import com.nvllz.stepsy.util.MockEffort
import com.nvllz.stepsy.util.MockFocus
import com.nvllz.stepsy.util.Util
import com.nvllz.stepsy.util.Util.UnitSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val storedTheme by prefsState({ AppPreferences.themeFlow() }, { AppPreferences.theme })
    val storedHeight by prefsState({ AppPreferences.heightFlow() }, { AppPreferences.height })
    val storedWeight by prefsState({ AppPreferences.weightFlow() }, { AppPreferences.weight })
    val storedStepLength by prefsState({ AppPreferences.stepLengthFlow() }, { AppPreferences.stepLength })
    val storedUnitSystem by prefsState({ AppPreferences.unitSystemFlow() }, { AppPreferences.unitSystem })
    val storedDateFormat by prefsState({ AppPreferences.dateFormatStringFlow() }, { AppPreferences.dateFormatString })
    val storedFirstDay by prefsState({ AppPreferences.firstDayOfWeekFlow() }, { AppPreferences.firstDayOfWeek })
    val storedDayStartHour by prefsState({ AppPreferences.dayStartHourFlow() }, { AppPreferences.dayStartHour })
    val storedVehicleFilter by prefsState({ AppPreferences.vehicleFilterEnabledFlow() }, { AppPreferences.vehicleFilterEnabled })
    val storedHourlyBreakdown by prefsState({ AppPreferences.hourlyBreakdownFlow() }, { AppPreferences.hourlyBreakdown })

    val imperial = storedUnitSystem == UnitSystem.IMPERIAL
    val fullBuild = BuildConfig.HAS_PROPRIETARY_LIBRARIES
    val hasPlayServices = fullBuild && isPlayServicesAvailable(ctx)
    val languageValue = remember {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) "system" else locales[0]?.language ?: "system"
    }

    val heightUnit = if (imperial) "${stringResource(R.string.unit_ft)}/${stringResource(R.string.unit_in)}"
    else stringResource(R.string.unit_cm)
    val stepLengthUnit = if (imperial) stringResource(R.string.unit_in) else stringResource(R.string.unit_cm)
    val weightUnit = if (imperial) stringResource(R.string.unit_lbs) else stringResource(R.string.unit_kg)

    val heightSummary = heightDisplay(storedHeight, imperial, heightUnit)
    val estimatedStepLength = Util.estimatedStepLengthCm(storedHeight)
    val stepLengthDefault = abs(storedStepLength - estimatedStepLength) < 0.01f
    val stepLengthSummary = (if (stepLengthDefault) "~" else "") +
            stepLengthDisplay(
                if (stepLengthDefault) estimatedStepLength else storedStepLength,
                imperial, stepLengthUnit,
            )
    val weightSummary = weightDisplay(storedWeight, imperial, weightUnit)

    val unitValue = if (imperial) "imperial" else "metric"
    val unitSummary = labelFromEntries(ctx, R.array.unit_system_entries, R.array.unit_system_values, unitValue)
    val firstDaySummary = labelFromEntries(
        ctx, R.array.weekdays, R.array.weekdays_values, storedFirstDay.toString(),
    )
    val languageSummary = labelFromEntries(ctx, R.array.language_names, R.array.language_values, languageValue)
    val themeSummary = labelFromEntries(ctx, R.array.theme_entries, R.array.theme_values, storedTheme)
    val aboutSummary = "${stringResource(R.string.about_version)} ${BuildConfig.VERSION_NAME} · GPL-3.0"

    val themeOptions = remember(ctx) { entriesZip(ctx, R.array.theme_entries, R.array.theme_values) }
    val unitSystemOptions = remember(ctx) { entriesZip(ctx, R.array.unit_system_entries, R.array.unit_system_values) }
    val dateFormatOptions = remember(ctx) { entriesZip(ctx, R.array.date_format_options, R.array.date_format_values) }
    val weekdayOptions = remember(ctx) {
        entriesZip(ctx, R.array.weekdays, R.array.weekdays_values)
            .map { (value, label) -> value.toInt() to label }
    }
    val languageOptions = remember(ctx) { entriesZip(ctx, R.array.language_names, R.array.language_values) }

    val is24 = android.text.format.DateFormat.is24HourFormat(ctx)
    val dayStartTimeFormat = remember(ctx, is24) {
        DateTimeFormatter.ofPattern(
            if (is24) "HH:mm" else "h:mm a",
            Locale.getDefault(),
        )
    }
    val dayStartSummary = remember(storedDayStartHour, dayStartTimeFormat) { dayStartTimeFormat.format(LocalTime.of(storedDayStartHour, 0)) }
    val dayStartOptions = remember(dayStartTimeFormat) {
        Util.DAY_START_PRESETS.map { hour -> hour to dayStartTimeFormat.format(LocalTime.of(hour, 0)) }
    }

    var activeDialog by remember { mutableStateOf<SettingsDialogState?>(null) }
    val toast = LocalToast.current

    val pushHourlyService: (Boolean) -> Unit = { clear ->
        ctx.startService(Intent(ctx, MotionService::class.java).apply {
            action = "UPDATE_NOTIFICATION"
            if (clear) putExtra("clear_hourly", true)
        })
    }

    Column(
        Modifier.fillMaxSize().background(StepsyTheme.colors.background).statusBarsPadding(),
    ) {
        ScreenHeader(stringResource(R.string.settings), onBack)

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            SetupGapsCard()

            SectionLabel(stringResource(R.string.header_personal_data))

            SettingGroup {
                SettingRow(
                    title = stringResource(R.string.pref_height),
                    summary = heightSummary,
                    icon = R.drawable.ic_height,
                    onClick = {
                        activeDialog = if (imperial) SettingsDialogState.HeightImperial
                        else SettingsDialogState.HeightMetric
                    },
                )

                SettingDivider()

                SettingRow(
                    title = stringResource(R.string.pref_step_length),
                    summary = stepLengthSummary,
                    icon = R.drawable.ic_step_length,
                    onClick = { activeDialog = SettingsDialogState.StepLength },
                )

                SettingDivider()

                SettingRow(
                    title = stringResource(R.string.pref_weight),
                    summary = weightSummary,
                    icon = R.drawable.ic_weight,
                    onClick = { activeDialog = SettingsDialogState.Weight },
                )
            }

            SectionLabel(stringResource(R.string.header_general))

            SettingGroup {
                SwitchSetting(
                    title = stringResource(R.string.pref_vehicle_filter_title),
                    summary = stringResource(
                        when {
                            !fullBuild -> R.string.pref_vehicle_filter_summary_foss
                            !hasPlayServices -> R.string.vehicle_filter_unavailable
                            else -> R.string.pref_vehicle_filter_summary
                        },
                    ),
                    icon = R.drawable.ic_car,
                    enabled = hasPlayServices,
                    checked = hasPlayServices && storedVehicleFilter,
                    onCheckedChange = { on ->
                        scope.launch {
                            AppPreferences.dataStore.edit { prefs ->
                                prefs[AppPreferences.PreferenceKeys.VEHICLE_FILTER_ENABLED] = on
                            }
                        }
                    },
                )

                SettingDivider()

                SwitchSetting(
                    title = stringResource(R.string.hourly_breakdown_title),
                    summary = stringResource(R.string.hourly_breakdown_summary),
                    icon = R.drawable.ic_clock_breakdown,
                    checked = storedHourlyBreakdown,
                    onCheckedChange = { on ->
                        if (on) {
                            scope.launch {
                                AppPreferences.dataStore.edit { prefs ->
                                    prefs[AppPreferences.PreferenceKeys.HOURLY_BREAKDOWN] = true
                                }
                                pushHourlyService(false)
                            }
                        } else {
                            activeDialog = SettingsDialogState.HourlyOff
                        }
                    },
                )

                SettingDivider()

                SettingRow(
                    title = stringResource(R.string.unit_system),
                    summary = unitSummary,
                    icon = R.drawable.ic_unit,
                    onClick = { activeDialog = SettingsDialogState.UnitSystem },
                )

                SettingDivider()

                SettingRow(
                    title = stringResource(R.string.day_start_title),
                    summary = dayStartSummary,
                    icon = R.drawable.ic_clock,
                    onClick = { activeDialog = SettingsDialogState.DayStartHour },
                )

                SettingDivider()

                SettingRow(
                    title = stringResource(R.string.date_format),
                    summary = storedDateFormat,
                    icon = R.drawable.ic_dateformat,
                    onClick = { activeDialog = SettingsDialogState.DateFormat },
                )

                SettingDivider()

                SettingRow(
                    title = stringResource(R.string.first_day_of_the_week),
                    summary = firstDaySummary,
                    icon = R.drawable.ic_firstday,
                    onClick = { activeDialog = SettingsDialogState.FirstDayOfWeek },
                )
            }

            SectionLabel(stringResource(R.string.header_appearance))

            SettingGroup {
                SettingRow(
                    title = stringResource(R.string.app_language),
                    summary = languageSummary,
                    icon = R.drawable.ic_language,
                    onClick = { activeDialog = SettingsDialogState.Language },
                )

                SettingDivider()

                SettingRow(
                    title = stringResource(R.string.theme),
                    summary = themeSummary,
                    icon = R.drawable.ic_theme,
                    onClick = null,
                    trailing = {},
                )
                PillSelector(
                    options = themeOptions,
                    selected = storedTheme,
                    onSelect = { chosen ->
                        scope.launch {
                            AppPreferences.dataStore.edit { prefs ->
                                prefs[AppPreferences.PreferenceKeys.THEME] = chosen
                            }
                        }
                    },
                    iconOf = { value ->
                        when (value) {
                            "system" -> R.drawable.ic_theme
                            "light" -> R.drawable.ic_theme_light
                            "dark" -> R.drawable.ic_theme_dark
                            else -> null
                        }
                    },
                    chipPadding = PaddingValues(horizontal = 28.dp, vertical = 7.dp),
                    modifier = Modifier.padding(start = 56.dp, end = 20.dp, top = 12.dp, bottom = 10.dp),
                )
            }

            SectionLabel(stringResource(R.string.header_data))

            SettingGroup {
                SettingRow(
                    title = stringResource(R.string.header_data_backup),
                    summary = stringResource(R.string.pref_backup_summary),
                    icon = R.drawable.ic_backup,
                    onClick = { ctx.startActivity(Intent(ctx, BackupActivity::class.java)) },
                )
            }

            if (BuildConfig.DEBUG) {
                SectionLabel(stringResource(R.string.debug_section))

                SettingGroup {
                    SettingRow(
                        title = stringResource(R.string.mock_data_title),
                        icon = R.drawable.ic_import,
                        onClick = { activeDialog = SettingsDialogState.MockData },
                    )

                    SettingDivider()

                    SettingRow(
                        title = stringResource(R.string.clear_data_title),
                        icon = R.drawable.ic_x,
                        onClick = { activeDialog = SettingsDialogState.ClearData },
                    )
                }
            }

            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }

    when (activeDialog) {
        SettingsDialogState.HeightMetric -> NumberFieldDialog(
            title = stringResource(R.string.pref_height),
            initial = storedHeight.toString(),
            suffix = heightUnit,
            isValid = { (it.toIntOrNull() ?: 0) in 1..250 },
            onConfirm = { input ->
                activeDialog = null
                scope.launch {
                    AppPreferences.dataStore.edit { prefs ->
                        prefs[AppPreferences.PreferenceKeys.HEIGHT] = input.toInt().toString()
                    }
                }
            },
            onDismiss = { activeDialog = null },
        )

        SettingsDialogState.HeightImperial -> HeightImperialDialog(
            initialCm = storedHeight,
            onConfirm = { cm ->
                activeDialog = null
                scope.launch {
                    AppPreferences.dataStore.edit { prefs ->
                        prefs[AppPreferences.PreferenceKeys.HEIGHT] = cm.toString()
                    }
                }
            },
            onDismiss = { activeDialog = null },
        )

        SettingsDialogState.StepLength -> {
            val range = if (imperial) 0.5f..60f else 1f..150f
            val current = if (imperial) storedStepLength / Util.CM_PER_INCH else storedStepLength
            NumberFieldDialog(
                title = stringResource(R.string.pref_step_length),
                initial = fmtNumber(current),
                suffix = stepLengthUnit,
                allowDecimal = true,
                isValid = { input ->
                    if (input.isBlank()) {
                        true
                    } else {
                        val parsed = input.replace(',', '.').toFloatOrNull()
                        parsed != null && parsed in range
                    }
                },
                onConfirm = { input ->
                    activeDialog = null
                    if (input.isBlank()) {
                        scope.launch {
                            AppPreferences.dataStore.edit { prefs ->
                                prefs.remove(AppPreferences.PreferenceKeys.STEP_LENGTH)
                            }
                        }
                    } else {
                        val cm = input.replace(',', '.').toFloat() * if (imperial) Util.CM_PER_INCH else 1f
                        scope.launch {
                            AppPreferences.dataStore.edit { prefs ->
                                prefs[AppPreferences.PreferenceKeys.STEP_LENGTH] = cm
                            }
                        }
                    }
                },
                onDismiss = { activeDialog = null },
            )
        }

        SettingsDialogState.Weight -> NumberFieldDialog(
            title = stringResource(R.string.pref_weight),
            initial = weightDialogInitial(storedWeight, imperial),
            suffix = weightUnit,
            isValid = {
                val v = it.toIntOrNull() ?: return@NumberFieldDialog false
                if (imperial) v in 1..1100 else v in 1..500
            },
            onConfirm = { input ->
                activeDialog = null
                val kg = bodyWeightToKg(input, imperial)
                scope.launch {
                    AppPreferences.dataStore.edit { prefs ->
                        prefs[AppPreferences.PreferenceKeys.WEIGHT] = kg.toString()
                    }
                }
            },
            onDismiss = { activeDialog = null },
        )

        SettingsDialogState.UnitSystem -> ChoiceDialog(
            title = stringResource(R.string.unit_system),
            options = unitSystemOptions,
            selected = unitValue,
            onSelect = { chosen ->
                activeDialog = null
                scope.launch {
                    AppPreferences.dataStore.edit { prefs ->
                        prefs[AppPreferences.PreferenceKeys.UNIT_SYSTEM] = chosen
                    }
                }
            },
            onDismiss = { activeDialog = null },
        )

        SettingsDialogState.DayStartHour -> ChoiceDialog(
            title = stringResource(R.string.day_start_title),
            options = dayStartOptions,
            selected = storedDayStartHour,
            description = stringResource(R.string.day_start_description),
            onSelect = { chosen ->
                activeDialog = null
                scope.launch {
                    val nowMs = System.currentTimeMillis()
                    val switchAt = withContext(Dispatchers.IO) {
                        AppPreferences.setDayStart(chosen, nowMs).also { Util.refreshDayStartCache() }
                    }
                    ctx.startService(Intent(ctx, MotionService::class.java).apply {
                        action = "UPDATE_NOTIFICATION"
                    })
                    if (switchAt - nowMs > 60_000L) {
                        val zone = ZoneId.systemDefault()
                        val nowZoned = Instant.ofEpochMilli(nowMs).atZone(zone)
                        val triggerZoned = Instant.ofEpochMilli(switchAt).atZone(zone)
                        val at = dayStartTimeFormat.format(triggerZoned)
                        val text = if (triggerZoned.toLocalDate() == nowZoned.toLocalDate()) {
                            ctx.getString(R.string.day_start_applies_toast, at)
                        } else {
                            ctx.getString(R.string.day_start_applies_toast_tomorrow, at)
                        }
                        toast.show(
                            text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                            ToastKind.INFO,
                            ToastState.DURATION_LONG,
                        )
                    }
                }
            },
            onDismiss = { activeDialog = null },
        )

        SettingsDialogState.DateFormat -> ChoiceDialog(
            title = stringResource(R.string.date_format),
            options = dateFormatOptions,
            selected = storedDateFormat,
            onSelect = { chosen ->
                activeDialog = null
                scope.launch {
                    AppPreferences.dataStore.edit { prefs ->
                        prefs[AppPreferences.PreferenceKeys.DATE_FORMAT] = chosen
                    }
                }
            },
            onDismiss = { activeDialog = null },
        )

        SettingsDialogState.FirstDayOfWeek -> ChoiceDialog(
            title = stringResource(R.string.first_day_of_the_week),
            options = weekdayOptions,
            selected = storedFirstDay,
            onSelect = { chosen ->
                activeDialog = null
                scope.launch {
                    AppPreferences.dataStore.edit { prefs ->
                        prefs[AppPreferences.PreferenceKeys.FIRST_DAY_OF_WEEK] = chosen.toString()
                    }
                }
            },
            onDismiss = { activeDialog = null },
        )

        SettingsDialogState.Language -> ChoiceDialog(
            title = stringResource(R.string.app_language),
            options = languageOptions,
            selected = languageValue,
            onSelect = { chosen ->
                activeDialog = null
                val newLocale = if (chosen == "system") {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.create(Locale(chosen))
                }
                AppCompatDelegate.setApplicationLocales(newLocale)
            },
            onDismiss = { activeDialog = null },
        )

        SettingsDialogState.HourlyOff -> {
            var clearHourlyData by remember { mutableStateOf(false) }
            StepsyDialog(
                title = stringResource(R.string.hourly_off_confirm_title),
                onDismiss = { activeDialog = null },
                confirmText = stringResource(R.string.hourly_off_confirm_action),
                onConfirm = {
                    activeDialog = null
                    scope.launch {
                        AppPreferences.dataStore.edit { prefs ->
                            prefs[AppPreferences.PreferenceKeys.HOURLY_BREAKDOWN] = false
                        }
                        pushHourlyService(clearHourlyData)
                    }
                },
                dismissText = stringResource(android.R.string.cancel),
            ) {
                Text(stringResource(R.string.hourly_off_confirm_message))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clickable { clearHourlyData = !clearHourlyData },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = clearHourlyData,
                        onCheckedChange = { clearHourlyData = it },
                    )
                    Text(stringResource(R.string.hourly_off_confirm_clear_data))
                }
            }
        }

        SettingsDialogState.MockData -> {
            var mockDays by remember { mutableStateOf(1096) }
            var mockHourly by remember { mutableStateOf(true) }
            var mockEffort by remember { mutableStateOf(MockEffort.AVERAGE) }
            var mockFocus by remember { mutableStateOf(MockFocus.BALANCED) }
            StepsyDialog(
                title = stringResource(R.string.mock_data_title),
                onDismiss = { activeDialog = null },
                confirmText = stringResource(R.string.mock_data_generate),
                onConfirm = {
                    activeDialog = null
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            MockData.generateAndStore(ctx, mockDays, mockHourly, mockEffort, mockFocus)
                        }
                    }
                },
            ) {
                Text(stringResource(R.string.mock_data_message))
                PillSelector(
                    options = listOf(
                        180 to stringResource(R.string.mock_data_6_mo),
                        365 to stringResource(R.string.mock_data_1_y),
                        1096 to stringResource(R.string.mock_data_3_y),
                    ),
                    selected = mockDays,
                    onSelect = { mockDays = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                )
                PillSelector(
                    options = listOf(
                        true to stringResource(R.string.mock_data_with_hourly),
                        false to stringResource(R.string.mock_data_without_hourly),
                    ),
                    selected = mockHourly,
                    onSelect = { mockHourly = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
                PillSelector(
                    options = listOf(
                        MockEffort.BEGINNER to stringResource(R.string.mock_data_effort_beginner),
                        MockEffort.AVERAGE to stringResource(R.string.mock_data_effort_average),
                        MockEffort.HEAVY to stringResource(R.string.mock_data_effort_heavy),
                    ),
                    selected = mockEffort,
                    onSelect = { mockEffort = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
                PillSelector(
                    options = listOf(
                        MockFocus.BALANCED to stringResource(R.string.mock_data_focus_balanced),
                        MockFocus.NIGHT to stringResource(R.string.mock_data_focus_night),
                        MockFocus.MORNING to stringResource(R.string.mock_data_focus_morning),
                        MockFocus.AFTERNOON to stringResource(R.string.mock_data_focus_afternoon),
                        MockFocus.EVENING to stringResource(R.string.mock_data_focus_evening),
                    ),
                    selected = mockFocus,
                    onSelect = { mockFocus = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
        }

        SettingsDialogState.ClearData -> ClearDataDialog(
            onDismiss = { activeDialog = null },
            onApply = { steps, settings, hourly ->
                activeDialog = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        if (steps) DebugTools.clearAllSteps(ctx)
                        if (settings) DebugTools.clearSettings(ctx)
                    }
                    if (hourly) pushHourlyService(true)
                }
            },
        )

        null -> {}
    }
}

@Composable
private fun <T> prefsState(flow: () -> Flow<T>, initial: () -> T): State<T> {
    val remembered = remember { flow() }
    return remembered.collectAsStateWithLifecycle(initialValue = remember { initial() })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClearDataDialog(
    onDismiss: () -> Unit,
    onApply: (steps: Boolean, settings: Boolean, hourly: Boolean) -> Unit,
) {
    var steps by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var hourly by remember { mutableStateOf(false) }
    StepsyDialog(
        title = stringResource(R.string.clear_data_title),
        onDismiss = onDismiss,
        confirmText = stringResource(android.R.string.ok),
        onConfirm = { onApply(steps, settings, hourly) },
    ) {
        FlowRow(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChipLabel(stringResource(R.string.clear_chip_steps), on = steps) { steps = !steps }
            FilterChipLabel(stringResource(R.string.clear_chip_settings), on = settings) { settings = !settings }
            FilterChipLabel(stringResource(R.string.clear_chip_hourly), on = hourly) { hourly = !hourly }
        }
    }
}

private fun entriesZip(ctx: Context, entriesRes: Int, valuesRes: Int): List<Pair<String, String>> =
    ctx.resources.getStringArray(valuesRes)
        .zip(ctx.resources.getStringArray(entriesRes)) { value, label -> value to label }

private fun labelFromEntries(ctx: Context, entriesRes: Int, valuesRes: Int, currentValue: String): String {
    val entries = ctx.resources.getStringArray(entriesRes)
    val values = ctx.resources.getStringArray(valuesRes)
    val index = values.indexOf(currentValue)
    return if (index >= 0) entries[index] else currentValue
}

private fun fmtNumber(value: Float): String {
    val fmt = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 2
        isGroupingUsed = false
    }
    return fmt.format(value.toDouble())
}

private fun weightDialogInitial(kg: Int, imperial: Boolean): String =
    if (imperial) (kg * Util.LB_PER_KG).roundToInt().toString() else kg.toString()

private fun cmToTotalInches(cm: Int): Int = (cm / Util.CM_PER_INCH).roundToInt()

private fun totalInchesToCm(inches: Int): Int = (inches * Util.CM_PER_INCH).roundToInt()

private fun inchesToFtIn(totalInches: Int): String {
    val ft = totalInches / 12
    val ins = totalInches % 12
    return "${ft}′${ins}″"
}

private fun heightDisplay(cm: Int, imperial: Boolean, unit: String): String = if (imperial) {
    inchesToFtIn(cmToTotalInches(cm))
} else {
    "$cm $unit"
}

private fun stepLengthDisplay(cm: Float, imperial: Boolean, unit: String): String =
    if (imperial) {
        "${fmtNumber(cm / Util.CM_PER_INCH)} $unit"
    } else {
        "${fmtNumber(cm)} $unit"
    }

private fun weightDisplay(kg: Int, imperial: Boolean, unit: String): String = if (imperial) {
    "${(kg * Util.LB_PER_KG).roundToInt()} $unit"
} else {
    "$kg $unit"
}

@Composable
private fun HeightImperialDialog(
    initialCm: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val totalInches = cmToTotalInches(initialCm)
    var ft by remember { mutableStateOf((totalInches / 12).toString()) }
    var ins by remember { mutableStateOf((totalInches % 12).toString()) }

    val valid = (ft.toIntOrNull() ?: 0) * 12 + (ins.toIntOrNull() ?: 0) in 12..98

    StepsyDialog(
        title = stringResource(R.string.pref_height),
        onDismiss = onDismiss,
        confirmText = stringResource(android.R.string.ok),
        onConfirm = {
            val total = (ft.toIntOrNull() ?: 0) * 12 + (ins.toIntOrNull() ?: 0)
            onConfirm(totalInchesToCm(total).coerceIn(1, 250))
        },
        confirmEnabled = valid,
        dismissText = stringResource(android.R.string.cancel),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NumberField(
                value = ft,
                digitsOnly = { ft = it },
                modifier = Modifier.weight(1f),
                focusOnMount = true,
                suffix = stringResource(R.string.unit_ft),
            )
            NumberField(
                value = ins,
                digitsOnly = { ins = it },
                modifier = Modifier.weight(1f),
                suffix = stringResource(R.string.unit_in),
            )
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    digitsOnly: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusOnMount: Boolean = false,
    suffix: String? = null,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusOnMount) {
        if (focusOnMount) focus.requestFocus()
    }
    var text by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    TextField(
        value = text,
        onValueChange = { next ->
            val filtered = next.text.filter(Char::isDigit)
            text = next.copy(text = filtered, selection = TextRange(filtered.length))
            digitsOnly(filtered)
        },
        suffix = suffix?.let { { Text(it, color = StepsyTheme.colors.accent) } },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = StepsyTheme.colors.accentOpaque,
            unfocusedContainerColor = StepsyTheme.colors.accentOpaque,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.onSurface,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.then(if (focusOnMount) Modifier.focusRequester(focus) else Modifier),
    )
}

private enum class SettingsDialogState {
    HeightMetric, HeightImperial, StepLength, Weight,
    UnitSystem, DayStartHour, DateFormat, FirstDayOfWeek, Language,
    HourlyOff, MockData, ClearData,
}

internal fun bodyHeightText(cm: Int, imperial: Boolean, unit: String): String = heightDisplay(cm, imperial, unit)

internal fun bodyWeightText(kg: Int, imperial: Boolean, unit: String): String = weightDisplay(kg, imperial, unit)

internal fun bodyStepLengthText(cm: Float, imperial: Boolean, unit: String): String =
    stepLengthDisplay(cm, imperial, unit)

internal fun bodyWeightDialogInitial(kg: Int, imperial: Boolean): String = weightDialogInitial(kg, imperial)

internal fun bodyWeightToKg(input: String, imperial: Boolean): Int =
    if (imperial) (input.toInt() / Util.LB_PER_KG).roundToInt().coerceIn(1, 500) else input.toInt()

@Composable
internal fun BodyHeightImperialDialog(initialCm: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    HeightImperialDialog(initialCm, onConfirm, onDismiss)
}
