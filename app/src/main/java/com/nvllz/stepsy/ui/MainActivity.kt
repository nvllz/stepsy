package com.nvllz.stepsy.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nvllz.stepsy.R
import com.nvllz.stepsy.service.MotionService
import com.nvllz.stepsy.service.StepSnapshot
import com.nvllz.stepsy.service.StepUpdates
import com.nvllz.stepsy.ui.announcement.AnnouncementManager
import com.nvllz.stepsy.ui.announcement.AnnouncementSheet
import com.nvllz.stepsy.ui.components.LocalToast
import com.nvllz.stepsy.ui.components.ToastHost
import com.nvllz.stepsy.ui.components.ToastKind
import com.nvllz.stepsy.ui.components.ToastState
import com.nvllz.stepsy.ui.components.rememberToastState
import com.nvllz.stepsy.ui.onboarding.OnboardingFlow
import com.nvllz.stepsy.ui.theme.StepsyTheme
import com.nvllz.stepsy.util.AppPreferences
import com.nvllz.stepsy.util.BackupScheduler
import com.nvllz.stepsy.util.TimedPauseManager
import com.nvllz.stepsy.util.TrackingAccess
import com.nvllz.stepsy.util.Util
import kotlinx.coroutines.flow.flowOf

internal class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Util.applyTheme(AppPreferences.theme)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        BackupScheduler.ensureBackupScheduled(applicationContext)

        setContent {
            val theme by AppPreferences.themeFlow().collectAsState(AppPreferences.theme)
            val systemDark = (LocalContext.current.applicationContext.resources.configuration.uiMode
                    and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val override = when (theme) {
                "light" -> Configuration.UI_MODE_NIGHT_NO
                "dark" -> Configuration.UI_MODE_NIGHT_YES
                else -> if (systemDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            StepsyTheme(uiModeOverride = override) {
                val dark = override == Configuration.UI_MODE_NIGHT_YES
                val view = LocalView.current
                DisposableEffect(dark) {
                    (view.context as? Activity)?.window?.let { window ->
                        windowInsetAppearance(window, view, dark)
                    }
                    onDispose { }
                }
                Root()
            }
        }
    }

    private data class DisplayPrefs(
        val firstDay: Int,
        val units: Util.UnitSystem,
        val dateFormat: String,
        val height: Int,
        val weight: Int,
        val stepLength: Float,
    )

    private fun readDisplayPrefs() = DisplayPrefs(
        AppPreferences.firstDayOfWeek,
        AppPreferences.unitSystem,
        AppPreferences.dateFormatString,
        AppPreferences.height,
        AppPreferences.weight,
        AppPreferences.stepLength,
    )

    @Composable
    private fun Root() {
        val ctx = LocalContext.current
        val lastDate = remember { Util.todayDateString() }
        val launchTheme = remember { AppPreferences.theme }
        val launchFirstDay = remember { AppPreferences.firstDayOfWeek }
        var canTrack by remember { mutableStateOf(ctx.canTrackSteps()) }
        var onboarding by remember { mutableStateOf(!AppPreferences.onboardingCompleted) }
        var snapshotTick by remember { mutableIntStateOf(0) }
        val initial = remember(snapshotTick) { StepSnapshot(AppPreferences.steps, AppPreferences.isPaused) }
        var pending by remember { mutableStateOf<Boolean?>(null) }

        val askSteps = rememberStepsAsk { result ->
            canTrack = ctx.canTrackSteps()
            if (result == TrackingAccess.Ask.BLOCKED) TrackingAccess.open(ctx, TrackingAccess.appSettingsIntent(ctx))
        }
        val stepsBlocked = remember(canTrack) { !canTrack && TrackingAccess.stepPermissionMissing(ctx) }
        var launchPrefs by remember { mutableStateOf(readDisplayPrefs()) }
        LifecycleResumeEffect(Unit) {
            canTrack = ctx.canTrackSteps()
            if (Util.todayDateString() != lastDate || (!onboarding && readDisplayPrefs() != launchPrefs)) recreate()
            onPauseOrDispose {}
        }

        val flow = remember(canTrack, if (canTrack) null else initial) { if (canTrack) StepUpdates.flow(ctx) else flowOf(initial) }
        val live by flow.collectAsStateWithLifecycle(initial)
        LaunchedEffect(live) { pending = null }
        val paused = pending ?: live.paused

        val toast = rememberToastState()

        val pauseOptionsLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.getStringExtra(TileDialogActivity.EXTRA_TOAST_TEXT)?.let { message ->
                    toast.show(message, ToastKind.INFO, ToastState.DURATION_LONG)
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalToast provides toast) {
                Crossfade(
                    targetState = onboarding,
                    animationSpec = tween(250),
                    label = "homeGate",
                ) { showOnboarding ->
                    if (showOnboarding) {
                        OnboardingFlow(
                            steps = live.steps,
                            canTrack = canTrack,
                            onTrackingChanged = { canTrack = ctx.canTrackSteps() },
                            onDataRestored = { snapshotTick++ },
                            onFinished = {
                                AppPreferences.onboardingCompleted = true
                                launchPrefs = readDisplayPrefs()
                                snapshotTick++
                                onboarding = false
                            },
                        )
                    } else {
                        HomeScreen(
                            steps = live.steps,
                            paused = paused,
                            onTogglePause = {
                                TimedPauseManager.clearPauseEndTime(ctx)
                                startService(Intent(ctx, MotionService::class.java).apply {
                                    action = if (paused) MotionService.ACTION_RESUME_COUNTING else MotionService.ACTION_PAUSE_COUNTING
                                    putExtra(MotionService.EXTRA_SUPPRESS_TOAST, true)
                                })
                                pending = !paused
                                toast.show(
                                    ctx.getString(if (paused) R.string.step_counting_resumed else R.string.step_counting_paused),
                                    if (paused) ToastKind.SUCCESS else ToastKind.INFO,
                                )
                            },
                            onPauseOptions = {
                                pauseOptionsLauncher.launch(
                                    Intent(ctx, TileDialogActivity::class.java)
                                        .putExtra(TileDialogActivity.EXTRA_FROM_APP, true)
                                )
                            },
                            onUpdateSteps = { n ->
                                ContextCompat.startForegroundService(ctx, Intent(ctx, MotionService::class.java)
                                    .putExtra("MANUAL_STEP_COUNT_CHANGE", true).putExtra(MotionService.KEY_STEPS, n))
                            },
                            onOpen = { cls, milestone ->
                                startActivity(Intent(ctx, cls).apply {
                                    milestone?.let { putExtra(AchievementsActivity.EXTRA_MILESTONE, it) }
                                })
                            },
                            stepsBlocked = stepsBlocked,
                            onFixSteps = askSteps,
                        )
                    }
                }
            }
            ToastHost(
                state = toast,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 84.dp),
            )
        }

        var pendingAnnouncement by remember {
            mutableStateOf(if (onboarding) null else AnnouncementManager.pendingKind(ctx))
        }
        pendingAnnouncement?.let { kind ->
            AnnouncementSheet(kind = kind, onDismiss = {
                AnnouncementManager.markHandled(kind)
                pendingAnnouncement = null
            })
        }
    }

    private fun Context.isGranted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun Context.canTrackSteps() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER) &&
            isGranted(Manifest.permission.ACTIVITY_RECOGNITION)

    companion object {
        const val RECEIVER_TAG = "RECEIVER_TAG"
    }
}