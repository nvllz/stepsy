package com.nvllz.stepsy.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.nvllz.stepsy.R
import androidx.core.content.edit
import com.google.android.material.button.MaterialButtonToggleGroup
import com.nvllz.stepsy.util.AppPreferences

class WidgetPlainConfigureActivity : Activity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private fun themedContext(themeMode: String): android.content.Context {
        val uiMode = when (themeMode) {
            "light" -> Configuration.UI_MODE_NIGHT_NO
            "dark"  -> Configuration.UI_MODE_NIGHT_YES
            else    -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        }
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (uiMode == currentNightMode) return this
        val config = Configuration(resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or uiMode
        return createConfigurationContext(config)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        setContentView(R.layout.widget_plain_configure)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val prefs = getSharedPreferences("widget_prefs_$appWidgetId", MODE_MULTI_PROCESS)

        val saveButton           = findViewById<Button>(R.id.save_button)
        val opacitySlider        = findViewById<Slider>(R.id.opacity_slider)
        val textSizeSlider       = findViewById<Slider>(R.id.text_size_slider)
        val dynamicColorsSwitch  = findViewById<MaterialSwitch>(R.id.dynamic_colors_switch)
        val inverseBgColorSwitch = findViewById<MaterialSwitch>(R.id.inverse_bg_color)
        val previewContainer     = findViewById<FrameLayout>(R.id.preview_widget_plain_container)
        val themeToggle          = findViewById<MaterialButtonToggleGroup>(R.id.theme_toggle)

        val previewBg = ContextCompat.getDrawable(this, R.drawable.widget_bg)?.mutate()
        previewContainer.background = previewBg

        val currentOpacity = prefs.getInt("opacity", 100)
        opacitySlider.value = currentOpacity.toFloat()
        previewBg?.alpha = (255 * (currentOpacity / 100f)).toInt()

        opacitySlider.addOnChangeListener { _, value, _ ->
            val alpha = (255 * (value / 100f)).toInt()
            previewBg?.alpha = alpha
            updatePreviewColor(dynamicColorsSwitch.isChecked, value)
        }

        val useDynamicColors = prefs.getBoolean("use_dynamic_colors", true)
        dynamicColorsSwitch.isChecked = useDynamicColors
        dynamicColorsSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("use_dynamic_colors", isChecked) }
            updatePreviewColor(isChecked, opacitySlider.value)
        }

        inverseBgColorSwitch.setOnCheckedChangeListener { _, isChecked ->
            updatePreviewBackgroundColor(isChecked)
        }

        val textScale = prefs.getInt("text_scale", 100)
        textSizeSlider.value = textScale.toFloat()
        textSizeSlider.addOnChangeListener { _, value, _ ->
            prefs.edit { putInt("text_scale", value.toInt()) }
            applyTextSizeScale(value.toInt())
        }
        applyTextSizeScale(textScale)

        val savedTheme = prefs.getString("theme_mode", "system") ?: "system"
        themeToggle.check(when (savedTheme) {
            "light" -> R.id.theme_light
            "dark"  -> R.id.theme_dark
            else    -> R.id.theme_system
        })

        themeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val newTheme = when (checkedId) {
                R.id.theme_light -> "light"
                R.id.theme_dark  -> "dark"
                else             -> "system"
            }
            prefs.edit { putString("theme_mode", newTheme) }
            updatePreviewColor(dynamicColorsSwitch.isChecked, opacitySlider.value)
        }

        updatePreviewColor(useDynamicColors, opacitySlider.value)

        saveButton.setOnClickListener {
            prefs.edit {
                putInt("opacity", opacitySlider.value.toInt())
                apply()
            }

            val steps = AppPreferences.steps

            WidgetPlainProvider.updateWidget(this@WidgetPlainConfigureActivity, appWidgetId, steps)

            val resultValue = Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            setResult(RESULT_OK, resultValue)
            finish()
        }
    }

    private fun currentThemeMode(): String {
        val prefs = getSharedPreferences("widget_prefs_$appWidgetId", MODE_MULTI_PROCESS)
        return prefs.getString("theme_mode", "system") ?: "system"
    }

    private fun updatePreviewColor(useDynamicColors: Boolean, opacity: Float) {
        val ctx = themedContext(currentThemeMode())

        val colorRes = if (useDynamicColors && Build.VERSION.SDK_INT >= 31) {
            R.color.widgetBackground
        } else {
            R.color.widgetBackground_default
        }

        val color = ContextCompat.getColor(ctx, colorRes)
        val alphaColor = ColorUtils.setAlphaComponent(color, (255 * (opacity / 100f)).toInt())
        val drawable = ContextCompat.getDrawable(this, R.drawable.widget_bg)?.mutate()
        drawable?.setTint(alphaColor)
        findViewById<FrameLayout>(R.id.preview_widget_plain_container).background = drawable

        applyWidgetColors(useDynamicColors)
    }

    private fun updatePreviewBackgroundColor(inverse: Boolean) {
        val colorRes = if (inverse) R.color.colorOnSurface else R.color.colorSurface
        val color = ContextCompat.getColor(this, colorRes)
        findViewById<LinearLayout?>(R.id.outer_widget_plain_container)?.setBackgroundColor(color)
    }

    private fun applyWidgetColors(useDynamicColors: Boolean) {
        val ctx = themedContext(currentThemeMode())

        val primaryRes = if (useDynamicColors && Build.VERSION.SDK_INT >= 31) {
            R.color.widgetPrimary
        } else {
            R.color.widgetPrimary_default
        }

        val primaryColor = ContextCompat.getColor(ctx, primaryRes)

        findViewById<TextView?>(R.id.preview_widget_plain_steps)?.setTextColor(primaryColor)
    }

    private fun applyTextSizeScale(scalePercent: Int) {
        val stepsText = findViewById<TextView>(R.id.preview_widget_plain_steps)

        val factor = scalePercent / 100f

        stepsText.textSize = 22f * factor
    }
}
