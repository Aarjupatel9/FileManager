package com.example.filemanager.services

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.res.Resources.Theme
import android.graphics.drawable.ColorDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.DialogFragment
import com.example.filemanager.Entities.FileEntry
import com.example.filemanager.R


class FileMusicPlayer(private var parentContext: Context, private var files: FileEntry) :
    DialogFragment() {

    private var maxVisibleFileNameLength = 30
    var musicPlayer: MediaPlayer = MediaPlayer().apply {
        setAudioAttributes(
            AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA).build()
        )
        setDataSource(parentContext, files.file.toUri())
        prepare()
        start()
    }
    private lateinit var musicLength: TextView
    private lateinit var currentMusicLength: TextView
    private lateinit var musicSeekBar: SeekBar
    private var infinitePlayEnable: Boolean = true



    @SuppressLint("ResourceAsColor")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)

        val view = inflater.inflate(R.layout.fragment_dialog_fragment, container, false)

        view.findViewById<TextView>(R.id.currentMusicFileName).text = getSortFileName(files.name)
        musicLength = view.findViewById(R.id.musicLengthText)
        currentMusicLength = view.findViewById(R.id.musicCurrentLengthText)
        startPlayerSetup()

        view.findViewById<ImageButton>(R.id.PlayOrPauseButton).setOnClickListener {
            if (musicPlayer.isPlaying) {
                musicPlayer.pause()
            } else {
                musicPlayer.start()
                startPlayerSetup()
            }
        }

        view.findViewById<ImageButton>(R.id.InfinitePlayButton).setOnClickListener {
            if(infinitePlayEnable){
                Log.d("FileMusicPlayer", "If setOnClickListener infinitePlayEnable=$infinitePlayEnable")
                it.background = ColorDrawable(R.color.white);
            }else{
                Log.d("FileMusicPlayer", "Else setOnClickListener infinitePlayEnable=$infinitePlayEnable")
                it.background = ColorDrawable(R.color.light_blue_900);
            }
            infinitePlayEnable = !infinitePlayEnable

//            val typedValue = TypedValue();
//            theme.resolveAttribute(R..colorPrimary, typedValue, true);
//            val color = ContextCompat.getColor(context?.applicationContext!!, typedValue.resourceId)

        }

        musicSeekBar = view.findViewById(R.id.musicTracker)
        musicSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seek: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    Log.d("MusicPlayerActivity", "seek changed")
                    musicPlayer.seekTo(progress * 1000)
                }
            }

            override fun onStartTrackingTouch(seek: SeekBar) {
            }

            override fun onStopTrackingTouch(seek: SeekBar) {
            }
        })

        musicPlayer.setOnCompletionListener {
            Log.d("FileMusicPlayer", "setOnCompletionListener infinitePlayEnable=$infinitePlayEnable")
            if (infinitePlayEnable){
                musicPlayer.seekTo(0);
                musicPlayer.start()
            }
        }

        return view
    }

    private fun startPlayerSetup() {
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post(object : Runnable {
            override fun run() {
                updateUI()
                mainHandler.postDelayed(this, 1000)
            }
        })
    }

    fun updateUI() {
        try {
            musicLength.text = getTimeStringFromSeconds(musicPlayer.duration.div(1000))
            val mCurrentPosition: Int = musicPlayer.currentPosition.div(1000)
            val mTotalDuration: Int = musicPlayer.duration.div(1000)
            musicSeekBar.setMax(mTotalDuration)
            musicSeekBar.progress = mCurrentPosition
            currentMusicLength.text = getTimeStringFromSeconds(mCurrentPosition)
            musicSeekBar.refreshDrawableState()
        }catch (_:Exception){}
    }

    @SuppressLint("DefaultLocale")
    private fun getTimeStringFromSeconds(seconds: Int): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }

    private fun getSortFileName(name: String): String {
        if (name.length > maxVisibleFileNameLength) {
            return name.slice(IntRange(0, maxVisibleFileNameLength)) + "..."
        }
        return name
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            musicPlayer.stop()
            musicPlayer.release()
        } catch (_: Exception) {
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        try {
            musicPlayer.stop()
            musicPlayer.release()
        } catch (_: Exception) {
        }
    }
}