package com.mhk.filemanager.ui.player

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.DialogFragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mhk.filemanager.R
import com.mhk.filemanager.data.model.FileEntry
import com.mhk.filemanager.services.MusicPlayerService

class FileMusicPlayer(private val fileEntry: FileEntry) : DialogFragment() {

    private val maxVisibleFileNameLength = 30
    private lateinit var musicLength: TextView
    private lateinit var currentMusicLength: TextView
    private lateinit var musicSeekBar: SeekBar
    private lateinit var playOrPauseButton: FloatingActionButton
    private lateinit var loopButton: ImageButton
    private lateinit var expandButton: ImageButton
    private lateinit var musicFileName: TextView

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isExpanding = false // Flag to prevent stopping music on expand

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    @SuppressLint("ResourceAsColor")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)

        val view = inflater.inflate(R.layout.fragment_dialog_fragment, container, false)

        // Initialize Views
        musicFileName = view.findViewById(R.id.currentMusicFileName)
        musicLength = view.findViewById(R.id.musicLengthText)
        currentMusicLength = view.findViewById(R.id.musicCurrentLengthText)
        playOrPauseButton = view.findViewById(R.id.PlayOrPauseButton)
        loopButton = view.findViewById(R.id.InfinitePlayButton)
        expandButton = view.findViewById(R.id.expandButton)
        musicSeekBar = view.findViewById(R.id.musicTracker)

        musicFileName.text = getSortFileName(fileEntry.name)

        // Initialize MediaPlayer
        mediaPlayer = MediaPlayer().apply {
            setDataSource(requireContext(), fileEntry.file.toUri())
            prepare()
            start()
            setOnCompletionListener {
                if (it.isLooping) {
                    it.seekTo(0)
                    it.start()
                } else {
                    updatePlayPauseButton()
                }
            }
        }
        updateUI()

        // Set Click Listeners
        playOrPauseButton.setOnClickListener {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            } else {
                mediaPlayer?.start()
            }
            updatePlayPauseButton()
        }

        loopButton.setOnClickListener {
            mediaPlayer?.isLooping = !(mediaPlayer?.isLooping ?: false)
            updateLoopButton()
        }

        expandButton.setOnClickListener {
            isExpanding = true // Set flag
            val currentPosition = mediaPlayer?.currentPosition ?: 0

            // Stop and release the local player BEFORE starting the service
            handler.removeCallbacks(updateSeekBarRunnable)
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null

            // Start the service for background playback
            val serviceIntent = Intent(requireContext(), MusicPlayerService::class.java).apply {
                putExtra("filePath", fileEntry.file.absolutePath)
                putExtra("initial_position", currentPosition)
            }
            ContextCompat.startForegroundService(requireContext(), serviceIntent)

            // Start the activity
            val activityIntent = Intent(requireContext(), MusicPlayerActivity::class.java).apply {
                putExtra("filePath", fileEntry.file.absolutePath)
            }
            startActivity(activityIntent)
            dismiss() // Dismiss the dialog
        }

        musicSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seek: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.seekTo(progress)
                }
            }
            override fun onStartTrackingTouch(seek: SeekBar) {}
            override fun onStopTrackingTouch(seek: SeekBar) {}
        })

        return view
    }

    private fun updateUI() {
        updatePlayPauseButton()
        updateLoopButton()
        startUpdatingSeekBar()
    }

    private val updateSeekBarRunnable: Runnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    val currentPosition = it.currentPosition
                    musicSeekBar.progress = currentPosition
                    currentMusicLength.text = formatDuration(currentPosition)
                }
            }
            handler.postDelayed(this, 1000)
        }
    }

    private fun startUpdatingSeekBar() {
        mediaPlayer?.let {
            musicSeekBar.max = it.duration
            musicLength.text = formatDuration(it.duration)
        }
        handler.post(updateSeekBarRunnable)
    }

    private fun updatePlayPauseButton() {
        if (mediaPlayer?.isPlaying == true) {
            playOrPauseButton.setImageResource(R.drawable.baseline_pause_circle_outline_24)
        } else {
            playOrPauseButton.setImageResource(R.drawable.baseline_play_circle_outline_24)
        }
    }

    private fun updateLoopButton() {
        val colorAttr = if (mediaPlayer?.isLooping == true) {
            com.google.android.material.R.attr.colorPrimary
        } else {
            com.google.android.material.R.attr.colorOnSurfaceVariant
        }
        val typedValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(colorAttr, typedValue, true)
        loopButton.imageTintList = ContextCompat.getColorStateList(requireContext(), typedValue.resourceId)
    }

    @SuppressLint("DefaultLocale")
    private fun formatDuration(durationMs: Int): String {
        val minutes = (durationMs / 1000) / 60
        val seconds = (durationMs / 1000) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun getSortFileName(name: String): String {
        if (name.length > maxVisibleFileNameLength) {
            return name.slice(IntRange(0, maxVisibleFileNameLength)) + "..."
        }
        return name
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        // Stop and release media player only if not expanding
        if (!isExpanding) {
            handler.removeCallbacks(updateSeekBarRunnable)
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }
}
 