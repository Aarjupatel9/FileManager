package com.mhk.filemanager.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.mhk.filemanager.R
import com.mhk.filemanager.services.MusicPlayerService
import com.mhk.filemanager.ui.player.MusicPlayerActivity

class MusicPlayerWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_WIDGET_PLAY_PAUSE = "com.mhk.filemanager.WIDGET_PLAY_PAUSE"
        const val ACTION_WIDGET_NEXT = "com.mhk.filemanager.WIDGET_NEXT"
        const val ACTION_WIDGET_PREVIOUS = "com.mhk.filemanager.WIDGET_PREVIOUS"

        fun updateAllWidgets(context: Context, trackName: String?, isPlaying: Boolean) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetComponent = ComponentName(context, MusicPlayerWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)

            if (appWidgetIds.isEmpty()) return

            for (appWidgetId in appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId, trackName, isPlaying)
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            trackName: String?,
            isPlaying: Boolean
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_music_player)

            // Track name
            if (!trackName.isNullOrEmpty()) {
                views.setTextViewText(R.id.widget_track_name, trackName)
                views.setTextViewText(R.id.widget_subtitle, "FileManager")
            } else {
                views.setTextViewText(R.id.widget_track_name, context.getString(R.string.widget_no_track))
                views.setTextViewText(R.id.widget_subtitle, context.getString(R.string.app_name))
            }

            // Play/Pause icon
            views.setImageViewResource(
                R.id.widget_play_pause,
                if (isPlaying) R.drawable.baseline_pause_circle_outline_24
                else R.drawable.baseline_play_circle_outline_24
            )

            // Button intents
            views.setOnClickPendingIntent(R.id.widget_play_pause, getWidgetPendingIntent(context, ACTION_WIDGET_PLAY_PAUSE, 1))
            views.setOnClickPendingIntent(R.id.widget_next, getWidgetPendingIntent(context, ACTION_WIDGET_NEXT, 2))
            views.setOnClickPendingIntent(R.id.widget_previous, getWidgetPendingIntent(context, ACTION_WIDGET_PREVIOUS, 3))

            // Tap on track name opens MusicPlayerActivity
            val openIntent = Intent(context, MusicPlayerActivity::class.java)
            val openPendingIntent = PendingIntent.getActivity(
                context, 4, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_track_name, openPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_subtitle, openPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun getWidgetPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, MusicPlayerWidgetProvider::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId, null, false)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_WIDGET_PLAY_PAUSE -> {
                val serviceIntent = Intent(context, MusicPlayerService::class.java).apply {
                    action = MusicPlayerService.ACTION_PLAY_PAUSE
                }
                androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
            }
            ACTION_WIDGET_NEXT -> {
                val serviceIntent = Intent(context, MusicPlayerService::class.java).apply {
                    action = MusicPlayerService.ACTION_NEXT
                }
                androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
            }
            ACTION_WIDGET_PREVIOUS -> {
                val serviceIntent = Intent(context, MusicPlayerService::class.java).apply {
                    action = MusicPlayerService.ACTION_PREVIOUS
                }
                androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}
