package com.mhk.filemanager.ui.japcounter

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    
    private var saveJob: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJapCounterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPrefs = getSharedPreferences("jap_prefs", Context.MODE_PRIVATE)
        isVibrateOn = sharedPrefs.getBoolean("vibrate", true)
        updateVibrateIcon()

        loadData()

        binding.backButton.setOnClickListener { finish() }
        
        binding.vibrateToggleButton.setOnClickListener {
            isVibrateOn = !isVibrateOn
            sharedPrefs.edit().putBoolean("vibrate", isVibrateOn).apply()
            updateVibrateIcon()
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
                it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            }
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
        AlertDialog.Builder(this)
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
        dataFile.writeText(json.toString())
    }

    private fun setupChips() {
        binding.categoryChipGroup.removeAllViews()
        categories.sortByDescending { categoryTotals.optLong(it, 0L) }
        if (categories.isNotEmpty() && currentCategory.isEmpty()) currentCategory = categories[0]
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
        if (colorStr.isNotEmpty()) {
            try { binding.countText.setTextColor(Color.parseColor(colorStr)) }
            catch (e: Exception) { binding.countText.setTextColor(ContextCompat.getColor(this, R.color.primary_color)) }
        } else {
            binding.countText.setTextColor(ContextCompat.getColor(this, R.color.primary_color))
        }
        val dailyTarget = targetsObj.optJSONObject(currentCategory)?.optLong("daily", 0L) ?: 0L
        if (dailyTarget > 0) {
            binding.targetProgressText.visibility = View.VISIBLE
            binding.targetProgressText.text = "Target: $todayCatCount / $dailyTarget"
        } else {
            binding.targetProgressText.visibility = View.GONE
        }
    }

    private fun showAddCategoryDialog() {
        val builder = AlertDialog.Builder(this)
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
        val builder = AlertDialog.Builder(this)
        val view = layoutInflater.inflate(R.layout.dialog_jap_settings, null)
        builder.setView(view)
        
        val titleText = view.findViewById<android.widget.TextView>(R.id.dialogTitle)
        titleText.text = "Settings for $currentCategory"
        
        val dailyEdit = view.findViewById<EditText>(R.id.editDailyTarget)
        val endEdit = view.findViewById<EditText>(R.id.editEndTarget)
        val spinner = view.findViewById<Spinner>(R.id.reminderSpinner)
        val colorLayout = view.findViewById<LinearLayout>(R.id.colorPickerLayout)
        val vibrate108Check = view.findViewById<CheckBox>(R.id.vibrate108Checkbox)
        val disconnectBtn = view.findViewById<Button>(R.id.disconnectCloudBtn)
        
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
            scheduleReminders(selectedMins)
            lifecycleScope.launch(Dispatchers.IO) { saveDataSync() }
            setupChips()
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
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
}
