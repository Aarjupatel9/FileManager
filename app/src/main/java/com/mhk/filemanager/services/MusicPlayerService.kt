package com.mhk.filemanager.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media.session.MediaButtonReceiver
import com.mhk.filemanager.R
import com.mhk.filemanager.ui.player.MusicPlayerActivity
import java.io.File
import java.util.concurrent.TimeUnit
import android.content.pm.ServiceInfo
import android.util.Log
import com.mhk.filemanager.widget.MusicPlayerWidgetProvider

class MusicPlayerService : MediaBrowserServiceCompat() {

    private var mediaPlayer: MediaPlayer? = null
    private val binder = MusicPlayerBinder()
    private var isRepeatEnabled = false
    private lateinit var notificationManager: NotificationManager
    private var currentTrackName: String = ""
    private var currentFilePath: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var mediaSession: MediaSessionCompat
    private var playlist: ArrayList<String> = ArrayList()
    private var currentTrackIndex = -1
    private var lastSkipTime = 0L
    private val SKIP_DEBOUNCE_MS = 1500L
    private var playbackStartTime = 0L
    private var isProgressRunnableRunning = false
    private var isPrepared = false
    private lateinit var prefs: SharedPreferences

    // Audio Focus
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var wasPlayingBeforeFocusLoss = false

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent loss (another app took focus) — pause
                if (isPlaying()) { mediaPlayer?.pause(); wasPlayingBeforeFocusLoss = false }
                updatePlaybackState(); updateNotification(); broadcastState()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Transient loss (phone call, navigation) — pause, remember state
                wasPlayingBeforeFocusLoss = isPlaying()
                if (wasPlayingBeforeFocusLoss) mediaPlayer?.pause()
                updatePlaybackState(); updateNotification(); broadcastState()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Lower volume briefly (e.g. notification sound)
                mediaPlayer?.setVolume(0.3f, 0.3f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Focus returned — restore volume and resume if we were playing
                mediaPlayer?.setVolume(1.0f, 1.0f)
                if (wasPlayingBeforeFocusLoss) {
                    mediaPlayer?.start()
                    wasPlayingBeforeFocusLoss = false
                    updatePlaybackState(); updateNotification(); broadcastState()
                }
            }
        }
    }


    companion object {
        const val CHANNEL_ID = "MusicPlayerServiceChannel_v4"
        const val ACTION_PLAY_PAUSE = "com.mhk.filemanager.ACTION_PLAY_PAUSE"
        const val ACTION_STOP = "com.mhk.filemanager.ACTION_STOP"
        const val ACTION_TOGGLE_REPEAT = "com.mhk.filemanager.ACTION_TOGGLE_REPEAT"
        const val ACTION_NEXT = "com.mhk.filemanager.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.mhk.filemanager.ACTION_PREVIOUS"
        const val ACTION_STATE_UPDATE = "com.mhk.filemanager.ACTION_STATE_UPDATE"
        const val EXTRA_IS_PLAYING = "EXTRA_IS_PLAYING"
        const val EXTRA_IS_REPEAT = "EXTRA_IS_REPEAT"
        const val EXTRA_TRACK_NAME = "EXTRA_TRACK_NAME"
        const val EXTRA_DURATION = "EXTRA_DURATION"
        const val EXTRA_POSITION = "EXTRA_POSITION"
        private const val PREFS_NAME = "music_player_state"
        private const val PREF_PLAYLIST = "pref_playlist"
        private const val PREF_TRACK_INDEX = "pref_track_index"
        private const val PREF_POSITION = "pref_position"
        private const val PREF_TRACK_PATH = "pref_track_path"
        private const val MY_MEDIA_ROOT_ID = "filemanager_music_root"
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        createNotificationChannel()

        mediaSession = MediaSessionCompat(this, "MusicPlayerService")
        mediaSession.setCallback(mediaSessionCallback)
        mediaSession.isActive = true
        sessionToken = mediaSession.sessionToken
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MusicPlayerService", "onStartCommand action: ${intent?.action}, flags: $flags")

        // Handle null intent (START_STICKY restart after system kill)
        if (intent == null) {
            Log.d("MusicPlayerService", "Null intent — attempting to restore last playback state")
            if (!restoreAndPlay()) {
                Log.d("MusicPlayerService", "Nothing to restore, stopping service")
                stopSelf()
            }
            return START_STICKY
        }

        MediaButtonReceiver.handleIntent(mediaSession, intent)

        when (intent.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_TOGGLE_REPEAT -> toggleRepeat()
            ACTION_NEXT -> playNextSong()
            ACTION_PREVIOUS -> playPreviousSong()
            else -> {
                val filePath = intent.getStringExtra("filePath")
                val initialPosition = intent.getIntExtra("initial_position", 0)
                playlist = intent.getStringArrayListExtra("playlist") ?: ArrayList()
                currentTrackIndex = playlist.indexOf(filePath)

                if (filePath != null) {
                    playSong(filePath, initialPosition)
                }
            }
        }
        return START_STICKY
    }

    private fun playSong(filePath: String, startPosition: Int = 0) {
        currentFilePath = filePath
        val file = File(filePath)
        currentTrackName = file.name
        isPrepared = false

        // Request audio focus before playing
        val focusGranted = requestAudioFocus()
        if (!focusGranted) {
            Log.w("MusicPlayerService", "Audio focus not granted, playing anyway")
        }

        mediaPlayer?.release()

        mediaPlayer = MediaPlayer().apply {
            setWakeMode(applicationContext, android.os.PowerManager.PARTIAL_WAKE_LOCK)
            setDataSource(applicationContext, file.toUri())
            setOnPreparedListener {
                Log.d("MusicPlayerService", "MediaPlayer prepared: $currentTrackName")
                isPrepared = true
                it.seekTo(startPosition)
                it.start()
                playbackStartTime = System.currentTimeMillis()
                updateMetadata()
                updatePlaybackState()
                broadcastState()
                updateNotification()
                startUpdatingSeekBar()
                savePlaybackState()
            }
            setOnCompletionListener {
                Log.d("MusicPlayerService", "MediaPlayer completion: $currentTrackName")
                val playDuration = System.currentTimeMillis() - playbackStartTime
                if (playDuration < 1000) {
                    Log.w("MusicPlayerService", "Song completed too fast ($playDuration ms). Stopping to prevent loop.")
                    return@setOnCompletionListener
                }
                
                if (!isRepeatEnabled) {
                    playNextSong()
                } else {
                    it.seekTo(0)
                    it.start()
                    playbackStartTime = System.currentTimeMillis()
                    updatePlaybackState()
                    broadcastState()
                }
            }
            setOnErrorListener { mp, what, extra ->
                Log.e("MusicPlayerService", "MediaPlayer error: $what, $extra")
                isPrepared = false
                mp.reset()
                true // Return true to prevent onCompletion from being called
            }
            prepareAsync()
        }
        
        // Initial notification and state (without querying duration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1001, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(1001, createNotification())
        }
        // Don't call broadcastState() or updatePlaybackState() here yet, onPrepared will handle it
    }

    fun playNextSong() {
        if (playlist.isNotEmpty()) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastSkipTime < SKIP_DEBOUNCE_MS) return
            lastSkipTime = currentTime
            
            currentTrackIndex = (currentTrackIndex + 1) % playlist.size
            playSong(playlist[currentTrackIndex])
        }
    }

    fun playPreviousSong() {
        if (playlist.isNotEmpty()) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastSkipTime < SKIP_DEBOUNCE_MS) return
            lastSkipTime = currentTime

            currentTrackIndex = if (currentTrackIndex > 0) currentTrackIndex - 1 else playlist.size - 1
            playSong(playlist[currentTrackIndex])
        }
    }

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            Log.d("MusicPlayerService", "MediaSession onPlay called")
            if (mediaPlayer != null && isPrepared) {
                if (mediaPlayer?.isPlaying == false) {
                    val focusGranted = requestAudioFocus()
                    if (!focusGranted) Log.w("MusicPlayerService", "Audio focus not granted on resume")
                    mediaPlayer?.start()
                    updatePlaybackState()
                    updateNotification()
                    broadcastState()
                }
            } else {
                // Cold start — nothing loaded, restore last playlist or load default
                Log.d("MusicPlayerService", "onPlay cold start — restoring or loading default")
                if (!restoreAndPlay()) {
                    loadDefaultPlaylist()
                }
            }
        }

        override fun onPause() {
            Log.d("MusicPlayerService", "MediaSession onPause called")
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                savePlaybackState()
                updatePlaybackState()
                updateNotification()
                broadcastState()
            }
        }

        override fun onStop() {
            stopMusic()
        }

        override fun onSeekTo(pos: Long) {
            seekTo(pos.toInt())
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
            if (mediaPlayer != null && isPlaying()) {
                updatePlaybackState() // Sync system media bar
                broadcastState() // Sync in-app UI
            }
            handler.postDelayed(this, 1000)
        }
    }

    fun stopMusic() {
        Log.d("MusicPlayerService", "stopMusic called")
        mediaPlayer?.release()
        mediaPlayer = null
        isPrepared = false
        isProgressRunnableRunning = false
        handler.removeCallbacks(updateSeekBarRunnable)

        abandonAudioFocus()
        mediaSession.isActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationManager.cancel(1001)

        // Broadcast that playback has stopped
        val intent = Intent(ACTION_STATE_UPDATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_IS_PLAYING, false)
            putExtra(EXTRA_TRACK_NAME, "")
            putExtra(EXTRA_DURATION, 0)
            putExtra(EXTRA_POSITION, 0)
        }
        sendBroadcast(intent)

        // Clear home screen widget
        MusicPlayerWidgetProvider.updateAllWidgets(this, null, false)

        stopSelf()
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .setAcceptsDelayedFocusGain(true)
                .build()
            audioFocusRequest = focusRequest
            audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    private fun startUpdatingSeekBar() {
        if (!isProgressRunnableRunning) {
            isProgressRunnableRunning = true
            handler.post(updateSeekBarRunnable)
        }
    }

    private fun savePlaybackState() {
        prefs.edit().apply {
            putString(PREF_TRACK_PATH, currentFilePath)
            putInt(PREF_TRACK_INDEX, currentTrackIndex)
            putInt(PREF_POSITION, getCurrentPosition())
            putStringSet(PREF_PLAYLIST, playlist.mapIndexed { i, s -> "$i|$s" }.toSet())
            apply()
        }
    }

    private fun restoreAndPlay(): Boolean {
        val trackPath = prefs.getString(PREF_TRACK_PATH, null) ?: return false
        val trackIndex = prefs.getInt(PREF_TRACK_INDEX, 0)
        val position = prefs.getInt(PREF_POSITION, 0)
        val playlistSet = prefs.getStringSet(PREF_PLAYLIST, null) ?: return false

        // Restore playlist in correct order
        playlist = ArrayList(playlistSet.sortedBy { 
            it.substringBefore("|").toIntOrNull() ?: 0 
        }.map { 
            it.substringAfter("|") 
        })

        if (playlist.isEmpty() || !File(trackPath).exists()) return false

        currentTrackIndex = trackIndex.coerceIn(0, playlist.size - 1)
        Log.d("MusicPlayerService", "Restoring: track=$trackPath, index=$currentTrackIndex, position=$position")
        playSong(trackPath, position)
        return true
    }

    private fun loadDefaultPlaylist() {
        // Scan FileManagerMusic library for any audio files
        val musicLibraryDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "FileManagerMusic")
        if (!musicLibraryDir.exists()) {
            Log.d("MusicPlayerService", "No music library found")
            return
        }

        // Look for first playlist sub-folder with audio files
        val playlistDirs = musicLibraryDir.listFiles { f -> f.isDirectory }?.sortedBy { it.name } ?: emptyList()
        for (dir in playlistDirs) {
            val audioFiles = dir.listFiles { f -> !f.isDirectory && isAudioFile(f.name) }
                ?.sortedBy { it.name.lowercase() }
                ?.map { it.absolutePath }
                ?.toCollection(ArrayList()) ?: continue
            if (audioFiles.isNotEmpty()) {
                playlist = audioFiles
                currentTrackIndex = 0
                Log.d("MusicPlayerService", "Loading default playlist from: ${dir.name} (${audioFiles.size} tracks)")
                playSong(audioFiles[0])
                return
            }
        }

        // Fallback: scan root of Music directory
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val allAudio = musicDir.listFiles { f -> !f.isDirectory && isAudioFile(f.name) }
            ?.sortedBy { it.name.lowercase() }
            ?.map { it.absolutePath }
            ?.toCollection(ArrayList()) ?: ArrayList()

        if (allAudio.isNotEmpty()) {
            playlist = allAudio
            currentTrackIndex = 0
            Log.d("MusicPlayerService", "Loading fallback playlist from Music dir (${allAudio.size} tracks)")
            playSong(allAudio[0])
        } else {
            Log.d("MusicPlayerService", "No audio files found anywhere")
        }
    }

    private fun isAudioFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".flac") ||
               lower.endsWith(".ogg") || lower.endsWith(".aac") || lower.endsWith(".m4a") ||
               lower.endsWith(".opus") || lower.endsWith(".wma") || lower.endsWith(".aiff")
    }

    override fun onBind(intent: Intent): IBinder? {
        // If the binding is from a MediaBrowser client (system UI), let MediaBrowserServiceCompat handle it
        if (SERVICE_INTERFACE == intent.action) {
            return super.onBind(intent)
        }
        // For our in-app binding, return custom binder
        return binder
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot {
        return BrowserRoot(MY_MEDIA_ROOT_ID, null)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        // Return empty list — we don't expose a browsable tree, just need this for media resumption
        result.sendResult(mutableListOf())
    }

    inner class MusicPlayerBinder : Binder() {
        fun getService(): MusicPlayerService = this@MusicPlayerService
    }

    fun togglePlayPause() {
        if (mediaPlayer != null && isPrepared) {
            if (mediaPlayer?.isPlaying == true) mediaPlayer?.pause() else mediaPlayer?.start()
            Log.d("MusicPlayerService", "togglePlayPause: isPlaying=${mediaPlayer?.isPlaying}")
            updatePlaybackState()
            updateNotification()
            broadcastState()
        } else {
            // Nothing loaded — restore last session or load default
            Log.d("MusicPlayerService", "togglePlayPause cold start — restoring or loading default")
            if (!restoreAndPlay()) {
                loadDefaultPlaylist()
            }
        }
    }

    fun toggleRepeat() {
        isRepeatEnabled = !isRepeatEnabled
        mediaPlayer?.isLooping = isRepeatEnabled
        updateNotification()
        broadcastState()
    }

    private fun broadcastState() {
        Log.d("MusicPlayerService", "broadcasting state: isPlaying=${isPlaying()}, track=${getTrackName()}")
        val intent = Intent(ACTION_STATE_UPDATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_IS_PLAYING, isPlaying())
            putExtra(EXTRA_IS_REPEAT, isRepeatEnabled())
            putExtra(EXTRA_TRACK_NAME, getTrackName())
            putExtra(EXTRA_DURATION, getDuration())
            putExtra(EXTRA_POSITION, getCurrentPosition())
        }
        sendBroadcast(intent)

        // Update home screen widget
        MusicPlayerWidgetProvider.updateAllWidgets(this, getTrackName(), isPlaying())
    }

    private fun updatePlaybackState() {
        val state = if (isPlaying()) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val position = getCurrentPosition().toLong()
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, position, 1.0f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or 
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or 
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_SEEK_TO
                )
                .build()
        )
    }

    private fun updateMetadata() {
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTrackName)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "FileManager")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDuration().toLong())
                .build()
        )
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        updatePlaybackState()
        broadcastState()
    }

    fun isPlaying(): Boolean = try { isPrepared && mediaPlayer?.isPlaying == true } catch (e: Exception) { false }
    fun isRepeatEnabled(): Boolean = isRepeatEnabled
    fun getDuration(): Int = try { if (isPrepared) mediaPlayer?.duration ?: 0 else 0 } catch (e: Exception) { 0 }
    fun getCurrentPosition(): Int = try { if (isPrepared) mediaPlayer?.currentPosition ?: 0 else 0 } catch (e: Exception) { 0 }
    fun getTrackName(): String = currentTrackName
    fun getCurrentFilePath(): String? = currentFilePath

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "Music Player", NotificationManager.IMPORTANCE_LOW
            )
            serviceChannel.setSound(null, null)
            serviceChannel.enableVibration(false)
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
        val repeatPendingIntent = PendingIntent.getService(this, 3, Intent(this, MusicPlayerService::class.java).setAction(ACTION_TOGGLE_REPEAT), PendingIntent.FLAG_IMMUTABLE)
        val nextPendingIntent = PendingIntent.getService(this, 4, Intent(this, MusicPlayerService::class.java).setAction(ACTION_NEXT), PendingIntent.FLAG_IMMUTABLE)
        val prevPendingIntent = PendingIntent.getService(this, 5, Intent(this, MusicPlayerService::class.java).setAction(ACTION_PREVIOUS), PendingIntent.FLAG_IMMUTABLE)


        // --- Configure Collapsed View ---
        collapsedView.setViewVisibility(R.id.notification_album_art, View.GONE) // Hide album art
        collapsedView.setTextViewText(R.id.notification_track_name, currentTrackName)
        collapsedView.setImageViewResource(R.id.notification_play_pause, if (isPlaying()) R.drawable.baseline_pause_circle_outline_24 else R.drawable.baseline_play_circle_outline_24)
        collapsedView.setOnClickPendingIntent(R.id.notification_play_pause, playPausePendingIntent)
        collapsedView.setImageViewResource(R.id.notification_stop, R.drawable.baseline_minimize_24)
        collapsedView.setOnClickPendingIntent(R.id.notification_stop, stopPendingIntent)

        // --- Configure Expanded View ---
        expandedView.setTextViewText(R.id.notification_track_name_expanded, currentTrackName)
        expandedView.setImageViewResource(R.id.notification_play_pause_expanded, if (isPlaying()) R.drawable.baseline_pause_circle_outline_24 else R.drawable.baseline_play_circle_outline_24)
        val repeatColor = if (isRepeatEnabled()) ContextCompat.getColor(this, com.google.android.material.R.color.design_default_color_primary) else ContextCompat.getColor(this, R.color.md_theme_light_onSurfaceVariant)
        expandedView.setInt(R.id.notification_loop_expanded, "setColorFilter", repeatColor)

        // Update seekbar and time text
        val duration = getDuration()
        val currentPosition = getCurrentPosition()
        expandedView.setProgressBar(R.id.notification_seekbar, duration, currentPosition, false)
        expandedView.setTextViewText(R.id.notification_current_time, formatDuration(currentPosition))
        expandedView.setTextViewText(R.id.notification_total_time, formatDuration(duration))

        expandedView.setOnClickPendingIntent(R.id.notification_play_pause_expanded, playPausePendingIntent)
        expandedView.setOnClickPendingIntent(R.id.notification_next_expanded, nextPendingIntent)
        expandedView.setOnClickPendingIntent(R.id.notification_previous_expanded, prevPendingIntent)
        expandedView.setOnClickPendingIntent(R.id.notification_loop_expanded, repeatPendingIntent)


        Log.d("MusicPlayerService", "Creating notification for: $currentTrackName")

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.baseline_music_note_24)
            .setContentTitle(currentTrackName)
            .setContentText("FileManager")
            .setLargeIcon(android.graphics.BitmapFactory.decodeResource(resources, R.drawable.baseline_music_note_24))
            .setStyle(MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0, 1, 2))
            .addAction(NotificationCompat.Action(R.drawable.ic_skip_previous_24, "Previous", prevPendingIntent))
            .addAction(NotificationCompat.Action(
                if (isPlaying()) R.drawable.baseline_pause_circle_outline_24 else R.drawable.baseline_play_circle_outline_24, 
                "Play/Pause", playPausePendingIntent))
            .addAction(NotificationCompat.Action(R.drawable.ic_skip_next_24, "Next", nextPendingIntent))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        return builder.build()
    }

    private fun formatDuration(durationMs: Int): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs.toLong())
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs.toLong()) - TimeUnit.MINUTES.toSeconds(minutes)
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun updateNotification() {
        if (currentTrackName.isNotEmpty()) {
            Log.d("MusicPlayerService", "updateNotification")
            notificationManager.notify(1001, createNotification())
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // When user swipes app from recents, keep the service alive if music is playing
        if (isPlaying()) {
            // Re-post the foreground notification to keep the service alive
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1001, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(1001, createNotification())
            }
        } else {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        savePlaybackState()
        handler.removeCallbacks(updateSeekBarRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
        isPrepared = false
        abandonAudioFocus()
        mediaSession.release()
    }
}
