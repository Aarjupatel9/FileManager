package com.example.filemanager.services

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.filemanager.services.LogManager
import java.util.Date

class MyBroadcastReceiver : BroadcastReceiver() {
    private val TAG = "MyBroadcastReceiver"
    lateinit var logManager: LogManager

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("MyBroadcastReceiver", "action: ${intent?.action}")

        context?.let { logManager = LogManager(it) }

        val x = arrayOf(
            BluetoothAdapter.ACTION_STATE_CHANGED,
            BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED,
            "android.bluetooth.device.action.ACL_DISCONNECTED",
            "android.bluetooth.device.action.ACL_CONNECTED"
        )

        val action = intent?.action

        if (action in x) {

            val packageName = intent?.getStringExtra("package")
            val notificationText = intent?.getStringExtra("text")
            Log.d("MyBroadcastReceiver", "Package: $packageName, Text: $notificationText")
            logManager.saveLog("Package: $packageName, Text: $notificationText", 3)

            val state = intent?.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            context?.let { scheduleJob(it) }

        //            when (state) {
//                BluetoothAdapter.STATE_OFF -> {
//                    context?.let { scheduleJob(it) }
//
//                }
//                BluetoothAdapter.STATE_TURNING_OFF -> {
//                    context?.let { scheduleJob(it) }
//
//                }
//                BluetoothAdapter.STATE_ON -> {
//                    context?.let { scheduleJob(it) }
//                }
//
//                BluetoothAdapter.STATE_TURNING_ON -> {
//                    context?.let { scheduleJob(it) }
//                }
//            }
        }
    }

    private fun scheduleJob(context: Context) {
        logManager.saveLog("MyBroadcastReceiver scheduleJob start at ${Date()}", 3)

        val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        val componentName = ComponentName(context, MyJobService::class.java)
        val jobInfo = JobInfo.Builder(1, componentName)
            .setPeriodic(20 * 60 * 1000) // 15 minutes (minimum allowed interval)
            .setPersisted(true) // Job will survive device reboots
            .build()
        val resultCode = jobScheduler.schedule(jobInfo)
        if (resultCode == JobScheduler.RESULT_SUCCESS) {
            Log.d(TAG, "Job scheduled successfully!")
            logManager.saveLog("Job scheduled successfully!", 3)

        } else {
            Log.e(TAG, "Job scheduling failed!")
            logManager.saveLog("ob scheduling failed!", 3)

        }
    }
}