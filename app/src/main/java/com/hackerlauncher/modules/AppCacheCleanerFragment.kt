package com.hackerlauncher.modules

import com.hackerlauncher.R

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppCacheCleanerFragment : Fragment() {

    private lateinit var recyclerViewApps: RecyclerView
    private lateinit var textViewTotalCache: TextView
    private lateinit var buttonCleanAll: MaterialButton
    private lateinit var buttonCleanSelected: MaterialButton
    private lateinit var progressBarScan: ProgressBar
    private lateinit var checkBoxSelectAll: CheckBox
    private lateinit var editTextSearch: EditText

    // ========== UPGRADE: New view references ==========
    private lateinit var buttonOneTapClean: MaterialButton
    private lateinit var buttonSmartRules: MaterialButton
    private lateinit var buttonScheduledClean: MaterialButton
    private lateinit var buttonCacheGrowth: MaterialButton
    private lateinit var spinnerSort: Spinner
    private lateinit var textViewCacheStats: TextView

    private val appCacheList = mutableListOf<AppCacheInfo>()
    private val filteredList = mutableListOf<AppCacheInfo>()
    private lateinit var appCacheAdapter: AppCacheAdapter

    private var scanJob: Job? = null
    private var searchQuery = ""

    // ========== UPGRADE: New state ==========
    private var currentSortMode = "cache_size" // cache_size, name, last_cleaned
    private var smartCacheThreshold = 100L * 1024 * 1024 // 100MB default
    private var isSmartCleanEnabled = false
    private var scheduledCleanEnabled = false
    private var scheduledCleanHour = 3 // 3 AM default
    private val cacheGrowthTracker = mutableMapOf<String, MutableList<CacheGrowthEntry>>()
    private val lastCleanedMap = mutableMapOf<String, Long>() // pkg -> timestamp
    private var totalFreedHistorical = 0L
    private var cleanCount = 0

    data class AppCacheInfo(
        val packageName: String,
        val appName: String,
        val cacheSize: Long,
        val dataSize: Long,
        val appIcon: Drawable?,
        var isSelected: Boolean = false
    )

    // ========== UPGRADE: Cache growth tracking ==========
    data class CacheGrowthEntry(
        val timestamp: Long,
        val cacheSize: Long
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_app_cache_cleaner, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerViewApps = view.findViewById(R.id.recyclerViewApps)
        textViewTotalCache = view.findViewById(R.id.textViewTotalCache)
        buttonCleanAll = view.findViewById(R.id.buttonCleanAll)
        buttonCleanSelected = view.findViewById(R.id.buttonCleanSelected)
        progressBarScan = view.findViewById(R.id.progressBarScan)
        checkBoxSelectAll = view.findViewById(R.id.checkBoxSelectAll)
        editTextSearch = view.findViewById(R.id.editTextSearch)

        appCacheAdapter = AppCacheAdapter(filteredList) { info, isChecked ->
            // Find the original item and update selection
            val originalItem = appCacheList.find { it.packageName == info.packageName }
            originalItem?.isSelected = isChecked
            updateTotalCache()
        }
        recyclerViewApps.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewApps.adapter = appCacheAdapter

        buttonCleanAll.setOnClickListener { cleanAllCaches() }
        buttonCleanSelected.setOnClickListener { cleanSelectedCaches() }

        checkBoxSelectAll.setOnCheckedChangeListener { _, isChecked ->
            appCacheList.forEach { it.isSelected = isChecked }
            applyFilter()
            updateTotalCache()
        }

        editTextSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.lowercase(Locale.getDefault()) ?: ""
                applyFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // ========== UPGRADE: Add upgrade views ==========
        addUpgradeViews(view)
        loadUpgradeSettings()

        // Auto-scan on open
        startScan()
    }

    // ========== UPGRADE: Add upgrade views dynamically ==========
    private fun addUpgradeViews(view: View) {
        try {
            val root = view.findViewById<ViewGroup>(R.id.recyclerViewApps).parent as? ViewGroup
            if (root != null) {
                // One-tap clean all
                buttonOneTapClean = MaterialButton(requireContext()).apply {
                    text = "⚡ ONE-TAP CLEAN ALL"
                    setTextColor(0xFFFF4444.toInt())
                    textSize = 14f
                    setOnClickListener { performOneTapClean() }
                }
                root.addView(buttonOneTapClean, 0)

                // Smart rules button
                buttonSmartRules = MaterialButton(requireContext()).apply {
                    text = "📋 Smart Rules"
                    setTextColor(0xFF00FFFF.toInt())
                    textSize = 12f
                    setOnClickListener { showSmartRulesDialog() }
                }

                // Scheduled clean button
                buttonScheduledClean = MaterialButton(requireContext()).apply {
                    text = "📅 Scheduled Clean"
                    setTextColor(0xFF2196F3.toInt())
                    textSize = 12f
                    setOnClickListener { showScheduledCleanDialog() }
                }

                // Cache growth button
                buttonCacheGrowth = MaterialButton(requireContext()).apply {
                    text = "📈 Cache Growth"
                    setTextColor(0xFF9C27B0.toInt())
                    textSize = 12f
                    setOnClickListener { showCacheGrowthDialog() }
                }

                val buttonRow = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                buttonRow.addView(buttonSmartRules)
                buttonRow.addView(buttonScheduledClean)
                buttonRow.addView(buttonCacheGrowth)
                root.addView(buttonRow, 1)

                // Sort spinner
                spinnerSort = Spinner(requireContext()).apply {
                    adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item,
                        arrayOf("Sort: Cache Size", "Sort: Name", "Sort: Last Cleaned"))
                    onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: android.widget.AdapterView<*>?, v: View?, position: Int, id: Long) {
                            currentSortMode = when (position) {
                                0 -> "cache_size"
                                1 -> "name"
                                2 -> "last_cleaned"
                                else -> "cache_size"
                            }
                            sortAndFilter()
                        }
                        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                    }
                }
                root.addView(spinnerSort, 2)

                // Cache stats
                textViewCacheStats = TextView(requireContext()).apply {
                    text = "Cache Stats: --"
                    setTextColor(0xFF888888.toInt())
                    textSize = 12f
                }
                root.addView(textViewCacheStats, 3)
            }
        } catch (_: Exception) {}
    }

    private fun startScan() {
        progressBarScan.visibility = View.VISIBLE
        buttonCleanAll.isEnabled = false
        buttonCleanSelected.isEnabled = false
        appCacheList.clear()
        filteredList.clear()
        appCacheAdapter.notifyDataSetChanged()

        scanJob = lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val pm = requireContext().packageManager
                    val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

                    for (appInfo in installedApps) {
                        try {
                            val appName = pm.getApplicationLabel(appInfo).toString()
                            val appIcon = try { pm.getApplicationIcon(appInfo) } catch (e: Exception) { null }
                            val cacheSize = getAppCacheSize(appInfo)
                            val dataSize = getAppDataSize(appInfo)

                            val info = AppCacheInfo(
                                packageName = appInfo.packageName,
                                appName = appName,
                                cacheSize = cacheSize,
                                dataSize = dataSize,
                                appIcon = appIcon,
                                isSelected = cacheSize > 0
                            )

                            appCacheList.add(info)

                            // UPGRADE: Track cache growth
                            recordCacheGrowth(appInfo.packageName, cacheSize)

                            // UPGRADE: Apply smart rules - auto-select apps over threshold
                            if (isSmartCleanEnabled && cacheSize > smartCacheThreshold) {
                                info.isSelected = true
                            }

                            withContext(Dispatchers.Main) {
                                applyFilter()
                                updateTotalCache()
                            }
                        } catch (e: Exception) {
                            // Skip apps we can't read
                        }
                    }

                    // Sort by cache size descending
                    sortAndFilter()

                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showToast("Scan error: ${e.message}")
                    }
                }
            }

            withContext(Dispatchers.Main) {
                progressBarScan.visibility = View.GONE
                buttonCleanAll.isEnabled = appCacheList.any { it.cacheSize > 0 }
                applyFilter()
                updateTotalCache()
                updateCacheStats()
                showToast("Scan complete: ${appCacheList.size} apps found")
            }
        }
    }

    private fun getAppCacheSize(appInfo: ApplicationInfo): Long {
        var cacheSize = 0L

        // Try to read app's cache directory
        try {
            val cacheDir = File(appInfo.dataDir, "cache")
            if (cacheDir.exists() && cacheDir.isDirectory) {
                cacheSize += calculateDirectorySize(cacheDir)
            }
        } catch (e: Exception) {
            // No access
        }

        // Try external cache
        try {
            val externalCache = File(Environment.getExternalStorageDirectory(), "Android/data/${appInfo.packageName}/cache")
            if (externalCache.exists() && externalCache.isDirectory) {
                cacheSize += calculateDirectorySize(externalCache)
            }
        } catch (e: Exception) {
            // No access
        }

        // Try code_cache
        try {
            val codeCache = File(appInfo.dataDir, "code_cache")
            if (codeCache.exists() && codeCache.isDirectory) {
                cacheSize += calculateDirectorySize(codeCache)
            }
        } catch (e: Exception) {
            // No access
        }

        return cacheSize
    }

    private fun getAppDataSize(appInfo: ApplicationInfo): Long {
        var dataSize = 0L
        try {
            val dataDir = File(appInfo.dataDir)
            if (dataDir.exists() && dataDir.isDirectory) {
                dataSize = calculateDirectorySize(dataDir)
            }
        } catch (e: Exception) {
            // No access
        }

        try {
            val externalData = File(Environment.getExternalStorageDirectory(), "Android/data/${appInfo.packageName}")
            if (externalData.exists() && externalData.isDirectory) {
                dataSize += calculateDirectorySize(externalData)
            }
        } catch (e: Exception) {
            // No access
        }

        return dataSize
    }

    private fun calculateDirectorySize(directory: File): Long {
        if (!directory.exists()) return 0L
        if (directory.isFile) return directory.length()

        var size = 0L
        val files = directory.listFiles() ?: return 0L
        for (file in files) {
            size += if (file.isDirectory) {
                calculateDirectorySize(file)
            } else {
                file.length()
            }
        }
        return size
    }

    // ========== UPGRADE: Sort and filter ==========
    private fun sortAndFilter() {
        when (currentSortMode) {
            "cache_size" -> appCacheList.sortByDescending { it.cacheSize }
            "name" -> appCacheList.sortBy { it.appName.lowercase(Locale.getDefault()) }
            "last_cleaned" -> appCacheList.sortedByDescending { lastCleanedMap[it.packageName] ?: 0L }
        }
        applyFilter()
    }

    private fun applyFilter() {
        filteredList.clear()
        val source = if (searchQuery.isEmpty()) {
            appCacheList
        } else {
            appCacheList.filter {
                it.appName.lowercase(Locale.getDefault()).contains(searchQuery) ||
                it.packageName.lowercase(Locale.getDefault()).contains(searchQuery)
            }
        }
        filteredList.addAll(source)
        appCacheAdapter.notifyDataSetChanged()
    }

    private fun updateTotalCache() {
        val totalCache = appCacheList.sumOf { it.cacheSize }
        val selectedCache = appCacheList.filter { it.isSelected }.sumOf { it.cacheSize }
        val selectedCount = appCacheList.count { it.isSelected }

        textViewTotalCache.text = "Total Cache: ${formatFileSize(totalCache)} | Selected: ${formatFileSize(selectedCache)} ($selectedCount apps)"
        buttonCleanSelected.text = "Clean Selected ($selectedCount)"
        buttonCleanSelected.isEnabled = selectedCount > 0
        buttonCleanAll.isEnabled = appCacheList.any { it.cacheSize > 0 }
    }

    // ========== UPGRADE: Update cache stats ==========
    private fun updateCacheStats() {
        try {
            val totalCache = appCacheList.sumOf { it.cacheSize }
            val appsOverThreshold = appCacheList.count { it.cacheSize > smartCacheThreshold }
            val avgCache = if (appCacheList.isNotEmpty()) totalCache / appCacheList.size else 0L

            textViewCacheStats.text = buildString {
                append("Apps: ${appCacheList.size} | ")
                append("Over ${smartCacheThreshold / (1024 * 1024)}MB: $appsOverThreshold | ")
                append("Avg: ${formatFileSize(avgCache)} | ")
                append("Freed: ${formatFileSize(totalFreedHistorical)} ($cleanCount sessions)")
            }
        } catch (_: Exception) {}
    }

    private fun cleanAllCaches() {
        val appsWithCache = appCacheList.filter { it.cacheSize > 0 }
        if (appsWithCache.isEmpty()) {
            showToast("No cache to clean")
            return
        }

        val totalSize = appsWithCache.sumOf { it.cacheSize }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Clean All App Caches")
            .setMessage("Clear cache for ${appsWithCache.size} apps (${formatFileSize(totalSize)})?\n\nThis may require clearing data for some apps.")
            .setPositiveButton("Clean All") { _, _ ->
                performCleanCache(appsWithCache.map { it.packageName })
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun cleanSelectedCaches() {
        val selectedApps = appCacheList.filter { it.isSelected }
        if (selectedApps.isEmpty()) {
            showToast("No apps selected")
            return
        }

        val totalSize = selectedApps.sumOf { it.cacheSize }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Clean Selected Caches")
            .setMessage("Clear cache for ${selectedApps.size} apps (${formatFileSize(totalSize)})?")
            .setPositiveButton("Clean") { _, _ ->
                performCleanCache(selectedApps.map { it.packageName })
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performCleanCache(packageNames: List<String>) {
        lifecycleScope.launch {
            var cleanedCount = 0
            var freedBytes = 0L
            var failedCount = 0

            withContext(Dispatchers.IO) {
                for (packageName in packageNames) {
                    try {
                        val freed = cleanAppCache(packageName)
                        if (freed > 0) {
                            cleanedCount++
                            freedBytes += freed
                            // UPGRADE: Record last cleaned time
                            lastCleanedMap[packageName] = System.currentTimeMillis()
                        }
                    } catch (e: Exception) {
                        failedCount++
                    }
                }
            }

            // UPGRADE: Update historical stats
            totalFreedHistorical += freedBytes
            cleanCount++
            saveUpgradeSettings()

            // UPGRADE: Send notification
            sendCacheCleanNotification(freedBytes, cleanedCount)

            showToast("Cleaned $cleanedCount apps, freed ${formatFileSize(freedBytes)}" +
                    if (failedCount > 0) " ($failedCount failed)" else "")

            // Rescan to update sizes
            startScan()
        }
    }

    private fun cleanAppCache(packageName: String): Long {
        var freed = 0L

        // Method 1: Try clearing app's internal cache directory directly
        try {
            val pm = requireContext().packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val cacheDir = File(appInfo.dataDir, "cache")
            if (cacheDir.exists()) {
                val beforeSize = calculateDirectorySize(cacheDir)
                deleteDirectory(cacheDir)
                freed += beforeSize
            }

            val codeCacheDir = File(appInfo.dataDir, "code_cache")
            if (codeCacheDir.exists()) {
                val beforeSize = calculateDirectorySize(codeCacheDir)
                deleteDirectory(codeCacheDir)
                freed += beforeSize
            }
        } catch (e: Exception) {
            // No direct access - try alternative method
        }

        // Method 2: Try clearing external cache
        try {
            val externalCache = File(Environment.getExternalStorageDirectory(), "Android/data/$packageName/cache")
            if (externalCache.exists()) {
                val beforeSize = calculateDirectorySize(externalCache)
                deleteDirectory(externalCache)
                freed += beforeSize
            }
        } catch (e: Exception) {
            // No access
        }

        // Method 3: Try using ActivityManager clearApplicationUserData via reflection
        try {
            val activityManager = requireContext().getSystemService(Context.ACTIVITY_SERVICE)
                    as android.app.ActivityManager
            val method = activityManager.javaClass.getMethod(
                "clearApplicationUserData",
                String::class.java,
                Class.forName("android.content.pm.IPackageDataObserver")
            )
            method.invoke(activityManager, packageName, null)
        } catch (e: Exception) {
            // Reflection method not available or permission denied
        }

        // Method 4: Try deleteApplicationCacheFiles via reflection
        try {
            val pm = requireContext().packageManager
            val method = pm.javaClass.getMethod(
                "deleteApplicationCacheFiles",
                String::class.java,
                Class.forName("android.content.pm.IPackageDataObserver")
            )
            method.invoke(pm, packageName, null)
        } catch (e: Exception) {
            // Reflection method not available or permission denied
        }

        return freed
    }

    // ========== UPGRADE: One-Tap Clean All ==========
    private fun performOneTapClean() {
        val appsWithCache = appCacheList.filter { it.cacheSize > 0 }
        if (appsWithCache.isEmpty()) {
            showToast("No cache to clean")
            return
        }

        val totalSize = appsWithCache.sumOf { it.cacheSize }
        AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("⚡ One-Tap Clean All")
            .setMessage("Clean ALL app caches in one tap?\n${appsWithCache.size} apps (${formatFileSize(totalSize)})")
            .setPositiveButton("⚡ CLEAN ALL NOW") { _, _ ->
                performCleanCache(appsWithCache.map { it.packageName })
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ========== UPGRADE: Smart Cache Rules ==========
    private fun showSmartRulesDialog() {
        val options = arrayOf(
            "Enable Smart Auto-Clean (auto-clean apps > threshold)",
            "Set Cache Threshold (currently ${smartCacheThreshold / (1024 * 1024)}MB)",
            "View Apps Over Threshold",
            "Disable Smart Auto-Clean"
        )
        val checkedItems = booleanArrayOf(
            isSmartCleanEnabled,
            false,
            false,
            false
        )

        AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("📋 Smart Cache Rules")
            .setMessage("Automatically clean cache for apps exceeding the size threshold.")
            .setMultiChoiceItems(options, checkedItems) { _, which, isChecked ->
                when (which) {
                    0 -> isSmartCleanEnabled = isChecked
                    1 -> {
                        if (isChecked) showThresholdDialog()
                    }
                    2 -> {
                        if (isChecked) showAppsOverThreshold()
                    }
                    3 -> {
                        if (isChecked) {
                            isSmartCleanEnabled = false
                            showToast("Smart auto-clean disabled")
                        }
                    }
                }
            }
            .setPositiveButton("Save") { _, _ ->
                saveUpgradeSettings()
                startScan() // Rescan with new rules
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showThresholdDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Threshold in MB (e.g. 100)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("${smartCacheThreshold / (1024 * 1024)}")
            setTextColor(0xFF00FF00.toInt())
            setHintTextColor(0xFF888888.toInt())
        }

        AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("Set Cache Threshold")
            .setMessage("Apps with cache exceeding this threshold will be auto-selected for cleaning.")
            .setView(input)
            .setPositiveButton("Set") { _, _ ->
                val mb = input.text.toString().toLongOrNull()
                if (mb != null && mb > 0) {
                    smartCacheThreshold = mb * 1024 * 1024
                    saveUpgradeSettings()
                    showToast("Threshold set to ${mb}MB")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAppsOverThreshold() {
        val appsOver = appCacheList.filter { it.cacheSize > smartCacheThreshold }
        if (appsOver.isEmpty()) {
            showToast("No apps over ${smartCacheThreshold / (1024 * 1024)}MB threshold")
            return
        }

        val message = buildString {
            append("Apps with cache > ${smartCacheThreshold / (1024 * 1024)}MB:\n\n")
            for ((index, app) in appsOver.withIndex()) {
                append("${index + 1}. ${app.appName}: ${formatFileSize(app.cacheSize)}\n")
            }
            append("\nTotal: ${formatFileSize(appsOver.sumOf { it.cacheSize })}")
        }

        AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("Apps Over Threshold")
            .setMessage(message)
            .setPositiveButton("Clean All", ) { _, _ ->
                performCleanCache(appsOver.map { it.packageName })
            }
            .setNegativeButton("Close", null)
            .show()
    }

    // ========== UPGRADE: Scheduled Cache Cleaning ==========
    private fun showScheduledCleanDialog() {
        val options = arrayOf("Disable Scheduled Clean", "Daily at 3:00 AM", "Daily at 6:00 AM", "Custom Time")
        val currentIndex = when {
            !scheduledCleanEnabled -> 0
            scheduledCleanHour == 3 -> 1
            scheduledCleanHour == 6 -> 2
            else -> 3
        }

        AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("📅 Scheduled Cache Cleaning")
            .setSingleChoiceItems(options, currentIndex) { _, which ->
                when (which) {
                    0 -> {
                        scheduledCleanEnabled = false
                        showToast("Scheduled clean disabled")
                    }
                    1 -> {
                        scheduledCleanEnabled = true
                        scheduledCleanHour = 3
                        showToast("Daily clean at 3:00 AM enabled")
                    }
                    2 -> {
                        scheduledCleanEnabled = true
                        scheduledCleanHour = 6
                        showToast("Daily clean at 6:00 AM enabled")
                    }
                    3 -> showCustomScheduleDialog()
                }
                saveUpgradeSettings()
            }
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showCustomScheduleDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Hour (0-23)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("3")
            setTextColor(0xFF00FF00.toInt())
            setHintTextColor(0xFF888888.toInt())
        }

        AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("Custom Schedule Time")
            .setMessage("Enter hour (0-23) for daily cache clean:")
            .setView(input)
            .setPositiveButton("Set") { _, _ ->
                val hour = input.text.toString().toIntOrNull()
                if (hour != null && hour in 0..23) {
                    scheduledCleanEnabled = true
                    scheduledCleanHour = hour
                    saveUpgradeSettings()
                    showToast("Daily clean at ${String.format("%02d:00", hour)} enabled")
                } else {
                    showToast("Enter a valid hour (0-23)")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ========== UPGRADE: Cache Growth Tracking ==========
    private fun recordCacheGrowth(packageName: String, cacheSize: Long) {
        if (!cacheGrowthTracker.containsKey(packageName)) {
            cacheGrowthTracker[packageName] = mutableListOf()
        }
        cacheGrowthTracker[packageName]!!.add(CacheGrowthEntry(System.currentTimeMillis(), cacheSize))
        // Keep last 30 readings
        if (cacheGrowthTracker[packageName]!!.size > 30) {
            cacheGrowthTracker[packageName]!!.removeAt(0)
        }
    }

    private fun showCacheGrowthDialog() {
        // Find apps with fastest growing cache
        val growthRates = mutableListOf<Pair<String, Long>>()
        for ((pkg, entries) in cacheGrowthTracker) {
            if (entries.size < 2) continue
            val firstSize = entries.first().cacheSize
            val lastSize = entries.last().cacheSize
            val growth = lastSize - firstSize
            if (growth > 0) {
                growthRates.add(pkg to growth)
            }
        }
        growthRates.sortByDescending { it.second }

        val message = buildString {
            append("═══ CACHE GROWTH TRACKING ═══\n\n")

            if (growthRates.isEmpty()) {
                append("No significant cache growth detected.\n")
                append("Data is collected over time - check back later.\n")
            } else {
                append("Apps with fastest growing cache:\n\n")
                for ((index, pair) in growthRates.take(15).withIndex()) {
                    val appName = try {
                        val pm = requireContext().packageManager
                        pm.getApplicationInfo(pair.first, 0).let { pm.getApplicationLabel(it) }
                    } catch (_: Exception) { pair.first }
                    append("${index + 1}. $appName: +${formatFileSize(pair.second)}\n")
                }
            }

            append("\n── Last Cleaned ──\n")
            if (lastCleanedMap.isEmpty()) {
                append("No cleaning history yet.\n")
            } else {
                val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                val sorted = lastCleanedMap.entries.sortedByDescending { it.value }
                for ((pkg, time) in sorted.take(10)) {
                    val appName = try {
                        val pm = requireContext().packageManager
                        pm.getApplicationInfo(pkg, 0).let { pm.getApplicationLabel(it) }
                    } catch (_: Exception) { pkg }
                    append("$appName: ${dateFormat.format(Date(time))}\n")
                }
            }
        }

        AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("📈 Cache Growth Tracking")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    // ========== UPGRADE: Notification ==========
    private fun sendCacheCleanNotification(freedBytes: Long, cleanedCount: Int) {
        try {
            val nm = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "cache_cleaner"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(channelId, "Cache Cleaner", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }

            NotificationCompat.Builder(requireContext(), channelId)
                .setContentTitle("🧹 Cache Clean Complete")
                .setContentText("Freed ${formatFileSize(freedBytes)} from $cleanedCount apps")
                .setSmallIcon(android.R.drawable.ic_menu_delete)
                .setAutoCancel(true)
                .build()
                .also { nm.notify(7001, it) }
        } catch (_: Exception) {}
    }

    // ========== UPGRADE: Settings ==========
    private fun loadUpgradeSettings() {
        val prefs = requireContext().getSharedPreferences("app_cache_cleaner_prefs", Context.MODE_PRIVATE)
        smartCacheThreshold = prefs.getLong("smart_threshold", 100L * 1024 * 1024)
        isSmartCleanEnabled = prefs.getBoolean("smart_enabled", false)
        scheduledCleanEnabled = prefs.getBoolean("scheduled_enabled", false)
        scheduledCleanHour = prefs.getInt("scheduled_hour", 3)
        totalFreedHistorical = prefs.getLong("total_freed", 0L)
        cleanCount = prefs.getInt("clean_count", 0)

        // Load last cleaned map
        val cleanedStr = prefs.getString("last_cleaned", "") ?: ""
        if (cleanedStr.isNotEmpty()) {
            cleanedStr.split(";").forEach { entry ->
                val parts = entry.split(",")
                if (parts.size == 2) {
                    try { lastCleanedMap[parts[0]] = parts[1].toLong() } catch (_: Exception) {}
                }
            }
        }
    }

    private fun saveUpgradeSettings() {
        val prefs = requireContext().getSharedPreferences("app_cache_cleaner_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("smart_threshold", smartCacheThreshold)
            .putBoolean("smart_enabled", isSmartCleanEnabled)
            .putBoolean("scheduled_enabled", scheduledCleanEnabled)
            .putInt("scheduled_hour", scheduledCleanHour)
            .putLong("total_freed", totalFreedHistorical)
            .putInt("clean_count", cleanCount)
            .apply()

        // Save last cleaned map (most recent 50)
        val cleanedStr = lastCleanedMap.entries.sortedByDescending { it.value }.take(50)
            .joinToString(";") { "${it.key},${it.value}" }
        prefs.edit().putString("last_cleaned", cleanedStr).apply()
    }

    private fun deleteDirectory(directory: File): Boolean {
        if (!directory.exists()) return true
        val files = directory.listFiles() ?: return directory.delete()
        for (file in files) {
            if (file.isDirectory) {
                deleteDirectory(file)
            } else {
                file.delete()
            }
        }
        return directory.delete()
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes <= 0 -> "0 B"
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scanJob?.cancel()
    }

    // --- Inner Adapter ---

    inner class AppCacheAdapter(
        private val items: List<AppCacheInfo>,
        private val onCheckChanged: (AppCacheInfo, Boolean) -> Unit
    ) : RecyclerView.Adapter<AppCacheAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val checkBox: CheckBox = view.findViewById(R.id.cbAppSelect)
            val imageViewIcon: ImageView = view.findViewById(R.id.ivAppIcon)
            val textViewAppName: TextView = view.findViewById(R.id.tvAppName)
            val textViewPackageName: TextView = view.findViewById(R.id.tvAppPackageName)
            val textViewCacheSize: TextView = view.findViewById(R.id.tvAppCacheSize)
            val textViewDataSize: TextView = view.findViewById(R.id.tvAppDataSize)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app_cache, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.checkBox.setOnCheckedChangeListener(null)
            holder.checkBox.isChecked = item.isSelected

            holder.textViewAppName.text = item.appName
            holder.textViewPackageName.text = item.packageName
            holder.textViewCacheSize.text = "Cache: ${formatFileSize(item.cacheSize)}"
            holder.textViewDataSize.text = "Data: ${formatFileSize(item.dataSize)}"

            // Set cache size color based on size
            holder.textViewCacheSize.setTextColor(
                when {
                    item.cacheSize > 100L * 1024 * 1024 -> 0xFFFF4444.toInt()
                    item.cacheSize > 50L * 1024 * 1024 -> 0xFFFFFF00.toInt()
                    item.cacheSize > 10L * 1024 * 1024 -> 0xFFFF9800.toInt()
                    else -> 0xFF00FF00.toInt()
                }
            )

            // UPGRADE: Show last cleaned time
            val lastCleaned = lastCleanedMap[item.packageName]
            if (lastCleaned != null && lastCleaned > 0) {
                val timeAgo = (System.currentTimeMillis() - lastCleaned) / 60000
                val timeStr = when {
                    timeAgo < 60 -> "${timeAgo}m ago"
                    timeAgo < 1440 -> "${timeAgo / 60}h ago"
                    else -> "${timeAgo / 1440}d ago"
                }
                holder.textViewDataSize.text = "Data: ${formatFileSize(item.dataSize)} | Cleaned: $timeStr"
            }

            // Set app icon
            if (item.appIcon != null) {
                holder.imageViewIcon.setImageDrawable(item.appIcon)
            } else {
                holder.imageViewIcon.setImageResource(android.R.drawable.ic_menu_manage)
            }

            holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
                onCheckChanged(item, isChecked)
            }

            holder.itemView.setOnClickListener {
                holder.checkBox.isChecked = !holder.checkBox.isChecked
            }
        }

        override fun getItemCount() = items.size
    }
}
