/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.nvllz.stepsy.ui.cards

import android.content.Context
import com.nvllz.stepsy.R
import com.nvllz.stepsy.util.Util
import java.util.*

/**
 * A specialized [TextItem] that is used to display step information
 */
internal open class MotionTextItem(context: Context, description: Int) : TextItem(context, description) {

    private val format: String = context.getString(R.string.steps_format)

    internal open fun updateSteps(steps: Int) {
        setContent(steps)
    }

    internal fun setContent(steps: Number) {
        setContent(String.format(Locale.getDefault(), format, Util.stepsToDistance(steps), Util.getDistanceUnitString(), steps.toInt()))
    }
}
