/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.nvllz.stepsy

import android.app.Application
import com.nvllz.stepsy.util.AppPreferences

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppPreferences.init(applicationContext)
    }
}