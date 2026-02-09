package com.mymeds.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-schedules all dose reminder alarms after device boot.
 * AlarmManager alarms are lost on reboot, so this receiver restores them.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val prefs = context.getSharedPreferences("mymeds_prefs", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("notifications_enabled", true)
            if (!enabled) return

            CoroutineScope(Dispatchers.IO).launch {
                DoseAlarmScheduler.scheduleDoseAlarms(context)
            }
        }
    }
}
