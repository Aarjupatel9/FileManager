package com.mhk.filemanager.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mhk.filemanager.R
import com.mhk.filemanager.data.datastore.SettingsManager
import com.mhk.filemanager.data.model.Constants
import com.mhk.filemanager.data.model.FileEntry
import com.mhk.filemanager.databinding.ActivityMainBinding
import com.mhk.filemanager.databinding.ItemFileBinding
import com.mhk.filemanager.services.MyBroadcastReceiver
import com.mhk.filemanager.services.MyJobService
import com.mhk.filemanager.ui.player.FileMusicPlayer
import com.mhk.filemanager.utils.Permissions
import com.mhk.filemanager.viewmodal.FileManagerViewModel
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private val TAG = "mainActivity"

    private lateinit var viewModel: FileManagerViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var fileTreeLayout: LinearLayout
    private lateinit var fileAdapter: FileAdapter
    private lateinit var binding: ActivityMainBinding
    private lateinit var settingsManager: SettingsManager
    private lateinit var permissionManager: Permissions


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

        observeSortOrder() // Start observing changes to the sort order

        checkManageExternalStoragePermission()
        initialNotificationSetup()
        // startApplicationServices() // Temporarily commented out as requested
        checkNotificationPermission()
        initialFileManagementTasks()

        binding.sortButton.setOnClickListener {
            showSortDialog()
        }

        binding.mainActivityBackButton.setOnClickListener{
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
    }

    // Observe the sort order Flow from DataStore
    private fun observeSortOrder() {
        lifecycleScope.launch {
            settingsManager.sortOrderFlow.collect { sortOrder ->
                currentSortOrder = sortOrder
                // Pass the updated sort order to the adapter
                fileAdapter.setSortOrder(sortOrder)
                // Refresh the file list with the new sort order
                viewModel.openedFile.value?.let { path ->
                    fileAdapter.loadMediaFiles(path, sortOrder)
                }
            }
        }
    }

    private fun showSortDialog() {
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

        val buttons = mapOf(
            Constants.SORT_CONSTANTS.SORT_BY_NAME_ASC to nameAsc,
            Constants.SORT_CONSTANTS.SORT_BY_NAME_DESC to nameDesc,
            Constants.SORT_CONSTANTS.SORT_BY_SIZE_ASC to sizeAsc,
            Constants.SORT_CONSTANTS.SORT_BY_SIZE_DESC to sizeDesc,
            Constants.SORT_CONSTANTS.SORT_BY_DATE_ASC to dateAsc,
            Constants.SORT_CONSTANTS.SORT_BY_DATE_DESC to dateDesc
        )

        val activeColor = getColorFromTheme(this, com.google.android.material.R.attr.colorPrimary)
        buttons[currentSortOrder]?.imageTintList = ColorStateList.valueOf(activeColor)

        buttons.forEach { (sortType, button) ->
            button.setOnClickListener {
                // Save the new sort order to DataStore
                lifecycleScope.launch {
                    settingsManager.setSortOrder(sortType)
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
            textView.setPadding(16, 8, 16, 8)
            textView.textSize = 16f
            val colorPrimary = getColorFromTheme(linearLayout.context, com.google.android.material.R.attr.colorPrimary)
            textView.setTextColor(colorPrimary)
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(2, 0, 2, 0)
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
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(8, 0, 8, 0)
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

    private fun checkManageExternalStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        }
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
        registerReceiver(MyBroadcastReceiver(), bluetoothStateIntentListener)
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
}

@Suppress("DEPRECATION")
class FileAdapter(
    private val context: AppCompatActivity,
    private val viewModel: FileManagerViewModel,
    private var sortOrder: Int // Accept initial sort order
) :
    RecyclerView.Adapter<FileAdapter.FileViewHolder>() {
    private var files: List<FileEntry> = listOf()
    private val maxVisibleFileNameLength = 30

    init {
        val permissionManager = Permissions(context, this)
        if (permissionManager.requestStoragePermissions()) {
            loadMediaFiles(Environment.getExternalStorageDirectory().absolutePath)
        }
        viewModel.openedFile.observe(context) {
            // Observer for current file path changes
        }
    }

    // Public method to update the sort order from MainActivity
    fun setSortOrder(newSortOrder: Int) {
        sortOrder = newSortOrder
    }

    @SuppressLint("NotifyDataSetChanged")
    fun loadMediaFiles(directoryPath: String, fileSortOrder: Int = sortOrder) {
        val fileInfo = getFileInfoFromPath(directoryPath)
        viewModel.updateOpenedFileTreeData(fileInfo)

        Log.d("MainActivity", "loadFiles start directoryPath : $directoryPath ")

        val externalUri: Uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.PARENT,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
        )

        val selection = "${MediaStore.Files.FileColumns.DATA} LIKE ? AND ${MediaStore.Files.FileColumns.DATA} NOT LIKE ? AND ${MediaStore.Files.FileColumns.DATA} != ?"
        val selectionArgs = arrayOf("$directoryPath%", "$directoryPath/%/%", directoryPath)

        val cursor = context.contentResolver.query(externalUri, projection, selection, selectionArgs, null)
        val fileList = mutableListOf<FileEntry>()
        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                val name = it.getString(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME))
                val data = it.getString(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA))
                val mimeType = it.getString(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE))
                val parentId = it.getString(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.PARENT))
                val size = it.getLong(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE))
                val dateModified = it.getLong(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)) * 1000

                if (!name.startsWith(".")) {
                    fileList.add(
                        FileEntry(
                            File(data), id, name, data, mimeType ?: "dir",
                            parentId.toLong(), false, size, dateModified
                        )
                    )
                }
            }
        }

        val directories = fileList.filter { it.mimetype == "dir" }
        val otherFiles = fileList.filterNot { it.mimetype == "dir" }

        val sortedDirectories = when (fileSortOrder) {
            Constants.SORT_CONSTANTS.SORT_BY_NAME_DESC,
            Constants.SORT_CONSTANTS.SORT_BY_SIZE_DESC,
            Constants.SORT_CONSTANTS.SORT_BY_DATE_DESC -> directories.sortedByDescending { it.name.lowercase() }
            else -> directories.sortedBy { it.name.lowercase() }
        }

        val sortedOtherFiles = when (fileSortOrder) {
            Constants.SORT_CONSTANTS.SORT_BY_NAME_ASC -> otherFiles.sortedBy { it.name.lowercase() }
            Constants.SORT_CONSTANTS.SORT_BY_NAME_DESC -> otherFiles.sortedByDescending { it.name.lowercase() }
            Constants.SORT_CONSTANTS.SORT_BY_SIZE_ASC -> otherFiles.sortedBy { it.size }
            Constants.SORT_CONSTANTS.SORT_BY_SIZE_DESC -> otherFiles.sortedByDescending { it.size }
            Constants.SORT_CONSTANTS.SORT_BY_DATE_ASC -> otherFiles.sortedBy { it.dateModified }
            Constants.SORT_CONSTANTS.SORT_BY_DATE_DESC -> otherFiles.sortedByDescending { it.dateModified }
            else -> otherFiles.sortedBy { it.name.lowercase() }
        }

        files = sortedDirectories + sortedOtherFiles
        notifyDataSetChanged()
    }

    private fun getFileInfoFromPath(filePath: String): List<String> {
        if (filePath == Environment.getExternalStorageDirectory().absolutePath) {
            return listOf(filePath, "Internal Storage")
        }
        val externalUri: Uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME
        )
        val selection = "${MediaStore.Files.FileColumns.DATA} = ?"
        val selectionArgs = arrayOf(filePath)
        val cursor = context.contentResolver.query(externalUri, projection, selection, selectionArgs, null)
        val fileData = mutableListOf<String>()
        cursor?.use {
            if (it.moveToFirst()) {
                val path = it.getString(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA))
                val name = it.getString(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME))
                fileData.add(path)
                fileData.add(name)
            }
        }
        if (fileData.isEmpty()) {
            val file = File(filePath)
            if (file.exists() && file.isDirectory) {
                fileData.add(file.absolutePath)
                fileData.add(file.name)
            }
        }
        return fileData
    }

    inner class FileViewHolder(private val binding: ItemFileBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(fileEntry: FileEntry) {
            binding.fileName.text = getSortFileName(fileEntry.name)
            binding.fileIcon.setImageResource(
                when (fileEntry.mimetype) {
                    "dir" -> R.drawable.ic_folder
                    "audio/mpeg", "audio/x-wav", "audio/mp3" -> R.drawable.baseline_music_note_24
                    "audio/mp4", "video/mp4" -> R.drawable.baseline_video_file_24
                    "application/pdf" -> R.drawable.baseline_picture_as_pdf_24
                    else -> R.drawable.ic_file
                }
            )
            binding.itemContainer.setOnClickListener { handleFileClick(fileEntry) }
            binding.menuButton.setOnClickListener { showPopupMenu(it, fileEntry) }
        }
    }

    private fun handleFileClick(file: FileEntry) {
        if (file.mimetype.startsWith("audio/")) {
            val musicPlayerDialog = FileMusicPlayer(file)
            musicPlayerDialog.show(context.supportFragmentManager, "FileMusicPlayer")
            return
        }
        if (file.mimetype != "dir") {
            openSelectedFile(file)
            return
        }
        loadMediaFiles(file.data as String)
    }

    private fun showPopupMenu(view: View, fileEntry: FileEntry) {
        val popup = PopupMenu(context, view)
        popup.menuInflater.inflate(R.menu.file_item_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_rename -> {
                    showRenameDialog(fileEntry)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showRenameDialog(fileEntry: FileEntry) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.rename_dialog, null)
        val newNameEditText = dialogView.findViewById<EditText>(R.id.newNameEditText)
        newNameEditText.setText(fileEntry.name)
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.rename_file)
            .setView(dialogView)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val newName = newNameEditText.text.toString().trim()
                if (newName.isNotEmpty() && newName != fileEntry.name) {
                    renameFile(fileEntry, newName)
                    dialog.dismiss()
                } else if (newName.isEmpty()) {
                    Toast.makeText(context, "File name cannot be empty", Toast.LENGTH_SHORT).show()
                } else {
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun renameFile(fileEntry: FileEntry, newName: String) {
        val oldFile = fileEntry.file
        val newFile = File(oldFile.parent, newName)
        if (oldFile.renameTo(newFile)) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Files.FileColumns.DISPLAY_NAME, newName)
                put(MediaStore.Files.FileColumns.DATA, newFile.absolutePath)
            }
            val uri = ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), fileEntry.id as Long)
            try {
                val updatedRows = context.contentResolver.update(uri, contentValues, null, null)
                if (updatedRows > 0) {
                    Toast.makeText(context, R.string.rename_success, Toast.LENGTH_SHORT).show()
                } else {
                    MediaScannerConnection.scanFile(context, arrayOf(newFile.absolutePath), null, null)
                }
            } catch (e: Exception) {
                Log.e("FileAdapter", "Error updating MediaStore", e)
                Toast.makeText(context, "Error updating media library", Toast.LENGTH_LONG).show()
            }
            viewModel.openedFile.value?.let { loadMediaFiles(it) }
        } else {
            Toast.makeText(context, R.string.rename_failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        return FileViewHolder(ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(files[position])
    }

    override fun getItemCount(): Int = files.size

    @SuppressLint("QueryPermissionsNeeded")
    private fun openSelectedFile(fileEntry: FileEntry) {
        val file: File = fileEntry.file
        if (!file.isFile) return
        try {
            val uri = FileProvider.getUriForFile(context, "${context.applicationContext.packageName}.fileProvider", file)
            val type = getFileType(uri)
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, type)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(viewIntent, "Open in..."))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No application found to open this file", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("FileAdapter", "Error opening file", e)
            Toast.makeText(context, "Could not open file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getFileType(url: Uri): String {
        return when {
            url.toString().endsWith(".doc", true) || url.toString().endsWith(".docx", true) -> "application/msword"
            url.toString().endsWith(".pdf", true) -> "application/pdf"
            url.toString().endsWith(".ppt", true) || url.toString().endsWith(".pptx", true) -> "application/vnd.ms-powerpoint"
            url.toString().endsWith(".xls", true) || url.toString().endsWith(".xlsx", true) -> "application/vnd.ms-excel"
            url.toString().endsWith(".zip", true) -> "application/zip"
            url.toString().endsWith(".rar", true) -> "application/x-rar-compressed"
            url.toString().endsWith(".rtf", true) -> "application/rtf"
            url.toString().endsWith(".wav", true) -> "audio/x-wav"
            url.toString().endsWith(".mp3", true) -> "audio/mpeg"
            url.toString().endsWith(".gif", true) -> "image/gif"
            url.toString().endsWith(".jpg", true) || url.toString().endsWith(".jpeg", true) || url.toString().endsWith(".png", true) -> "image/jpeg"
            url.toString().endsWith(".txt", true) -> "text/plain"
            url.toString().endsWith(".3gp", true) || url.toString().endsWith(".mpg", true) || url.toString().endsWith(".mpeg", true) || url.toString().endsWith(".mpe", true) || url.toString().endsWith(".mp4", true) || url.toString().endsWith(".avi", true) -> "video/*"
            else -> "*/*"
        }
    }

    private fun getSortFileName(name: String): String {
        if (name.length > maxVisibleFileNameLength) {
            return name.slice(0..maxVisibleFileNameLength) + "..."
        }
        return name
    }
}
