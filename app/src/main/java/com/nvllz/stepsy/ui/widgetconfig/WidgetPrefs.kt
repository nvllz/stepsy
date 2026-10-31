package com.nvllz.stepsy.ui.widgetconfig

import android.content.Context
import androidx.core.content.edit

enum class WidgetThemeMode(val pref: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    companion object {
        fun fromPref(value: String?): WidgetThemeMode =
            entries.firstOrNull { it.pref == value } ?: SYSTEM
    }
}

data class WidgetConfigValues(
    val opacity: Int = 100,
    val useDynamicColors: Boolean = false,
    val textScale: Int = 100,
    val themeMode: WidgetThemeMode = WidgetThemeMode.SYSTEM,
)

class WidgetPrefs(context: Context, private val appWidgetId: Int) {

    private val prefs =
        context.getSharedPreferences("widget_prefs_$appWidgetId", Context.MODE_PRIVATE)

    fun load(): WidgetConfigValues = WidgetConfigValues(
        opacity = prefs.getInt(KEY_OPACITY, 100),
        useDynamicColors = prefs.getBoolean(KEY_DYNAMIC_COLORS, false),
        textScale = prefs.getInt(KEY_TEXT_SCALE, 100),
        themeMode = WidgetThemeMode.fromPref(prefs.getString(KEY_THEME_MODE, null)),
    )

    fun save(values: WidgetConfigValues) {
        prefs.edit {
            putInt(KEY_OPACITY, values.opacity)
            putBoolean(KEY_DYNAMIC_COLORS, values.useDynamicColors)
            putInt(KEY_TEXT_SCALE, values.textScale)
            putString(KEY_THEME_MODE, values.themeMode.pref)
        }
    }

    companion object {
        const val KEY_OPACITY = "opacity"
        const val KEY_DYNAMIC_COLORS = "use_dynamic_colors"
        const val KEY_TEXT_SCALE = "text_scale"
        const val KEY_THEME_MODE = "theme_mode"
    }
}
