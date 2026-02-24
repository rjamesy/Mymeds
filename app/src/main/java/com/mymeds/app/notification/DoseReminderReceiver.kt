package com.mymeds.app.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mymeds.app.MainActivity
import com.mymeds.app.R
import android.util.Log
import com.mymeds.app.data.db.AppDatabase
import com.mymeds.app.data.repository.MedsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * BroadcastReceiver that fires at each scheduled dose time via AlarmManager.
 * Checks if the dose is still pending and sends a notification if so.
 * After firing, reschedules alarms for remaining doses.
 */
class DoseReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "mymeds_dose_reminders"
        const val CHANNEL_NAME = "Dose Reminders"
        private const val TAG = "MyMeds"

        const val NOTIFICATION_ID = 1001

        const val EXTRA_MEDICATION_ID = "medication_id"
        const val EXTRA_MEDICATION_NAME = "medication_name"
        const val EXTRA_SCHEDULED_TIME = "scheduled_time"

        fun createNotificationChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for scheduled medication doses"
                enableVibration(true)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "DoseReminderReceiver.onReceive triggered")

        val prefs = context.getSharedPreferences("mymeds_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("notifications_enabled", true)
        if (!enabled) {
            Log.d(TAG, "Notifications disabled in preferences, skipping")
            return
        }

        // Check notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "POST_NOTIFICATIONS permission: $hasPermission")
            if (!hasPermission) return
        }

        val medId = intent.getStringExtra(EXTRA_MEDICATION_ID) ?: return
        val medName = intent.getStringExtra(EXTRA_MEDICATION_NAME) ?: return
        val scheduledTime = intent.getStringExtra(EXTRA_SCHEDULED_TIME) ?: return

        val isSnooze = medId == "snooze"
        Log.d(TAG, "Alarm for: $medName at $scheduledTime (id=$medId, snooze=$isSnooze)")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val repo = MedsRepository(db)
                val logsForToday = repo.ensureDoseLogsForDate(LocalDate.now())

                // Find all pending (not yet taken/skipped) scheduled doses
                val pendingDoses = logsForToday.filter {
                    it.status == "pending" && it.scheduledTime != "PRN"
                }

                // For snooze, skip the specific dose check — just check if any are pending
                if (!isSnooze) {
                    // Check if THIS specific dose is still pending
                    val thisStillPending = pendingDoses.any {
                        it.medicationId == medId && it.scheduledTime == scheduledTime
                    }

                    if (!thisStillPending) {
                        Log.d(TAG, "Dose already taken/skipped, skipping notification")
                        return@launch
                    }
                } else if (pendingDoses.isEmpty()) {
                    Log.d(TAG, "Snooze fired but no pending doses, skipping notification")
                    return@launch
                }

                // Count total overdue pending doses (scheduled time <= now)
                val overduePending = pendingDoses.filter { isTimeAtOrPast(it.scheduledTime) }

                // Get unique medication names for all overdue doses
                val overdueNames = overduePending.mapNotNull { log ->
                    db.medicationDao().getById(log.medicationId)?.name
                }.distinct()

                val title: String
                val body: String

                if (overdueNames.size > 1) {
                    title = "Medications Overdue"
                    body = "You have ${overdueNames.size} medications overdue"
                } else {
                    title = "Medication Reminder"
                    body = "You are due for your $medName"
                }

                showNotification(context, title, body, NOTIFICATION_ID)

                // Reschedule remaining alarms for upcoming doses
                DoseAlarmScheduler.scheduleDoseAlarms(context)

            } catch (e: Exception) {
                Log.e(TAG, "Error in DoseReminderReceiver", e)
                // If DB check fails, still send the notification for the known med
                showNotification(
                    context,
                    "Medication Reminder",
                    "You are due for your $medName",
                    NOTIFICATION_ID
                )
            }
        }
    }

    private fun isTimeAtOrPast(scheduledTime: String): Boolean {
        val parts = scheduledTime.split(":")
        if (parts.size != 2) return false
        val h = parts[0].toIntOrNull() ?: return false
        val m = parts[1].toIntOrNull() ?: return false
        val now = java.util.Calendar.getInstance()
        val scheduled = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, h)
            set(java.util.Calendar.MINUTE, m)
            set(java.util.Calendar.SECOND, 0)
        }
        return !now.before(scheduled)
    }

    private fun showNotification(context: Context, title: String, body: String, notificationId: Int) {
        createNotificationChannel(context)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze action — reschedule reminder for 15 minutes later
        val snoozeIntent = Intent(context, SnoozeReceiver::class.java)
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            9998, // Unique request code for snooze action
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_notification,
                "Remind in 15 min",
                snoozePendingIntent
            )
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, notification)
        Log.d(TAG, "Notification shown: id=$notificationId, title=$title")
    }
}
