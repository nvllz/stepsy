package com.nvllz.stepsy.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.nvllz.stepsy.R
import androidx.core.content.edit
import com.nvllz.stepsy.util.AppPreferences

class WidgetPlainConfigureActivity : Activity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        setContentView(R.layout.widget_plain_configure)

        // Retrieve widget ID
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val prefs = getSharedPreferences("widget_prefs_$appWidgetId", MODE_PRIVATE)

        val saveButton = findViewById<Button>(R.id.save_button)
        val opacitySlider = findViewById<Slider>(R.id.opacity_slider)
        val textSizeSlider = findViewById<Slider>(R.id.text_size_slider)
        val dynamicColorsSwitch = findViewById<MaterialSwitch>(R.id.dynamic_colors_switch)
        val inverseBgColorSwitch = findViewById<MaterialSwitch>(R.id.inverse_bg_color)
        val previewContainer = findViewById<FrameLayout>(R.id.preview_widget_plain_container)

        // Create dynamic preview string
        val stepsText = findViewById<TextView>(R.id.preview_widget_plain_steps)
        val stepsPreviewStr = resources.getQuantityString(
            R.plurals.steps_text,
            12345,
            12345
        )
        stepsText.text = stepsPreviewStr

        val previewBg = ContextCompat.getDrawable(this, R.drawable.widget_bg)?.mutate()
        previewContainer.background = previewBg

        // Opacity setup
        val currentOpacity = prefs.getInt("opacity", 100)
        opacitySlider.value = currentOpacity.toFloat()
        previewBg?.alpha = (255 * (currentOpacity / 100f)).toInt()

        opacitySlider.addOnChangeListener { _, value, _ ->
            val alpha = (255 * (value / 100f)).toInt()
            previewBg?.alpha = alpha
            updatePreviewColor(dynamicColorsSwitch.isChecked, value)
        }

        // Dynamic color setup
        val useDynamicColors = prefs.getBoolean("use_dynamic_colors", true)
        dynamicColorsSwitch.isChecked = useDynamicColors
        dynamicColorsSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("use_dynamic_colors", isChecked) }
            updatePreviewColor(isChecked, opacitySlider.value)
        }

        // Inverse background toggle
        inverseBgColorSwitch.setOnCheckedChangeListener { _, isChecked ->
            updatePreviewBackgroundColor(isChecked)
        }

        // Text setup
        val textScale = prefs.getInt("text_scale", 100)
        textSizeSlider.value = textScale.toFloat()
        textSizeSlider.addOnChangeListener { _, value, _ ->
            prefs.edit { putInt("text_scale", value.toInt()) }
            applyTextSizeScale(value.toInt())
        }
        applyTextSizeScale(textScale)

        // Initial preview update
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

    private fun updatePreviewColor(useDynamicColors: Boolean, opacity: Float) {
        val colorRes = if (useDynamicColors && Build.VERSION.SDK_INT >= 31) {
            R.color.widgetBackground
        } else {
            R.color.widgetBackground_default
        }

        val color = ContextCompat.getColor(this, colorRes)
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
        val primaryRes = if (useDynamicColors && Build.VERSION.SDK_INT >= 31) {
            R.color.widgetPrimary
        } else {
            R.color.widgetPrimary_default
        }

        val primaryColor = ContextCompat.getColor(this, primaryRes)

        findViewById<TextView?>(R.id.preview_widget_plain_steps)?.setTextColor(primaryColor)
    }

    private fun applyTextSizeScale(scalePercent: Int) {
        val stepsText = findViewById<TextView>(R.id.preview_widget_plain_steps)

        val baseStepsSize = 22f
        val factor = scalePercent / 100f

        stepsText.textSize = baseStepsSize * factor
    }
}
