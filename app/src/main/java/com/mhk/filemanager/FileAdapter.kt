package com.mhk.filemanager

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.mhk.filemanager.Entities.Constants.SORT_CONSTANTS
import com.mhk.filemanager.Entities.FileEntry
import com.mhk.filemanager.MainActivity.Companion.sortOrder
import com.mhk.filemanager.databinding.ItemFileBinding
import com.mhk.filemanager.services.FileMusicPlayer
import com.mhk.filemanager.utils.Permissions
import com.mhk.filemanager.viewmodal.FileManagerViewModel
import java.io.File

@Suppress("DEPRECATION")
class FileAdapter(
    private val context: AppCompatActivity,
    private val viewModel: FileManagerViewModel
) :
    RecyclerView.Adapter<FileAdapter.FileViewHolder>() {
    private var files: List<FileEntry> = listOf()
    private val maxVisibleFileNameLength = 30
    private var musicService: FileMusicPlayer? = null

    init {
        val permissionManager = Permissions(context, this)
        if (permissionManager.requestStoragePermissions()) {
            loadMediaFiles(Environment.getExternalStorageDirectory().absolutePath)
        }
        viewModel.openedFile.observe(context) {
            // Observer for current file path changes
        }
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

        // Corrected sorting logic for directories
        val sortedDirectories = when (fileSortOrder) {
            SORT_CONSTANTS.SORT_BY_NAME_DESC,
            SORT_CONSTANTS.SORT_BY_SIZE_DESC,
            SORT_CONSTANTS.SORT_BY_DATE_DESC -> directories.sortedByDescending { it.name.lowercase() }
            else -> directories.sortedBy { it.name.lowercase() } // Handles all ASC cases and default
        }

        // Corrected sorting logic for files
        val sortedOtherFiles = when (fileSortOrder) {
            SORT_CONSTANTS.SORT_BY_NAME_ASC -> otherFiles.sortedBy { it.name.lowercase() }
            SORT_CONSTANTS.SORT_BY_NAME_DESC -> otherFiles.sortedByDescending { it.name.lowercase() }
            SORT_CONSTANTS.SORT_BY_SIZE_ASC -> otherFiles.sortedBy { it.size }
            SORT_CONSTANTS.SORT_BY_SIZE_DESC -> otherFiles.sortedByDescending { it.size }
            SORT_CONSTANTS.SORT_BY_DATE_ASC -> otherFiles.sortedBy { it.dateModified }
            SORT_CONSTANTS.SORT_BY_DATE_DESC -> otherFiles.sortedByDescending { it.dateModified }
            else -> otherFiles.sortedBy { it.name.lowercase() }
        }

        files = sortedDirectories + sortedOtherFiles
        notifyDataSetChanged()
    }

    private fun getFileInfoFromPath(filePath: String): List<String> {
        // Handle the root storage directory case, which may not be in MediaStore
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

        // Fallback to File API if MediaStore fails to find the directory
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
                    "audio/mpeg", "audio/x-wav" -> R.drawable.baseline_music_note_24
                    "audio/mp4", "video/mp4" -> R.drawable.baseline_video_file_24
                    "application/pdf" -> R.drawable.baseline_picture_as_pdf_24
                    else -> R.drawable.ic_file
                }
            )

            binding.itemContainer.setOnClickListener {
                handleFileClick(fileEntry)
            }

            binding.menuButton.setOnClickListener {
                showPopupMenu(it, fileEntry)
            }
        }
    }

    private fun handleFileClick(file: FileEntry) {
        if (file.mimetype != "dir") {
            openSelectedFile(file)
            return
        }
        stopMusicPlayer()
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
            .setPositiveButton(R.string.save, null) // Set to null to override and prevent auto-dismiss
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
                    dialog.dismiss() // Dismiss if name is unchanged
                }
            }
        }
        dialog.show()
    }

    private fun renameFile(fileEntry: FileEntry, newName: String) {
        val oldFile = fileEntry.file
        val newFile = File(oldFile.parent, newName)

        if (oldFile.renameTo(newFile)) {
            // Update MediaStore
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
                    // Fallback to media scanner if update fails
                    MediaScannerConnection.scanFile(context, arrayOf(newFile.absolutePath), null, null)
                }
            } catch (e: Exception) {
                Log.e("FileAdapter", "Error updating MediaStore", e)
                Toast.makeText(context, "Error updating media library", Toast.LENGTH_LONG).show()
            }

            // Refresh the current directory
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

    private fun stopMusicPlayer() {
        musicService?.dismiss()
        musicService = null
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun openSelectedFile(fileEntry: FileEntry) {
        val file: File = fileEntry.file
        if (!file.isFile) return

        try {
            val uri = FileProvider.getUriForFile(context, "${context.applicationContext.packageName}.fileProvider", file)
            val type = getFileType(uri)

            if (type == "audio/x-wav" || type == "audio/mpeg") {
                stopMusicPlayer()
                musicService = FileMusicPlayer(context, fileEntry)
                musicService?.show(context.supportFragmentManager, "Music Player")
                return
            }

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
