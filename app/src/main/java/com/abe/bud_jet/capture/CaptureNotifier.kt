package com.abe.bud_jet.capture

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.abe.bud_jet.MainActivity
import com.abe.bud_jet.R

/** One grouped reminder that some captured payments wait for the user's confirmation. */
object CaptureNotifier {

    private const val CHANNEL_ID = "capture_review"
    private const val NOTIFICATION_ID = 3031
    private const val SOURCE_NOTIFICATION_ID_BASE = 3100
    const val EXTRA_OPEN_CAPTURE = "open_auto_capture"

    fun showPendingReminder(context: Context, pendingCount: Int) {
        if (pendingCount <= 0) return
        val manager = prepare(context) ?: return
        val contentIntent = openCaptureIntent(context, NOTIFICATION_ID)
        val text = context.resources.getQuantityString(
            R.plurals.capture_pending_count,
            pendingCount,
            pendingCount
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications_black_24dp)
            .setContentTitle(context.getString(R.string.capture_pending_notification_title))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * The first payment-like notification from an untracked app is not added (only tracked
     * apps are read), so ask the user whether to track this app.
     */
    fun showSourceSuggestion(context: Context, appLabel: String) {
        val manager = prepare(context) ?: return
        val id = SOURCE_NOTIFICATION_ID_BASE + (appLabel.hashCode() and 0xFFFF)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications_black_24dp)
            .setContentTitle(context.getString(R.string.capture_source_notification_title, appLabel))
            .setContentText(context.getString(R.string.capture_source_notification_text))
            .setContentIntent(openCaptureIntent(context, id))
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
    }

    /** Null when notifications are not allowed. */
    private fun prepare(context: Context): NotificationManager? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return null
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.capture_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        return manager
    }

    private fun openCaptureIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_CAPTURE, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }
}
