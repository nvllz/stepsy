package com.nvllz.stepsy.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.nvllz.stepsy.R
import com.nvllz.stepsy.service.MotionService
import com.nvllz.stepsy.ui.components.PauseStepCountingDialog
import com.nvllz.stepsy.ui.components.TimePickerDialog
import com.nvllz.stepsy.ui.theme.StepsyTheme
import com.nvllz.stepsy.util.AppPreferences
import com.nvllz.stepsy.util.Util
import kotlinx.coroutines.delay
import java.util.Calendar

class TileDialogActivity : AppCompatActivity() {

    private val fromApp: Boolean
        get() = intent?.getBooleanExtra(EXTRA_FROM_APP, false) == true

    override fun onCreate(savedInstanceState: Bundle?) {
        Util.applyTheme(AppPreferences.theme)
        super.onCreate(savedInstanceState)
        setContent {
            StepsyTheme(drawBackground = false) {
                PauseDialogHost(
                    onDismiss = { finish() },
                    onPauseForMinutes = { pauseForDuration(it) },
                    onPauseIndefinitely = { pauseIndefinitely() },
                    onPauseUntil = { hour, minute, now -> pauseUntil(hour, minute, now) },
                )
            }
        }
    }

    @Composable
    private fun PauseDialogHost(
        onDismiss: () -> Unit,
        onPauseForMinutes: (Int) -> Unit,
        onPauseIndefinitely: () -> Unit,
        onPauseUntil: (hour: Int, minute: Int, now: Calendar) -> Unit,
    ) {
        var showCustomTime by rememberSaveable { mutableStateOf(false) }
        var customNowMillis by rememberSaveable { mutableStateOf(0L) }
        var pauseDialogVisible by remember { mutableStateOf(true) }

        LaunchedEffect(showCustomTime) {
            if (showCustomTime) {
                delay(250)
                pauseDialogVisible = false
            }
        }

        if (showCustomTime) {
            val now = remember(customNowMillis) {
                Calendar.getInstance().apply { timeInMillis = customNowMillis }
            }
            TimePickerDialog(
                title = stringResource(R.string.resume_at_time),
                initialHour = now.get(Calendar.HOUR_OF_DAY),
                initialMinute = (now.get(Calendar.MINUTE) + 1).coerceAtMost(59),
                onConfirm = { hour, minute -> onPauseUntil(hour, minute, now) },
                onDismiss = onDismiss,
            )
        }

        if (pauseDialogVisible) {
            PauseStepCountingDialog(
                onDismiss = onDismiss,
                onPauseForMinutes = onPauseForMinutes,
                onCustomTime = {
                    customNowMillis = System.currentTimeMillis()
                    showCustomTime = true
                },
                onPauseIndefinitely = onPauseIndefinitely,
            )
        }
    }

    private fun pauseUntil(hour: Int, minute: Int, now: Calendar) {
        val resumeTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        val durationMinutes =
            ((resumeTime.timeInMillis - now.timeInMillis + 59_999L) / 60_000L).toInt()
                .coerceAtLeast(1)
        pauseForDuration(durationMinutes, resumeTime.timeInMillis)
    }

    private fun pauseForDuration(durationMinutes: Int, specificEndTime: Long = 0L) {
        val endTime = if (specificEndTime > 0L) specificEndTime
        else System.currentTimeMillis() + durationMinutes * 60_000L

        Intent(applicationContext, MotionService::class.java).also {
            it.action = MotionService.ACTION_PAUSE_COUNTING
            it.putExtra("TIMED_PAUSE", true)
            it.putExtra("END_TIME", endTime)
            it.putExtra("DURATION_MINUTES", durationMinutes)
            startService(it)
        }

        AppPreferences.isPaused = true

        val formatted = android.text.format.DateFormat
            .getTimeFormat(this)
            .format(java.util.Date(endTime))
        val message = getString(R.string.step_counting_paused_until, formatted)

        sendBroadcast(Intent("com.nvllz.stepsy.STATE_UPDATE").setPackage(packageName))
        finishWithPauseToast(message, Toast.LENGTH_LONG)
    }

    private fun pauseIndefinitely() {
        Intent(applicationContext, MotionService::class.java).also {
            it.action = MotionService.ACTION_PAUSE_COUNTING
            it.putExtra("TIMED_PAUSE", false)
            if (fromApp) it.putExtra(MotionService.EXTRA_SUPPRESS_TOAST, true)
            startService(it)
        }

        AppPreferences.isPaused = true

        sendBroadcast(Intent("com.nvllz.stepsy.STATE_UPDATE").setPackage(packageName))
        finishWithPauseToast(getString(R.string.step_counting_paused), Toast.LENGTH_SHORT)
    }

    private fun finishWithPauseToast(message: String, systemDuration: Int) {
        if (fromApp) {
            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_TOAST_TEXT, message))
        } else {
            Toast.makeText(this, message, systemDuration).show()
        }
        finish()
    }

    companion object {
        const val EXTRA_FROM_APP = "FROM_APP"
        const val EXTRA_TOAST_TEXT = "TOAST_TEXT"
    }
}
