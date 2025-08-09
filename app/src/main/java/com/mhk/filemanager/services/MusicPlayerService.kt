package com.mhk.filemanager.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media.session.MediaButtonReceiver
import com.mhk.filemanager.R
import com.mhk.filemanager.ui.player.MusicPlayerActivity
import java.io.File
import java.util.concurrent.TimeUnit

class MusicPlayerService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private val binder = MusicPlayerBinder()
    private var isLooping = false
    private lateinit var notificationManager: NotificationManager
    private var currentTrackName: String = ""
    private var currentFilePath: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var mediaSession: MediaSessionCompat
    private var playlist: ArrayList<String> = ArrayList()
    private var currentTrackIndex = -1


    companion object {
        const val CHANNEL_ID = "MusicPlayerServiceChannel"
        const val ACTION_PLAY_PAUSE = "com.mhk.filemanager.ACTION_PLAY_PAUSE"
        const val ACTION_STOP = "com.mhk.filemanager.ACTION_STOP"
        const val ACTION_TOGGLE_LOOP = "com.mhk.filemanager.ACTION_TOGGLE_LOOP"
        const val ACTION_NEXT = "com.mhk.filemanager.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.mhk.filemanager.ACTION_PREVIOUS"
        const val ACTION_STATE_UPDATE = "com.mhk.filemanager.ACTION_STATE_UPDATE"
        const val EXTRA_IS_PLAYING = "EXTRA_IS_PLAYING"
        const val EXTRA_IS_LOOPING = "EXTRA_IS_LOOPING"
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        mediaSession = MediaSessionCompat(this, "MusicPlayerService")
        mediaSession.setCallback(mediaSessionCallback)
        mediaSession.isActive = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)

        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_STOP -> {
                stopForeground(true)
                stopSelf()
            }
            ACTION_TOGGLE_LOOP -> toggleLooping()
            ACTION_NEXT -> playNextSong()
            ACTION_PREVIOUS -> playPreviousSong()
            else -> {
                val filePath = intent?.getStringExtra("filePath")
                val initialPosition = intent?.getIntExtra("initial_position", 0) ?: 0
                playlist = intent?.getStringArrayListExtra("playlist") ?: ArrayList()
                currentTrackIndex = playlist.indexOf(filePath)

                if (filePath != null) {
                    playSong(filePath, initialPosition)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun playSong(filePath: String, startPosition: Int = 0) {
        currentFilePath = filePath
        val file = File(filePath)
        currentTrackName = file.name

        mediaPlayer?.release()

        mediaPlayer = MediaPlayer().apply {
            setDataSource(applicationContext, file.toUri())
            prepare()
            seekTo(startPosition)
            start()
            setOnCompletionListener {
                if (!isLooping) {
                    playNextSong()
                } else {
                    it.seekTo(0)
                    it.start()
                }
            }
        }
        startForeground(1, createNotification())
        updatePlaybackState()
        broadcastState()
    }

    fun playNextSong() {
        if (playlist.isNotEmpty()) {
            currentTrackIndex = (currentTrackIndex + 1) % playlist.size
            playSong(playlist[currentTrackIndex])
        }
    }

    fun playPreviousSong() {
        if (playlist.isNotEmpty()) {
            currentTrackIndex = if (currentTrackIndex > 0) currentTrackIndex - 1 else playlist.size - 1
            playSong(playlist[currentTrackIndex])
        }
    }

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            if (mediaPlayer?.isPlaying == false) togglePlayPause()
        }

        override fun onPause() {
            if (mediaPlayer?.isPlaying == true) togglePlayPause()
        }

        override fun onSkipToNext() {
            playNextSong()
        }

        override fun onSkipToPrevious() {
            playPreviousSong()
        }
    }

    private val updateSeekBarRunnable: Runnable = object : Runnable {
        override fun run() {
            if (mediaPlayer != null && mediaPlayer!!.isPlaying) {
                updateNotification() // Update notification with seekbar progress
            }
            handler.postDelayed(this, 1000) // Update every second
        }
    }

    private fun startUpdatingSeekBar() {
        handler.post(updateSeekBarRunnable)
    }

    override fun onBind(intent: Intent): IBinder = binder

    inner class MusicPlayerBinder : Binder() {
        fun getService(): MusicPlayerService = this@MusicPlayerService
    }

    fun togglePlayPause() {
        if (mediaPlayer?.isPlaying == true) mediaPlayer?.pause() else mediaPlayer?.start()
        updatePlaybackState()
        updateNotification()
        broadcastState()
    }

    fun toggleLooping() {
        isLooping = !isLooping
        mediaPlayer?.isLooping = isLooping
        updateNotification()
        broadcastState()
    }

    private fun broadcastState() {
        val intent = Intent(ACTION_STATE_UPDATE).apply {
            putExtra(EXTRA_IS_PLAYING, isPlaying())
            putExtra(EXTRA_IS_LOOPING, isLooping())
        }
        sendBroadcast(intent)
    }

    private fun updatePlaybackState() {
        val state = if (isPlaying()) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, mediaPlayer?.currentPosition?.toLong() ?: 0L, 1.0f)
                .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
                .build()
        )
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        updatePlaybackState()
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false
    fun isLooping(): Boolean = isLooping
    fun getDuration(): Int = mediaPlayer?.duration ?: 0
    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0
    fun getTrackName(): String = currentTrackName

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "Music Player", NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MusicPlayerActivity::class.java)
        notificationIntent.putExtra("filePath", currentFilePath)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Setup RemoteViews for both layouts
        val collapsedView = RemoteViews(packageName, R.layout.notification_player_collapsed)
        val expandedView = RemoteViews(packageName, R.layout.notification_player_expanded)

        // Common actions
        val playPausePendingIntent = PendingIntent.getService(this, 1, Intent(this, MusicPlayerService::class.java).setAction(ACTION_PLAY_PAUSE), PendingIntent.FLAG_IMMUTABLE)
        val stopPendingIntent = PendingIntent.getService(this, 2, Intent(this, MusicPlayerService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val loopPendingIntent = PendingIntent.getService(this, 3, Intent(this, MusicPlayerService::class.java).setAction(ACTION_TOGGLE_LOOP), PendingIntent.FLAG_IMMUTABLE)
        val nextPendingIntent = PendingIntent.getService(this, 4, Intent(this, MusicPlayerService::class.java).setAction(ACTION_NEXT), PendingIntent.FLAG_IMMUTABLE)
        val prevPendingIntent = PendingIntent.getService(this, 5, Intent(this, MusicPlayerService::class.java).setAction(ACTION_PREVIOUS), PendingIntent.FLAG_IMMUTABLE)


        // --- Configure Collapsed View ---
        collapsedView.setViewVisibility(R.id.notification_album_art, View.GONE) // Hide album art
        collapsedView.setTextViewText(R.id.notification_track_name, currentTrackName)
        collapsedView.setImageViewResource(R.id.notification_play_pause, if (isPlaying()) R.drawable.baseline_pause_circle_outline_24 else R.drawable.baseline_play_circle_outline_24)
        collapsedView.setOnClickPendingIntent(R.id.notification_play_pause, playPausePendingIntent)
        collapsedView.setOnClickPendingIntent(R.id.notification_stop, stopPendingIntent)

        // --- Configure Expanded View ---
        expandedView.setTextViewText(R.id.notification_track_name_expanded, currentTrackName)
        expandedView.setImageViewResource(R.id.notification_play_pause_expanded, if (isPlaying()) R.drawable.baseline_pause_circle_outline_24 else R.drawable.baseline_play_circle_outline_24)
        val loopColor = if (isLooping()) ContextCompat.getColor(this, com.google.android.material.R.color.design_default_color_primary) else ContextCompat.getColor(this, R.color.md_theme_light_onSurfaceVariant)
        expandedView.setInt(R.id.notification_loop_expanded, "setColorFilter", loopColor)

        // Update seekbar and time text
        val duration = getDuration()
        val currentPosition = getCurrentPosition()
        expandedView.setProgressBar(R.id.notification_seekbar, duration, currentPosition, false)
        expandedView.setTextViewText(R.id.notification_current_time, formatDuration(currentPosition))
        expandedView.setTextViewText(R.id.notification_total_time, formatDuration(duration))

        expandedView.setOnClickPendingIntent(R.id.notification_play_pause_expanded, playPausePendingIntent)
        expandedView.setOnClickPendingIntent(R.id.notification_next_expanded, nextPendingIntent)
        expandedView.setOnClickPendingIntent(R.id.notification_previous_expanded, prevPendingIntent)
        expandedView.setOnClickPendingIntent(R.id.notification_loop_expanded, loopPendingIntent)


        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.baseline_music_note_24)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsedView)
            .setCustomBigContentView(expandedView)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun formatDuration(durationMs: Int): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs.toLong())
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs.toLong()) - TimeUnit.MINUTES.toSeconds(minutes)
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun updateNotification() {
        notificationManager.notify(1, createNotification())
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateSeekBarRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
        mediaSession.release()
    }
}
