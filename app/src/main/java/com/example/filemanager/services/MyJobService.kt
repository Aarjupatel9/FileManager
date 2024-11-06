package com.example.filemanager.services

import android.app.ActivityManager
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.Date

class MyJobService : JobService() {
    private lateinit var logManager: LogManager

    override fun onStartJob(params: JobParameters?): Boolean {
        if (applicationContext == null) {
            Log.d("MyJobService", "applicationContext null")

            return false
        }
        logManager = LogManager(applicationContext)
        logManager.saveLog("MyJobService scheduled at ${Date()}", 2)
        if (!isMyServiceRunning(applicationContext)) {
            val serviceIntent = Intent(applicationContext, MyNotificationListener::class.java)
            logManager.saveLog("MyNotificationListener not running", 2)
            Log.d("MyJobService", "MyNotificationListener intent created")
            logManager.saveLog("MyNotificationListener intent created", 2)
            applicationContext.startService(serviceIntent)
            Log.d("MyJobService", "MyNotificationListener stated")
            logManager.saveLog("MyNotificationListener stated", 2)
        } else {
            logManager.saveLog("MyNotificationListener already running", 2)
            Log.d("MyJobService", "MyNotificationListener already running")
        }
        jobFinished(params, false)
        return false
    }

    fun isMyServiceRunning(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (MyNotificationListener::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }


    override fun onStopJob(params: JobParameters?): Boolean {
        return false
    }
}
