/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.nvllz.stepsy.ui.cards

import android.content.Context

/**
 * Further specialized [TextItem] that is used to show statistics. It is initialized with
 * data from database and updated with data from step sensor.
 */
internal class MotionStatisticsTextItem(context: Context, description: Int, var initialSteps: Int) : MotionTextItem(context, description) {

    fun updateSteps() {
        setContent(this.initialSteps)
    }
}