package com.abe.bud_jet.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.abe.bud_jet.database.preferences.PreferenceManager

/** Alarms are cleared on reboot and app update; restore the daily reminder if it is enabled. */
class ReminderRescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!PreferenceManager.getInstance(context).isNotificationsEnabled()) return
        NotificationReminderScheduler.scheduleDailyExpenseReminder(context)
    }
}
