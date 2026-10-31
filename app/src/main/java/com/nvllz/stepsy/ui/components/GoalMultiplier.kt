package com.nvllz.stepsy.ui.components

import java.util.Locale

fun goalMultiplier(steps: Int, goal: Int): String? =
    if (goal <= 0 || steps < goal * 1.05) null else "×%.1f".format(Locale.getDefault(), steps.toFloat() / goal)
