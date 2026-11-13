package com.nvllz.stepsy.ui.widgetconfig

import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nvllz.stepsy.R
import com.nvllz.stepsy.ui.components.PillSelector
import com.nvllz.stepsy.ui.components.PrimaryButton
import com.nvllz.stepsy.ui.components.SettingDivider
import com.nvllz.stepsy.ui.components.SettingGroup
import com.nvllz.stepsy.ui.components.SettingRow
import com.nvllz.stepsy.ui.components.SwitchSetting
import com.nvllz.stepsy.ui.theme.StepsyTheme
import java.text.NumberFormat
import kotlin.math.roundToInt

@Composable
fun WidgetConfigScreen(
    type: WidgetConfigType,
    appWidgetId: Int,
    initial: WidgetConfigValues,
    onSave: (WidgetConfigValues) -> Unit,
    modifier: Modifier = Modifier,
) {
    var opacityPercent by rememberSaveable(appWidgetId) { mutableStateOf(initial.opacity.toFloat()) }
    var textScalePercent by rememberSaveable(appWidgetId) { mutableStateOf(initial.textScale.toFloat()) }
    var dynamicColors by rememberSaveable(appWidgetId) { mutableStateOf(initial.useDynamicColors) }
    var themeMode by rememberSaveable(appWidgetId) { mutableStateOf(initial.themeMode) }
    var stepCountFormat by rememberSaveable(appWidgetId) { mutableStateOf(initial.stepCountFormat) }
    var inverseBackdrop by rememberSaveable(appWidgetId) { mutableStateOf(false) }

    val previewValues = WidgetConfigValues(
        opacity = opacityPercent.toInt(),
        useDynamicColors = dynamicColors,
        textScale = textScalePercent.toInt(),
        themeMode = themeMode,
        stepCountFormat = stepCountFormat,
    )

    val scroll = rememberScrollState()

    val percentFormat = remember {
        NumberFormat.getPercentInstance().apply { maximumFractionDigits = 0 }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(StepsyTheme.colors.background)
            .safeDrawingPadding(),
    ) {
        val backdropLabel = stringResource(R.string.widget_pref_invert_bg)
        val darkChrome = (LocalConfiguration.current.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        Box(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp).clip(RoundedCornerShape(24.dp))) {
            WidgetPreview(type, previewValues, inverseBackdrop)

            val targetIcon = if (inverseBackdrop == darkChrome) {
                R.drawable.ic_theme_dark
            } else {
                R.drawable.ic_theme_light
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(36.dp)
                    .border(1.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    .clickable(
                        onClick = { inverseBackdrop = !inverseBackdrop },
                        role = Role.Button,
                    )
                    .semantics { contentDescription = backdropLabel },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(1.5.dp)
                        .clip(CircleShape)
                        .background(StepsyTheme.colors.background),
                )
                Icon(
                    painterResource(targetIcon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll)) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                val dynamicColorsAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                if (dynamicColorsAvailable) {
                    SettingGroup(Modifier.padding(top = 16.dp)) {
                        SwitchSetting(
                            title = stringResource(R.string.widget_pref_dynamic_colors),
                            checked = dynamicColors,
                            onCheckedChange = { dynamicColors = it },
                        )
                    }
                }

                SettingGroup(
                    Modifier.padding(
                        top = if (dynamicColorsAvailable) 12.dp else 16.dp,
                        bottom = 16.dp,
                    ),
                ) {
                    SettingRow(
                        title = stringResource(R.string.theme),
                        summary = stringResource(
                            when (themeMode) {
                                WidgetThemeMode.SYSTEM -> R.string.preference_system
                                WidgetThemeMode.LIGHT -> R.string.theme_light
                                WidgetThemeMode.DARK -> R.string.theme_dark
                            },
                        ),
                        icon = R.drawable.ic_theme,
                        onClick = null,
                        trailing = {},
                    )
                    PillSelector(
                        options = listOf(
                            WidgetThemeMode.SYSTEM to stringResource(R.string.preference_system),
                            WidgetThemeMode.LIGHT to stringResource(R.string.theme_light),
                            WidgetThemeMode.DARK to stringResource(R.string.theme_dark),
                        ),
                        selected = themeMode,
                        onSelect = { themeMode = it },
                        iconOf = { value ->
                            when (value) {
                                WidgetThemeMode.SYSTEM -> R.drawable.ic_theme
                                WidgetThemeMode.LIGHT -> R.drawable.ic_theme_light
                                WidgetThemeMode.DARK -> R.drawable.ic_theme_dark
                            }
                        },
                        chipPadding = PaddingValues(horizontal = 28.dp, vertical = 7.dp),
                        modifier = Modifier.padding(start = 56.dp, end = 20.dp, top = 12.dp, bottom = 10.dp),
                    )
                    SettingDivider()
                    val formatSampleContext = LocalContext.current
                    SettingRow(
                        title = stringResource(R.string.widget_pref_step_count_format),
                        summary = stepCountFormat.label(),
                        icon = R.drawable.ic_steps,
                        onClick = null,
                        trailing = {},
                    )
                    PillSelector(
                        options = WidgetStepCountFormat.entries.map {
                            it to it.format(formatSampleContext, STEP_COUNT_FORMAT_SAMPLE)
                        },
                        selected = stepCountFormat,
                        onSelect = { stepCountFormat = it },
                        modifier = Modifier.padding(start = 56.dp, end = 20.dp, top = 12.dp, bottom = 10.dp),
                    )
                    SettingDivider()
                    SliderRow(
                        label = stringResource(R.string.widget_pref_text_scale),
                        value = textScalePercent,
                        onValueChange = { textScalePercent = it },
                        valueRange = type.textScaleFrom..type.textScaleTo,
                        steps = textScaleSteps(type),
                        valueLabel = percentFormat.format(textScalePercent.roundToInt() / 100.0),
                    )
                    SettingDivider()
                    SliderRow(
                        label = stringResource(R.string.widget_pref_bg_opacity),
                        value = opacityPercent,
                        onValueChange = { opacityPercent = it },
                        valueRange = 0f..100f,
                        steps = 0,
                        valueLabel = percentFormat.format(opacityPercent.roundToInt() / 100.0),
                    )
                }
            }
        }

        PrimaryButton(
            text = stringResource(R.string.widget_pref_btn_save),
            onClick = {
                onSave(
                    WidgetConfigValues(
                        opacity = opacityPercent.toInt(),
                        useDynamicColors = dynamicColors,
                        textScale = textScalePercent.toInt(),
                        themeMode = themeMode,
                        stepCountFormat = stepCountFormat,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 12.dp),
        )
    }
}

private const val STEP_COUNT_FORMAT_SAMPLE = 12345

@Composable
private fun WidgetStepCountFormat.label(): String = stringResource(
    when (this) {
        WidgetStepCountFormat.PLAIN -> R.string.widget_step_count_format_plain
        WidgetStepCountFormat.DELIMITED -> R.string.widget_step_count_format_delimited
        WidgetStepCountFormat.SHORT -> R.string.widget_step_count_format_short
    },
)

private fun textScaleSteps(type: WidgetConfigType): Int =
    ((type.textScaleTo - type.textScaleFrom) / WidgetConfigType.TEXT_SCALE_STEP - 1)
        .roundToInt()
        .coerceAtLeast(0)

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: String,
) {
    Column(Modifier.padding(horizontal = 16.dp).padding(top = 13.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                valueLabel,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        val track = MaterialTheme.colorScheme.onSurface
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
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
