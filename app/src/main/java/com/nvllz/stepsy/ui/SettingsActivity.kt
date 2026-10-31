package com.nvllz.stepsy.ui

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.Window
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nvllz.stepsy.ui.theme.StepsyTheme
import com.nvllz.stepsy.util.AppPreferences
import com.nvllz.stepsy.util.Util

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Util.applyTheme(AppPreferences.theme)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val ctx = LocalContext.current
            val storedTheme by AppPreferences.themeFlow()
                .collectAsStateWithLifecycle(initialValue = AppPreferences.theme)
            val systemDark = (ctx.applicationContext.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val uiModeOverride = when (storedTheme) {
                "light" -> Configuration.UI_MODE_NIGHT_NO
                "dark" -> Configuration.UI_MODE_NIGHT_YES
                else -> if (systemDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            StepsyTheme(uiModeOverride = uiModeOverride) {
                val configDark = (LocalConfiguration.current.uiMode and
                        Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                val dark = uiModeOverride?.let { it == Configuration.UI_MODE_NIGHT_YES } ?: configDark
                val view = LocalView.current
                DisposableEffect(dark) {
                    val activity = view.context as? Activity
                    activity?.window?.let { window ->
                        windowInsetAppearance(window, view, dark)
                    }
                    onDispose { }
                }
                SettingsScreen(onBack = { onBackPressedDispatcher.onBackPressed() })
            }
        }
    }
}

private fun windowInsetAppearance(window: Window, view: View, dark: Boolean) {
    val controller = WindowInsetsControllerCompat(window, view)
    controller.isAppearanceLightStatusBars = !dark
    controller.isAppearanceLightNavigationBars = !dark
}
