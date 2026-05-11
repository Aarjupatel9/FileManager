package com.mhk.filemanager.ui.main

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
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.mhk.filemanager.R
import com.mhk.filemanager.data.model.Constants
import com.mhk.filemanager.data.model.FileEntry
import com.mhk.filemanager.databinding.ItemFileBinding
import com.mhk.filemanager.services.MusicPlayerService
import com.mhk.filemanager.utils.Permissions
import com.mhk.filemanager.viewmodal.FileManagerViewModel
import java.io.File

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
        // Update the library view state in MainActivity
        if (context is MainActivity) {
            context.updateLibraryViewState(directoryPath)
        }

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
                val name = it.getString(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)) ?: ""
                val data = it.getString(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)) ?: ""
                val mimeType = it.getString(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE))
                val parentIdStr = it.getString(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.PARENT))
                val size = it.getLong(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE))
                val dateModified = it.getLong(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)) * 1000

                if (data.isNotEmpty() && !name.startsWith(".")) {
                    val parentId = parentIdStr?.toLongOrNull() ?: 0L
                    fileList.add(
                        FileEntry(
                            File(data), id, name, data, mimeType ?: "dir",
                            parentId, false, size, dateModified
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
        val musicLibraryPath = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "FileManagerMusic").absolutePath
        if (filePath == musicLibraryPath) {
            return listOf(filePath, "Music Library")
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
                val path = it.getString(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)) ?: ""
                val name = it.getString(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)) ?: ""
                if (path.isNotEmpty()) {
                    fileData.add(path)
                    fileData.add(name)
                }
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
            val audioFiles = files.filter { it.mimetype.startsWith("audio/") }.map { it.file.absolutePath }.toCollection(ArrayList())
            val serviceIntent = Intent(context, MusicPlayerService::class.java).apply {
                putExtra("filePath", file.file.absolutePath)
                putStringArrayListExtra("playlist", audioFiles)
            }
            ContextCompat.startForegroundService(context, serviceIntent)
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

        val moveItem = popup.menu.findItem(R.id.action_move)

        // Check if the file is already in the music library
        val musicLibraryPath = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "FileManagerMusic").absolutePath
        val fileParentPath = fileEntry.file.parentFile?.absolutePath ?: ""

        // Show "Move" only for audio files that are NOT already in the library
        moveItem.isVisible = fileEntry.mimetype.startsWith("audio/") && !fileParentPath.startsWith(musicLibraryPath)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_rename -> {
                    showRenameDialog(fileEntry)
                    true
                }
                R.id.action_move -> {
                    showMoveToPlaylistDialog(fileEntry)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showMoveToPlaylistDialog(fileEntry: FileEntry) {
        val musicLibraryDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "FileManagerMusic")
        val playlists = musicLibraryDir.listFiles { file -> file.isDirectory }?.map { it.name }?.toMutableList() ?: mutableListOf()

        playlists.add(0, context.getString(R.string.create_new_playlist))

        val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, playlists)

        AlertDialog.Builder(context)
            .setTitle(R.string.move_to_playlist)
            .setAdapter(adapter) { _, which ->
                if (which == 0) {
                    // Create New Playlist
                    showCreatePlaylistDialog(fileEntry)
                } else {
                    // Move to existing playlist
                    val playlistName = playlists[which]
                    val destinationDir = File(musicLibraryDir, playlistName)
                    moveFile(fileEntry, destinationDir)
                }
            }
            .show()
    }

    private fun showCreatePlaylistDialog(fileEntry: FileEntry) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.create_folder_dialog, null)
        val folderNameEditText = dialogView.findViewById<EditText>(R.id.folderNameEditText)

        AlertDialog.Builder(context)
            .setTitle(R.string.create_new_playlist)
            .setView(dialogView)
            .setPositiveButton(R.string.create) { _, _ ->
                val playlistName = folderNameEditText.text.toString().trim()
                if (playlistName.isNotEmpty()) {
                    val musicLibraryDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "FileManagerMusic")
                    val newPlaylistDir = File(musicLibraryDir, playlistName)
                    if (newPlaylistDir.mkdirs()) {
                        moveFile(fileEntry, newPlaylistDir)
                    } else {
                        Toast.makeText(context, "Failed to create playlist", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun moveFile(fileEntry: FileEntry, destinationDir: File) {
        val sourceFile = fileEntry.file
        val destinationFile = File(destinationDir, sourceFile.name)

        if (sourceFile.renameTo(destinationFile)) {
            // Update MediaStore for the old path (delete) and new path (scan)
            context.contentResolver.delete(
                MediaStore.Files.getContentUri("external"),
                "${MediaStore.Files.FileColumns.DATA}=?",
                arrayOf(sourceFile.absolutePath)
            )
            MediaScannerConnection.scanFile(context, arrayOf(destinationFile.absolutePath), null, null)

            Toast.makeText(context, R.string.move_success, Toast.LENGTH_SHORT).show()
            // Refresh the current directory
            viewModel.openedFile.value?.let { loadMediaFiles(it) }
        } else {
            Toast.makeText(context, R.string.move_failed, Toast.LENGTH_SHORT).show()
        }
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
