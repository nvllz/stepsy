/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.nvllz.stepsy.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.CalendarView

class CalendarViewScrollable(context: Context, attrs: AttributeSet?) : CalendarView(context, attrs) {

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (ev.historySize > 0) {
                    val diffX = kotlin.math.abs(ev.x - ev.getHistoricalX(0))
                    val diffY = kotlin.math.abs(ev.y - ev.getHistoricalY(0))

                    if (diffY > diffX) {
                        parent.requestDisallowInterceptTouchEvent(false)
                        return false
                    }
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

}