package com.example.filemanager.viewmodal

import android.os.Environment
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel


class FileManagerViewModel : ViewModel() {
    private val _fileTreeLiveData =
        MutableLiveData<List<List<String>>>(emptyList()) // Initialize with an empty list
    private val _currentFileValue =
        MutableLiveData<String>(Environment.getExternalStorageDirectory().absolutePath) // Initialize with an empty list
    val openedFileTree: LiveData<List<List<String>>> = _fileTreeLiveData
    val openedFile: LiveData<String> = _currentFileValue


    fun updateOpenedFileTreeData(fileInfo: List<String>) {
        Log.d("ViewModel", "updateOpenedFileTreeData start ${fileInfo[0]} and ${fileInfo[1]}")

        // Get the current value of _fileTreeLiveData, or initialize with an empty list if null
        var openedFileTree = _fileTreeLiveData.value ?: emptyList()

        // Find the index of fileInfo[0] (the first element in fileInfo, presumably the file path)
        val index = openedFileTree.indexOfFirst { it[0] == fileInfo[0] }

        if (index != -1) {
            // If the fileInfo[0] is found, keep everything up to that index (inclusive)
            openedFileTree = openedFileTree.subList(0, index + 1)
        } else {
            // If not found, append the new fileInfo and update the openedFileTree list
            openedFileTree = openedFileTree + listOf(fileInfo)  // Use `+` to create a new list and assign it
        }

        // Update the LiveData with the new list
        _fileTreeLiveData.value = openedFileTree
        Log.d("ViewModel", "updateOpenedFileTreeData appending value not found in tree ${openedFileTree.size}")

        // If the tree is not empty, update the current file value (assumes the last file in the list)
        if (openedFileTree.isNotEmpty()) {
            updateOpenedFileValue(openedFileTree.last())
        }
    }

    private fun updateOpenedFileValue(openedFile: List<String>) {
        _currentFileValue.value = openedFile.get(0)
    }


}