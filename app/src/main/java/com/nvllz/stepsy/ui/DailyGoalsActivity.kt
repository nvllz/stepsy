package com.nvllz.stepsy.ui

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.widget.addTextChangedListener
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.nvllz.stepsy.R
import com.nvllz.stepsy.service.MotionService
import com.nvllz.stepsy.util.AppPreferences
import com.nvllz.stepsy.util.Database
import kotlinx.coroutines.launch

class DailyGoalsActivity : AppCompatActivity() {
    private lateinit var database: Database

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_daily_goals)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            setBackgroundDrawable(
                ContextCompat.getColor(
                    this@DailyGoalsActivity,
                    R.color.colorBackground
                ).toDrawable()
            )
            elevation = 0f
        }

        database = Database.getInstance(this)

        setupViews()
        initializePreferences()
    }

    private fun setupViews() {
        val notificationSwitch = findViewById<MaterialSwitch>(R.id.notification_switch)

        notificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                AppPreferences.dataStore.edit { preferences ->
                    preferences[AppPreferences.PreferenceKeys.DAILY_GOAL_NOTIFICATION] = isChecked
                }
            }
        }

        val notificationProgressbarSwitch = findViewById<MaterialSwitch>(R.id.notification_progressbar_switch)

        notificationProgressbarSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                AppPreferences.dataStore.edit { preferences ->
                    preferences[AppPreferences.PreferenceKeys.DAILY_GOAL_NOTIFICATION_PROGRESSBAR] = isChecked
                }
                updateNotification()
            }
        }

        findViewById<TextInputEditText>(R.id.goal_target_input).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            keyListener = DigitsKeyListener.getInstance("0123456789")

            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    saveGoalTargetIfValid(text.toString())
                }
            }

            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    saveGoalTargetIfValid(text.toString())
                    clearFocus()
                    true
                } else {
                    false
                }
            }

            addTextChangedListener { text ->
                if (!text.isNullOrEmpty()) {
                    saveGoalTargetIfValid(text.toString())
                }
            }
        }
    }

    private fun saveGoalTargetIfValid(text: String) {
        try {
            val target = text.toInt()
            if (target > 0) {
                lifecycleScope.launch {
                    AppPreferences.dataStore.edit { preferences ->
                        preferences[AppPreferences.PreferenceKeys.DAILY_GOAL_TARGET] = target
                    }
                    updateNotification()
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun initializePreferences() {
        val notificationEnabled = AppPreferences.dailyGoalNotification
        val notificationProgressbar = AppPreferences.dailyGoalNotificationProgressbar

        findViewById<MaterialSwitch>(R.id.notification_switch).isChecked = notificationEnabled
        findViewById<MaterialSwitch>(R.id.notification_progressbar_switch).isChecked = notificationProgressbar

        val dailyTarget = AppPreferences.dailyGoalTarget
        findViewById<TextInputEditText>(R.id.goal_target_input).setText(dailyTarget.toString())
    }

    private fun updateNotification() {
        val intent = Intent(this, MotionService::class.java)
        intent.action = "UPDATE_NOTIFICATION"

        intent.putExtra("show_progressbar", findViewById<MaterialSwitch>(R.id.notification_progressbar_switch).isChecked)
        intent.putExtra("daily_target", findViewById<TextInputEditText>(R.id.goal_target_input).text.toString().toIntOrNull() ?: 10000)

        startService(intent)
    }
}