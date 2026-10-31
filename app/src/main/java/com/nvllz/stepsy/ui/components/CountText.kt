package com.nvllz.stepsy.ui.components

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle

const val CountTextDefaultDuration = 220

@Composable
fun CountText(
    value: Int, format: (Int) -> String, style: TextStyle, modifier: Modifier = Modifier,
    duration: Int = CountTextDefaultDuration, easing: Easing = FastOutSlowInEasing,
) {
    val v by animateIntAsState(value, tween(duration, easing = easing), label = "count")
    Text(format(v), style = style.copy(fontFeatureSettings = "tnum"), modifier = modifier)
}
