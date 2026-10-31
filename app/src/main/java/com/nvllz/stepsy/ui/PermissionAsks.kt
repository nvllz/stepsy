package com.nvllz.stepsy.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.nvllz.stepsy.util.TrackingAccess
import com.nvllz.stepsy.util.TrackingAccess.Ask

@Composable
internal fun rememberStepsAsk(onResult: (Ask) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val latest by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        latest(TrackingAccess.judge(ctx, Manifest.permission.ACTIVITY_RECOGNITION))
    }
    return remember(launcher) { { launcher.launch(TrackingAccess.stepPermissions()) } }
}

@Composable
internal fun rememberNotificationAsk(onResult: (Ask) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val latest by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        latest(TrackingAccess.judge(ctx, Manifest.permission.POST_NOTIFICATIONS))
    }
    return remember(launcher) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                latest(Ask.GRANTED)
            }
        }
    }
}
