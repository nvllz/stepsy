package com.nvllz.stepsy.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.nvllz.stepsy.R
import com.nvllz.stepsy.service.MotionService
import android.widget.Toast
import androidx.core.content.edit
import java.util.Calendar

class TileDialogActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showPauseOptionsDialog()
    }

    private fun showPauseOptionsDialog() {
        data class Option(val labelRes: Int, val action: () -> Unit)

        val options = listOf(
            Option(R.string.pause_30_minutes)  { pauseForDuration(30) },
            Option(R.string.pause_1_hour)      { pauseForDuration(60) },
            Option(R.string.pause_2_hours)     { pauseForDuration(120) },
            Option(R.string.pause_custom_time) { showCustomDurationDialog() },
            Option(R.string.pause_indefinitely){ pauseIndefinitely() }
        )

        val entries = options.map { getString(it.labelRes) }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pause_step_counting)
            .setSingleChoiceItems(entries, -1) { dialog, which ->
                options[which].action()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun showCustomDurationDialog() {
        val now = Calendar.getInstance()

        val picker = MaterialTimePicker.Builder()
            .setTitleText(R.string.resume_at_time)
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(now.get(Calendar.HOUR_OF_DAY))
            .setMinute((now.get(Calendar.MINUTE) + 1).coerceAtMost(59))
            .setTheme(R.style.ThemeOverlay_stepsy_TimePicker)
            .build()

        picker.addOnPositiveButtonClickListener {
            val resumeTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, picker.hour)
                set(Calendar.MINUTE, picker.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
            }
            val durationMinutes =
                ((resumeTime.timeInMillis - now.timeInMillis) / 60_000L).toInt()
            pauseForDuration(durationMinutes, resumeTime.timeInMillis)
        }

        picker.addOnNegativeButtonClickListener { finish() }
        picker.addOnCancelListener { finish() }

        picker.show(supportFragmentManager, "tile_time_picker")
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

        getSharedPreferences("StepsyPrefs", MODE_PRIVATE).edit {
            putBoolean(MotionService.KEY_IS_PAUSED, true)
        }

        val formatted = android.text.format.DateFormat
            .getTimeFormat(this)
            .format(java.util.Date(endTime))
        Toast.makeText(
            this,
            getString(R.string.step_counting_paused_until, formatted),
            Toast.LENGTH_LONG
        ).show()

        sendBroadcast(Intent("com.nvllz.stepsy.STATE_UPDATE"))
        finish()
    }

    private fun pauseIndefinitely() {
        Intent(applicationContext, MotionService::class.java).also {
            it.action = MotionService.ACTION_PAUSE_COUNTING
            it.putExtra("TIMED_PAUSE", false)
            startService(it)
        }

        getSharedPreferences("StepsyPrefs", MODE_PRIVATE).edit {
            putBoolean(MotionService.KEY_IS_PAUSED, true)
        }

        Toast.makeText(this, R.string.step_counting_paused, Toast.LENGTH_SHORT).show()
        sendBroadcast(Intent("com.nvllz.stepsy.STATE_UPDATE"))
        finish()
    }
}
