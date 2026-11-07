package com.nvllz.stepsy.ui.onboarding

import android.app.Activity
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.nvllz.stepsy.R
import com.nvllz.stepsy.ui.components.ImportWarningDialog
import com.nvllz.stepsy.ui.components.LocalToast
import com.nvllz.stepsy.ui.components.ToastKind
import com.nvllz.stepsy.ui.rememberNotificationAsk
import com.nvllz.stepsy.ui.rememberStepsAsk
import com.nvllz.stepsy.ui.theme.Chivo
import com.nvllz.stepsy.ui.theme.StepsyTheme
import com.nvllz.stepsy.util.BackupImport
import com.nvllz.stepsy.util.TrackingAccess
import com.nvllz.stepsy.util.TrackingAccess.Ask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat

internal enum class OnboardingStep { WELCOME, STEPS, POCKET, BODY, GOAL }

private fun sequenceFor(ctx: android.content.Context): List<OnboardingStep> = buildList {
    add(OnboardingStep.WELCOME)
    if (TrackingAccess.hasStepSensor(ctx)) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(OnboardingStep.STEPS)
        add(OnboardingStep.POCKET)
    }
    add(OnboardingStep.BODY)
    add(OnboardingStep.GOAL)
}

@Composable
internal fun OnboardingFlow(
    steps: Int,
    canTrack: Boolean,
    onTrackingChanged: () -> Unit,
    onDataRestored: () -> Unit,
    onFinished: () -> Unit,
) {
    val ctx = LocalContext.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val sequence = remember { sequenceFor(ctx) }
    var index by rememberSaveable { mutableStateOf(0) }
    val step = sequence[index.coerceIn(0, sequence.lastIndex)]

    var finishing by remember { mutableStateOf(false) }
    val contentAlpha = remember { Animatable(1f) }
    LaunchedEffect(finishing) {
        if (finishing) {
            onFinished()
            contentAlpha.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
        }
    }

    fun next() {
        if (index >= sequence.lastIndex) finishing = true else index++
    }

    var stepsAsk by rememberSaveable { mutableStateOf(NOT_ASKED) }
    var notificationsAsk by rememberSaveable { mutableStateOf(NOT_ASKED) }
    var batteryTried by rememberSaveable { mutableStateOf(false) }
    var batteryRoute by rememberSaveable { mutableStateOf(-1) }
    var restoreState by rememberSaveable { mutableStateOf(RESTORE_IDLE) }
    var restoredRows by rememberSaveable { mutableStateOf(0) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var importing by remember { mutableStateOf(false) }

    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick++
        onPauseOrDispose { }
    }
    val batteryExempt = remember(resumeTick) { TrackingAccess.batteryExempt(ctx) }
    val notificationsOn = remember(resumeTick) { !TrackingAccess.notificationPermissionMissing(ctx) }

    val askSteps = rememberStepsAsk { result ->
        stepsAsk = result.ordinal
        onTrackingChanged()
    }
    val askNotifications = rememberNotificationAsk { result ->
        notificationsAsk = result.ordinal
        resumeTick++
    }
    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        resumeTick++
    }

    fun importFrom(uri: Uri) {
        importing = true
        scope.launch {
            val result = withContext(Dispatchers.IO + NonCancellable) {
                BackupImport.importAndClear(ctx, uri, repushService = canTrack)
            }
            importing = false
            when (result) {
                is BackupImport.Result.Imported -> {
                    restoredRows = result.rows
                    restoreState = RESTORE_DONE
                    onDataRestored()
                }
                is BackupImport.Result.Failed -> restoreState = RESTORE_FAILED
                is BackupImport.Result.Unsupported ->
                    scope.launch {
                        toast.show(ctx.getString(R.string.import_unsupported), ToastKind.ERROR)
                    }
            }
        }
    }

    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            scope.launch {
                val format = withContext(Dispatchers.IO) { BackupImport.detectFormat(ctx, uri) }
                when (format) {
                    BackupImport.Format.INVALID -> restoreState = RESTORE_INVALID
                    BackupImport.Format.UNSUPPORTED -> {
                        scope.launch {
                            toast.show(ctx.getString(R.string.import_unsupported), ToastKind.ERROR)
                        }
                    }
                    else -> {
                        if (BackupImport.hasHistory(ctx)) pendingImport = uri
                        else importFrom(uri)
                    }
                }
            }
        }
    }

    BackHandler(enabled = index > 0 && !finishing) { index-- }

    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        intro.animateTo(1f, tween(350, easing = FastOutSlowInEasing))
    }

    Column(
        Modifier.fillMaxSize().background(StepsyTheme.colors.background).statusBarsPadding()
            .graphicsLayer {
                alpha = intro.value
                translationY = (1f - intro.value) * 24.dp.toPx()
            },
    ) {
        TopRow(
            canGoBack = index > 0,
            onBack = { index-- },
            onSkipAll = { finishing = true },
            modifier = Modifier.graphicsLayer { alpha = contentAlpha.value },
        )
        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f).fillMaxWidth()
                .graphicsLayer { alpha = contentAlpha.value },
            transitionSpec = {
                val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { dir * it }
                    .togetherWith(slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -dir * it })
            },
            label = "onboardingPage",
        ) { page ->
            when (page) {
                OnboardingStep.WELCOME -> WelcomePage(
                    hasSensor = TrackingAccess.hasStepSensor(ctx),
                    restoreState = restoreState,
                    restoredRows = restoredRows,
                    busy = importing,
                    onRestore = { pickerLauncher.launch(BackupImport.pickerIntent()) },
                    onNext = ::next,
                )
                OnboardingStep.STEPS -> StepsPage(
                    steps = steps,
                    granted = canTrack,
                    ask = stepsAsk,
                    onAllow = askSteps,
                    onOpenSettings = { TrackingAccess.open(ctx, TrackingAccess.appSettingsIntent(ctx)) },
                    onNext = ::next,
                )
                OnboardingStep.POCKET -> PocketPage(
                    exempt = batteryExempt,
                    tried = batteryTried,
                    route = batteryRoute,
                    onAllow = {
                        batteryTried = true
                        batteryRoute = TrackingAccess.launchFirst(TrackingAccess.batteryIntents(ctx)) {
                            batteryLauncher.launch(it)
                        }
                    },
                    onNext = ::next,
                )
                OnboardingStep.BODY -> BodyPage(onNext = ::next)
                OnboardingStep.GOAL -> GoalPage(
                    notificationsOn = notificationsOn,
                    notificationsBlocked = notificationsAsk == Ask.BLOCKED.ordinal,
                    onAllowNotifications = askNotifications,
                    onOpenNotificationSettings = {
                        TrackingAccess.open(
                            ctx,
                            TrackingAccess.notificationSettingsIntent(ctx),
                            TrackingAccess.appSettingsIntent(ctx),
                        )
                    },
                    onDone = { finishing = true },
                )
            }
        }
    }

    pendingImport?.let { uri ->
        ImportWarningDialog(
            onConfirm = {
                pendingImport = null
                importFrom(uri)
            },
            onDismiss = { pendingImport = null },
        )
    }
}

internal const val NOT_ASKED = -1
internal const val RESTORE_IDLE = 0
internal const val RESTORE_DONE = 1
internal const val RESTORE_INVALID = 2
internal const val RESTORE_FAILED = 3

@Composable
private fun TopRow(
    canGoBack: Boolean,
    onBack: () -> Unit,
    onSkipAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 8.dp).heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canGoBack) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(StepsyTheme.colors.accentOpaque),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_chevron_right),
                        stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                        Modifier.size(24.dp).rotate(180f),
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.heightIn(min = 48.dp).clip(CircleShape)
                .clickable(role = Role.Button, onClick = onSkipAll)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.onboarding_skip_setup).uppercase(),
                fontSize = 13.sp, letterSpacing = 2.sp,
                fontWeight = FontWeight.SemiBold, color = StepsyTheme.colors.accent,
            )
        }
    }
}

@Composable
internal fun OnboardingHero(steps: Int, compact: Boolean) {
    val shown by animateIntAsState(steps, tween(400), label = "onboardingCount")
    Column(
        Modifier.fillMaxWidth().padding(top = 0.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.label_today).uppercase(),
            modifier = Modifier.padding(bottom = 6.dp),
            fontSize = 13.sp, letterSpacing = 2.sp,
            fontWeight = FontWeight.SemiBold, color = StepsyTheme.colors.accent,
        )
        Text(
            NumberFormat.getIntegerInstance().format(shown.toLong()),
            style = TextStyle(
                fontSize = if (compact) 40.sp else 56.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Chivo,
                color = MaterialTheme.colorScheme.onSurface,
            ),
        )
        if (!compact) {
            Text(
                pluralStringResource(R.plurals.steps_word, steps).uppercase(),
                fontSize = 13.sp, letterSpacing = 2.sp,
                fontWeight = FontWeight.SemiBold, color = StepsyTheme.colors.accent,
                modifier = Modifier.offset(y = (-2).dp),
            )
        }
    }
}
