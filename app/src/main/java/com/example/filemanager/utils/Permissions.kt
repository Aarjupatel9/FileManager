package com.example.filemanager.utils

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.READ_MEDIA_VIDEO
import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.filemanager.FileAdapter

class Permissions(private var context: AppCompatActivity, var fileAdapter: FileAdapter?) {

    private val readExternal = READ_EXTERNAL_STORAGE
    private val readVideo = READ_MEDIA_VIDEO
    private val readImages = READ_MEDIA_IMAGES
    private val permissions = arrayOf(
        readVideo, readImages
    )

    private val notificationPermission = POST_NOTIFICATIONS

    fun requestStoragePermissions(): Boolean {
        //check the API level
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            //filter permissions array in order to get permissions that have not been granted
            val notGrantedPermissions = permissions.filterNot { permission ->
                ContextCompat.checkSelfPermission(
                    context, permission
                ) == PackageManager.PERMISSION_GRANTED
            }
            if (notGrantedPermissions.isNotEmpty()) {
                //check if permission was previously denied and return a boolean value
                AlertDialog.Builder(context).setTitle("Storage Permission")
                    .setMessage("Storage permission is needed in order to show images and videos and other files in the device")
                    .setNegativeButton("Cancel") { dialog, _ ->
                        Toast.makeText(
                            context,
                            "Please Give Storage permission in order to use this app!",
                            Toast.LENGTH_LONG
                        ).show()
                        dialog.dismiss()
                    }.setPositiveButton("OK") { _, _ ->
                        videoImagesPermission.launch(notGrantedPermissions.toTypedArray())
                    }.show()

            } else {
                return true
            }
        } else {
            //check if permission is granted
            if (ContextCompat.checkSelfPermission(
                    context, readExternal
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                return true
            } else {
                AlertDialog.Builder(context).setTitle("Storage Permission")
                    .setMessage("Storage permission is needed in order to show images and video")
                    .setNegativeButton("Cancel") { dialog, _ ->
                        Toast.makeText(
                            context, "Read external storage permission denied!", Toast.LENGTH_SHORT
                        ).show()
                        dialog.dismiss()
                    }.setPositiveButton("OK") { _, _ ->
                        readExternalPermission.launch(readExternal)
                    }.show()
            }
        }
        return false
    }


    private val videoImagesPermission =
        context.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionMap ->
            if (permissionMap.all { it.value }) {
                fileAdapter?.loadMediaFiles(Environment.getExternalStorageDirectory().absolutePath)
            } else {
                Toast.makeText(
                    context,
                    "Media permissions not granted!, Please Give Storage permission in order to use this app!",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    //register a permissions activity launcher for a single permission
    private val readExternalPermission =
        context.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                fileAdapter?.loadMediaFiles(Environment.getExternalStorageDirectory().absolutePath)
            } else {
                Toast.makeText(
                    context, "Read external storage permission denied!", Toast.LENGTH_SHORT
                ).show()
            }
        }


    fun requestNotificationPermissions(): Boolean {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context, notificationPermission
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                return true
            } else {
                AlertDialog.Builder(context).setTitle("Storage Permission")
                    .setMessage("Storage permission is needed in order to show images and video")
                    .setNegativeButton("Cancel") { dialog, _ ->
                        Toast.makeText(
                            context, "Read external storage permission denied!", Toast.LENGTH_SHORT
                        ).show()
                        dialog.dismiss()
                    }.setPositiveButton("OK") { _, _ ->
                        notificationPermissionListener.launch(notificationPermission)
                    }.show()
            }
        }
        return false
    }


    private val notificationPermissionListener =
        context.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                fileAdapter?.loadMediaFiles(Environment.getExternalStorageDirectory().absolutePath)
            } else {
                Toast.makeText(
                    context,
                    "Please give Notification permission in order to use full power of application's notification service",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }


}