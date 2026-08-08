package com.mhk.filemanager.ui.japcounter

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random

class DailyNudgeReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_TIME_SLOT = "time_slot" // 0=morning, 1=midday, 2=evening

        private val morningMessages = listOf(
            "A new day, a new beginning. Don't forget your special moments today.",
            "Today is a gift. Make time for what matters to your heart.",
            "Begin your day with intention. Your soul knows the way.",
            "Quiet moments shape the loudest days. Find yours today.",
            "The best time to start is now. Your inner journey awaits.",
            "Every sunrise is an invitation to reconnect with yourself.",
            "Take a moment today for what your heart holds sacred."
        )

        private val middayMessages = listOf(
            "Half the day has passed. Have you made time for your special practice?",
            "A gentle reminder: your soul deserves attention today.",
            "Pause. Breathe. Remember what grounds you.",
            "Don't let the day slip away without your moment of stillness.",
            "Amidst the busyness, find a quiet corner for yourself today.",
            "Your heart is waiting. Give it a few moments of your time."
        )

        private val eveningMessages = listOf(
            "The day is winding down. There's still time for what matters.",
            "Before the day ends, take a moment for yourself.",
            "Evenings are for reflection. Have you tended to your heart today?",
            "A few quiet minutes can change everything. Don't miss them.",
            "The night is near, but there's still time to nurture your soul.",
            "End your day the way it deserves — with a moment of stillness."
        )

        private val streakMessages = mapOf(
            7 to "7 days of dedication. Your consistency is beautiful.",
            30 to "A month of devotion. You're building something special.",
            100 to "100 days! Your commitment is inspiring."
        )

        fun scheduleDailyNudges(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT

            val calendar = java.util.Calendar.getInstance()

            // Morning — 9:00 AM
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 9)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            val morningIntent = Intent(context, DailyNudgeReceiver::class.java).putExtra(EXTRA_TIME_SLOT, 0)
            val morningPI = PendingIntent.getBroadcast(context, 2000, morningIntent, flags)
            alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, calendar.timeInMillis, morningPI)

            // Midday — 1:00 PM
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 13)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            val middayIntent = Intent(context, DailyNudgeReceiver::class.java).putExtra(EXTRA_TIME_SLOT, 1)
            val middayPI = PendingIntent.getBroadcast(context, 2001, middayIntent, flags)
            alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, calendar.timeInMillis, middayPI)

            // Evening — 7:00 PM
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 19)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            val eveningIntent = Intent(context, DailyNudgeReceiver::class.java).putExtra(EXTRA_TIME_SLOT, 2)
            val eveningPI = PendingIntent.getBroadcast(context, 2002, eveningIntent, flags)
            alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, calendar.timeInMillis, eveningPI)
        }

        fun cancelDailyNudges(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_NO_CREATE

            for (id in intArrayOf(2000, 2001, 2002)) {
                val intent = Intent(context, DailyNudgeReceiver::class.java)
                val pi = PendingIntent.getBroadcast(context, id, intent, flags)
                if (pi != null) alarmManager.cancel(pi)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val timeSlot = intent.getIntExtra(EXTRA_TIME_SLOT, 0)
        val prefs = context.getSharedPreferences("jap_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("daily_nudge_enabled", true)) return

        // Check today's count for smart logic
        val dataFile = File(context.filesDir, "jap_data.json")
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        var todayTotal = 0L
        var currentStreak = 0

        if (dataFile.exists()) {
            try {
                val json = JSONObject(dataFile.readText())
                val dailyCounts = json.optJSONObject("daily_counts") ?: JSONObject()
                val todayObj = dailyCounts.optJSONObject(today)
                if (todayObj != null) {
                    val keys = todayObj.keys()
                    while (keys.hasNext()) todayTotal += todayObj.optLong(keys.next(), 0L)
                }

                // Calculate streak
                val cal = java.util.Calendar.getInstance()
                for (i in 0 until 365) {
                    cal.time = Date()
                    cal.add(java.util.Calendar.DAY_OF_YEAR, -i)
                    val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                    val dayObj = dailyCounts.optJSONObject(dateStr)
                    var dayTotal = 0L
                    if (dayObj != null) {
                        val dk = dayObj.keys()
                        while (dk.hasNext()) dayTotal += dayObj.optLong(dk.next(), 0L)
                    }
                    if (dayTotal > 0) currentStreak++
                    else { if (i == 0) continue else break }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Streak celebration notification
        if (streakMessages.containsKey(currentStreak)) {
            showNotification(context, "A Beautiful Milestone", streakMessages[currentStreak]!!, 3000 + currentStreak)
            // Re-schedule for next day
            scheduleDailyNudges(context)
            return
        }

        // Smart logic: skip midday/evening if already counted today
        if (timeSlot == 1 && todayTotal > 0) {
            scheduleDailyNudges(context)
            return
        }

        // Smart logic: skip evening if target met (if targets exist)
        if (timeSlot == 2 && todayTotal > 0) {
            try {
                if (dataFile.exists()) {
                    val json = JSONObject(dataFile.readText())
                    val targets = json.optJSONObject("targets") ?: JSONObject()
                    val todayObj = json.optJSONObject("daily_counts")?.optJSONObject(today) ?: JSONObject()
                    var allMet = true
                    val tKeys = targets.keys()
                    while (tKeys.hasNext()) {
                        val cat = tKeys.next()
                        val dailyTarget = targets.optJSONObject(cat)?.optLong("daily", 0L) ?: 0L
                        if (dailyTarget > 0 && todayObj.optLong(cat, 0L) < dailyTarget) {
                            allMet = false
                            break
                        }
                    }
                    if (allMet && targets.length() > 0) {
                        scheduleDailyNudges(context)
                        return
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val messages = when (timeSlot) {
            0 -> morningMessages
            1 -> middayMessages
            else -> eveningMessages
        }
        val message = messages[Random().nextInt(messages.size)]
        val title = when (timeSlot) {
            0 -> "A Gentle Start"
            1 -> "A Gentle Reminder"
            else -> "Before the Day Ends"
        }

        showNotification(context, title, message, 2000 + timeSlot)

        // Re-schedule for next day
        scheduleDailyNudges(context)
    }

    private fun showNotification(context: Context, title: String, message: String, notifId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "daily_nudge_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Daily Gentle Reminders",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Gentle daily reminders to nurture your practice"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 100, 100, 100)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val contentIntent = Intent(context, JapCounterActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, contentIntent, pendingIntentFlags)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.mhk.filemanager.R.drawable.baseline_vibration_24)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        notificationManager.notify(notifId, builder.build())
    }
}
