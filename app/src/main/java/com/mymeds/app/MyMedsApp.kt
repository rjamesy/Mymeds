package com.mymeds.app

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mymeds.app.data.db.AppDatabase
import com.mymeds.app.notification.DoseAlarmScheduler
import com.mymeds.app.notification.DoseReminderReceiver
import com.mymeds.app.notification.OverdueDoseWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MyMedsApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Create notification channel
        DoseReminderReceiver.createNotificationChannel(this)

        // Schedule dose reminder alarms via AlarmManager
        appScope.launch {
            DoseAlarmScheduler.scheduleDoseAlarms(this@MyMedsApp)
        }

        // Start periodic WorkManager job to check for overdue doses every 15 minutes.
        // This is the reliable fallback — works even if AlarmManager alarms are
        // missed due to app being killed, Doze mode, etc.
        scheduleOverdueCheckWorker()
    }

    private fun scheduleOverdueCheckWorker() {
        val workRequest = PeriodicWorkRequestBuilder<OverdueDoseWorker>(
            15, TimeUnit.MINUTES
        ).setConstraints(
            Constraints.Builder().build()
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            OverdueDoseWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
