package com.abe.bud_jet.capture

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat

/** Notification access and battery-optimization state for automatic capture. */
object CaptureAccess {

    fun component(context: Context) = ComponentName(context, NotificationCaptureService::class.java)

    /** Notification access is granted only in system settings, not via a runtime dialog. */
    fun isAccessGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** Opens the system screen where the user grants notification access to Bud-Jet. */
    fun openAccessSettings(context: Context) {
        val detail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                component(context).flattenToString()
            )
        } else {
            null
        }
        val general = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        // Some OEM builds lack the detail screen; fall back to the list.
        val opened = listOfNotNull(detail, general).any { intent ->
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        }
        if (!opened) {
            runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    /** Aggressive battery savers (Samsung, Xiaomi, Huawei...) may stop the listener. */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Opens the battery optimization list where the user can exempt Bud-Jet. The direct
     * "ignore optimizations" dialog needs a permission Google Play restricts, so it is not used.
     */
    fun openBatterySettings(context: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        intents.any { intent ->
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        }
    }

    /** Asks the system to reconnect the listener, e.g. after it was killed. */
    fun requestRebind(context: Context) {
        if (!isAccessGranted(context)) return
        runCatching { NotificationListenerService.requestRebind(component(context)) }
    }
}
