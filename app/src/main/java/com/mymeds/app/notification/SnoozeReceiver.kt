package com.mymeds.app.notification

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Handles the "Snooze 15 min" action from dose reminder notifications.
 * Dismisses the current notification and schedules a new alarm 15 minutes later.
 */
class SnoozeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MyMeds"
        const val SNOOZE_DELAY_MILLIS = 15 * 60 * 1000L // 15 minutes
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "SnoozeReceiver: snooze requested")

        // Dismiss the current notification
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(DoseReminderReceiver.NOTIFICATION_ID)

        // Schedule a new alarm 15 minutes from now
        val triggerAt = System.currentTimeMillis() + SNOOZE_DELAY_MILLIS

        val alarmIntent = Intent(context, DoseReminderReceiver::class.java).apply {
            putExtra(DoseReminderReceiver.EXTRA_MEDICATION_ID, "snooze")
            putExtra(DoseReminderReceiver.EXTRA_MEDICATION_NAME, "Snoozed Reminder")
            putExtra(DoseReminderReceiver.EXTRA_SCHEDULED_TIME, "snooze")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            9999, // Unique request code for snooze
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent
                )
            }
            Log.d(TAG, "Snooze alarm set for 15 min from now")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set snooze alarm", e)
        }
    }
}
