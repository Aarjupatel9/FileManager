package com.example.filemanager

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LabeledIntent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.example.filemanager.Entities.Constants.SORT_CONSTANTS
import com.example.filemanager.Entities.FileEntry
import com.example.filemanager.MainActivity.Companion.currentFile
import com.example.filemanager.MainActivity.Companion.sortOrder
import com.example.filemanager.databinding.ItemFileBinding
import com.example.filemanager.services.FileMusicPlayer
import com.example.filemanager.utils.Permissions
import java.io.File


@Suppress("DEPRECATION")
class FileAdapter(private val context: AppCompatActivity) :
    RecyclerView.Adapter<FileAdapter.FileViewHolder>() {
    private var files: List<FileEntry> = listOf()
    private val maxVisibleFileNameLength = 30
    private var musicService: FileMusicPlayer? = null

    init {
        var permissionManager: Permissions = Permissions(context, this)
        if (permissionManager.requestStoragePermissions()) {
            loadMediaFiles(Environment.getExternalStorageDirectory().absolutePath)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun loadMediaFiles(directoryPath: String, fileSortOrder: Int = sortOrder) {
        currentFile = directoryPath
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

        val selection =
            "${MediaStore.Files.FileColumns.DATA} LIKE ? AND ${MediaStore.Files.FileColumns.DATA} NOT LIKE ? AND ${MediaStore.Files.FileColumns.DATA} != ?"
        val selectionArgs = arrayOf(
            "$directoryPath%", "$directoryPath/%/%", directoryPath
        )

        val cursor =
            context.contentResolver.query(externalUri, projection, selection, selectionArgs, null)

        val fileList = mutableListOf<FileEntry>()
        cursor?.use {
            val idIndex = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameIndex = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val dataIndex = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val mimeTypeIndex = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val parentIndex = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.PARENT)
            val sizeIndex = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dateModifiedIndex =
                it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

            while (it.moveToNext()) {
                val id = it.getLong(idIndex)
                val name = it.getString(nameIndex)
                val data = it.getString(dataIndex)
                val mimeType = it.getString(mimeTypeIndex)
                val parentId = it.getString(parentIndex)
                val size = it.getLong(sizeIndex)
                val dateModified = it.getLong(dateModifiedIndex) * 1000 // Convert to miliseconds

                Log.d("MainActivity", "FileAdapter Cursor : $id $parentId $name $mimeType $data ")
                if (!name.startsWith(".")) {
                    if (mimeType == null) {
                        fileList.add(
                            FileEntry(
                                File(data),
                                id,
                                name,
                                data,
                                "dir",
                                parentId.toLong(),
                                false,
                                size,
                                dateModified
                            )
                        )
                    } else {
                        fileList.add(
                            FileEntry(
                                File(data),
                                id,
                                name,
                                data,
                                mimeType,
                                parentId.toLong(),
                                false,
                                size,
                                dateModified
                            )
                        )
                    }
                }
            }
        }
        Log.d("MainActivity", "loadFiles list : ${fileList.size}")

//        fileList.sortWith { a, b ->
//            when {
//                a.mimetype == "dir" && b.mimetype != "dir" -> -1
//                a.mimetype != "dir" && b.mimetype == "dir" -> 1
//                else -> a.name.compareTo(b.name, ignoreCase = true)
//            }
//        }

        // Separate directories and files
        val directories = fileList.filter { it.mimetype == "dir" }
        val otherFiles = fileList.filterNot { it.mimetype == "dir" }

        // Sort directories and files separately
        val sortedDirectories = when (fileSortOrder) {
            SORT_CONSTANTS.SORT_BY_NAME_ASC -> directories.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            SORT_CONSTANTS.SORT_BY_NAME_DESC -> directories.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
            SORT_CONSTANTS.SORT_BY_SIZE_ASC -> directories.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            SORT_CONSTANTS.SORT_BY_SIZE_DESC -> directories.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
            SORT_CONSTANTS.SORT_BY_DATE_ASC -> directories.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            SORT_CONSTANTS.SORT_BY_DATE_DESC -> directories.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
            else -> directories // Default or invalid sort order
        }
        val sortedOtherFiles = when (fileSortOrder) {
            SORT_CONSTANTS.SORT_BY_NAME_ASC -> otherFiles.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            SORT_CONSTANTS.SORT_BY_NAME_DESC -> otherFiles.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
            SORT_CONSTANTS.SORT_BY_SIZE_ASC -> otherFiles.sortedBy { it.size }
            SORT_CONSTANTS.SORT_BY_SIZE_DESC -> otherFiles.sortedByDescending { it.dateModified }
            SORT_CONSTANTS.SORT_BY_DATE_ASC -> otherFiles.sortedBy { it.dateModified }
            SORT_CONSTANTS.SORT_BY_DATE_DESC -> otherFiles.sortedByDescending { it.dateModified }
            else -> otherFiles // Default or invalid sort order
        }

        // Combine sorted directories and files
        fileList.clear()
        fileList.addAll(sortedDirectories)
        fileList.addAll(sortedOtherFiles)
        fileList.add(
            0, FileEntry(
                File(directoryPath),
                -1,
                "..",
                directoryPath,
                "dir",
                getParentDirectoryId(directoryPath),
                false, 0, 0
            )
        )
        files = fileList
        notifyDataSetChanged()
    }

    fun getParentDirectoryPath(parentId: Long): String? {
        val externalUri: Uri = MediaStore.Files.getContentUri("external")

        // Define the projection (columns you want to retrieve)
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA // This will give us the file path
        )

        // Define the selection to filter by parent ID
        val selection = "${MediaStore.Files.FileColumns._ID} = ?"
        val selectionArgs = arrayOf(parentId.toString())

        // Query the MediaStore
        val cursor = context.contentResolver.query(
            externalUri, projection, selection, selectionArgs, null
        )

        var path: String? = null

        cursor?.use {
            if (it.moveToFirst()) {
                // Retrieve the file path
                path = it.getString(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA))
            }
        }

        return path
    }

    private fun getParentDirectoryId(path: String): Long? {
        val externalUri: Uri = MediaStore.Files.getContentUri("external")

        // Define the projection (columns you want to retrieve)
        val projection = arrayOf(
            MediaStore.Files.FileColumns.PARENT // This will give us the file path
        )

        // Define the selection to filter by parent ID
        val selection = "${MediaStore.Files.FileColumns.DATA} = ?"
        val selectionArgs = arrayOf(path)

        // Query the MediaStore
        val cursor = context.contentResolver.query(
            externalUri, projection, selection, selectionArgs, null
        )

        var parentId: Long? = null

        cursor?.use {
            if (it.moveToFirst()) {
                // Retrieve the file path
                parentId = it.getLong(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.PARENT))
            }
        }

        return parentId
    }

    private fun getSortFileName(name: String): String {
        if (name.length > maxVisibleFileNameLength) {
            return name.slice(IntRange(0, maxVisibleFileNameLength)) + "..."
        }
        return name
    }

    @SuppressLint("RestrictedApi")
    inner class FileViewHolder(private val binding: ItemFileBinding) : ViewHolder(binding.root),
        View.OnClickListener, View.OnLongClickListener {
        fun bind(fileEntry: FileEntry) {
            binding.fileName.text = getSortFileName(fileEntry.name)
            binding.fileIcon.setImageResource(
                when (fileEntry.mimetype) {
                    "dir" -> R.drawable.ic_folder
                    "audio/mpeg" -> R.drawable.baseline_music_note_24
                    "audio/mp4" -> R.drawable.baseline_video_file_24
                    "video/mp4" -> R.drawable.baseline_video_file_24
                    "application/pdf" -> R.drawable.baseline_picture_as_pdf_24
                    "application/vnd.android.package-archive" -> R.drawable.ic_file
                    else -> R.drawable.ic_file
                }
            )
        }

        init {
            itemView.setOnClickListener(this)
            itemView.setOnLongClickListener(this)
        }

        override fun onClick(v: View?) {
            val position = this.bindingAdapterPosition
            val file = files[position]
            Log.d(
                "FileAdapter", "OnClick $position  ${file.data}  ${file.parentId}"
            )
            if (file.mimetype != "dir") {
                openSelectedFile(file)
                return
            }
            stopMusicPlayer() // stop existing musicService Instance
            if (file.id == -1) {
                file.parentId.let { itp ->
                    if (itp != null) {
                        getParentDirectoryPath(itp).let {
                            if (it != null) {
                                loadMediaFiles(it)
                            } else {
                                Log.d("FileAdapter", "Parent file is null")
                            }
                        }
                    } else {
                        Log.d("FileAdapter", "Parent file is null")
                    }
                }
            } else {
                loadMediaFiles(file.data as String)
            }
        }

        override fun onLongClick(v: View?): Boolean {
            val position = this.bindingAdapterPosition
            return false
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        return FileViewHolder(
            ItemFileBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(files[position])
    }

    override fun getItemCount(): Int = files.size

    private fun stopMusicPlayer() {
        musicService = null
    }

    @SuppressLint("QueryPermissionsNeeded")
    fun openSelectedFile(fileEntry: FileEntry) {
        val file: File = fileEntry.file

        Log.d("FileAdapter", "openSelectedFile start : ${file.toURI()} : ${file.toURL()}")
        if (!file.isFile) {
            Log.d("FileAdapter", "openSelectedFile start : is file  = ${file.isFile}")
            return
        }

        try {
            // Use FileProvider to get a content URI
            val uri = FileProvider.getUriForFile(
                context, "${context.applicationContext.packageName}.fileProvider", file
            )
            val type = getFileType(uri)

            if (type == "audio/x-wav") {
                stopMusicPlayer()
                musicService = FileMusicPlayer(context, fileEntry) // self handle the file
                musicService?.show(context.supportFragmentManager, "Music Player")
                return
            }

            Log.d("FileAdapter", "openSelectedFile uri : $uri , type : $type")
//
            val pm: PackageManager = context.applicationContext.packageManager
            val viewIntent = Intent(Intent.ACTION_VIEW)
            viewIntent.setDataAndType(uri, type)
            viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            val openInChooser = Intent.createChooser(viewIntent, "Open in...")

            val editIntent = Intent(Intent.ACTION_EDIT)
            editIntent.setDataAndType(uri, type)
            editIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            // Append " (for editing)" to applicable apps, otherwise they will show up twice identically
            val forEditing: Spannable = SpannableString(" (for editing)")
            forEditing.setSpan(
                ForegroundColorSpan(Color.CYAN),
                0,
                forEditing.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            val resInfo = pm.queryIntentActivities(editIntent, 0)
            val extraIntents = arrayOfNulls<Intent>(resInfo.size)
            for (i in resInfo.indices) {
                // Extract the label, append it, and repackage it in a LabeledIntent
                val ri = resInfo[i]
                val packageName = ri.activityInfo.packageName
                val intent = Intent()
                intent.setComponent(ComponentName(packageName, ri.activityInfo.name))
                intent.setAction(Intent.ACTION_EDIT)
                intent.setDataAndType(uri, type)
                val label = TextUtils.concat(ri.loadLabel(pm), forEditing)
                extraIntents[i] = LabeledIntent(intent, packageName, label, ri.icon)
            }

            openInChooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, extraIntents)
            // Grant temporary read permission to the content URI
            openInChooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(openInChooser)

        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                context, "No application found which can open the file", Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun getFileType(url: Uri): String {
        if (url.toString().contains(".doc") || url.toString().contains(".docx")) {
            // Word document
            return "application/msword"
        } else if (url.toString().contains(".pdf")) {
            // PDF file
            return "application/pdf"
        } else if (url.toString().contains(".ppt") || url.toString().contains(".pptx")) {
            // Powerpoint file
            return "application/vnd.ms-powerpoint"
        } else if (url.toString().contains(".xls") || url.toString().contains(".xlsx")) {
            // Excel file
            return "application/vnd.ms-excel"
        } else if (url.toString().contains(".zip")) {
            // ZIP file
            return "application/zip"
        } else if (url.toString().contains(".rar")) {
            // RAR file
            return "application/x-rar-compressed"
        } else if (url.toString().contains(".rtf")) {
            // RTF file
            return "application/rtf"
        } else if (url.toString().contains(".wav") || url.toString().contains(".mp3")) {
            // WAV audio file
            return "audio/x-wav"
        } else if (url.toString().contains(".gif")) {
            // GIF file
            return "image/gif"
        } else if (url.toString().contains(".jpg") || url.toString()
                .contains(".jpeg") || url.toString().contains(".png")
        ) {
            // JPG file
            return "image/jpeg"
        } else if (url.toString().contains(".txt")) {
            // Text file
            return "text/plain"
        } else if (url.toString().contains(".3gp") || url.toString()
                .contains(".mpg") || url.toString().contains(".mpeg") || url.toString()
                .contains(".mpe") || url.toString().contains(".mp4") || url.toString()
                .contains(".avi")
        ) {
            // Video files
            return "video/*"
        } else {
            return "*/*"
        }
    }
}
