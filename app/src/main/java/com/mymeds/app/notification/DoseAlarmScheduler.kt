package com.mymeds.app.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mymeds.app.data.db.AppDatabase
import com.mymeds.app.data.repository.MedsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Schedules exact alarms for each medication dose time using AlarmManager.
 *
 * - Schedules alarms for today's remaining doses and tomorrow's doses
 * - Uses unique request codes per (medicationId, time) pair
 * - Called on app start, after medication changes, and on device boot
 */
object DoseAlarmScheduler {

    /**
     * Cancel all existing dose reminder alarms, then schedule new ones
     * for today's remaining pending doses and all of tomorrow's doses.
     */
    suspend fun scheduleDoseAlarms(context: Context) {
        val prefs = context.getSharedPreferences("mymeds_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("notifications_enabled", true)
        if (!enabled) {
            cancelAllAlarms(context)
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(context)
                val repo = MedsRepository(db)
                val activeMeds = db.medicationDao().getAllOnce().filter { it.active }
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

                val today = LocalDate.now()
                val tomorrow = today.plusDays(1)
                val now = LocalDateTime.now()

                // Ensure today's logs exist so alarms and background checks are aligned.
                val todayLogs = repo.ensureDoseLogsForDate(today)
                val takenOrSkippedKeys = todayLogs
                    .filter { it.status == "taken" || it.status == "skipped" }
                    .map { it.medicationId to it.scheduledTime }
                    .toSet()

                for (med in activeMeds) {
                    if (med.frequency == "as_needed") continue

                    // Check if weekly medication should fire today/tomorrow
                    val createdDate = try {
                        LocalDate.parse(med.createdAt.take(10))
                    } catch (_: Exception) {
                        try {
                            LocalDateTime.parse(
                                med.createdAt,
                                DateTimeFormatter.ISO_LOCAL_DATE_TIME
                            ).toLocalDate()
                        } catch (_: Exception) {
                            null
                        }
                    }

                    for (time in med.scheduledTimes) {
                        if (time == "PRN") continue

                        val parts = time.split(":")
                        if (parts.size != 2) continue
                        val hour = parts[0].toIntOrNull() ?: continue
                        val minute = parts[1].toIntOrNull() ?: continue

                        // Schedule for today if not already taken/skipped and time hasn't passed
                        val todayDateTime = today.atTime(hour, minute)
                        if (todayDateTime.isAfter(now)) {
                            val key = med.id to time
                            if (key !in takenOrSkippedKeys) {
                                // For weekly meds, only schedule if today is the right day
                                if (med.frequency == "weekly" && createdDate != null) {
                                    if (today.dayOfWeek != createdDate.dayOfWeek) continue
                                }

                                val triggerMillis = todayDateTime.atZone(ZoneId.systemDefault())
                                    .toInstant().toEpochMilli()
                                scheduleAlarm(
                                    context, alarmManager, med.id, med.name, time,
                                    triggerMillis, today
                                )
                            }
                        }

                        // Schedule for tomorrow
                        val tomorrowShouldSchedule = if (med.frequency == "weekly" && createdDate != null) {
                            tomorrow.dayOfWeek == createdDate.dayOfWeek
                        } else {
                            true
                        }

                        if (tomorrowShouldSchedule) {
                            val tomorrowDateTime = tomorrow.atTime(hour, minute)
                            val triggerMillis = tomorrowDateTime.atZone(ZoneId.systemDefault())
                                .toInstant().toEpochMilli()
                            scheduleAlarm(
                                context, alarmManager, med.id, med.name, time,
                                triggerMillis, tomorrow
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // Silently fail — don't crash the app over notifications
            }
        }
    }

    private fun scheduleAlarm(
        context: Context,
        alarmManager: AlarmManager,
        medId: String,
        medName: String,
        scheduledTime: String,
        triggerAtMillis: Long,
        date: LocalDate
    ) {
        val intent = Intent(context, DoseReminderReceiver::class.java).apply {
            putExtra(DoseReminderReceiver.EXTRA_MEDICATION_ID, medId)
            putExtra(DoseReminderReceiver.EXTRA_MEDICATION_NAME, medName)
            putExtra(DoseReminderReceiver.EXTRA_SCHEDULED_TIME, scheduledTime)
        }

        val requestCode = generateRequestCode(medId, scheduledTime, date)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                    )
                } else {
                    // Fallback: use inexact alarm
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                )
            }
        } catch (_: SecurityException) {
            // If exact alarm permission denied, use inexact
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                )
            } catch (_: Exception) {
                // Give up silently
            }
        }
    }

    /**
     * Cancel all pending alarms. Called when notifications are disabled.
     */
    fun cancelAllAlarms(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val meds = db.medicationDao().getAllOnce()
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val dates = listOf(LocalDate.now(), LocalDate.now().plusDays(1))

                for (med in meds) {
                    for (time in med.scheduledTimes) {
                        if (time == "PRN") continue
                        for (date in dates) {
                            val requestCode = generateRequestCode(med.id, time, date)
                            val pendingIntent = PendingIntent.getBroadcast(
                                context,
                                requestCode,
                                Intent(context, DoseReminderReceiver::class.java),
                                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                            )
                            if (pendingIntent != null) {
                                alarmManager.cancel(pendingIntent)
                                pendingIntent.cancel()
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Best effort; ignore failures so notification toggle never crashes.
            }
        }
    }

    /**
     * Generate a stable request code from medication ID + time + date.
     */
    private fun generateRequestCode(medId: String, time: String, date: LocalDate): Int {
        val key = "$medId|$time|$date"
        return key.hashCode() and 0x7FFFFFFF // Ensure positive
    }
}
