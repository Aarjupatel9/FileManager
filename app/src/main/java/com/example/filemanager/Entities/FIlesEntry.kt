package com.example.filemanager.Entities

import java.io.File


data class FileEntry(
    val file: File,
    val id: Any,
    val name: String,
    val data: Any,
    val mimetype: String,
    var parentId: Long?,
    var hidden: Boolean = false // Status can be "hidden" if name starts with "."
)