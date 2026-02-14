package com.mhk.filemanager.viewmodal

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
        _currentFileValue.value = openedFile[0]
    }

    fun resetFileTree() {
        _fileTreeLiveData.value = emptyList()
    }

    fun navigateBack(): String? {
        val currentTree = _fileTreeLiveData.value ?: emptyList()
        val currentPath = _currentFileValue.value ?: Environment.getExternalStorageDirectory().absolutePath
        val rootPath = Environment.getExternalStorageDirectory().absolutePath
        
        // If already at root and tree is empty, return null to close app
        if (currentTree.isEmpty() && currentPath == rootPath) {
            return null
        }
        
        // If tree is empty but not at root, go to root
        if (currentTree.isEmpty()) {
            _currentFileValue.value = rootPath
            return rootPath
        }
        
        if (currentTree.size > 1) {
            // Remove last item and return the new current path
            val newTree = currentTree.dropLast(1)
            _fileTreeLiveData.value = newTree
            val parentPath = newTree.last()[0]
            _currentFileValue.value = parentPath
            return parentPath
        } else {
            // Only one item in tree, check if it's root
            val lastPath = currentTree.last()[0]
            if (lastPath == rootPath) {
                // At root, clear tree and return null to close app
                _fileTreeLiveData.value = emptyList()
                return null
            } else {
                // Not at root, go to root
                _fileTreeLiveData.value = emptyList()
                _currentFileValue.value = rootPath
                return rootPath
            }
        }
    }
}
