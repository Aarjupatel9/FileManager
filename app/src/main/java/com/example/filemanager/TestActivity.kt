package com.example.filemanager

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class TestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onCreate(savedInstanceState, persistentState)

        setContentView(R.layout.activity_home)

        verifyAllRequests()

    }

    private val requestPermissionLauncher2 =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {

            } else {
                Toast.makeText(
                    this.applicationContext,
                    "Can't continue without the required permissions",
                    Toast.LENGTH_LONG
                ).show()
            }
        }


    private fun verifyAllRequests() {

        if (ActivityCompat.checkSelfPermission(
                this.applicationContext,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher2.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {

            Log.d("test_activity", "granted")
        }

    }
}