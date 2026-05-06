package com.abe.bud_jet.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object NotificationReminderScheduler {
    const val ACTION_EXPENSE_REMINDER = "com.abe.bud_jet.action.EXPENSE_REMINDER"
    private const val REQUEST_CODE = 4242

    /**
     * Schedules an inexact daily reminder around 20:00 device local time.
     * The receiver sends at most one reminder per day and checks
     * whether notifications are enabled and permission is granted.
     */
    fun scheduleDailyExpenseReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val triggerAtMillis = nextEvening20hMillis()
        val intervalMillis = 24L * 60L * 60L * 1000L

        val intent = Intent(context, ExpenseReminderReceiver::class.java).apply {
            action = ACTION_EXPENSE_REMINDER
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            intervalMillis,
            pendingIntent
        )
    }

    fun cancelDailyExpenseReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ExpenseReminderReceiver::class.java).apply {
            action = ACTION_EXPENSE_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun nextEvening20hMillis(): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis
    }
}

