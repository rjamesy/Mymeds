package com.mymeds.app.notification

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mymeds.app.MainActivity
import com.mymeds.app.R
import com.mymeds.app.data.db.AppDatabase
import com.mymeds.app.data.repository.MedsRepository
import com.mymeds.app.util.getDaysSupply
import com.mymeds.app.util.getStockStatus
import java.time.LocalDate
import java.util.Calendar

/**
 * Periodic WorkManager worker that runs every 15 minutes.
 * Checks if any scheduled doses are overdue (past their time and still pending).
 * Sends a notification if overdue doses are found.
 *
 * This is the reliable fallback — AlarmManager alarms may be missed if
 * the app was killed, but WorkManager persists across app restarts.
 */
class OverdueDoseWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val WORK_NAME = "overdue_dose_check"
        private const val TAG = "MyMeds"
        private const val REFILL_NOTIFICATION_ID = 1003
        private const val REFILL_PREFS_KEY = "last_refill_notify_date"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "OverdueDoseWorker.doWork running")

        // Check if notifications are enabled
        val prefs = appContext.getSharedPreferences("mymeds_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("notifications_enabled", true)
        if (!enabled) {
            Log.d(TAG, "OverdueDoseWorker: notifications disabled")
            return Result.success()
        }

        // Check notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "OverdueDoseWorker: POST_NOTIFICATIONS=$hasPermission")
            if (!hasPermission) return Result.success()
        }

        try {
            val db = AppDatabase.getInstance(appContext)
            val repo = MedsRepository(db)
            val logsForToday = repo.ensureDoseLogsForDate(LocalDate.now())

            // Find all pending scheduled doses that are overdue
            val overdueDoses = logsForToday.filter { log ->
                log.status == "pending" && log.scheduledTime != "PRN" && isTimePast(log.scheduledTime)
            }

            if (overdueDoses.isEmpty()) return Result.success()

            // Get medication names for the overdue doses
            val medNames = overdueDoses.mapNotNull { log ->
                db.medicationDao().getById(log.medicationId)?.name
            }.distinct()

            val title: String
            val body: String

            if (medNames.size > 1) {
                title = "Medications Overdue"
                body = "You have ${medNames.size} medications overdue"
            } else if (medNames.size == 1) {
                title = "Medication Reminder"
                body = "You are due for your ${medNames.first()}"
            } else {
                return Result.success()
            }

            showNotification(title, body)

            // Also reschedule alarms for any upcoming doses today
            DoseAlarmScheduler.scheduleDoseAlarms(appContext)

        } catch (e: Exception) {
            Log.e(TAG, "OverdueDoseWorker error", e)
        }

        // Check for low stock medications (once per day)
        checkLowStock()

        return Result.success()
    }

    /**
     * Check if any active medications have low/critical stock and send
     * a single refill reminder notification. Only fires once per day.
     */
    private suspend fun checkLowStock() {
        try {
            val prefs = appContext.getSharedPreferences("mymeds_prefs", Context.MODE_PRIVATE)
            val today = LocalDate.now().toString()
            val lastNotifyDate = prefs.getString(REFILL_PREFS_KEY, "")

            // Only notify once per day
            if (lastNotifyDate == today) return

            val db = AppDatabase.getInstance(appContext)
            val activeMeds = db.medicationDao().getAllOnce().filter { it.active }

            val lowStockMeds = activeMeds.filter { med ->
                val status = getStockStatus(med)
                status == "critical" || status == "empty" || status == "low"
            }

            if (lowStockMeds.isEmpty()) return

            val refillTitle: String
            val refillBody: String

            if (lowStockMeds.size == 1) {
                val med = lowStockMeds.first()
                val daysLeft = getDaysSupply(med)
                refillTitle = "Refill Reminder"
                refillBody = if (med.currentStock <= 0) {
                    "${med.name} is out of stock"
                } else {
                    "${med.name} — ${daysLeft} day${if (daysLeft != 1) "s" else ""} supply remaining"
                }
            } else {
                refillTitle = "Refill Reminder"
                refillBody = "${lowStockMeds.size} medications running low on stock"
            }

            showRefillNotification(refillTitle, refillBody)
            prefs.edit().putString(REFILL_PREFS_KEY, today).apply()

        } catch (e: Exception) {
            Log.e(TAG, "checkLowStock error", e)
        }
    }

    private fun showRefillNotification(title: String, body: String) {
        DoseReminderReceiver.createNotificationChannel(appContext)

        val tapIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            1,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, DoseReminderReceiver.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = appContext.getSystemService(NotificationManager::class.java)
        manager.notify(REFILL_NOTIFICATION_ID, notification)
        Log.d(TAG, "Refill notification shown: $title")
    }

    private fun isTimePast(scheduledTime: String): Boolean {
        val parts = scheduledTime.split(":")
        if (parts.size != 2) return false
        val h = parts[0].toIntOrNull() ?: return false
        val m = parts[1].toIntOrNull() ?: return false
        val now = Calendar.getInstance()
        val scheduled = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
        }
        return now.after(scheduled)
    }

    private fun showNotification(title: String, body: String) {
        DoseReminderReceiver.createNotificationChannel(appContext)

        val tapIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze action — reschedule reminder for 15 minutes later
        val snoozeIntent = Intent(appContext, SnoozeReceiver::class.java)
        val snoozePendingIntent = PendingIntent.getBroadcast(
            appContext,
            9998,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, DoseReminderReceiver.CHANNEL_ID)
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

        val manager = appContext.getSystemService(NotificationManager::class.java)
        manager.notify(DoseReminderReceiver.NOTIFICATION_ID, notification)
        Log.d(TAG, "Overdue notification shown: $title")
    }
}
