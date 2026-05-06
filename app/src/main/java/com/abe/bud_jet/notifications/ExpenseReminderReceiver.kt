package com.abe.bud_jet.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.abe.bud_jet.R
import com.abe.bud_jet.database.preferences.PreferenceManager
import java.util.Calendar

class ExpenseReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: android.content.Intent) {
        val preferenceManager = PreferenceManager.getInstance(context)

        if (!preferenceManager.isNotificationsEnabled()) return
        if (!hasNotificationPermission(context)) return

        val todayStart = todayStartMillis()
        val lastSent = preferenceManager.getLastNotificationSentTime()

        // Send at most one reminder per day.
        if (lastSent >= todayStart) return

        sendReminderNotification(context)
        preferenceManager.setLastNotificationSentTime(todayStart)
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun todayStartMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun sendReminderNotification(context: Context) {
        val channelId = "expense_reminder"
        val channelName = context.getString(R.string.notifications_reminder_channel_name)
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val title = context.getString(R.string.notifications_reminder_title)
        val message = context.getString(R.string.notifications_reminder_message)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notifications_black_24dp)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(2026, notification)
    }
}

