package com.nvllz.stepsy.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.nvllz.stepsy.R

@Composable
fun lapPalette(): List<Color> = listOf(
    colorResource(R.color.colorGoal), colorResource(R.color.colorLap2), colorResource(R.color.colorLap3),
    colorResource(R.color.colorLap4), colorResource(R.color.colorLap5),
)

fun lapOf(palette: List<Color>, i: Int): Color = palette[Math.floorMod(i, palette.size)]
