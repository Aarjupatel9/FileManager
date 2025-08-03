package com.mhk.filemanager.ui.player

import android.app.TaskStackBuilder
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.mhk.filemanager.R
import com.mhk.filemanager.services.MusicPlayerService
import com.mhk.filemanager.ui.main.MainActivity

class MusicPlayerActivity : AppCompatActivity() {

    private lateinit var musicFileName: TextView
    private lateinit var musicSeekBar: SeekBar
    private lateinit var musicCurrentPosition: TextView
    private lateinit var musicDuration: TextView
    private lateinit var playPauseButton: ImageButton
    private lateinit var loopButton: ImageButton
    private lateinit var minimizeButton: ImageButton
    private lateinit var backButton: ImageButton

    private var musicPlayerService: MusicPlayerService? = null
    private var isBound = false
    private val handler = Handler(Looper.getMainLooper())

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicPlayerService.MusicPlayerBinder
            musicPlayerService = binder.getService()
            isBound = true
            initializeUI()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }

    private val musicStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MusicPlayerService.ACTION_STATE_UPDATE) {
                updatePlayPauseButton()
                updateLoopButton()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_music_player)

        // --- Handle Intent from "Open With" ---
        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            val fileUri = intent.data
            val filePath = getPathFromUri(fileUri)

            if (filePath != null) {
                val serviceIntent = Intent(this, MusicPlayerService::class.java).apply {
                    putExtra("filePath", filePath)
                }
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                Toast.makeText(this, "Could not open file", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        }

        musicFileName = findViewById(R.id.musicFileName)
        musicSeekBar = findViewById(R.id.musicSeekBar)
        musicCurrentPosition = findViewById(R.id.musicCurrentPosition)
        musicDuration = findViewById(R.id.musicDuration)
        playPauseButton = findViewById(R.id.playPauseButton)
        loopButton = findViewById(R.id.loopButton)
        minimizeButton = findViewById(R.id.minimizeButton)
        backButton = findViewById(R.id.backButton)

        playPauseButton.setOnClickListener {
            musicPlayerService?.togglePlayPause()
        }

        loopButton.setOnClickListener {
            musicPlayerService?.toggleLooping()
        }

        minimizeButton.setOnClickListener {
            finish()
        }

        backButton.setOnClickListener {
            handleBackNavigation()
        }

        onBackPressedDispatcher.addCallback(this, object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })

        musicSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    musicPlayerService?.seekTo(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun getPathFromUri(uri: Uri?): String? {
        if (uri == null) return null
        var path: String? = null
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, projection, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                path = cursor.getString(columnIndex)
            }
        } finally {
            cursor?.close()
        }
        return path
    }

    private fun handleBackNavigation() {
        stopService(Intent(this, MusicPlayerService::class.java))
        if (isTaskRoot) {
            val intent = Intent(this, MainActivity::class.java)
            val stackBuilder = TaskStackBuilder.create(this)
            stackBuilder.addNextIntentWithParentStack(intent)
            stackBuilder.startActivities()
        } else {
            finish()
        }
    }

    private fun initializeUI() {
        musicFileName.text = musicPlayerService?.getTrackName()
        // Enable marquee effect
        musicFileName.isSelected = true
        updatePlayPauseButton()
        updateLoopButton()
        startUpdatingSeekBar()
    }

    private val updateSeekBarRunnable: Runnable = object : Runnable {
        override fun run() {
            if (isBound && musicPlayerService != null) {
                val currentPosition = musicPlayerService!!.getCurrentPosition()
                musicSeekBar.progress = currentPosition
                musicCurrentPosition.text = formatDuration(currentPosition)
                musicSeekBar.max = musicPlayerService!!.getDuration()
                musicDuration.text = formatDuration(musicPlayerService!!.getDuration())
                handler.postDelayed(this, 1000)
            }
        }
    }

    private fun startUpdatingSeekBar() {
        handler.post(updateSeekBarRunnable)
    }

    private fun updatePlayPauseButton() {
        if (musicPlayerService?.isPlaying() == true) {
            playPauseButton.setImageResource(R.drawable.baseline_pause_circle_outline_24)
        } else {
            playPauseButton.setImageResource(R.drawable.baseline_play_circle_outline_24)
        }
    }

    private fun updateLoopButton() {
        if (musicPlayerService?.isLooping() == true) {
            loopButton.imageTintList = ContextCompat.getColorStateList(this, com.google.android.material.R.color.design_default_color_primary)
        } else {
            loopButton.imageTintList = ContextCompat.getColorStateList(this, R.color.md_theme_light_onSurfaceVariant)
        }
    }

    private fun formatDuration(duration: Int): String {
        val minutes = duration / 1000 / 60
        val seconds = duration / 1000 % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onStart() {
        super.onStart()
        Intent(this, MusicPlayerService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(musicStateReceiver, IntentFilter(MusicPlayerService.ACTION_STATE_UPDATE), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(musicStateReceiver, IntentFilter(MusicPlayerService.ACTION_STATE_UPDATE))
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        unregisterReceiver(musicStateReceiver)
        handler.removeCallbacks(updateSeekBarRunnable)
    }
}
