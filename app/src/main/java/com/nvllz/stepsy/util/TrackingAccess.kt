package com.nvllz.stepsy.util

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

internal object TrackingAccess {

    enum class Ask { GRANTED, DENIED, BLOCKED }

    fun hasStepSensor(ctx: Context): Boolean =
        ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER)

    private fun isGranted(ctx: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED

    fun stepPermissionMissing(ctx: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasStepSensor(ctx) &&
            !isGranted(ctx, Manifest.permission.ACTIVITY_RECOGNITION)

    fun stepPermissions(): Array<String> = buildList {
        add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(Manifest.permission.FOREGROUND_SERVICE_HEALTH)
        }
    }.toTypedArray()

    fun notificationPermissionMissing(ctx: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !isGranted(ctx, Manifest.permission.POST_NOTIFICATIONS)

    fun batteryExempt(ctx: Context): Boolean =
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(ctx.packageName)

    fun judge(ctx: Context, permission: String): Ask {
        if (isGranted(ctx, permission)) return Ask.GRANTED
        val activity = ctx.findActivity() ?: return Ask.DENIED
        return if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) Ask.DENIED
        else Ask.BLOCKED
    }

    private fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    fun appSettingsIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))

    fun notificationSettingsIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)

    fun batteryIntents(ctx: Context): List<Intent> = listOf(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        appSettingsIntent(ctx),
    )

    fun launchFirst(intents: List<Intent>, launch: (Intent) -> Unit): Int {
        intents.forEachIndexed { index, intent ->
            try {
                launch(intent)
                return index
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        return -1
    }

    fun open(ctx: Context, vararg intents: Intent): Boolean =
        launchFirst(intents.toList()) { ctx.startActivity(it) } >= 0
}
