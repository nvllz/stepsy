package com.nvllz.stepsy.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nvllz.stepsy.ui.components.LocalToast
import com.nvllz.stepsy.ui.components.ToastHost
import com.nvllz.stepsy.ui.components.rememberToastState
import com.nvllz.stepsy.ui.theme.StepsyTheme
import com.nvllz.stepsy.util.AppPreferences
import com.nvllz.stepsy.util.Util

class AchievementsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Util.applyTheme(AppPreferences.theme)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val target = if (savedInstanceState == null) {
            intent?.getIntExtra(EXTRA_MILESTONE, -1)?.takeIf { it > 0 }
        } else null

        val targetShelf = savedInstanceState == null && target == null &&
                hasUnseenMilestones(applicationContext)

        setContent {
            StepsyTheme {
                val toast = rememberToastState()
                Box(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalToast provides toast) {
                        AchievementsScreen(target, targetShelf) { onBackPressedDispatcher.onBackPressed() }
                    }
                    ToastHost(
                        state = toast,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp),
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_MILESTONE = "extra_milestone"
    }
}