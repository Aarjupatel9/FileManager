package com.example.filemanager.services

import android.content.Context
import java.io.File
import java.io.FileOutputStream

class LogManager(context: Context) {
    private val externalFilesDir = context.getExternalFilesDir(null)
    private val logFiles = arrayOf(
        File(externalFilesDir, "notification_listener_logs.txt"),
        File(externalFilesDir, "job_scheduler_log.txt"),
        File(externalFilesDir, "notification_listener_logs_text_to_speak.txt"),
        File(externalFilesDir, "default_log.txt")
    )

    fun saveLog(logMessage: String, type: Int) {

        val text = "$\n$logMessage"
        try {
            val file = if (type < logFiles.size) {
                logFiles[type]
            } else {
                logFiles[logFiles.size - 1]
            }
            val fos = FileOutputStream(file, true) // Open the file in append mode

            fos.write(text.toByteArray())
            fos.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}