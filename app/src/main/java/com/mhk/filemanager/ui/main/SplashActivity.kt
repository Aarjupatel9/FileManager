package com.mhk.filemanager.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mhk.filemanager.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Preload root directory files if storage permission is already granted
        val hasPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (hasPermission) {
            val rootPath = android.os.Environment.getExternalStorageDirectory().absolutePath
            FileAdapter.preloadFiles(applicationContext, lifecycleScope, rootPath)
        }

        lifecycleScope.launch {
            // Wait for 1.5 seconds (1500ms)
            delay(1500)
            
            // Launch MainActivity
            val intent = Intent(this@SplashActivity, MainActivity::class.java)
            startActivity(intent)
            
            // Finish SplashActivity and execute a smooth transition animation
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }
}
