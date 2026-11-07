package com.nvllz.stepsy.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import com.nvllz.stepsy.R

@Immutable
data class StepsyColors(
    val accent: Color, val accentOpaque: Color, val background: Color,
    val dialogSurface: Color, val special: Color, val goal: Color,
)

private val LocalColors = staticCompositionLocalOf<StepsyColors> { error("StepsyTheme missing") }
private val LocalStepsyDark = staticCompositionLocalOf { false }

object StepsyTheme {
    val colors: StepsyColors @Composable get() = LocalColors.current
    val dark: Boolean @Composable get() = LocalStepsyDark.current
}

val Chivo = FontFamily(
    Font(R.font.chivo_extralight, FontWeight.Light),
    Font(R.font.chivo_light, FontWeight.Normal),
    Font(R.font.chivo_regular, FontWeight.SemiBold),
    Font(R.font.chivo_medium, FontWeight.Bold),
)

private fun TextStyle.c() = copy(fontFamily = Chivo)

@Composable
fun StepsyTheme(
    uiModeOverride: Int? = null,
    drawBackground: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val themedContext = if (uiModeOverride != null) {
        val cfg = Configuration(configuration).apply {
            val mask = Configuration.UI_MODE_NIGHT_MASK.inv()
            uiMode = (uiMode and mask) or uiModeOverride
        }
        context.createConfigurationContext(cfg)
    } else {
        null
    }
    val dark = if (themedContext != null) {
        themedContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    } else {
        (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
    @Composable
    fun palette(id: Int): Color = if (themedContext != null) {
        Color(ContextCompat.getColor(themedContext, id))
    } else {
        colorResource(id)
    }
    val accent = palette(R.color.colorAccent)
    val surface = palette(R.color.colorSurface)
    val onSurface = palette(R.color.colorOnSurface)
    val primary = palette(R.color.colorPrimary)
    val dialog = palette(R.color.colorDialogSurface)
    val colors = StepsyColors(
        accent = accent,
        accentOpaque = palette(R.color.colorAccentOpaque),
        background = palette(R.color.colorBackground),
        dialogSurface = dialog,
        special = palette(R.color.colorSpecial),
        goal = palette(R.color.colorGoal),
    )
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = primary, onPrimary = palette(R.color.colorOnPrimary),
        background = colors.background, onBackground = onSurface,
        surface = surface, onSurface = onSurface, onSurfaceVariant = accent,
        surfaceContainer = dialog, surfaceContainerHigh = dialog, outline = accent,
    )
    val t = Typography()
    val typography = t.copy(
        displayLarge = t.displayLarge.c(), displayMedium = t.displayMedium.c(), displaySmall = t.displaySmall.c(),
        headlineLarge = t.headlineLarge.c(), headlineMedium = t.headlineMedium.c(), headlineSmall = t.headlineSmall.c(),
        titleLarge = t.titleLarge.c(), titleMedium = t.titleMedium.c(), titleSmall = t.titleSmall.c(),
        bodyLarge = t.bodyLarge.c(), bodyMedium = t.bodyMedium.c(), bodySmall = t.bodySmall.c(),
        labelLarge = t.labelLarge.c(), labelMedium = t.labelMedium.c(), labelSmall = t.labelSmall.c(),
    )
    CompositionLocalProvider(LocalColors provides colors, LocalStepsyDark provides dark) {
        MaterialTheme(colorScheme = scheme, typography = typography) {
            if (drawBackground) {
                Surface(color = colors.background, contentColor = onSurface, content = content)
            } else {
                CompositionLocalProvider(LocalContentColor provides onSurface, content = content)
            }
        }
    }
}