package com.nvllz.stepsy.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nvllz.stepsy.R
import com.nvllz.stepsy.ui.components.SettingDivider
import com.nvllz.stepsy.ui.components.SettingGroup
import com.nvllz.stepsy.ui.components.SettingRow
import com.nvllz.stepsy.ui.theme.StepsyTheme
import com.nvllz.stepsy.util.AppPreferences
import com.nvllz.stepsy.util.TrackingAccess
import com.nvllz.stepsy.util.TrackingAccess.Ask
import kotlinx.coroutines.launch

private enum class Gap(val id: String) {
    ACTIVITY("activity"), BATTERY("battery"), NOTIFICATIONS("notifications")
}

private fun missingGaps(ctx: android.content.Context): List<Gap> = buildList {
    if (!TrackingAccess.hasStepSensor(ctx)) return@buildList
    if (TrackingAccess.stepPermissionMissing(ctx)) add(Gap.ACTIVITY)
    if (!TrackingAccess.batteryExempt(ctx)) add(Gap.BATTERY)
    if (TrackingAccess.notificationPermissionMissing(ctx)) add(Gap.NOTIFICATIONS)
}

@Composable
internal fun SetupGapsCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dismissedFlow = remember { AppPreferences.setupCardDismissedFlow() }
    val dismissed by dismissedFlow.collectAsStateWithLifecycle(remember { AppPreferences.setupCardDismissed })

    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick++
        onPauseOrDispose { }
    }
    val gaps = remember(resumeTick, dismissed) {
        missingGaps(ctx).filter { gap -> gap == Gap.ACTIVITY || gap.id !in dismissed }
    }
    var shownGaps by remember { mutableStateOf(gaps) }
    if (gaps.isNotEmpty()) shownGaps = gaps

    val askSteps = rememberStepsAsk { result ->
        if (result == Ask.BLOCKED) TrackingAccess.open(ctx, TrackingAccess.appSettingsIntent(ctx))
        resumeTick++
    }
    val askNotifications = rememberNotificationAsk { result ->
        if (result == Ask.BLOCKED) {
            TrackingAccess.open(
                ctx,
                TrackingAccess.notificationSettingsIntent(ctx),
                TrackingAccess.appSettingsIntent(ctx),
            )
        }
        resumeTick++
    }
    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        resumeTick++
    }

    fun dismissNotifications() {
        scope.launch {
            AppPreferences.dataStore.edit { prefs ->
                prefs[AppPreferences.PreferenceKeys.SETUP_CARD_DISMISSED] = dismissed + Gap.NOTIFICATIONS.id
            }
        }
    }

    val closeLabel = stringResource(R.string.onboarding_card_dismiss)

    AnimatedVisibility(
        visible = gaps.isNotEmpty(),
        enter = fadeIn(tween(200)) + expandVertically(tween(280, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(250)) + shrinkVertically(tween(280, easing = FastOutSlowInEasing)),
    ) {
        Column(
            Modifier.padding(top = 8.dp, bottom = 12.dp).fillMaxWidth().animateContentSize(tween(280, easing = FastOutSlowInEasing))
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
                .padding(vertical = 4.dp),
        ) {
            shownGaps.forEachIndexed { i, gap ->
                AnimatedVisibility(
                    visible = gap in gaps,
                    enter = fadeIn(tween(200)) + expandVertically(tween(280, easing = FastOutSlowInEasing)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(280, easing = FastOutSlowInEasing)),
                ) {
                    Column {
                        if (i > 0) SettingDivider()
                        when (gap) {
                            Gap.ACTIVITY -> SettingRow(
                                title = stringResource(R.string.onboarding_card_activity_title),
                                summary = stringResource(R.string.onboarding_steps_body),
                                icon = R.drawable.ic_activity,
                                onClick = askSteps,
                            )
                            Gap.BATTERY -> SettingRow(
                                title = stringResource(R.string.onboarding_card_battery_title),
                                summary = stringResource(R.string.onboarding_card_battery_summary),
                                icon = R.drawable.ic_battery,
                                onClick = {
                                    TrackingAccess.launchFirst(TrackingAccess.batteryIntents(ctx)) {
                                        batteryLauncher.launch(it)
                                    }
                                },
                            )
                            Gap.NOTIFICATIONS -> SettingRow(
                                title = stringResource(R.string.section_notifications),
                                summary = stringResource(R.string.onboarding_reminders_summary),
                                icon = R.drawable.ic_notifications_active,
                                onClick = askNotifications,
                                trailing = {
                                    Box(
                                        Modifier.size(40.dp).clip(CircleShape)
                                            .clickable(role = Role.Button, onClick = ::dismissNotifications)
                                            .semantics { contentDescription = closeLabel },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            painterResource(R.drawable.ic_x), null,
                                            tint = StepsyTheme.colors.accent,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun StepsOffStrip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    SettingGroup(modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
        SettingRow(
            title = stringResource(R.string.onboarding_strip_title),
            summary = stringResource(R.string.onboarding_strip_summary),
            icon = R.drawable.ic_activity,
            onClick = onClick,
        )
    }
}
