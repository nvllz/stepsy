package com.nvllz.stepsy.ui.widgetconfig

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.nvllz.stepsy.R

@Immutable
data class WidgetPalette(val primary: Color, val secondary: Color, val background: Color)

fun resolveWidgetPalette(
    context: Context,
    mode: WidgetThemeMode,
    useDynamicColors: Boolean,
    opacityPercent: Float,
): WidgetPalette {
    val themed = themedContext(context, mode.pref)
    val dynamic = useDynamicColors && Build.VERSION.SDK_INT >= 31

    val primary = ContextCompat.getColor(
        themed, if (dynamic) R.color.widgetPrimary else R.color.widgetPrimary_default)
    val secondary = ContextCompat.getColor(
        themed, if (dynamic) R.color.widgetSecondary else R.color.widgetSecondary_default)
    val background = ContextCompat.getColor(
        themed, if (dynamic) R.color.widgetBackground else R.color.widgetBackground_default)

    return WidgetPalette(
        primary = Color(primary),
        secondary = Color(secondary),
        background = Color(
            ColorUtils.setAlphaComponent(background, (255 * (opacityPercent / 100f)).toInt())),
    )
}

@Composable
fun rememberWidgetPalette(
    mode: WidgetThemeMode,
    useDynamicColors: Boolean,
    opacityPercent: Float,
): WidgetPalette {
    val context = LocalContext.current
    val nightMode = LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return remember(mode, useDynamicColors, opacityPercent, nightMode) {
        resolveWidgetPalette(context, mode, useDynamicColors, opacityPercent)
    }
}

@Composable
fun rememberAnimatedWidgetPalette(
    mode: WidgetThemeMode,
    useDynamicColors: Boolean,
    opacityPercent: Float,
): WidgetPalette {
    val target = rememberWidgetPalette(mode, useDynamicColors, opacityPercent)
    val primary by animateColorAsState(target.primary, tween(250), label = "widgetPrimary")
    val secondary by animateColorAsState(target.secondary, tween(250), label = "widgetSecondary")
    return remember(primary, secondary, target) {
        WidgetPalette(primary, secondary, target.background)
    }
}

private fun themedContext(context: Context, themeMode: String): Context {
    val uiMode = when (themeMode) {
        "light" -> Configuration.UI_MODE_NIGHT_NO
        "dark"  -> Configuration.UI_MODE_NIGHT_YES
        else    -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    }
    val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    if (uiMode == currentNightMode) return context
    val config = Configuration(context.resources.configuration)
    config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or uiMode
    return context.createConfigurationContext(config)
}
