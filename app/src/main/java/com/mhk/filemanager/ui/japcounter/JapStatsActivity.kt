package com.mhk.filemanager.ui.japcounter

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
import java.util.Date
import java.util.Locale

class JapStatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJapStatsBinding
    private val dataFile by lazy { File(filesDir, "jap_data.json") }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private var jsonData: JSONObject? = null
    private var categories = mutableListOf<String>()
    private var activeCategory = "" // Empty means "All"
    private var historyAdapter: HistoryAdapter? = null

    private val sharedPrefs by lazy { getSharedPreferences("jap_prefs", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJapStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener { finish() }
        binding.syncButton.setOnClickListener { handleSyncChoice() }
        
        binding.historyRecyclerView.layoutManager = LinearLayoutManager(this)
        historyAdapter = HistoryAdapter()
        binding.historyRecyclerView.adapter = historyAdapter

        activeCategory = intent.getStringExtra("EXTRA_CATEGORY") ?: ""

        loadData()
    }

    private fun loadData() {
        lifecycleScope.launch(Dispatchers.IO) {
            if (dataFile.exists()) {
                val json = JSONObject(dataFile.readText())
                jsonData = json
                
                val catArray = json.optJSONArray("categories") ?: JSONArray()
                categories.clear()
                for (i in 0 until catArray.length()) {
                    categories.add(catArray.getString(i))
                }
                
                val categoryTotals = json.optJSONObject("category_totals") ?: JSONObject()
                categories.sortByDescending { categoryTotals.optLong(it, 0L) }
                
                // Don't auto-set activeCategory if we want to support "All"
                
                withContext(Dispatchers.Main) {
                    setupChips()
                    updateStats()
                }
            }
        }
    }
    
    private fun setupChips() {
        binding.categoryChipGroup.removeAllViews()
        
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
        
        lifecycleScope.launch(Dispatchers.Default) {
            val dailyCounts = json.optJSONObject("daily_counts") ?: JSONObject()
            val categoryTotals = json.optJSONObject("category_totals") ?: JSONObject()
            val grandTotal = json.optLong("grand_total", 0L)
            
            val today = dateFormat.format(Date())
            val todayObj = dailyCounts.optJSONObject(today)
            
            // Stats Calculations
            val todayCatCount = if (activeCategory.isEmpty()) {
                calculateTotalForDay(todayObj)
            } else {
                todayObj?.optLong(activeCategory, 0L) ?: 0L
            }
            
            var todayAllCategories = calculateTotalForDay(todayObj)
            
            val overallCatCount = if (activeCategory.isEmpty()) grandTotal else categoryTotals.optLong(activeCategory, 0L)
            
            // History List Optimization
            val historyList = mutableListOf<Pair<String, Long>>()
            val dates = dailyCounts.keys()
            while (dates.hasNext()) {
                val date = dates.next()
                val dayObj = dailyCounts.optJSONObject(date) ?: continue
                
                val count = if (activeCategory.isEmpty()) {
                    calculateTotalForDay(dayObj)
                } else {
                    dayObj.optLong(activeCategory, 0L)
                }
                
                historyList.add(date to count)
            }
            historyList.sortByDescending { it.first }

            withContext(Dispatchers.Main) {
                binding.todayCategoryCountText.text = todayCatCount.toString()
                binding.todayTotalCountText.text = todayAllCategories.toString()
                binding.overallCategoryCountText.text = overallCatCount.toString()
                binding.grandTotalCountText.text = grandTotal.toString()
                historyAdapter?.submitList(historyList)
            }
        }
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

        AlertDialog.Builder(this)
            .setTitle("Cloud Sync")
            .setMessage("Choose an action:")
            .setPositiveButton("Push to Cloud") { _, _ -> performPush(token) }
            .setNegativeButton("Pull from Cloud") { _, _ -> confirmPull(token) }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun confirmPull(token: String) {
        AlertDialog.Builder(this)
            .setTitle("Pull from Cloud")
            .setMessage("This will overwrite your local counter data with the data from the server. Are you sure?")
            .setPositiveButton("Yes, Overwrite") { _, _ -> performPull(token) }
            .setNegativeButton("No", null)
            .show()
    }

    private fun showLoginDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_login, null)
        val emailEdit = dialogView.findViewById<EditText>(R.id.emailEdit)
        val passwordEdit = dialogView.findViewById<EditText>(R.id.passwordEdit)

        AlertDialog.Builder(this)
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

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val cookies = conn.headerFields["Set-Cookie"]
                    var token: String? = null
                    cookies?.forEach { cookie ->
                        if (cookie.contains("token=")) {
                            val parts = cookie.split(";")
                            parts.forEach { part ->
                                if (part.trim().startsWith("token=")) {
                                    token = part.trim().substring("token=".length)
                                }
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        if (token != null) {
                            sharedPrefs.edit().putString("auth_token", token).apply()
                            Toast.makeText(this@JapStatsActivity, "Login successful", Toast.LENGTH_SHORT).show()
                            handleSyncChoice()
                        } else {
                            Toast.makeText(this@JapStatsActivity, "Login failed: No token", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this@JapStatsActivity, "Login failed ($responseCode)", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@JapStatsActivity, "Connection error: ${e.message}", Toast.LENGTH_LONG).show()
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
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.doOutput = true

                val body = JSONObject().apply { put("data", data) }
                OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

                val responseCode = conn.responseCode
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (responseCode == 200) {
                        Toast.makeText(this@JapStatsActivity, "Push successful", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@JapStatsActivity, "Push failed: $responseCode", Toast.LENGTH_LONG).show()
                        if (responseCode == 401) sharedPrefs.edit().remove("auth_token").apply()
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

    private fun performPull(token: String) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://codeshare.auctionng.org/api/counter/data")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer $token")

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = JSONObject(response)
                    val pulledData = jsonResponse.optJSONObject("data")

                    if (pulledData != null) {
                        // Overwrite local file
                        dataFile.writeText(pulledData.toString())
                        jsonData = pulledData
                        
                        withContext(Dispatchers.Main) {
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(this@JapStatsActivity, "Pull successful", Toast.LENGTH_SHORT).show()
                            loadData() // Reload UI with new data
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(this@JapStatsActivity, "No data found on server", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this@JapStatsActivity, "Pull failed: $responseCode", Toast.LENGTH_LONG).show()
                        if (responseCode == 401) sharedPrefs.edit().remove("auth_token").apply()
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

    private inner class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {
        private var items = listOf<Pair<String, Long>>()

        fun submitList(newList: List<Pair<String, Long>>) {
            items = newList
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
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val dateText: TextView = view.findViewById(R.id.historyDateText)
            val countText: TextView = view.findViewById(R.id.historyCountText)
        }
    }
}
