package com.mhk.filemanager.ui.japcounter

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
                // Trigger Haptic Feedback natively
                it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            }
            incrementCount()
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
            val oldFile = File(filesDir, "jap_counts.json")
            if (oldFile.exists() && !dataFile.exists()) {
                val oldJson = JSONObject(oldFile.readText())
                val oldCatArray = oldJson.optJSONArray("categories") ?: JSONArray()
                for (i in 0 until oldCatArray.length()) {
                    categories.add(oldCatArray.getString(i))
                }
                dailyCounts = oldJson.optJSONObject("counts") ?: JSONObject()
                
                val days = dailyCounts.keys()
                while (days.hasNext()) {
                    val day = days.next()
                    val dayObj = dailyCounts.getJSONObject(day)
                    val cats = dayObj.keys()
                    while (cats.hasNext()) {
                        val c = cats.next()
                        val count = dayObj.getInt(c)
                        val currentTotal = categoryTotals.optLong(c, 0)
                        categoryTotals.put(c, currentTotal + count)
                        grandTotal += count
                    }
                }
                saveDataSync()
                oldFile.delete() 
            } else if (!dataFile.exists()) {
                categories.add("Guru mantra")
                categories.add("Radha")
                categories.add("Om Namah Shivaya")
                saveDataSync()
            } else {
                val content = dataFile.readText()
                val json = JSONObject(content)
                
                categories.clear()
                val catArray = json.optJSONArray("categories") ?: JSONArray()
                for (i in 0 until catArray.length()) {
                    categories.add(catArray.getString(i))
                }
                
                dailyCounts = json.optJSONObject("daily_counts") ?: JSONObject()
                categoryTotals = json.optJSONObject("category_totals") ?: JSONObject()
                targetsObj = json.optJSONObject("targets") ?: JSONObject()
                grandTotal = json.optLong("grand_total", 0L)
            }
            
            withContext(Dispatchers.Main) {
                setupChips()
            }
        }
    }

    private fun saveDataSync() {
        val json = JSONObject()
        json.put("categories", JSONArray(categories))
        json.put("daily_counts", dailyCounts)
        json.put("category_totals", categoryTotals)
        json.put("targets", targetsObj)
        json.put("grand_total", grandTotal)
        dataFile.writeText(json.toString())
    }

    private fun setupChips() {
        binding.categoryChipGroup.removeAllViews()
        
        // Auto-sort categories so the most used items appear first
        categories.sortByDescending { categoryTotals.optLong(it, 0L) }

        if (categories.isNotEmpty() && currentCategory.isEmpty()) {
            currentCategory = categories[0]
        }
        
        for (category in categories) {
            val chip = layoutInflater.inflate(R.layout.item_chip, binding.categoryChipGroup, false) as Chip
            chip.text = category
            chip.isClickable = true
            chip.isCheckable = true
            
            if (category == currentCategory) {
                chip.isChecked = true
            }
            
            chip.setOnClickListener {
                currentCategory = category
                updateUI()
            }
            binding.categoryChipGroup.addView(chip)
        }
        
        // Add new category chip
        val addChip = layoutInflater.inflate(R.layout.item_chip, binding.categoryChipGroup, false) as Chip
        addChip.text = "+ Add New"
        addChip.isClickable = true
        addChip.isCheckable = false
        addChip.setOnClickListener {
            showAddCategoryDialog()
        }
        binding.categoryChipGroup.addView(addChip)
        
        updateUI()
    }

    private fun getTodayKey(): String {
        return dateFormat.format(Date())
    }

    private fun incrementCount() {
        if (currentCategory.isEmpty()) return
        val today = getTodayKey()
        
        if (!dailyCounts.has(today)) {
            dailyCounts.put(today, JSONObject())
        }
        val todayObj = dailyCounts.getJSONObject(today)
        val todayCatCount = todayObj.optLong(currentCategory, 0L)
        todayObj.put(currentCategory, todayCatCount + 1)
        
        val catTotal = categoryTotals.optLong(currentCategory, 0L)
        categoryTotals.put(currentCategory, catTotal + 1)
        
        grandTotal += 1
        
        // Debounce / Batch the disk writes
        saveJob?.cancel()
        saveJob = lifecycleScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(5000) // Wait 5 seconds after tapping stops to write to disk
            saveDataSync()
        }
        
        updateUI()
    }

    private fun updateUI() {
        if (currentCategory.isEmpty()) return
        
        val today = getTodayKey()
        val todayObj = dailyCounts.optJSONObject(today)
        val todayCatCount = todayObj?.optLong(currentCategory, 0L) ?: 0L
        
        binding.countText.text = todayCatCount.toString()
        
        val catTargets = targetsObj.optJSONObject(currentCategory)
        if (catTargets != null) {
            val dailyTarget = catTargets.optLong("daily", 0L)
            if (dailyTarget > 0) {
                binding.targetProgressText.visibility = android.view.View.VISIBLE
                binding.targetProgressText.text = "Target: $todayCatCount / $dailyTarget"
            } else {
                binding.targetProgressText.visibility = android.view.View.GONE
            }
        } else {
            binding.targetProgressText.visibility = android.view.View.GONE
        }
    }

    private fun showAddCategoryDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Add New Chant Category")
        val input = EditText(this)
        builder.setView(input)
        builder.setPositiveButton("Add") { _, _ ->
            val newCat = input.text.toString().trim()
            if (newCat.isNotEmpty() && !categories.contains(newCat)) {
                categories.add(newCat)
                currentCategory = newCat // select it!
                lifecycleScope.launch(Dispatchers.IO) {
                    saveDataSync()
                    withContext(Dispatchers.Main) {
                        setupChips()
                    }
                }
            } else if (newCat.isEmpty()) {
                Toast.makeText(this, "Category name cannot be empty", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Category already exists", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
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
        
        val catTargets = targetsObj.optJSONObject(currentCategory)
        if (catTargets != null) {
            val d = catTargets.optLong("daily", 0L)
            val e = catTargets.optLong("end", 0L)
            if (d > 0) dailyEdit.setText(d.toString())
            if (e > 0) endEdit.setText(e.toString())
        }
        
        val options = arrayOf("Off", "Every 1 Minute", "Every 30 Minutes", "Every 1 Hour", "Every 3 Hours", "Every 6 Hours")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        
        // Handle migration from old 'hours' value if needed
        val legacyInterval = sharedPrefs.getInt("reminderInterval", -1)
        var currentIntervalMins = sharedPrefs.getInt("reminderIntervalMins", 0)
        if (legacyInterval > 0 && currentIntervalMins == 0) {
            currentIntervalMins = legacyInterval * 60
        }
        
        val selection = when(currentIntervalMins) {
            1 -> 1
            30 -> 2
            60 -> 3
            180 -> 4
            360 -> 5
            else -> 0
        }
        spinner.setSelection(selection)
        
        builder.setPositiveButton("Save Tasks") { _, _ ->
            val dailyVal = dailyEdit.text.toString().toLongOrNull() ?: 0L
            val endVal = endEdit.text.toString().toLongOrNull() ?: 0L
            
            val newCatTargets = JSONObject()
            newCatTargets.put("daily", dailyVal)
            newCatTargets.put("end", endVal)
            targetsObj.put(currentCategory, newCatTargets)
            
            val selectedMins = when (spinner.selectedItemPosition) {
                1 -> 1
                2 -> 30
                3 -> 60
                4 -> 180
                5 -> 360
                else -> 0
            }
            sharedPrefs.edit().putInt("reminderIntervalMins", selectedMins).apply()
            
            scheduleReminders(selectedMins)
            
            lifecycleScope.launch(Dispatchers.IO) {
                saveDataSync()
            }
            updateUI()
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }
    
    private fun scheduleReminders(intervalMins: Int) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, JapReminderReceiver::class.java)
        val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, flags)
        
        alarmManager.cancel(pendingIntent)
        
        if (intervalMins > 0) {
            val intervalMillis = intervalMins * 60 * 1000L
            val triggerAt = System.currentTimeMillis() + intervalMillis
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, triggerAt, intervalMillis, pendingIntent)
        }
    }

    override fun onPause() {
        super.onPause()
        // Force save immediately if leaving the app or navigating away
        saveJob?.cancel()
        if (categories.isNotEmpty()) {
            lifecycleScope.launch(Dispatchers.IO) {
                saveDataSync()
            }
        }
    }
}
