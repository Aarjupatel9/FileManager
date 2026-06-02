package com.mhk.filemanager.utils

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.mhk.filemanager.ui.main.FileAdapter

class Permissions(private var context: AppCompatActivity, var fileAdapter: FileAdapter?) {

    private val readExternal = READ_EXTERNAL_STORAGE
    private val notificationPermission = POST_NOTIFICATIONS

    fun requestStoragePermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: request MANAGE_EXTERNAL_STORAGE (All Files Access)
            if (Environment.isExternalStorageManager()) {
                return true
            } else {
                AlertDialog.Builder(context).setTitle("All Files Access Permission")
                    .setMessage("This app is a File Manager and requires access to manage all files on your device. Please grant this permission on the next screen.")
                    .setNegativeButton("Cancel") { dialog, _ ->
                        Toast.makeText(
                            context,
                            "Please grant All Files Access permission to use this app!",
                            Toast.LENGTH_LONG
                        ).show()
                        dialog.dismiss()
                    }.setPositiveButton("OK") { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        context.startActivity(intent)
                    }.show()
                return false
            }
        } else {
            // Android 10 and below: request READ_EXTERNAL_STORAGE and WRITE_EXTERNAL_STORAGE
            val readGranted = ContextCompat.checkSelfPermission(context, readExternal) == PackageManager.PERMISSION_GRANTED
            val writeGranted = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            } else {
                true // On Android 10 (Q), WRITE_EXTERNAL_STORAGE is ignored if requestLegacyExternalStorage isn't used
            }

            if (readGranted && writeGranted) {
                return true
            } else {
                val neededPermissions = mutableListOf<String>()
                neededPermissions.add(readExternal)
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    neededPermissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }

                AlertDialog.Builder(context).setTitle("Storage Permission")
                    .setMessage("Storage permission is needed to show and manage your files.")
                    .setNegativeButton("Cancel") { dialog, _ ->
                        Toast.makeText(
                            context, "Storage permission denied!", Toast.LENGTH_SHORT
                        ).show()
                        dialog.dismiss()
                    }.setPositiveButton("OK") { _, _ ->
                        legacyStoragePermissions.launch(neededPermissions.toTypedArray())
                    }.show()
            }
        }
        return false
    }

    private val legacyStoragePermissions =
        context.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionMap ->
            if (permissionMap.values.all { it }) {
                fileAdapter?.loadMediaFiles(Environment.getExternalStorageDirectory().absolutePath)
            } else {
                Toast.makeText(
                    context,
                    "Storage permissions not granted! Please grant Storage permission to use this app.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }


    fun requestNotificationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context, notificationPermission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(notificationPermission)
            }
        }
    }


    private val notificationPermissionLauncher =
        context.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(
                    context,
                    "You won't see music notifications without this permission.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(android.app.AlarmManager::class.java)
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                AlertDialog.Builder(context)
                    .setTitle("Exact Alarm Permission")
                    .setMessage("To receive reminders at the exact time you set, please allow 'Alarms & Reminders' for this app in Settings.")
                    .setPositiveButton("Open Settings") { _, _ ->
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                        )
                        context.startActivity(intent)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }
}
