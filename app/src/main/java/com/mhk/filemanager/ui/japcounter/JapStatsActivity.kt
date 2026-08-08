package com.mhk.filemanager.ui.japcounter

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.mhk.filemanager.R
import com.mhk.filemanager.databinding.ActivityJapStatsBinding
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
import java.util.Calendar
import java.util.Date
import java.util.Locale

class JapStatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJapStatsBinding
    private val dataFile by lazy { File(filesDir, "jap_data.json") }
    private lateinit var sharedPrefs: SharedPreferences
    private var jsonData: JSONObject? = null
    private var categories = mutableListOf<String>()
    private var activeCategory = ""
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private var historyAdapter: HistoryAdapter? = null
    private var currentPeriod = 0 // 0=Week, 1=Month, 2=All

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJapStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPrefs = getSharedPreferences("jap_prefs", Context.MODE_PRIVATE)
        activeCategory = intent.getStringExtra("EXTRA_CATEGORY") ?: ""

        setupRecyclerView()
        loadData()

        binding.backButton.setOnClickListener { finish() }
        binding.syncButton.setOnClickListener { handleSyncChoice() }

        binding.periodTabLayout.addOnTabSelectedListener(object :
            com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                currentPeriod = tab.position
                updateStats()
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter()
        binding.historyRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.historyRecyclerView.adapter = historyAdapter
    }

    private fun loadData() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (dataFile.exists()) {
                    val jsonStr = dataFile.readText()
                    if (jsonStr.isEmpty()) return@launch
                    val json = JSONObject(jsonStr)
                    jsonData = json
                    
                    val catArray = json.optJSONArray("categories") ?: JSONArray()
                    categories.clear()
                    for (i in 0 until catArray.length()) {
                        categories.add(catArray.getString(i))
                    }
                    
                    val categoryTotals = json.optJSONObject("category_totals") ?: JSONObject()
                    categories.sortByDescending { categoryTotals.optLong(it, 0L) }
                    
                    withContext(Dispatchers.Main) {
                        setupChips()
                        updateStats()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun setupChips() {
        binding.categoryChipGroup.removeAllViews()
        val colors = jsonData?.optJSONObject("category_colors") ?: JSONObject()

        // Add "All" chip
        val allChip = layoutInflater.inflate(R.layout.item_chip, binding.categoryChipGroup, false) as Chip
        allChip.text = "All"
        allChip.isClickable = true
        allChip.isCheckable = true
        if (activeCategory.isEmpty()) allChip.isChecked = true
        allChip.setOnClickListener {
            activeCategory = ""
            updateStats()
        }
        binding.categoryChipGroup.addView(allChip)
        
        for (category in categories) {
            val chip = layoutInflater.inflate(R.layout.item_chip, binding.categoryChipGroup, false) as Chip
            chip.text = category
            chip.isClickable = true
            chip.isCheckable = true
            
            val colorStr = colors.optString(category, "")
            if (colorStr.isNotEmpty()) {
                try {
                    chip.setTextColor(Color.parseColor(colorStr))
                    chip.chipStrokeColor = ContextCompat.getColorStateList(this, R.color.chip_stroke_color)
                } catch (e: Exception) {}
            }
            
            if (category == activeCategory) {
                chip.isChecked = true
            }
            
            chip.setOnClickListener {
                activeCategory = category
                updateStats()
            }
            binding.categoryChipGroup.addView(chip)
        }
    }

    private fun updateStats() {
        val json = jsonData ?: return
        val colors = json.optJSONObject("category_colors") ?: JSONObject()
        
        lifecycleScope.launch(Dispatchers.Default) {
            val dailyCounts = json.optJSONObject("daily_counts") ?: JSONObject()
            val categoryTotals = json.optJSONObject("category_totals") ?: JSONObject()
            val grandTotal = json.optLong("grand_total", 0L)
            
            val today = dateFormat.format(Date())
            val todayObj = dailyCounts.optJSONObject(today)
            
            val todayCatCount = if (activeCategory.isEmpty()) {
                calculateTotalForDay(todayObj)
            } else {
                todayObj?.optLong(activeCategory, 0L) ?: 0L
            }
            
            val todayAllCategories = calculateTotalForDay(todayObj)
            val overallCatCount = if (activeCategory.isEmpty()) grandTotal else categoryTotals.optLong(activeCategory, 0L)
            
            val historyList = mutableListOf<Pair<String, Long>>()
            val dates = dailyCounts.keys()
            while (dates.hasNext()) {
                val date = dates.next()
                val dayObj = dailyCounts.optJSONObject(date) ?: continue
                val count = if (activeCategory.isEmpty()) calculateTotalForDay(dayObj) else dayObj.optLong(activeCategory, 0L)
                historyList.add(date to count)
            }
            historyList.sortByDescending { it.first }

            // Bar chart data based on current period
            val chartEntries = buildChartEntries(dailyCounts)

            // Streak calculation
            val streak = calculateStreak(dailyCounts)

            withContext(Dispatchers.Main) {
                binding.todayCategoryCountText.text = todayCatCount.toString()
                binding.todayTotalCountText.text = todayAllCategories.toString()
                binding.overallCategoryCountText.text = overallCatCount.toString()
                binding.grandTotalCountText.text = grandTotal.toString()

                val activeColorStr = if (activeCategory.isNotEmpty()) colors.optString(activeCategory, "") else ""
                val activeColor = if (activeColorStr.isNotEmpty()) {
                    try { Color.parseColor(activeColorStr) } catch (e: Exception) { null }
                } else null

                if (activeColor != null) {
                    binding.todayCategoryCountText.setTextColor(activeColor)
                    binding.barChartView.barColor = activeColor
                } else {
                    val tv = android.util.TypedValue()
                    theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
                    val primaryColor = tv.data
                    binding.todayCategoryCountText.setTextColor(primaryColor)
                    binding.barChartView.barColor = primaryColor
                }

                // Streak
                binding.streakText.text = "$streak-day streak"
                binding.streakEmoji.text = when {
                    streak >= 30 -> "🔥🔥🔥"
                    streak >= 7 -> "🔥🔥"
                    streak >= 1 -> "🔥"
                    else -> "❄️"
                }

                // Bar chart
                binding.barChartView.setData(chartEntries)

                historyAdapter?.submitList(historyList, activeColor)
            }
        }
    }

    private fun buildChartEntries(dailyCounts: JSONObject): List<BarChartView.BarEntry> {
        val cal = Calendar.getInstance()
        val today = dateFormat.format(Date())
        val entries = mutableListOf<BarChartView.BarEntry>()

        when (currentPeriod) {
            0 -> { // Week — last 7 days
                for (i in 6 downTo 0) {
                    cal.time = Date()
                    cal.add(Calendar.DAY_OF_YEAR, -i)
                    val dateStr = dateFormat.format(cal.time)
                    val dayObj = dailyCounts.optJSONObject(dateStr)
                    val count = if (activeCategory.isEmpty()) {
                        calculateTotalForDay(dayObj)
                    } else {
                        dayObj?.optLong(activeCategory, 0L) ?: 0L
                    }
                    val label = SimpleDateFormat("EEE", Locale.getDefault()).format(cal.time)
                    entries.add(BarChartView.BarEntry(label, count, dateStr))
                }
            }
            1 -> { // Month — last 30 days, grouped by day
                val dayFormat = SimpleDateFormat("d", Locale.getDefault())
                for (i in 29 downTo 0) {
                    cal.time = Date()
                    cal.add(Calendar.DAY_OF_YEAR, -i)
                    val dateStr = dateFormat.format(cal.time)
                    val dayObj = dailyCounts.optJSONObject(dateStr)
                    val count = if (activeCategory.isEmpty()) {
                        calculateTotalForDay(dayObj)
                    } else {
                        dayObj?.optLong(activeCategory, 0L) ?: 0L
                    }
                    val label = dayFormat.format(cal.time)
                    entries.add(BarChartView.BarEntry(label, count, dateStr))
                }
            }
            2 -> { // All — group by date, show all (may be many)
                val allDates = mutableListOf<String>()
                val keys = dailyCounts.keys()
                while (keys.hasNext()) allDates.add(keys.next())
                allDates.sort()
                val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
                for (dateStr in allDates) {
                    val dayObj = dailyCounts.optJSONObject(dateStr) ?: continue
                    val count = if (activeCategory.isEmpty()) {
                        calculateTotalForDay(dayObj)
                    } else {
                        dayObj.optLong(activeCategory, 0L)
                    }
                    try {
                        val date = dateFormat.parse(dateStr)
                        val label = monthFormat.format(date)
                        entries.add(BarChartView.BarEntry(label, count, dateStr))
                    } catch (e: Exception) {}
                }
            }
        }

        return entries
    }

    private fun calculateStreak(dailyCounts: JSONObject): Int {
        val cal = Calendar.getInstance()
        var streak = 0

        // Start from today, go backwards
        for (i in 0 until 365) {
            cal.time = Date()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val dateStr = dateFormat.format(cal.time)
            val dayObj = dailyCounts.optJSONObject(dateStr)
            val count = if (activeCategory.isEmpty()) {
                calculateTotalForDay(dayObj)
            } else {
                dayObj?.optLong(activeCategory, 0L) ?: 0L
            }

            if (count > 0) {
                streak++
            } else {
                // Allow today to be 0 without breaking streak (haven't counted yet today)
                if (i == 0) continue
                break
            }
        }

        return streak
    }

    private fun calculateTotalForDay(dayObj: JSONObject?): Long {
        if (dayObj == null) return 0L
        var total = 0L
        val keys = dayObj.keys()
        while (keys.hasNext()) {
            total += dayObj.optLong(keys.next(), 0L)
        }
        return total
    }

    private fun handleSyncChoice() {
        val token = sharedPrefs.getString("auth_token", null)
        if (token == null) {
            showLoginDialog()
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_sync_choice, null)
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.pushOption).setOnClickListener {
            dialog.dismiss()
            performPush(token)
        }
        dialogView.findViewById<View>(R.id.pullOption).setOnClickListener {
            dialog.dismiss()
            confirmPull(token)
        }
        dialogView.findViewById<View>(R.id.resolveConflictsOption).setOnClickListener {
            dialog.dismiss()
            performResolveConflicts(token)
        }
        dialogView.findViewById<View>(R.id.disconnectOption).setOnClickListener {
            dialog.dismiss()
            sharedPrefs.edit().remove("auth_token").apply()
            Toast.makeText(this, "Disconnected from Cloud", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun confirmPull(token: String) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Pull from Cloud")
            .setMessage("This will overwrite your local counter data with the data from the server. Are you sure?")
            .setPositiveButton("Yes, Pull Data") { _, _ -> performPull(token) }
            .setNegativeButton("No", null)
            .show()
    }

    private fun performResolveConflicts(token: String) {
        val data = jsonData ?: return
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val merged = fetchAndMergeConflicts(token, data)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (merged != null && merged != data) {
                        dataFile.writeText(merged.toString())
                        jsonData = merged
                        updateStats()
                        Toast.makeText(this@JapStatsActivity, "Conflicts resolved — data saved", Toast.LENGTH_SHORT).show()
                    } else if (merged != null && merged == data) {
                        Toast.makeText(this@JapStatsActivity, "No conflicts found — local is up to date", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@JapStatsActivity, "Failed to fetch conflicts", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@JapStatsActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showLoginDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_login, null)
        val emailEdit = dialogView.findViewById<EditText>(R.id.emailEdit)
        val passwordEdit = dialogView.findViewById<EditText>(R.id.passwordEdit)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Login to CodeShare")
            .setView(dialogView)
            .setPositiveButton("Login") { _, _ ->
                val email = emailEdit.text.toString()
                val password = passwordEdit.text.toString()
                if (email.isNotEmpty() && password.isNotEmpty()) {
                    login(email, password)
                } else {
                    Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun login(email: String, password: String) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://codeshare.auctionng.org/api/auth/login")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                val body = JSONObject().apply {
                    put("email", email)
                    put("password", password)
                }
                OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
                if (conn.responseCode == 200) {
                    val cookies = conn.headerFields["Set-Cookie"]
                    var token: String? = null
                    cookies?.forEach { cookie ->
                        if (cookie.contains("token=")) {
                            cookie.split(";").forEach { if (it.trim().startsWith("token=")) token = it.trim().substring(6) }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        if (token != null) {
                            sharedPrefs.edit().putString("auth_token", token).apply()
                            Toast.makeText(this@JapStatsActivity, "Login successful", Toast.LENGTH_SHORT).show()
                            handleSyncChoice()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this@JapStatsActivity, "Login failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@JapStatsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun performPush(token: String) {
        val data = jsonData ?: return
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://codeshare.auctionng.org/api/counter/sync")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.doOutput = true
                val body = JSONObject().apply { put("data", data) }
                OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
                if (conn.responseCode == 200) {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this@JapStatsActivity, "Cloud Push Successful", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val errorResponse = try {
                        conn.errorStream?.bufferedReader()?.use { it.readText() }
                    } catch (e: Exception) { null }

                    val errorMessage = try {
                        if (errorResponse != null) JSONObject(errorResponse).getString("message") else "Sync Blocked"
                    } catch(e: Exception) { "Sync Blocked" }

                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this@JapStatsActivity, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@JapStatsActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun fetchAndMergeConflicts(token: String, localData: JSONObject): JSONObject? {
        val url = URL("https://codeshare.auctionng.org/api/counter/conflicts")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.doOutput = true
        val body = JSONObject().apply { put("data", localData) }
        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

        if (conn.responseCode != 200) return null

        val response = conn.inputStream.bufferedReader().use { it.readText() }
        val responseJson = JSONObject(response)
        val conflicts = responseJson.optJSONArray("conflicts") ?: return localData

        val merged = JSONObject(localData.toString())
        val dailyCounts = merged.optJSONObject("daily_counts") ?: JSONObject().also { merged.put("daily_counts", it) }

        for (i in 0 until conflicts.length()) {
            val conflict = conflicts.getJSONObject(i)
            val date = conflict.getString("date")
            val category = conflict.getString("category")
            val serverCount = conflict.getInt("serverCount")

            val dayCounts = dailyCounts.optJSONObject(date) ?: JSONObject().also { dailyCounts.put(date, it) }
            dayCounts.put(category, serverCount)
        }

        // Recalculate grand_total
        var grandTotal = 0L
        for (dateKey in dailyCounts.keys()) {
            val dayObj = dailyCounts.optJSONObject(dateKey) ?: continue
            for (catKey in dayObj.keys()) {
                grandTotal += dayObj.optLong(catKey, 0L)
            }
        }
        merged.put("grand_total", grandTotal)

        // Update category_totals from merged daily counts
        val categoryTotals = merged.optJSONObject("category_totals") ?: JSONObject().also { merged.put("category_totals", it) }
        for (catKey in categoryTotals.keys()) {
            var catTotal = 0L
            for (dateKey in dailyCounts.keys()) {
                val dayObj = dailyCounts.optJSONObject(dateKey) ?: continue
                catTotal += dayObj.optLong(catKey, 0L)
            }
            categoryTotals.put(catKey, catTotal)
        }

        return merged
    }

    private fun performPull(token: String) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://codeshare.auctionng.org/api/counter/data")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer $token")
                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val remoteJson = JSONObject(response)
                    
                    // Unwrap data if it exists
                    val actualData = if (remoteJson.has("data")) {
                        remoteJson.getJSONObject("data")
                    } else {
                        remoteJson
                    }
                    
                    dataFile.writeText(actualData.toString())
                    
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this@JapStatsActivity, "Cloud Pull Successful", Toast.LENGTH_SHORT).show()
                        
                        // Immediately update in-memory state and UI
                        jsonData = actualData
                        
                        val catArray = actualData.optJSONArray("categories") ?: JSONArray()
                        categories.clear()
                        for (i in 0 until catArray.length()) {
                            categories.add(catArray.getString(i))
                        }
                        val categoryTotals = actualData.optJSONObject("category_totals") ?: JSONObject()
                        categories.sortByDescending { categoryTotals.optLong(it, 0L) }
                        
                        // Apply preferences from cloud if present
                        val prefsJson = actualData.optJSONObject("preferences")
                        if (prefsJson != null) {
                            val japPrefs = getSharedPreferences("jap_prefs", Context.MODE_PRIVATE)
                            val editor = japPrefs.edit()
                            if (prefsJson.has("vibrate")) editor.putBoolean("vibrate", prefsJson.getBoolean("vibrate"))
                            if (prefsJson.has("mala_mode")) editor.putBoolean("mala_mode", prefsJson.getBoolean("mala_mode"))
                            if (prefsJson.has("chip_count_badge")) editor.putBoolean("chip_count_badge", prefsJson.getBoolean("chip_count_badge"))
                            if (prefsJson.has("daily_nudge_enabled")) editor.putBoolean("daily_nudge_enabled", prefsJson.getBoolean("daily_nudge_enabled"))
                            if (prefsJson.has("reminderIntervalMins")) editor.putInt("reminderIntervalMins", prefsJson.getInt("reminderIntervalMins"))
                            editor.apply()

                            if (prefsJson.optBoolean("daily_nudge_enabled", true)) {
                                DailyNudgeReceiver.scheduleDailyNudges(this@JapStatsActivity)
                            } else {
                                DailyNudgeReceiver.cancelDailyNudges(this@JapStatsActivity)
                            }
                        }
                        
                        setupChips()
                        updateStats()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@JapStatsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private inner class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {
        private var items = listOf<Pair<String, Long>>()
        private var activeColor: Int? = null
        private var maxValue: Long = 1L

        fun submitList(newList: List<Pair<String, Long>>, color: Int?) {
            items = newList
            activeColor = color
            maxValue = newList.maxOfOrNull { it.second } ?: 1L
            if (maxValue == 0L) maxValue = 1L
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_jap_history, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.dateText.text = item.first
            holder.countText.text = item.second.toString()
            if (activeColor != null) {
                holder.countText.setTextColor(activeColor!!)
                holder.progressBar.setIndicatorColor(activeColor!!)
            } else {
                val tv = android.util.TypedValue()
                theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
                val primaryColor = tv.data
                holder.countText.setTextColor(primaryColor)
                holder.progressBar.setIndicatorColor(primaryColor)
            }
            val progress = ((item.second.toFloat() / maxValue.toFloat()) * 100).toInt()
            holder.progressBar.progress = progress
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val dateText: TextView = view.findViewById(R.id.historyDateText)
            val countText: TextView = view.findViewById(R.id.historyCountText)
            val progressBar: com.google.android.material.progressindicator.LinearProgressIndicator = view.findViewById(R.id.historyProgressBar)
        }
    }
}
