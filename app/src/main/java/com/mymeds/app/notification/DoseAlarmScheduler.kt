package com.mymeds.app.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.mymeds.app.data.db.AppDatabase
import com.mymeds.app.data.model.DoseLog
import com.mymeds.app.data.model.Medication
import com.mymeds.app.data.repository.MedsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Schedules exact alarms for each medication dose time using AlarmManager.
 *
 * - Schedules alarms for today's and tomorrow's pending dose logs
 * - Uses stable request codes based on dose-log IDs
 * - Called on app start, after medication changes, and on device boot
 */
object DoseAlarmScheduler {

    private const val TAG = "MyMeds"

    /**
     * Cancel all existing dose reminder alarms, then schedule new ones
     * for today's remaining pending doses and all of tomorrow's doses.
     */
    suspend fun scheduleDoseAlarms(context: Context) {
        Log.d(TAG, "scheduleDoseAlarms: starting")
        val prefs = context.getSharedPreferences("mymeds_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("notifications_enabled", true)
        if (!enabled) {
            Log.d(TAG, "scheduleDoseAlarms: notifications disabled, cancelling all")
            cancelAllAlarms(context)
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(context)
                val repo = MedsRepository(db)
                val meds = db.medicationDao().getAllOnce()
                val medNamesById = meds.associate { it.id to it.name }
                val activeMedIds = meds.filter { it.active }.map { it.id }.toSet()
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

                val today = LocalDate.now()
                val tomorrow = today.plusDays(1)
                val now = LocalDateTime.now()

                val todayLogs = repo.ensureDoseLogsForDate(today)
                val tomorrowLogs = repo.ensureDoseLogsForDate(tomorrow)

                cancelKnownAlarms(
                    context = context,
                    alarmManager = alarmManager,
                    logs = todayLogs + tomorrowLogs,
                    meds = meds
                )

                val schedulableLogs = (todayLogs + tomorrowLogs)
                    .asSequence()
                    .filter {
                        it.medicationId in activeMedIds &&
                            it.status == "pending" &&
                            it.scheduledTime != "PRN"
                    }
                    .mapNotNull { log ->
                        val triggerDateTime = parseScheduledDateTime(log) ?: return@mapNotNull null
                        if (!triggerDateTime.isAfter(now)) return@mapNotNull null
                        val medName = medNamesById[log.medicationId] ?: return@mapNotNull null
                        Triple(log, medName, triggerDateTime)
                    }
                    .toList()

                for ((log, medName, triggerDateTime) in schedulableLogs) {
                    val triggerMillis = triggerDateTime.atZone(ZoneId.systemDefault())
                        .toInstant().toEpochMilli()
                    scheduleAlarm(
                        context = context,
                        alarmManager = alarmManager,
                        medId = log.medicationId,
                        medName = medName,
                        scheduledTime = log.scheduledTime,
                        triggerAtMillis = triggerMillis,
                        requestCode = generateDoseLogRequestCode(log.id),
                        date = triggerDateTime.toLocalDate()
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "scheduleDoseAlarms: error", e)
            }
        }
    }

    private fun cancelKnownAlarms(
        context: Context,
        alarmManager: AlarmManager,
        logs: List<DoseLog>,
        meds: List<Medication>
    ) {
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val dates = listOf(today, tomorrow)

        // Current scheme: request code by dose-log id.
        logs.map { it.id }
            .distinct()
            .forEach { doseLogId ->
                cancelRequestCode(
                    context = context,
                    alarmManager = alarmManager,
                    requestCode = generateDoseLogRequestCode(doseLogId)
                )
            }

        // Legacy scheme: request code by medId + scheduledTime + date.
        meds.forEach { med ->
            med.scheduledTimes
                .filter { it != "PRN" }
                .forEach { time ->
                    dates.forEach { date ->
                        cancelRequestCode(
                            context = context,
                            alarmManager = alarmManager,
                            requestCode = generateLegacyRequestCode(med.id, time, date)
                        )
                    }
                }
        }
    }

    private fun cancelRequestCode(
        context: Context,
        alarmManager: AlarmManager,
        requestCode: Int
    ) {
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

    private fun parseScheduledDateTime(log: DoseLog): LocalDateTime? {
        val date = runCatching { LocalDate.parse(log.scheduledDate) }.getOrNull() ?: return null
        val parts = log.scheduledTime.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return date.atTime(hour, minute)
    }

    private fun scheduleAlarm(
        context: Context,
        alarmManager: AlarmManager,
        medId: String,
        medName: String,
        scheduledTime: String,
        triggerAtMillis: Long,
        requestCode: Int,
        date: LocalDate
    ) {
        val intent = Intent(context, DoseReminderReceiver::class.java).apply {
            putExtra(DoseReminderReceiver.EXTRA_MEDICATION_ID, medId)
            putExtra(DoseReminderReceiver.EXTRA_MEDICATION_NAME, medName)
            putExtra(DoseReminderReceiver.EXTRA_SCHEDULED_TIME, scheduledTime)
        }

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
                    Log.d(TAG, "Exact alarm scheduled: $medName at $scheduledTime on $date")
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                    )
                    Log.d(TAG, "Inexact alarm scheduled (no exact perm): $medName at $scheduledTime on $date")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                )
                Log.d(TAG, "Exact alarm scheduled: $medName at $scheduledTime on $date")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling exact alarm for $medName", e)
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                )
                Log.d(TAG, "Fallback inexact alarm scheduled: $medName at $scheduledTime on $date")
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to schedule any alarm for $medName", e2)
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
                val repo = MedsRepository(db)
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val today = LocalDate.now()
                val tomorrow = today.plusDays(1)
                val logs = repo.ensureDoseLogsForDate(today) + repo.ensureDoseLogsForDate(tomorrow)

                cancelKnownAlarms(
                    context = context,
                    alarmManager = alarmManager,
                    logs = logs,
                    meds = meds
                )
            } catch (e: Exception) {
                Log.e(TAG, "cancelAllAlarms: error", e)
            }
        }
    }

    /**
     * Generate a stable request code from dose-log ID.
     */
    private fun generateDoseLogRequestCode(doseLogId: String): Int {
        return ("log|$doseLogId").hashCode() and 0x7FFFFFFF
    }

    /**
     * Legacy request code format (medId + time + date) kept for backward alarm cleanup.
     */
    private fun generateLegacyRequestCode(medId: String, time: String, date: LocalDate): Int {
        val key = "$medId|$time|$date"
        return key.hashCode() and 0x7FFFFFFF // Ensure positive
    }
}
