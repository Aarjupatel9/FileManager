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
    private var activeCategory = ""
    private var historyAdapter: HistoryAdapter? = null

    private val sharedPrefs by lazy { getSharedPreferences("jap_prefs", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJapStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener { finish() }
        binding.syncButton.setOnClickListener { handleSync() }
        
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
                
                if (activeCategory.isEmpty() && categories.isNotEmpty()) {
                    activeCategory = categories[0]
                }
                
                withContext(Dispatchers.Main) {
                    setupChips()
                    updateStats()
                }
            }
        }
    }
    
    private fun setupChips() {
        binding.categoryChipGroup.removeAllViews()
        
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
        
        val dailyCounts = json.optJSONObject("daily_counts") ?: JSONObject()
        val categoryTotals = json.optJSONObject("category_totals") ?: JSONObject()
        val grandTotal = json.optLong("grand_total", 0L)
        
        val today = dateFormat.format(Date())
        val todayObj = dailyCounts.optJSONObject(today)
        
        val todayCatCount = todayObj?.optLong(activeCategory, 0L) ?: 0L
        
        var todayAllCategories = 0L
        if (todayObj != null) {
            val keys = todayObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                todayAllCategories += todayObj.getLong(key)
            }
        }
        
        val overallCatCount = categoryTotals.optLong(activeCategory, 0L)
        
        binding.todayCategoryCountText.text = todayCatCount.toString()
        binding.todayTotalCountText.text = todayAllCategories.toString()
        binding.overallCategoryCountText.text = overallCatCount.toString()
        binding.grandTotalCountText.text = grandTotal.toString()

        // Update history list
        val historyList = mutableListOf<Pair<String, Long>>()
        val dates = dailyCounts.keys()
        while (dates.hasNext()) {
            val date = dates.next()
            val dayObj = dailyCounts.getJSONObject(date)
            if (dayObj.has(activeCategory)) {
                historyList.add(date to dayObj.getLong(activeCategory))
            }
        }
        historyList.sortByDescending { it.first }
        historyAdapter?.submitList(historyList)
    }

    private fun handleSync() {
        val token = sharedPrefs.getString("auth_token", null)
        if (token == null) {
            showLoginDialog()
        } else {
            performSync(token)
        }
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
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = JSONObject(response)
                    
                    // Extracts token from Set-Cookie header
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
                            performSync(token!!)
                        } else {
                            Toast.makeText(this@JapStatsActivity, "Login failed: Could not extract token", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    val errorMsg = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                    val jsonError = try { JSONObject(errorMsg).optString("message", "Login failed") } catch(e:Exception) { "Login failed" }
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this@JapStatsActivity, "Login failed ($responseCode): $jsonError", Toast.LENGTH_LONG).show()
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

    private fun performSync(token: String) {
        val data = jsonData
        if (data == null) {
            Toast.makeText(this, "No data to sync", Toast.LENGTH_SHORT).show()
            return
        }
        
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

                val body = JSONObject().apply {
                    put("data", data)
                }

                OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

                val responseCode = conn.responseCode
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (responseCode == 200) {
                        Toast.makeText(this@JapStatsActivity, "Sync successful", Toast.LENGTH_SHORT).show()
                    } else {
                        val errorMsg = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                        Toast.makeText(this@JapStatsActivity, "Sync failed ($responseCode): $errorMsg", Toast.LENGTH_LONG).show()
                        if (responseCode == 401) {
                            sharedPrefs.edit().remove("auth_token").apply()
                            Toast.makeText(this@JapStatsActivity, "Session expired. Please login again.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@JapStatsActivity, "Sync connection error: ${e.message}", Toast.LENGTH_LONG).show()
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
