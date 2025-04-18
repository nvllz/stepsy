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
    private val resources = context.resources

    internal fun setContent(steps: Number) {
        val stepCount = steps.toInt()

        val stepsPlural = resources.getQuantityString(
            R.plurals.steps_text,
            stepCount,
            stepCount
        )

        val formatted = String.format(
            Locale.getDefault(),
            format,
            Util.stepsToDistance(steps),
            Util.getDistanceUnitString(),
            stepsPlural
        )

        setContent(formatted)
    }
}
