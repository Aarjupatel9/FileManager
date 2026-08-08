package com.mhk.filemanager.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mhk.filemanager.R
import com.mhk.filemanager.data.datastore.SettingsManager
import com.mhk.filemanager.data.model.Constants
import com.mhk.filemanager.databinding.ActivityMainBinding
import com.mhk.filemanager.services.MyBroadcastReceiver
import com.mhk.filemanager.services.MyJobService
import com.mhk.filemanager.services.MusicPlayerService
import com.mhk.filemanager.ui.player.MusicPlayerActivity
import com.mhk.filemanager.utils.Permissions
import com.mhk.filemanager.viewmodal.FileManagerViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private val TAG = "mainActivity"

    private lateinit var viewModel: FileManagerViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var fileTreeLayout: LinearLayout
    private lateinit var fileAdapter: FileAdapter
    private lateinit var binding: ActivityMainBinding
    lateinit var settingsManager: SettingsManager
    private lateinit var permissionManager: Permissions
    private var myBroadcastReceiver: MyBroadcastReceiver? = null
    
    // Music Player Service binding
    private var musicPlayerService: MusicPlayerService? = null
    private var isBound = false

    private var isLibraryViewActive = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicPlayerService.MusicPlayerBinder
            musicPlayerService = binder.getService()
            isBound = true
            updateMusicCardVisibility()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }

    private val musicStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("MainActivity", "musicStateReceiver onReceive action: ${intent?.action}")
            if (intent?.action == MusicPlayerService.ACTION_STATE_UPDATE) {
                updateMusicCardUI(intent)
            }
        }
    }


    // This will hold the current sort order, loaded from DataStore
    private var currentSortOrder: Int = Constants.SORT_CONSTANTS.SORT_BY_NAME_ASC

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startMain()
    }

    @SuppressLint("InflateParams")
    private fun startMain() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize SettingsManager
        settingsManager = SettingsManager(this)
        permissionManager = Permissions(this, null)


        fileTreeLayout = binding.fileTreeLayout
        recyclerView = binding.recyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)

        viewModel = ViewModelProvider(this)[FileManagerViewModel::class.java]

        // Pass the initial sort order to the adapter
        fileAdapter = FileAdapter(this, viewModel, currentSortOrder)
        recyclerView.adapter = fileAdapter
        permissionManager.fileAdapter = fileAdapter
        setupItemTouchHelper()

        // Handle back navigation to parent folder
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateToParentFolder()
            }
        })

        initialNotificationSetup()
        // startApplicationServices() // Temporarily commented out as requested
        checkNotificationPermission()
        initialFileManagementTasks()
        createMusicLibraryFolder()


        binding.sortButton.setOnClickListener {
            showSortDialog()
        }

        binding.libraryButton.setOnClickListener {
            if (isLibraryViewActive) {
                // Go back to all files
                viewModel.resetFileTree()
                fileAdapter.loadMediaFiles(Environment.getExternalStorageDirectory().absolutePath)
            } else {
                // Go to library
                val musicLibraryPath = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "FileManagerMusic").absolutePath
                viewModel.resetFileTree()
                fileAdapter.loadMediaFiles(musicLibraryPath)
            }
        }

        binding.addFab.setOnClickListener { view ->
            showAddMenu(view)
        }

        binding.japCounterButton.setOnClickListener {
            val intent = Intent(this, com.mhk.filemanager.ui.japcounter.JapCounterActivity::class.java)
            startActivity(intent)
        }

        binding.mainActivityBackButton.setOnClickListener{
            handleBackAction()
        }

        setupMusicCard()
        checkAndPromptDefaultMusicPlayer()
    }

    private fun setupMusicCard() {
        binding.musicCard.cardPlayPauseButton.setOnClickListener {
            musicPlayerService?.togglePlayPause()
        }

        binding.musicCard.cardNextButton.setOnClickListener {
            musicPlayerService?.playNextSong()
        }

        binding.musicCard.cardPreviousButton.setOnClickListener {
            musicPlayerService?.playPreviousSong()
        }

        binding.musicCard.cardRepeatButton.setOnClickListener {
            musicPlayerService?.toggleRepeat()
        }

        binding.musicCard.cardStopButton.setOnClickListener {
            musicPlayerService?.stopMusic()
        }

        binding.musicCard.cardTrackName.isSelected = true

        binding.musicCard.root.setOnClickListener {
            // Consume clicks to prevent pass-through
        }

        binding.musicCard.cardProgressBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && musicPlayerService != null) {
                    val duration = musicPlayerService?.getDuration() ?: 0
                    val newPosition = (progress * duration) / 100
                    musicPlayerService?.seekTo(newPosition)
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        binding.musicCard.cardExpandButton.setOnClickListener {
            val intent = Intent(this, MusicPlayerActivity::class.java)
            if (musicPlayerService != null) {
                intent.putExtra("filePath", musicPlayerService?.getCurrentFilePath())
            }
            startActivity(intent)
        }
        
        // Play All button
        binding.playAllButton.setOnClickListener {
            val currentPath = viewModel.openedFile.value ?: return@setOnClickListener
            fileAdapter.playFolderAsPlaylist(java.io.File(currentPath))
        }
        
        // Hide card initially unless service is already running and playing
        updateMusicCardVisibility()
    }

    private fun updateMusicCardVisibility() {
        val hasTrack = !android.text.TextUtils.isEmpty(musicPlayerService?.getTrackName())
        if (isBound && musicPlayerService != null && hasTrack) {
            binding.musicCard.root.visibility = android.view.View.VISIBLE
        } else {
            binding.musicCard.root.visibility = android.view.View.GONE
        }
    }

    private fun updateMusicCardUI(intent: Intent) {
        val isPlaying = intent.getBooleanExtra(MusicPlayerService.EXTRA_IS_PLAYING, false)
        val isRepeatEnabled = intent.getBooleanExtra(MusicPlayerService.EXTRA_IS_REPEAT, false)
        val trackName = intent.getStringExtra(MusicPlayerService.EXTRA_TRACK_NAME)
        val duration = intent.getIntExtra(MusicPlayerService.EXTRA_DURATION, 0)
        val position = intent.getIntExtra(MusicPlayerService.EXTRA_POSITION, 0)

        Log.d("MainActivity", "updateMusicCardUI: track=$trackName, isPlaying=$isPlaying")

        if (!android.text.TextUtils.isEmpty(trackName)) {
            binding.musicCard.root.visibility = android.view.View.VISIBLE
            binding.musicCard.cardTrackName.text = trackName
            binding.musicCard.cardTrackName.isSelected = true 
            
            if (isPlaying) {
                binding.musicCard.cardPlayPauseButton.setImageResource(R.drawable.ic_pause_24)
            } else {
                binding.musicCard.cardPlayPauseButton.setImageResource(R.drawable.ic_play_arrow_24)
            }

            if (isRepeatEnabled) {
                binding.musicCard.cardRepeatButton.imageTintList = ContextCompat.getColorStateList(this, com.google.android.material.R.color.design_default_color_primary)
            } else {
                binding.musicCard.cardRepeatButton.imageTintList = ContextCompat.getColorStateList(this, R.color.md_theme_light_onSurfaceVariant)
            }

            if (duration > 0) {
                binding.musicCard.cardProgressBar.progress = (position * 100 / duration)
                binding.musicCard.cardCurrentTime.text = formatDuration(position)
                binding.musicCard.cardTotalTime.text = formatDuration(duration)
            } else {
                binding.musicCard.cardProgressBar.progress = 0
                binding.musicCard.cardCurrentTime.text = "--:--"
                binding.musicCard.cardTotalTime.text = "--:--"
            }
        } else {
            binding.musicCard.root.visibility = android.view.View.GONE
        }
    }

    private fun formatDuration(durationMs: Int): String {
        val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(durationMs.toLong())
        val seconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(durationMs.toLong()) - java.util.concurrent.TimeUnit.MINUTES.toSeconds(minutes)
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onResume() {
        super.onResume()
        if (permissionManager.requestStoragePermissions()) {
            val currentPath = viewModel.openedFile.value ?: Environment.getExternalStorageDirectory().absolutePath
            if (::fileAdapter.isInitialized) {
                fileAdapter.loadMediaFiles(currentPath)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d("MainActivity", "onStart: binding service and registering receiver")
        Intent(this, MusicPlayerService::class.java).also { intent ->
            bindService(intent, connection, BIND_AUTO_CREATE)
        }
        val filter = IntentFilter(MusicPlayerService.ACTION_STATE_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(musicStateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(musicStateReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        unregisterReceiver(musicStateReceiver)
    }

    private fun handleBackAction() {
        val size = viewModel.openedFileTree.value?.size
        if (size != null) {
            if(size>1){
                val parentFile = viewModel.openedFileTree.value?.get(size-2)
                if (parentFile != null) {
                    fileAdapter.loadMediaFiles(parentFile[0]);
                }
            }
        }
    }

    fun updateLibraryViewState(path: String) {
        val musicLibraryPath = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "FileManagerMusic").absolutePath
        if (path.startsWith(musicLibraryPath)) {
            isLibraryViewActive = true
            binding.libraryButton.setImageResource(R.drawable.ic_folder)
        } else {
            isLibraryViewActive = false
            binding.libraryButton.setImageResource(R.drawable.ic_library_music_24)
        }
        updatePlayAllButtonVisibility(path)
    }

    private fun updatePlayAllButtonVisibility(path: String) {
        val musicLibraryPath = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "FileManagerMusic").absolutePath
        // Show "Play All" only inside a playlist sub-folder (not the root library folder itself)
        val isInsidePlaylistFolder = path.startsWith(musicLibraryPath) && path != musicLibraryPath
        binding.playAllButton.visibility = if (isInsidePlaylistFolder) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun showAddMenu(anchor: android.view.View) {
        val popup = androidx.appcompat.widget.PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "New Folder")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    showCreateFolderDialog()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showCreateFolderDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.create_folder_dialog, null)
        val folderNameEditText = dialogView.findViewById<EditText>(R.id.folderNameEditText)

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.create_folder)
            .setView(dialogView)
            .setPositiveButton(R.string.create) { _, _ ->
                val folderName = folderNameEditText.text.toString().trim()
                if (folderName.isNotEmpty()) {
                    createFolder(folderName)
                } else {
                    Toast.makeText(this, "Folder name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
    }

    private fun createFolder(folderName: String) {
        val currentPath = viewModel.openedFile.value ?: Environment.getExternalStorageDirectory().absolutePath
        val newFolder = File(currentPath, folderName)

        if (!newFolder.exists()) {
            if (newFolder.mkdir()) {
                Toast.makeText(this, R.string.folder_creation_success, Toast.LENGTH_SHORT).show()
                // Refresh the file list to show the new folder
                fileAdapter.loadMediaFiles(currentPath)
            } else {
                Toast.makeText(this, R.string.folder_creation_failed, Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Folder with this name already exists", Toast.LENGTH_SHORT).show()
        }
    }


    private fun createMusicLibraryFolder() {
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val libraryDir = File(musicDir, "FileManagerMusic")
        if (!libraryDir.exists()) {
            libraryDir.mkdirs()
        }
    }

    private fun setupItemTouchHelper() {
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition

                val itemFrom = fileAdapter.getItemAt(fromPosition)
                val itemTo = fileAdapter.getItemAt(toPosition)
                if (itemFrom?.mimetype == "dir" || itemTo?.mimetype == "dir") {
                    return false
                }

                fileAdapter.moveItem(fromPosition, toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun isLongPressDragEnabled(): Boolean {
                return fileAdapter.isCustomOrderActive()
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun showSortDialog() {
        val currentPath = viewModel.openedFile.value ?: Environment.getExternalStorageDirectory().absolutePath
        lifecycleScope.launch {
            val pathSortOrder = settingsManager.getSortOrderForPath(currentPath).first()
            currentSortOrder = pathSortOrder
            showSortDialogWithOrder(currentPath, pathSortOrder)
        }
    }

    private fun showSortDialogWithOrder(currentPath: String, activeOrder: Int) {
        val dialogView = layoutInflater.inflate(R.layout.sort_dialog, null)
        val dialog = Dialog(this)
        dialog.setContentView(dialogView)

        dialog.window?.setBackgroundDrawable(ContextCompat.getDrawable(this, R.drawable.dialog_rounded_bg))
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.90).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

        val nameAsc = dialogView.findViewById<ImageButton>(R.id.sortByNameAsc)
        val nameDesc = dialogView.findViewById<ImageButton>(R.id.sortByNameDesc)
        val sizeAsc = dialogView.findViewById<ImageButton>(R.id.sortBySizeAsc)
        val sizeDesc = dialogView.findViewById<ImageButton>(R.id.sortBySizeDesc)
        val dateAsc = dialogView.findViewById<ImageButton>(R.id.sortByDateAsc)
        val dateDesc = dialogView.findViewById<ImageButton>(R.id.sortByDateDesc)
        val customOrderLayout = dialogView.findViewById<LinearLayout>(R.id.sortByCustomOrderLayout)
        val customOrder = dialogView.findViewById<ImageButton>(R.id.sortByCustomOrder)

        val musicLibraryPath = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "FileManagerMusic").absolutePath
        val isInsidePlaylist = currentPath.startsWith(musicLibraryPath) && currentPath != musicLibraryPath

        if (isInsidePlaylist) {
            customOrderLayout.visibility = android.view.View.VISIBLE
        } else {
            customOrderLayout.visibility = android.view.View.GONE
        }

        val buttons = mutableMapOf(
            Constants.SORT_CONSTANTS.SORT_BY_NAME_ASC to nameAsc,
            Constants.SORT_CONSTANTS.SORT_BY_NAME_DESC to nameDesc,
            Constants.SORT_CONSTANTS.SORT_BY_SIZE_ASC to sizeAsc,
            Constants.SORT_CONSTANTS.SORT_BY_SIZE_DESC to sizeDesc,
            Constants.SORT_CONSTANTS.SORT_BY_DATE_ASC to dateAsc,
            Constants.SORT_CONSTANTS.SORT_BY_DATE_DESC to dateDesc
        )
        if (isInsidePlaylist) {
            buttons[Constants.SORT_CONSTANTS.SORT_BY_CUSTOM_ORDER] = customOrder
        }

        val textPrimary = getColorFromTheme(this, com.google.android.material.R.attr.colorPrimary)
        val textNormal = getColorFromTheme(this, com.google.android.material.R.attr.colorOnSurface)

        val nameText = dialogView.findViewById<android.widget.TextView>(R.id.sortByNameText)
        val sizeText = dialogView.findViewById<android.widget.TextView>(R.id.sortBySizeText)
        val dateText = dialogView.findViewById<android.widget.TextView>(R.id.sortByDateText)
        val customText = dialogView.findViewById<android.widget.TextView>(R.id.sortByCustomOrderText)

        nameText.setTextColor(textNormal)
        sizeText.setTextColor(textNormal)
        dateText.setTextColor(textNormal)
        customText.setTextColor(textNormal)

        when (activeOrder) {
            Constants.SORT_CONSTANTS.SORT_BY_NAME_ASC, Constants.SORT_CONSTANTS.SORT_BY_NAME_DESC -> {
                nameText.setTextColor(textPrimary)
            }
            Constants.SORT_CONSTANTS.SORT_BY_SIZE_ASC, Constants.SORT_CONSTANTS.SORT_BY_SIZE_DESC -> {
                sizeText.setTextColor(textPrimary)
            }
            Constants.SORT_CONSTANTS.SORT_BY_DATE_ASC, Constants.SORT_CONSTANTS.SORT_BY_DATE_DESC -> {
                dateText.setTextColor(textPrimary)
            }
            Constants.SORT_CONSTANTS.SORT_BY_CUSTOM_ORDER -> {
                customText.setTextColor(textPrimary)
            }
        }

        val activeColor = getColorFromTheme(this, com.google.android.material.R.attr.colorOnPrimaryContainer)
        val activeBgColor = getColorFromTheme(this, com.google.android.material.R.attr.colorPrimaryContainer)
        val inactiveColor = getColorFromTheme(this, com.google.android.material.R.attr.colorOnSurfaceVariant)

        buttons.forEach { (sortType, button) ->
            if (sortType == activeOrder) {
                button.imageTintList = ColorStateList.valueOf(activeColor)
                button.backgroundTintList = ColorStateList.valueOf(activeBgColor)
                button.setBackgroundResource(R.drawable.breadcrumb_chip_background)
            } else {
                button.imageTintList = ColorStateList.valueOf(inactiveColor)
                button.backgroundTintList = null
                val outValue = TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
                button.setBackgroundResource(outValue.resourceId)
            }

            button.setOnClickListener {
                lifecycleScope.launch {
                    settingsManager.setSortOrderForPath(currentPath, sortType)
                    currentSortOrder = sortType
                    fileAdapter.loadMediaFiles(currentPath, sortType)
                }
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun initialFileManagementTasks() {
        viewModel.openedFileTree.observe(this) { updatedOpenedFileTree ->
            Log.d(TAG,"ViewModel observe Opened File Tree : $updatedOpenedFileTree")
            populateLinearLayout(
                fileTreeLayout, updatedOpenedFileTree
            )
        }
    }

    private fun populateLinearLayout(linearLayout: LinearLayout, openedFileTree: List<List<String>>) {
        linearLayout.removeAllViews()
        for (i in openedFileTree.indices) {
            val textView = TextView(linearLayout.context)
            textView.text = openedFileTree[i][1]
            textView.setPadding(32, 16, 32, 16)
            textView.textSize = 14f
            
            // Highlight the active directory differently from parent directories
            val isLast = i == openedFileTree.size - 1
            textView.setBackgroundResource(R.drawable.breadcrumb_chip_background)
            if (isLast) {
                textView.backgroundTintList = ColorStateList.valueOf(getColorFromTheme(linearLayout.context, com.google.android.material.R.attr.colorPrimaryContainer))
                textView.setTextColor(getColorFromTheme(linearLayout.context, com.google.android.material.R.attr.colorOnPrimaryContainer))
                textView.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                textView.backgroundTintList = ColorStateList.valueOf(getColorFromTheme(linearLayout.context, com.google.android.material.R.attr.colorSurfaceVariant))
                textView.setTextColor(getColorFromTheme(linearLayout.context, com.google.android.material.R.attr.colorOnSurfaceVariant))
            }

            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(4, 0, 4, 0)
            }
            textView.layoutParams = layoutParams
            textView.setOnClickListener {
                fileAdapter.loadMediaFiles(openedFileTree[i][0])
            }
            linearLayout.addView(textView)
            if (i < openedFileTree.size - 1) {
                val imageView = ImageView(linearLayout.context)
                imageView.setImageResource(R.drawable.baseline_arrow_forward_ios_24)
                val typedValue = TypedValue()
                linearLayout.context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
                val arrowColor = typedValue.data
                imageView.setColorFilter(arrowColor)
                val imageParams = LinearLayout.LayoutParams(
                    12, // smaller arrow size for cleaner look
                    12
                ).apply {
                    setMargins(4, 0, 4, 0)
                }
                imageView.layoutParams = imageParams
                linearLayout.addView(imageView)
            }
        }
    }

    private fun getColorFromTheme(context: Context, attributeId: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attributeId, typedValue, true)
        return typedValue.data
    }

    private fun initialNotificationSetup() {
        val importance = NotificationManager.IMPORTANCE_HIGH
        val chatChannel =
            NotificationChannel(Constants.notificationId, Constants.notificationName, importance)
        chatChannel.description = "To get alert notification for important chat messages"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(chatChannel)
    }

    private fun startApplicationServices() {
        val bluetoothStateIntentListener = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (myBroadcastReceiver == null) {
            myBroadcastReceiver = MyBroadcastReceiver()
            registerReceiver(myBroadcastReceiver, bluetoothStateIntentListener)
        }
        if (!isNotificationServiceEnabled(this)) {
            Toast.makeText(
                this,
                "Please give notification listener permission to run application services",
                Toast.LENGTH_LONG
            ).show()
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            startActivity(intent)
        } else {
            if (ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                val requiredPermissions = arrayOf(
                    Manifest.permission.INTERNET,
                    Manifest.permission.ACCESS_NETWORK_STATE,
                    Manifest.permission.CHANGE_NETWORK_STATE,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH,
                )
                ActivityCompat.requestPermissions(
                    this, requiredPermissions, 102
                )
            } else {
                scheduleJob()
            }
        }
    }

    private fun scheduleJob() {
        val jobScheduler = getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler
        val componentName = ComponentName(this, MyJobService::class.java)
        val jobInfo = JobInfo.Builder(1, componentName)
            .setPeriodic(20 * 60 * 1000)
            .setPersisted(true)
            .build()
        val resultCode = jobScheduler.schedule(jobInfo)
        if (resultCode == JobScheduler.RESULT_SUCCESS) {
            Log.d(TAG, "Job scheduled successfully!")
        } else {
            Log.e(TAG, "Job scheduling failed!")
        }
    }

    private fun isNotificationServiceEnabled(c: Context): Boolean {
        val pkgName = c.packageName
        val flat: String = Settings.Secure.getString(
            c.contentResolver, "enabled_notification_listeners"
        )
        if (!TextUtils.isEmpty(flat)) {
            val names = flat.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (i in names.indices) {
                val cn = ComponentName.unflattenFromString(names[i])
                if (cn != null) {
                    if (TextUtils.equals(pkgName, cn.packageName)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun checkNotificationPermission() {
        permissionManager.requestNotificationPermissions()
    }

    private fun navigateToParentFolder() {
        val parentPath = viewModel.navigateBack()
        
        if (parentPath != null) {
            // Navigate to parent folder
            fileAdapter.loadMediaFiles(parentPath)
        } else {
            // No parent folder, close the app
            finish()
        }
    }

    private fun isDefaultMusicPlayer(): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("file:///dummy.mp3"), "audio/*")
        }
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        val defaultPackage = resolveInfo?.activityInfo?.packageName
        return defaultPackage == packageName
    }

    private fun checkAndPromptDefaultMusicPlayer() {
        if (isDefaultMusicPlayer()) return

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val lastPrompted = prefs.getLong("last_music_default_prompt", 0L)

        if (lastPrompted > 0) {
            val daysSince = (System.currentTimeMillis() - lastPrompted) / (24 * 60 * 60 * 1000L)
            if (daysSince < 10) return
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Set Default Music Player")
            .setMessage("To enjoy a seamless music playback experience, set File Manager as your default music player.")
            .setPositiveButton("Set Default") { _, _ ->
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                    } else {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                        startActivity(intent)
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Please set Default Apps in Settings manually", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Later") { _, _ ->
                prefs.edit().putLong("last_music_default_prompt", System.currentTimeMillis()).apply()
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        myBroadcastReceiver?.let {
            unregisterReceiver(it)
            myBroadcastReceiver = null
        }
    }
}
