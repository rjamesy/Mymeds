package com.mymeds.app.notification

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mymeds.app.MainActivity
import com.mymeds.app.R
import com.mymeds.app.data.db.AppDatabase
import com.mymeds.app.data.repository.MedsRepository
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
        const val NOTIFICATION_ID_OVERDUE = 1002
    }

    override suspend fun doWork(): Result {
        // Check if notifications are enabled
        val prefs = appContext.getSharedPreferences("mymeds_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("notifications_enabled", true)
        if (!enabled) return Result.success()

        // Check notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
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

        } catch (_: Exception) {
            // Don't crash — just retry next time
        }

        return Result.success()
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

        val notification = NotificationCompat.Builder(appContext, DoseReminderReceiver.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = appContext.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID_OVERDUE, notification)
    }
}
