package com.nvllz.stepsy.ui.widgetconfig

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nvllz.stepsy.ui.theme.StepsyTheme
import com.nvllz.stepsy.util.AppPreferences

abstract class WidgetConfigureActivityBase(private val type: WidgetConfigType) :
    ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setResult(RESULT_CANCELED)

        enableEdgeToEdge()

        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val initial = WidgetPrefs(this, appWidgetId).load(type.defaultStepCountFormat)

        setContent {
            StepsyTheme {
                WidgetConfigScreen(
                    type = type,
                    appWidgetId = appWidgetId,
                    initial = initial,
                    onSave = { values ->
                        WidgetPrefs(this, appWidgetId).save(values)
                        type.update(this, appWidgetId, AppPreferences.steps)
                        setResult(
                            RESULT_OK,
                            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                        )
                        finish()
                    },
                )
            }
        }
    }
}
