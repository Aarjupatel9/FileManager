package com.mhk.filemanager.ui.japcounter

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.mhk.filemanager.R
import com.mhk.filemanager.databinding.ActivityJapStatsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJapStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener { finish() }
        
        activeCategory = intent.getStringExtra("EXTRA_CATEGORY") ?: ""

        loadData()
    }

    private fun loadData() {
        lifecycleScope.launch(Dispatchers.IO) {
            if (dataFile.exists()) {
                val json = JSONObject(dataFile.readText())
                jsonData = json
                
                val catArray = json.optJSONArray("categories") ?: JSONArray()
                for (i in 0 until catArray.length()) {
                    categories.add(catArray.getString(i))
                }
                
                // Sort categories descending by usage
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
        
        // Today this category
        val todayCatCount = todayObj?.optLong(activeCategory, 0L) ?: 0L
        
        // Today all categories
        var todayAllCategories = 0L
        if (todayObj != null) {
            val keys = todayObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                todayAllCategories += todayObj.getLong(key)
            }
        }
        
        // Overall this category
        val overallCatCount = categoryTotals.optLong(activeCategory, 0L)
        
        binding.todayCategoryCountText.text = todayCatCount.toString()
        binding.todayTotalCountText.text = todayAllCategories.toString()
        binding.overallCategoryCountText.text = overallCatCount.toString()
        binding.grandTotalCountText.text = grandTotal.toString()
    }
}
