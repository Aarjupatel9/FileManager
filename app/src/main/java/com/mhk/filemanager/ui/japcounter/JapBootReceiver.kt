package com.mhk.filemanager.ui.japcounter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class JapBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {

            val prefs = context.getSharedPreferences("jap_prefs", Context.MODE_PRIVATE)

            // Re-schedule daily nudges if enabled
            if (prefs.getBoolean("daily_nudge_enabled", true)) {
                DailyNudgeReceiver.scheduleDailyNudges(context)
            }

            // Re-schedule interval reminders if enabled
            val intervalMins = prefs.getInt("reminderIntervalMins", 0)
            if (intervalMins > 0) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                val reminderIntent = Intent(context, JapReminderReceiver::class.java)
                val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                else android.app.PendingIntent.FLAG_UPDATE_CURRENT
                val pendingIntent = android.app.PendingIntent.getBroadcast(context, 0, reminderIntent, flags)
                val intervalMillis = intervalMins * 60 * 1000L
                alarmManager.setRepeating(
                    android.app.AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + intervalMillis,
                    intervalMillis,
                    pendingIntent
                )
            }
        }
    }
}
