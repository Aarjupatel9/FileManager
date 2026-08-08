package com.mhk.filemanager.ui.japcounter

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.mhk.filemanager.R
import com.mhk.filemanager.databinding.ActivityJapCounterBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.mhk.filemanager.utils.Permissions

class JapCounterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJapCounterBinding
    private val dataFile by lazy { File(filesDir, "jap_data.json") }
    private lateinit var sharedPrefs: SharedPreferences
    
    private var categories = mutableListOf<String>()
    private var dailyCounts = JSONObject()
    private var categoryTotals = JSONObject()
    private var targetsObj = JSONObject()
    private var categoryColors = JSONObject()
    private var categoryVibrate108 = JSONObject()
    private var grandTotal = 0L
    
    private var currentCategory = ""
    private var isVibrateOn = true
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private var isMalaMode = false
    private var malaCount = 0
    
    private var saveJob: kotlinx.coroutines.Job? = null
    private lateinit var importFileLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJapCounterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPrefs = getSharedPreferences("jap_prefs", Context.MODE_PRIVATE)
        isVibrateOn = sharedPrefs.getBoolean("vibrate", true)
        isMalaMode = sharedPrefs.getBoolean("mala_mode", false)
        updateVibrateIcon()
        updateMalaModeIcon()

        importFileLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) importData(uri)
        }

        // Request exact alarm permission for reminders (Android 12+)
        Permissions(this, null).requestExactAlarmPermission()

        // Schedule daily gentle nudges if enabled
        if (sharedPrefs.getBoolean("daily_nudge_enabled", true)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1002)
                }
            }
            DailyNudgeReceiver.scheduleDailyNudges(this)
        }

        loadData()

        binding.backButton.setOnClickListener { finish() }
        
        binding.vibrateToggleButton.setOnClickListener {
            isVibrateOn = !isVibrateOn
            sharedPrefs.edit().putBoolean("vibrate", isVibrateOn).apply()
            updateVibrateIcon()
        }

        binding.malaModeButton.setOnClickListener {
            isMalaMode = !isMalaMode
            sharedPrefs.edit().putBoolean("mala_mode", isMalaMode).apply()
            updateMalaModeIcon()
            updateUI()
        }

        binding.statsButton.setOnClickListener {
            val intent = Intent(this, JapStatsActivity::class.java)
            intent.putExtra("EXTRA_CATEGORY", currentCategory)
            startActivity(intent)
        }
        
        binding.settingsButton.setOnClickListener {
            showSettingsDialog()
        }

        binding.tapArea.setOnClickListener {
            if (isVibrateOn) {
                // Use a more premium, clicky haptic feedback constant
                val hapticType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    HapticFeedbackConstants.KEYBOARD_RELEASE
                } else {
                    HapticFeedbackConstants.KEYBOARD_TAP
                }
                it.performHapticFeedback(hapticType)
            }
            
            // Premium micro-animation bounce effect on the circular card
            binding.counterCard.animate()
                .scaleX(0.92f)
                .scaleY(0.92f)
                .setDuration(60)
                .withEndAction {
                    binding.counterCard.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(120)
                        .start()
                }
                .start()

            incrementCount()
        }

        checkAutoSync()
    }

    private fun checkAutoSync() {
        val lastSync = sharedPrefs.getLong("last_sync_timestamp", 0L)
        val lastPrompt = sharedPrefs.getLong("last_cloud_prompt_timestamp", 0L)
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSync > 24 * 60 * 60 * 1000L) {
            val token = sharedPrefs.getString("auth_token", null)
            if (token != null) {
                autoPushToCloud(token)
            } else if (currentTime - lastPrompt > 24 * 60 * 60 * 1000L) {
                showCloudConnectPrompt()
            }
        }
    }

    private fun showCloudConnectPrompt() {
        sharedPrefs.edit().putLong("last_cloud_prompt_timestamp", System.currentTimeMillis()).apply()
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Cloud Sync Available")
            .setMessage("Protect your data! Connect to https://codeshare.auctionng.org to securely backup your counts and sync across devices.")
            .setPositiveButton("Login/Sync Now") { _, _ ->
                val intent = Intent(this, JapStatsActivity::class.java)
                intent.putExtra("EXTRA_CATEGORY", currentCategory)
                startActivity(intent)
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun autoPushToCloud(token: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (!dataFile.exists()) return@launch
                val data = JSONObject(dataFile.readText())
                val url = URL("https://codeshare.auctionng.org/api/counter/sync")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.doOutput = true
                val body = JSONObject().apply { put("data", data) }
                OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
                if (conn.responseCode == 200) {
                    sharedPrefs.edit().putLong("last_sync_timestamp", System.currentTimeMillis()).apply()
                } else if (conn.responseCode == 401) {
                    sharedPrefs.edit().remove("auth_token").apply()
                    withContext(Dispatchers.Main) { showCloudConnectPrompt() }
                }
            } catch (e: Exception) {}
        }
    }

    private fun updateVibrateIcon() {
        if (isVibrateOn) {
            binding.vibrateToggleButton.setImageResource(R.drawable.baseline_vibration_24)
        } else {
            binding.vibrateToggleButton.setImageResource(R.drawable.baseline_mobile_off_24)
        }
    }

    private fun updateMalaModeIcon() {
        if (isMalaMode) {
            binding.malaModeButton.setImageResource(R.drawable.baseline_check_circle_24)
            val tv = android.util.TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
            binding.malaModeButton.imageTintList = android.content.res.ColorStateList.valueOf(tv.data)
        } else {
            binding.malaModeButton.setImageResource(R.drawable.baseline_radio_button_unchecked_24)
            binding.malaModeButton.imageTintList = androidx.core.content.ContextCompat.getColorStateList(this, android.R.color.darker_gray)
        }
    }

    private fun loadData() {
        lifecycleScope.launch(Dispatchers.IO) {
            if (dataFile.exists()) {
                val json = JSONObject(dataFile.readText())
                categories.clear()
                val catArray = json.optJSONArray("categories") ?: JSONArray()
                for (i in 0 until catArray.length()) categories.add(catArray.getString(i))
                dailyCounts = json.optJSONObject("daily_counts") ?: JSONObject()
                categoryTotals = json.optJSONObject("category_totals") ?: JSONObject()
                targetsObj = json.optJSONObject("targets") ?: JSONObject()
                categoryColors = json.optJSONObject("category_colors") ?: JSONObject()
                categoryVibrate108 = json.optJSONObject("category_vibrate_108") ?: JSONObject()
                grandTotal = json.optLong("grand_total", 0L)
                // Restore preferences from file if present
                val prefsJson = json.optJSONObject("preferences")
                if (prefsJson != null) {
                    withContext(Dispatchers.Main) { applyPreferencesJson(prefsJson) }
                }
            } else {
                categories.addAll(listOf("Guru mantra", "Radha", "Om Namah Shivaya"))
                saveDataSync()
            }
            withContext(Dispatchers.Main) { setupChips() }
        }
    }

    private fun saveDataSync() {
        val json = JSONObject()
        json.put("categories", JSONArray(categories))
        json.put("daily_counts", dailyCounts)
        json.put("category_totals", categoryTotals)
        json.put("targets", targetsObj)
        json.put("category_colors", categoryColors)
        json.put("category_vibrate_108", categoryVibrate108)
        json.put("grand_total", grandTotal)
        json.put("preferences", buildPreferencesJson())
        dataFile.writeText(json.toString())
    }

    private fun buildPreferencesJson(): JSONObject {
        return JSONObject().apply {
            put("vibrate", sharedPrefs.getBoolean("vibrate", true))
            put("mala_mode", sharedPrefs.getBoolean("mala_mode", false))
            put("chip_count_badge", sharedPrefs.getBoolean("chip_count_badge", true))
            put("daily_nudge_enabled", sharedPrefs.getBoolean("daily_nudge_enabled", true))
            put("reminderIntervalMins", sharedPrefs.getInt("reminderIntervalMins", 0))
        }
    }

    fun applyPreferencesJson(prefs: JSONObject) {
        val editor = sharedPrefs.edit()
        if (prefs.has("vibrate")) editor.putBoolean("vibrate", prefs.getBoolean("vibrate"))
        if (prefs.has("mala_mode")) editor.putBoolean("mala_mode", prefs.getBoolean("mala_mode"))
        if (prefs.has("chip_count_badge")) editor.putBoolean("chip_count_badge", prefs.getBoolean("chip_count_badge"))
        if (prefs.has("daily_nudge_enabled")) editor.putBoolean("daily_nudge_enabled", prefs.getBoolean("daily_nudge_enabled"))
        if (prefs.has("reminderIntervalMins")) editor.putInt("reminderIntervalMins", prefs.getInt("reminderIntervalMins"))
        editor.apply()

        // Apply to in-memory state
        isVibrateOn = sharedPrefs.getBoolean("vibrate", true)
        isMalaMode = sharedPrefs.getBoolean("mala_mode", false)
        updateVibrateIcon()
        updateMalaModeIcon()

        // Re-schedule alarms based on restored preferences
        val intervalMins = sharedPrefs.getInt("reminderIntervalMins", 0)
        scheduleReminders(intervalMins)
        if (sharedPrefs.getBoolean("daily_nudge_enabled", true)) {
            DailyNudgeReceiver.scheduleDailyNudges(this)
        } else {
            DailyNudgeReceiver.cancelDailyNudges(this)
        }
    }

    private fun setupChips() {
        binding.categoryChipGroup.removeAllViews()
        categories.sortByDescending { categoryTotals.optLong(it, 0L) }
        if (categories.isNotEmpty() && currentCategory.isEmpty()) currentCategory = categories[0]
        val showBadge = sharedPrefs.getBoolean("chip_count_badge", true)
        val today = dateFormat.format(Date())
        for (category in categories) {
            val chip = layoutInflater.inflate(R.layout.item_chip, binding.categoryChipGroup, false) as Chip
            chip.text = category
            chip.isClickable = true
            chip.isCheckable = true
            val colorStr = categoryColors.optString(category, "")
            if (colorStr.isNotEmpty()) {
                try {
                    val color = Color.parseColor(colorStr)
                    chip.setTextColor(color)
                    chip.chipStrokeColor = ContextCompat.getColorStateList(this, R.color.chip_stroke_color)
                } catch (e: Exception) {}
            }
            // Count badge
            if (showBadge) {
                val todayCount = dailyCounts.optJSONObject(today)?.optLong(category, 0L) ?: 0L
                if (todayCount > 0) {
                    chip.isChipIconVisible = false
                    val badgeText = if (todayCount >= 1000) "${todayCount / 1000}k" else todayCount.toString()
                    chip.text = "$category  · $badgeText"
                }
            }
            if (category == currentCategory) chip.isChecked = true
            chip.setOnClickListener {
                currentCategory = category
                updateUI()
            }
            binding.categoryChipGroup.addView(chip)
        }
        val addChip = layoutInflater.inflate(R.layout.item_chip, binding.categoryChipGroup, false) as Chip
        addChip.text = "+ Add New"
        addChip.isCheckable = false
        addChip.setOnClickListener { showAddCategoryDialog() }
        binding.categoryChipGroup.addView(addChip)
        updateUI()
    }

    private fun incrementCount() {
        if (currentCategory.isEmpty()) return
        val today = dateFormat.format(Date())
        if (!dailyCounts.has(today)) dailyCounts.put(today, JSONObject())
        val todayObj = dailyCounts.getJSONObject(today)
        val newCount = todayObj.optLong(currentCategory, 0L) + 1
        todayObj.put(currentCategory, newCount)
        categoryTotals.put(currentCategory, categoryTotals.optLong(currentCategory, 0L) + 1)
        grandTotal += 1

        if (newCount % 108 == 0L && categoryVibrate108.optBoolean(currentCategory, true)) {
            playSpecialVibration()
        }

        val dailyTarget = targetsObj.optJSONObject(currentCategory)?.optLong("daily", 0L) ?: 0L
        if (dailyTarget > 0 && newCount == dailyTarget) triggerCelebration()

        // Mala mode tracking
        if (isMalaMode) {
            val malaProgress = (newCount % 108).toInt()
            malaCount = (newCount / 108).toInt()
            if (malaProgress == 0 && newCount > 0) {
                // Completed a mala — show toast
                Toast.makeText(this, "Mala complete! ($malaCount total)", Toast.LENGTH_SHORT).show()
            }
        }

        saveJob?.cancel()
        saveJob = lifecycleScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(5000)
            saveDataSync()
        }
        updateUI()
    }

    private fun playSpecialVibration() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val pattern = longArrayOf(0, 200, 100, 200, 100, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            vibrator.vibrate(pattern, -1)
        }
    }

    private fun triggerCelebration() {
        Toast.makeText(this, "Daily Target Reached for $currentCategory! 🎉", Toast.LENGTH_LONG).show()
        val fadeIn = AlphaAnimation(0f, 1f).apply { duration = 1000 }
        binding.countText.startAnimation(fadeIn)
    }

    private fun updateUI() {
        if (currentCategory.isEmpty()) return
        val today = dateFormat.format(Date())
        val todayCatCount = dailyCounts.optJSONObject(today)?.optLong(currentCategory, 0L) ?: 0L
        binding.countText.text = todayCatCount.toString()
        val colorStr = categoryColors.optString(currentCategory, "")
        val tv = android.util.TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
        val primaryColor = tv.data
        if (colorStr.isNotEmpty()) {
            try { binding.countText.setTextColor(Color.parseColor(colorStr)) }
            catch (e: Exception) { binding.countText.setTextColor(primaryColor) }
        } else {
            binding.countText.setTextColor(primaryColor)
        }
        val dailyTarget = targetsObj.optJSONObject(currentCategory)?.optLong("daily", 0L) ?: 0L
        if (dailyTarget > 0) {
            binding.targetProgressText.visibility = View.VISIBLE
            binding.targetProgressText.text = "Target: $todayCatCount / $dailyTarget"
        } else {
            binding.targetProgressText.visibility = View.GONE
        }

        // Mala mode UI
        if (isMalaMode) {
            binding.malaProgressView.visibility = View.VISIBLE
            binding.malaCountText.visibility = View.VISIBLE
            val malaProgress = (todayCatCount % 108).toInt()
            malaCount = (todayCatCount / 108).toInt()
            binding.malaProgressView.progress = malaProgress
            binding.malaCountText.text = "$malaCount mala${if (malaCount != 1) "s" else ""} completed"
            if (colorStr.isNotEmpty()) {
                try { binding.malaProgressView.activeColor = Color.parseColor(colorStr) }
                catch (e: Exception) {}
            }
        } else {
            binding.malaProgressView.visibility = View.GONE
            binding.malaCountText.visibility = View.GONE
        }

        // Streak
        val streak = calculateStreak()
        if (streak > 0) {
            binding.streakText.visibility = View.VISIBLE
            binding.streakText.text = "$streak-day streak 🔥"
        } else {
            binding.streakText.visibility = View.GONE
        }
    }

    private fun calculateStreak(): Int {
        val cal = java.util.Calendar.getInstance()
        var streak = 0

        for (i in 0 until 365) {
            cal.time = Date()
            cal.add(java.util.Calendar.DAY_OF_YEAR, -i)
            val dateStr = dateFormat.format(cal.time)
            val dayObj = dailyCounts.optJSONObject(dateStr)
            val count = if (currentCategory.isEmpty()) {
                var total = 0L
                val keys = dayObj?.keys()
                while (keys?.hasNext() == true) total += dayObj.optLong(keys.next(), 0L)
                total
            } else {
                dayObj?.optLong(currentCategory, 0L) ?: 0L
            }

            if (count > 0) {
                streak++
            } else {
                if (i == 0) continue
                break
            }
        }

        return streak
    }

    private fun showAddCategoryDialog() {
        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
        builder.setTitle("Add New Category")
        val input = EditText(this)
        builder.setView(input)
        builder.setPositiveButton("Add") { _, _ ->
            val newCat = input.text.toString().trim()
            if (newCat.isNotEmpty() && !categories.contains(newCat)) {
                categories.add(newCat)
                currentCategory = newCat
                lifecycleScope.launch(Dispatchers.IO) {
                    saveDataSync()
                    withContext(Dispatchers.Main) { setupChips() }
                }
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }
    
    private fun showSettingsDialog() {
        if (currentCategory.isEmpty()) return
        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
        val view = layoutInflater.inflate(R.layout.dialog_jap_settings, null)
        builder.setView(view)
        
        val titleText = view.findViewById<android.widget.TextView>(R.id.dialogTitle)
        titleText.text = "Settings for $currentCategory"
        
        val dailyEdit = view.findViewById<EditText>(R.id.editDailyTarget)
        val endEdit = view.findViewById<EditText>(R.id.editEndTarget)
        val spinner = view.findViewById<Spinner>(R.id.reminderSpinner)
        val colorLayout = view.findViewById<LinearLayout>(R.id.colorPickerLayout)
        val vibrate108Check = view.findViewById<CheckBox>(R.id.vibrate108Checkbox)
        val chipBadgeCheck = view.findViewById<CheckBox>(R.id.chipBadgeCheckbox)
        val dailyNudgeCheck = view.findViewById<CheckBox>(R.id.dailyNudgeCheckbox)
        val disconnectBtn = view.findViewById<Button>(R.id.disconnectCloudBtn)
        val exportBtn = view.findViewById<Button>(R.id.exportBtn)
        val importBtn = view.findViewById<Button>(R.id.importBtn)

        exportBtn.setOnClickListener { exportData() }
        importBtn.setOnClickListener {
            importFileLauncher.launch(arrayOf("application/json", "*/*"))
        }
        
        val catTargets = targetsObj.optJSONObject(currentCategory)
        dailyEdit.setText(catTargets?.optLong("daily", 0L)?.takeIf { it > 0 }?.toString() ?: "")
        endEdit.setText(catTargets?.optLong("end", 0L)?.takeIf { it > 0 }?.toString() ?: "")
        
        val options = arrayOf("Off", "Every 1 Minute", "Every 30 Minutes", "Every 1 Hour", "Every 3 Hours", "Every 6 Hours")
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val selection = when(sharedPrefs.getInt("reminderIntervalMins", 0)) {
            1 -> 1; 30 -> 2; 60 -> 3; 180 -> 4; 360 -> 5; else -> 0
        }
        spinner.setSelection(selection)
        vibrate108Check.isChecked = categoryVibrate108.optBoolean(currentCategory, true)
        chipBadgeCheck.isChecked = sharedPrefs.getBoolean("chip_count_badge", true)
        dailyNudgeCheck.isChecked = sharedPrefs.getBoolean("daily_nudge_enabled", true)

        // Token disconnect logic
        val token = sharedPrefs.getString("auth_token", null)
        if (token != null) {
            disconnectBtn.visibility = View.VISIBLE
            disconnectBtn.setOnClickListener {
                sharedPrefs.edit().remove("auth_token").apply()
                Toast.makeText(this, "Disconnected from Cloud", Toast.LENGTH_SHORT).show()
                disconnectBtn.visibility = View.GONE
            }
        } else {
            disconnectBtn.visibility = View.GONE
        }

        // Expanded Color Palette
        val colors = arrayOf(
            "#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5", 
            "#2196F3", "#03A9F4", "#00BCD4", "#009688", "#4CAF50", 
            "#8BC34A", "#CDDC39", "#FFC107", "#FF9800", "#FF5722", 
            "#795548", "#9E9E9E", "#607D8B", "#000000"
        )
        var selectedColor = categoryColors.optString(currentCategory, "#2196F3")
        colorLayout.removeAllViews()
        for (colorHex in colors) {
            val colorView = View(this)
            val size = (34 * resources.displayMetrics.density).toInt()
            val params = LinearLayout.LayoutParams(size, size).apply { marginStart = 8 }
            colorView.layoutParams = params
            colorView.setBackgroundColor(Color.parseColor(colorHex))
            colorView.setOnClickListener {
                selectedColor = colorHex
                Toast.makeText(this, "Color selected!", Toast.LENGTH_SHORT).show()
            }
            colorLayout.addView(colorView)
        }
        
        builder.setPositiveButton("Save") { _, _ ->
            val dailyVal = dailyEdit.text.toString().toLongOrNull() ?: 0L
            val endVal = endEdit.text.toString().toLongOrNull() ?: 0L
            targetsObj.put(currentCategory, JSONObject().apply { put("daily", dailyVal); put("end", endVal) })
            categoryColors.put(currentCategory, selectedColor)
            categoryVibrate108.put(currentCategory, vibrate108Check.isChecked)
            
            val selectedMins = when (spinner.selectedItemPosition) {
                1 -> 1; 2 -> 30; 3 -> 60; 4 -> 180; 5 -> 360; else -> 0
            }
            sharedPrefs.edit().putInt("reminderIntervalMins", selectedMins).apply()
            sharedPrefs.edit().putBoolean("chip_count_badge", chipBadgeCheck.isChecked).apply()
            sharedPrefs.edit().putBoolean("daily_nudge_enabled", dailyNudgeCheck.isChecked).apply()
            if (dailyNudgeCheck.isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1002)
                    }
                }
                DailyNudgeReceiver.scheduleDailyNudges(this)
            } else {
                DailyNudgeReceiver.cancelDailyNudges(this)
            }
            scheduleReminders(selectedMins)
            lifecycleScope.launch(Dispatchers.IO) { saveDataSync() }
            setupChips()
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun exportData() {
        if (!dataFile.exists()) {
            Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val exportDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "FileManager")
            if (!exportDir.exists()) exportDir.mkdirs()
            val exportFile = File(exportDir, "jap_data_backup_$timestamp.json")
            FileOutputStream(exportFile).use { it.write(dataFile.readText().toByteArray()) }
            Toast.makeText(this, "Exported to Downloads/FileManager/${exportFile.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun importData(uri: Uri) {
        try {
            val jsonStr = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: throw Exception("Could not read file")
            val json = JSONObject(jsonStr)

            // Validate it has expected fields
            if (!json.has("daily_counts") || !json.has("grand_total")) {
                Toast.makeText(this, "Invalid backup file format", Toast.LENGTH_LONG).show()
                return
            }

            // Replace local data
            categories.clear()
            val catArray = json.optJSONArray("categories") ?: JSONArray()
            for (i in 0 until catArray.length()) categories.add(catArray.getString(i))
            dailyCounts = json.optJSONObject("daily_counts") ?: JSONObject()
            categoryTotals = json.optJSONObject("category_totals") ?: JSONObject()
            targetsObj = json.optJSONObject("targets") ?: JSONObject()
            categoryColors = json.optJSONObject("category_colors") ?: JSONObject()
            categoryVibrate108 = json.optJSONObject("category_vibrate_108") ?: JSONObject()
            grandTotal = json.optLong("grand_total", 0L)

            saveDataSync()
            setupChips()
            Toast.makeText(this, "Data imported successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun scheduleReminders(intervalMins: Int) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, JapReminderReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, flags)
        alarmManager.cancel(pendingIntent)
        if (intervalMins > 0) {
            val intervalMillis = intervalMins * 60 * 1000L
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + intervalMillis, intervalMillis, pendingIntent)
        }
    }

    override fun onPause() {
        super.onPause()
        saveJob?.cancel()
        if (categories.isNotEmpty()) lifecycleScope.launch(Dispatchers.IO) { saveDataSync() }
    }

    private var isFirstResume = true

    override fun onResume() {
        super.onResume()
        if (isFirstResume) {
            isFirstResume = false
            return // onCreate already called loadData()
        }
        // Reload from file in case JapStatsActivity modified it (conflict resolution, pull)
        loadData()
    }
}
