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

class JapReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val file = File(context.filesDir, "jap_data.json")
        if (!file.exists()) return
        
        try {
            val json = JSONObject(file.readText())
            val targets = json.optJSONObject("targets") ?: return
            val dailyCounts = json.optJSONObject("daily_counts") ?: JSONObject()
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val todayObj = dailyCounts.optJSONObject(today) ?: JSONObject()
            
            var missedAny = false
            var reminderMessage = "You haven't reached your daily targets yet. Keep chanting!"
            
            val cats = targets.keys()
            while (cats.hasNext()) {
                val cat = cats.next()
                val catTargets = targets.optJSONObject(cat) ?: continue
                val dailyTarget = catTargets.optLong("daily", 0L)
                if (dailyTarget > 0) {
                    val current = todayObj.optLong(cat, 0L)
                    if (current < dailyTarget) {
                        missedAny = true
                        reminderMessage = "You are at $current / $dailyTarget for $cat. Keep chanting!"
                        break
                    }
                }
            }
            
            if (missedAny) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channelId = "jap_reminders_channel"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(channelId, "Nam Jap Reminders", NotificationManager.IMPORTANCE_DEFAULT)
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
                    .setContentTitle("Nam Jap Reminder")
                    .setContentText(reminderMessage)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    
                notificationManager.notify(1001, builder.build())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
