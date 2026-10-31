package com.nvllz.stepsy

import android.app.Application
import com.nvllz.stepsy.util.AppPreferences
import com.nvllz.stepsy.util.NotificationChannels
import com.nvllz.stepsy.util.Util

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppPreferences.init(applicationContext)
        Util.init(applicationContext)
        NotificationChannels.ensureAll(this)
    }
}