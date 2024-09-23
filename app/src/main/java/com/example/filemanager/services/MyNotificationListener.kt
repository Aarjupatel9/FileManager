package com.example.filemanager.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.media.AudioManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.filemanager.R
import com.example.filemanager.services.LogManager
import com.example.filemanager.Entities.Constants.notificationId
import com.example.filemanager.Entities.Constants.notificationIdForNotification

class MyNotificationListener : NotificationListenerService() {
    private val TAG = "MyNotificationListener";
    private lateinit var logManager: LogManager
    private  var currentNotificationVolume = 0;


    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (applicationContext == null) {
            return
        }
        logManager = LogManager(applicationContext)


        val isOngoingCall = sbn.notification?.flags?.and(Notification.FLAG_ONGOING_EVENT) != 0
        val isForegroundService =
            sbn.notification?.flags?.and(Notification.FLAG_FOREGROUND_SERVICE) != 0


        val packageName = sbn.packageName
        val opPkg = sbn.opPkg
        val packageManager = applicationContext.packageManager
        val applicationInfo = try {
            packageManager.getApplicationInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }

        val applicationName = if (applicationInfo != null) {
            packageManager.getApplicationLabel(applicationInfo).toString()
        } else {
            packageName // Use package name if application name is not available
        }

        val notificationText = sbn.notification?.tickerText?.toString() ?: ""

        val textToSpeak = extractAppNameFromPackageName(packageName)

        Log.d(
            "NotificationListener",
            "applicationName: $applicationName,  opPkg: $opPkg,  textToSpeak: $textToSpeak,   Text: $notificationText"
        )
        Log.d(
            "NotificationListener",
            "textToSpeak1: $textToSpeak,  isClearable: ${sbn.isClearable},  id: ${sbn.id},   sbn: ${sbn.toString()},  key: ${sbn.key}"
        )
        Log.d(
                "NotificationListener",
        "textToSpeak2: ${sbn.user},  uid: ${sbn.uid},  settingText: ${sbn.notification.extras},   bubbleMetadata: ${sbn.notification.bubbleMetadata.toString()},  shortcutId: ${sbn.notification.shortcutId}"
        )

        val messageDetail = sbn.notification.extras;
//        messageDetail.getString("Big Text");

        Log.d(
            "NotificationListener",
            "textToSpeak3: ${sbn.user},  uid: ${sbn.uid},  messageDetail: ${sbn.notification.extras},   bubbleMetadata: ${sbn.notification.bubbleMetadata.toString()},  shortcutId: ${sbn.notification.shortcutId}"
        )


        if(opPkg == "com.google.android.gm"){
            logManager.saveLog(
                "textToSpeak: $textToSpeak,  isClearable: ${sbn.isClearable},  id: ${sbn.id},   Tag: ${sbn.tag},  falgs: ${sbn.notification.flags}, isForegroundService: $isForegroundService , isOngoingCall: $isOngoingCall, sbn: ${sbn.toString()}, sbn: ${sbn.packageName}, bubbleMetadata: ${sbn.notification.bubbleMetadata.toString()}, extras:${sbn.notification.extras}",
                3
            )
            playGoogleChatNotificationSound(sbn);
        }

        if (isBluetoothConnected() && !isForegroundService && !isOngoingCall) {
            var pass: Boolean=true;
            when(textToSpeak){
                "music"->{
                    pass=false
                }
                "whatsapp"->{
                    if(sbn.tag==null){
                        pass=false
                    }
                }
                else -> pass=true
            }
            if(pass){
                Log.d("NotificationListener", "bluetooth connected")
                logManager.saveLog("bluetooth connected , packageName: ${sbn.packageName}", 1)
                var textToSpeechManager =
                    TextToSpeechManager(applicationContext, textToSpeak);
            }
        }

        logManager.saveLog(
            "textToSpeak: $textToSpeak,  isClearable: ${sbn.isClearable},  id: ${sbn.id},   Tag: ${sbn.tag},  falgs: ${sbn.notification.flags}, isForegroundService: $isForegroundService , isOngoingCall: $isOngoingCall, sbn: ${sbn.toString()}, sbn: ${sbn.packageName}, bubbleMetadata: ${sbn.notification.bubbleMetadata.toString()}, extras:${sbn.notification.extras}",
            1
        )

    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Handle removed notifications here
    }

    fun extractAppNameFromPackageName(packageName: String): String {
        val segments = packageName.split(".")
        return when {
            segments.size == 2 -> segments.last()
            segments.size >= 3 -> segments.subList(2, segments.size).joinToString(".")
            else -> packageName // Return the original package name if no segments are found
        }
    }

    @SuppressLint("MissingPermission", "SuspiciousIndentation")
    fun isBluetoothConnected(): Boolean {
        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

        val profiles = intArrayOf(
            BluetoothProfile.A2DP,   // Advanced Audio Distribution Profile (for audio streaming)
            BluetoothProfile.HEADSET // Headset Profile (for audio streaming and voice calls)
            // Add more profiles as needed
        )

        for (profile in profiles) {
            val connectionState = bluetoothAdapter?.getProfileConnectionState(profile)
            if (connectionState == BluetoothProfile.STATE_CONNECTED) {
                return true
            }
        }
        return false
    }
    fun playGoogleChatNotificationSound(sbn:StatusBarNotification){

        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val notificationMaxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
        currentNotificationVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
        Log.d(TAG, "notification max volume : "+notificationMaxVol);
        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, notificationMaxVol, 0)
        Thread(kotlinx.coroutines.Runnable {

            Log.d(TAG, "Thread start")
            Thread.sleep(3000);
            reminderChatNotification();
            Thread.sleep(3000);
            Log.d(TAG, "Thread end")
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, currentNotificationVolume, 0)
            currentNotificationVolume = 0;
        }).start()
    }

     fun reminderChatNotification(){
        Log.d(TAG, "generateNotification start")

        val builder: NotificationCompat.Builder =
            NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentTitle("File Manager")
                .setContentText("Google Chat Alert").setChannelId(notificationId).setDefaults(Notification.DEFAULT_SOUND).setPriority(Notification.PRIORITY_MAX)
                .setAutoCancel(false);


        Log.d(TAG, "generateNotification start 2");

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        Log.d(TAG, "generateNotification build start")

        notificationManager.notify(notificationIdForNotification, builder.build())
        Log.d(TAG, "generateNotification end")
    }
}
