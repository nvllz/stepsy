package com.nvllz.stepsy.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.nvllz.stepsy.ui.theme.StepsyTheme
import com.nvllz.stepsy.util.AppPreferences
import com.nvllz.stepsy.util.Util

class DailyGoalsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Util.applyTheme(AppPreferences.theme)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            StepsyTheme {
                DailyGoalsScreen(onBack = { onBackPressedDispatcher.onBackPressed() })
            }
        }
    }
}
