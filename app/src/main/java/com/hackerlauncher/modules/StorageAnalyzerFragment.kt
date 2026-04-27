package com.hackerlauncher.modules

import com.hackerlauncher.R

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.io.File
import java.io.FileWriter
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StorageAnalyzerFragment : Fragment() {

    private lateinit var storagePieChart: StoragePieView
    private lateinit var storageBarChart: StorageBarView
    private lateinit var recyclerViewCategories: RecyclerView
    private lateinit var recyclerViewLargeFiles: RecyclerView
    private lateinit var recyclerViewFolderRanking: RecyclerView
    private lateinit var textViewTotalStorage: TextView
    private lateinit var textViewUsedStorage: TextView
    private lateinit var textViewFreeStorage: TextView
    private lateinit var storageProgressBar: ProgressBar
    private lateinit var buttonQuickClean: MaterialButton
    private lateinit var tabStorage: TabLayout

    // ========== UPGRADE: New view references ==========
    private lateinit var buttonDuplicateDetect: MaterialButton
    private lateinit var buttonStorageTrend: MaterialButton
    private lateinit var buttonExportReport: MaterialButton
    private lateinit var textViewStorageTrend: TextView

    private val categoryList = mutableListOf<StorageCategory>()
    private val largeFiles = mutableListOf<LargeFileInfo>()
    private val folderRanking = mutableListOf<FolderInfo>()
    private val duplicateGroups = mutableListOf<DuplicateGroup>()
    private lateinit var categoryAdapter: StorageCategoryAdapter
    private lateinit var largeFileAdapter: LargeFileAdapter
    private lateinit var folderAdapter: FolderAdapter

    private var analyzeJob: Job? = null
    private var isInternal = true

    // ========== UPGRADE: New state ==========
    private val storageTrendHistory = mutableListOf<StorageTrendEntry>()
    private var lastAnalysisTime = 0L
    private var lastUsedBytes = 0L

    data class StorageCategory(
        val name: String,
        var size: Long,
        val color: Int
    )

    data class LargeFileInfo(
        val fileName: String,
        val filePath: String,
        val fileSize: Long,
        val category: String
    )

    // ========== UPGRADE: New data classes ==========
    data class FolderInfo(
        val folderPath: String,
        val folderName: String,
        val size: Long,
        val fileCount: Int
    )

    data class DuplicateGroup(
        val fileSize: Long,
        val files: MutableList<String>,
        val md5Hash: String
    )

    data class StorageTrendEntry(
        val timestamp: Long,
        val usedBytes: Long,
        val totalBytes: Long
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_storage_analyzer, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val storagePieContainer: android.widget.FrameLayout = view.findViewById(R.id.storagePieContainer)
        storagePieChart = StoragePieView(requireContext())
        storagePieContainer.addView(storagePieChart, android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT))
        recyclerViewCategories = view.findViewById(R.id.recyclerViewCategories)
        recyclerViewLargeFiles = view.findViewById(R.id.recyclerViewLargeFiles)
        textViewTotalStorage = view.findViewById(R.id.textViewTotalStorage)
        textViewUsedStorage = view.findViewById(R.id.textViewUsedStorage)
        textViewFreeStorage = view.findViewById(R.id.textViewFreeStorage)
        storageProgressBar = view.findViewById(R.id.storageProgressBar)
        buttonQuickClean = view.findViewById(R.id.buttonQuickClean)
        tabStorage = view.findViewById(R.id.tabStorage)

        categoryAdapter = StorageCategoryAdapter(categoryList)
        recyclerViewCategories.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewCategories.adapter = categoryAdapter

        largeFileAdapter = LargeFileAdapter(largeFiles)
        recyclerViewLargeFiles.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewLargeFiles.adapter = largeFileAdapter

        tabStorage.addTab(tabStorage.newTab().setText("Internal"))
        tabStorage.addTab(tabStorage.newTab().setText("External"))

        tabStorage.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                isInternal = tab.position == 0
                startAnalysis()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {
                startAnalysis()
            }
        })

        buttonQuickClean.setOnClickListener { showQuickCleanSuggestions() }

        // ========== UPGRADE: Add upgrade views ==========
        addUpgradeViews(view)

        // Load trend data
        loadStorageTrend()

        startAnalysis()
    }

    // ========== UPGRADE: Add upgrade views dynamically ==========
    private fun addUpgradeViews(view: View) {
        try {
            // Add bar chart next to pie chart
            val chartContainer = view.findViewById<LinearLayout>(R.id.storagePieContainer).parent as? ViewGroup
            if (chartContainer != null) {
                storageBarChart = StorageBarView(requireContext())
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (200 * resources.displayMetrics.density).toInt()
                )
                chartContainer.addView(storageBarChart, params)
            }

            // Add folder ranking recycler
            val rootContainer = (view as? ViewGroup) ?: (view.parent as? ViewGroup) ?: view.rootView as ViewGroup

            // Add upgrade buttons
            val buttonContainer = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            buttonDuplicateDetect = MaterialButton(requireContext()).apply {
                text = "🔍 Duplicates"
                setTextColor(0xFF9C27B0.toInt())
                textSize = 11f
                setOnClickListener { startDuplicateDetection() }
            }

            buttonStorageTrend = MaterialButton(requireContext()).apply {
                text = "📈 Trend"
                setTextColor(0xFF2196F3.toInt())
                textSize = 11f
                setOnClickListener { showStorageTrend() }
            }

            buttonExportReport = MaterialButton(requireContext()).apply {
                text = "📤 Export"
                setTextColor(0xFF4CAF50.toInt())
                textSize = 11f
                setOnClickListener { exportStorageReport() }
            }

            buttonContainer.addView(buttonDuplicateDetect)
            buttonContainer.addView(buttonStorageTrend)
            buttonContainer.addView(buttonExportReport)

            // Try to add to the existing layout
            try {
                val parentLayout = view.findViewById<LinearLayout>(R.id.storagePieContainer).parent?.parent as? ViewGroup
                if (parentLayout != null) {
                    val index = parentLayout.indexOfChild(view.findViewById<View>(R.id.storagePieContainer).parent as View)
                    if (index >= 0) {
                        parentLayout.addView(buttonContainer, index + 1)
                    } else {
                        parentLayout.addView(buttonContainer)
                    }
                }
            } catch (_: Exception) {}

            // Folder ranking recycler
            recyclerViewFolderRanking = RecyclerView(requireContext()).apply {
                layoutManager = LinearLayoutManager(requireContext())
                adapter = FolderAdapter(folderRanking)
            }

            try {
                val parentLayout = view.findViewById<ViewGroup>(R.id.recyclerViewLargeFiles).parent as? ViewGroup
                if (parentLayout != null) {
                    val folderLabel = TextView(requireContext()).apply {
                        text = "═══ Top 20 Largest Folders ═══"
                        setTextColor(0xFFFFFF00.toInt())
                        textSize = 14f
                        setPadding(0, 16, 0, 8)
                    }
                    parentLayout.addView(folderLabel)
                    parentLayout.addView(recyclerViewFolderRanking)
                }
            } catch (_: Exception) {}

            // Storage trend text
            textViewStorageTrend = TextView(requireContext()).apply {
                text = "Storage Trend: No data yet"
                setTextColor(0xFF888888.toInt())
                textSize = 12f
            }
            try {
                val parentLayout = view.findViewById<ViewGroup>(R.id.recyclerViewLargeFiles).parent as? ViewGroup
                parentLayout?.addView(textViewStorageTrend)
            } catch (_: Exception) {}

        } catch (_: Exception) {
            // Views not available in layout
        }
    }

    private fun startAnalysis() {
        analyzeJob?.cancel()
        categoryList.clear()
        largeFiles.clear()
        folderRanking.clear()
        categoryAdapter.notifyDataSetChanged()
        largeFileAdapter.notifyDataSetChanged()

        analyzeJob = lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val storageRoot = if (isInternal) {
                        Environment.getDataDirectory()
                    } else {
                        Environment.getExternalStorageDirectory()
                    }

                    val stat = StatFs(storageRoot.path)
                    val totalBytes = stat.totalBytes
                    val usedBytes = totalBytes - stat.availableBytes

                    // ========== UPGRADE: Record storage trend ==========
                    recordStorageTrend(usedBytes, totalBytes)

                    withContext(Dispatchers.Main) {
                        textViewTotalStorage.text = "Total: ${formatFileSize(totalBytes)}"
                        textViewUsedStorage.text = "Used: ${formatFileSize(usedBytes)}"
                        textViewFreeStorage.text = "Free: ${formatFileSize(stat.availableBytes)}"
                        val percent = ((usedBytes.toDouble() / totalBytes.toDouble()) * 100).toInt()
                        storageProgressBar.progress = percent
                    }

                    // Analyze by categories
                    val imagesSize = analyzeDirectorySize(File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DCIM)) +
                            analyzeDirectorySize(File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_PICTURES))

                    val videosSize = analyzeDirectorySize(File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_MOVIES))

                    val audioSize = analyzeDirectorySize(File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_MUSIC)) +
                            analyzeDirectorySize(File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_RINGTONES)) +
                            analyzeDirectorySize(File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_ALARMS)) +
                            analyzeDirectorySize(File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_NOTIFICATIONS))

                    val documentsSize = analyzeDirectorySize(File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DOCUMENTS)) +
                            analyzeDirectorySize(File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DOWNLOADS))

                    val cacheSize = analyzeCacheSize()
                    val appsSize = analyzeAppsSize()

                    // UPGRADE: Scan APK files separately
                    val apkSize = analyzeApkSize()

                    val accountedSize = imagesSize + videosSize + audioSize + documentsSize + cacheSize + appsSize + apkSize
                    val otherSize = maxOf(0L, usedBytes - accountedSize)

                    val categories = listOf(
                        StorageCategory("Apps", appsSize, 0xFF2196F3.toInt()),
                        StorageCategory("Images", imagesSize, 0xFF4CAF50.toInt()),
                        StorageCategory("Videos", videosSize, 0xFFFF5722.toInt()),
                        StorageCategory("Audio", audioSize, 0xFF9C27B0.toInt()),
                        StorageCategory("Documents", documentsSize, 0xFFFF9800.toInt()),
                        StorageCategory("Cache", cacheSize, 0xFFFFEB3B.toInt()),
                        StorageCategory("APKs", apkSize, 0xFF00BCD4.toInt()),
                        StorageCategory("Other", otherSize, 0xFF888888.toInt())
                    )

                    withContext(Dispatchers.Main) {
                        categoryList.clear()
                        categoryList.addAll(categories)
                        categoryAdapter.notifyDataSetChanged()
                        storagePieChart.setData(categories)
                        try { storageBarChart.setData(categories) } catch (_: Exception) {}
                    }

                    // Find large files
                    val foundLargeFiles = mutableListOf<LargeFileInfo>()
                    scanForLargeFiles(Environment.getExternalStorageDirectory(), foundLargeFiles, 50L * 1024 * 1024)
                    foundLargeFiles.sortByDescending { it.fileSize }

                    withContext(Dispatchers.Main) {
                        largeFiles.clear()
                        largeFiles.addAll(foundLargeFiles.take(20))
                        largeFileAdapter.notifyDataSetChanged()
                    }

                    // ========== UPGRADE: Folder size ranking (top 20) ==========
                    val foundFolders = mutableListOf<FolderInfo>()
                    scanForLargeFolders(Environment.getExternalStorageDirectory(), foundFolders)
                    foundFolders.sortByDescending { it.size }

                    withContext(Dispatchers.Main) {
                        folderRanking.clear()
                        folderRanking.addAll(foundFolders.take(20))
                        try { recyclerViewFolderRanking.adapter?.notifyDataSetChanged() } catch (_: Exception) {}
                    }

                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showToast("Analysis error: ${e.message}")
                    }
                }
            }
        }
    }

    private fun analyzeDirectorySize(directory: File): Long {
        if (!directory.exists() || !directory.isDirectory) return 0L
        var size = 0L
        val files = directory.listFiles() ?: return 0L
        for (file in files) {
            size += if (file.isDirectory) {
                analyzeDirectorySize(file)
            } else {
                file.length()
            }
        }
        return size
    }

    private fun analyzeCacheSize(): Long {
        var totalCache = 0L

        // App's own cache
        requireContext().cacheDir?.let { totalCache += analyzeDirectorySize(it) }
        requireContext().externalCacheDir?.let { totalCache += analyzeDirectorySize(it) }
        requireContext().externalCacheDirs?.forEach { dir ->
            totalCache += analyzeDirectorySize(dir)
        }

        // Thumbnail cache
        val thumbDir = File(Environment.getExternalStorageDirectory(), ".thumbnails")
        totalCache += analyzeDirectorySize(thumbDir)

        return totalCache
    }

    private fun analyzeAppsSize(): Long {
        var totalSize = 0L
        val pm = requireContext().packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        for (appInfo in installedApps) {
            try {
                val dataDir = appInfo.dataDir
                if (dataDir != null) {
                    val dir = File(dataDir)
                    totalSize += analyzeDirectorySize(dir)
                }
            } catch (e: Exception) {
                // Skip inaccessible app directories
            }
        }
        return totalSize
    }

    // ========== UPGRADE: APK size analysis ==========
    private fun analyzeApkSize(): Long {
        var totalSize = 0L
        val searchRoots = listOf(
            Environment.getExternalStorageDirectory(),
            File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DOWNLOADS)
        )

        for (root in searchRoots) {
            if (!root.exists()) continue
            val apkFiles = findApkFiles(root)
            for (file in apkFiles) {
                totalSize += file.length()
            }
        }
        return totalSize
    }

    private fun findApkFiles(directory: File): List<File> {
        if (!directory.exists() || !directory.isDirectory) return emptyList()
        val apkFiles = mutableListOf<File>()
        val files = directory.listFiles() ?: return emptyList()
        for (file in files) {
            if (file.isFile && file.extension.lowercase(Locale.getDefault()) == "apk") {
                apkFiles.add(file)
            } else if (file.isDirectory) {
                apkFiles.addAll(findApkFiles(file))
            }
        }
        return apkFiles
    }

    private suspend fun scanForLargeFiles(
        directory: File,
        results: MutableList<LargeFileInfo>,
        minSize: Long
    ) {
        if (!directory.exists() || !directory.isDirectory) return
        val files = directory.listFiles() ?: return

        for (file in files) {
            coroutineContext.ensureActive()
            if (file.isDirectory) {
                scanForLargeFiles(file, results, minSize)
            } else if (file.length() >= minSize) {
                val category = categorizeFile(file)
                results.add(
                    LargeFileInfo(
                        fileName = file.name,
                        filePath = file.absolutePath,
                        fileSize = file.length(),
                        category = category
                    )
                )
            }
        }
    }

    // ========== UPGRADE: Folder size ranking ==========
    private suspend fun scanForLargeFolders(
        directory: File,
        results: MutableList<FolderInfo>
    ) {
        if (!directory.exists() || !directory.isDirectory) return
        val files = directory.listFiles() ?: return

        var fileCount = 0
        for (file in files) {
            if (file.isFile) fileCount++
        }

        val size = analyzeDirectorySize(directory)
        if (size > 10L * 1024 * 1024) { // Only folders > 10MB
            results.add(
                FolderInfo(
                    folderPath = directory.absolutePath,
                    folderName = directory.name,
                    size = size,
                    fileCount = fileCount
                )
            )
        }

        // Recurse into subdirectories (limited depth)
        for (file in files) {
            coroutineContext.ensureActive()
            if (file.isDirectory) {
                scanForLargeFolders(file, results)
            }
        }
    }

    // ========== UPGRADE: Duplicate File Detection ==========
    private fun startDuplicateDetection() {
        showToast("Scanning for duplicate files...")
        lifecycleScope.launch {
            duplicateGroups.clear()
            withContext(Dispatchers.IO) {
                val fileHashes = mutableMapOf<String, MutableList<String>>()
                val storageRoot = Environment.getExternalStorageDirectory()

                scanForDuplicates(storageRoot, fileHashes, 1024 * 1024) // Min 1MB

                // Find groups with duplicates
                for ((hash, paths) in fileHashes) {
                    if (paths.size > 1) {
                        val size = File(paths[0]).length()
                        duplicateGroups.add(DuplicateGroup(size, paths.toMutableList(), hash))
                    }
                }
                duplicateGroups.sortByDescending { it.fileSize * (it.files.size - 1) }
            }

            withContext(Dispatchers.Main) {
                showDuplicateResults()
            }
        }
    }

    private suspend fun scanForDuplicates(directory: File, hashMap: MutableMap<String, MutableList<String>>, minSize: Long) {
        if (!directory.exists() || !directory.isDirectory) return
        val files = directory.listFiles() ?: return

        for (file in files) {
            try {
                coroutineContext.ensureActive()
                if (file.isDirectory) {
                    scanForDuplicates(file, hashMap, minSize)
                } else if (file.length() >= minSize) {
                    // Quick hash based on size + name first, then MD5 for matches
                    val quickKey = "${file.length()}_${file.name}"
                    if (!hashMap.containsKey(quickKey)) {
                        hashMap[quickKey] = mutableListOf()
                    }
                    hashMap[quickKey]!!.add(file.absolutePath)

                    // For files with same size+name, compute MD5
                    if (hashMap[quickKey]!!.size == 2) {
                        // Compute MD5 for all files in this group
                        val md5Map = mutableMapOf<String, MutableList<String>>()
                        for (path in hashMap[quickKey]!!) {
                            try {
                                val md5 = computeMD5(File(path))
                                if (!md5Map.containsKey(md5)) md5Map[md5] = mutableListOf()
                                md5Map[md5]!!.add(path)
                            } catch (_: Exception) {}
                        }
                        // Replace quick key group with MD5 groups
                        hashMap.remove(quickKey)
                        for ((md5, paths) in md5Map) {
                            if (paths.size > 1) {
                                hashMap[md5] = paths
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun computeMD5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        val inputStream = java.io.FileInputStream(file)
        val buffer = ByteArray(8192)
        var read: Int
        while (inputStream.read(buffer).also { read = it } > 0) {
            digest.update(buffer, 0, read)
        }
        inputStream.close()
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun showDuplicateResults() {
        if (duplicateGroups.isEmpty()) {
            showToast("No duplicate files found!")
            return
        }

        val totalWasted = duplicateGroups.sumOf { it.fileSize * (it.files.size - 1) }
        val message = buildString {
            append("Found ${duplicateGroups.size} groups of duplicate files\n")
            append("Total wasted space: ${formatFileSize(totalWasted)}\n\n")
            for ((index, group) in duplicateGroups.take(10).withIndex()) {
                append("─── Group ${index + 1} (${formatFileSize(group.fileSize)} each) ───\n")
                for (path in group.files.take(3)) {
                    append("  ${File(path).name}\n")
                }
                if (group.files.size > 3) append("  ... +${group.files.size - 3} more\n")
                append("\n")
            }
        }

        AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("🔍 Duplicate Files Found")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    // ========== UPGRADE: Storage Trend Tracking ==========
    private fun recordStorageTrend(usedBytes: Long, totalBytes: Long) {
        val now = System.currentTimeMillis()
        // Record at most once per hour
        if (now - lastAnalysisTime < 3_600_000 && storageTrendHistory.isNotEmpty()) return

        storageTrendHistory.add(StorageTrendEntry(now, usedBytes, totalBytes))
        if (storageTrendHistory.size > 168) { // 7 days at 1/hr
            storageTrendHistory.removeAt(0)
        }
        lastAnalysisTime = now
        lastUsedBytes = usedBytes
        saveStorageTrend()

        // Update trend display
        try {
            if (storageTrendHistory.size >= 2) {
                val oldest = storageTrendHistory.first()
                val newest = storageTrendHistory.last()
                val timeDiffHours = (newest.timestamp - oldest.timestamp) / 3_600_000
                val bytesDiff = newest.usedBytes - oldest.usedBytes

                if (timeDiffHours > 0) {
                    val ratePerDay = (bytesDiff.toDouble() / timeDiffHours) * 24
                    val remainingBytes = newest.totalBytes - newest.usedBytes
                    val daysUntilFull = if (ratePerDay > 0) (remainingBytes / ratePerDay).toInt() else Int.MAX_VALUE

                    val trendText = "📈 Storage Trend: ${formatFileSize(ratePerDay.toLong())}/day | Full in ~$daysUntilFull days"
                    try {
                        textViewStorageTrend.text = trendText
                        textViewStorageTrend.setTextColor(if (daysUntilFull < 30) 0xFFFF4444.toInt() else 0xFF00FF00.toInt())
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    private fun showStorageTrend() {
        val message = buildString {
            append("═══ STORAGE TREND ═══\n\n")

            if (storageTrendHistory.size < 2) {
                append("Not enough data yet. Check back after a few hours.\n")
            } else {
                val oldest = storageTrendHistory.first()
                val newest = storageTrendHistory.last()
                val timeDiffHours = (newest.timestamp - oldest.timestamp) / 3_600_000
                val bytesDiff = newest.usedBytes - oldest.usedBytes
                val ratePerDay = if (timeDiffHours > 0) (bytesDiff.toDouble() / timeDiffHours) * 24 else 0.0
                val remainingBytes = newest.totalBytes - newest.usedBytes
                val daysUntilFull = if (ratePerDay > 0) (remainingBytes / ratePerDay).toInt() else Int.MAX_VALUE

                append("Current usage: ${formatFileSize(newest.usedBytes)} / ${formatFileSize(newest.totalBytes)}\n")
                append("Rate: ${formatFileSize(ratePerDay.toLong())} per day\n")
                append("Est. days until full: ${if (daysUntilFull > 365) "1+ year" else "$daysUntilFull days"}\n\n")

                append("── History ──\n")
                val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                for (entry in storageTrendHistory.takeLast(24).reversed()) {
                    val pct = ((entry.usedBytes.toDouble() / entry.totalBytes.toDouble()) * 100).toInt()
                    append("[${dateFormat.format(Date(entry.timestamp))}] ${pct}% (${formatFileSize(entry.usedBytes)})\n")
                }
            }
        }

        AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("📈 Storage Trend")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun saveStorageTrend() {
        try {
            val prefs = requireContext().getSharedPreferences("storage_analyzer_prefs", Context.MODE_PRIVATE)
            val trendStr = storageTrendHistory.takeLast(168).joinToString(";") { entry ->
                "${entry.timestamp},${entry.usedBytes},${entry.totalBytes}"
            }
            prefs.edit()
                .putString("storage_trend", trendStr)
                .putLong("last_analysis_time", lastAnalysisTime)
                .apply()
        } catch (_: Exception) {}
    }

    private fun loadStorageTrend() {
        try {
            val prefs = requireContext().getSharedPreferences("storage_analyzer_prefs", Context.MODE_PRIVATE)
            lastAnalysisTime = prefs.getLong("last_analysis_time", 0L)
            val trendStr = prefs.getString("storage_trend", "") ?: ""
            storageTrendHistory.clear()

            if (trendStr.isNotEmpty()) {
                trendStr.split(";").forEach { entryStr ->
                    val parts = entryStr.split(",")
                    if (parts.size == 3) {
                        try {
                            storageTrendHistory.add(
                                StorageTrendEntry(
                                    timestamp = parts[0].toLong(),
                                    usedBytes = parts[1].toLong(),
                                    totalBytes = parts[2].toLong()
                                )
                            )
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}
    }

    // ========== UPGRADE: Export Storage Report ==========
    private fun exportStorageReport() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
                    val reportFile = File(
                        Environment.getExternalStorageDirectory(),
                        "Download/storage_report_${dateFormat.format(Date())}.txt"
                    )

                    val writer = FileWriter(reportFile)
                    writer.write(buildString {
                        append("═══ HACKERLAUNCHER STORAGE REPORT ═══\n")
                        append("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n\n")

                        append("── Storage Overview ──\n")
                        for (cat in categoryList) {
                            val pct = if (categoryList.sumOf { it.size } > 0)
                                ((cat.size.toDouble() / categoryList.sumOf { it.size }.toDouble()) * 100).toInt() else 0
                            append("${cat.name}: ${formatFileSize(cat.size)} ($pct%)\n")
                        }

                        append("\n── Top 20 Largest Folders ──\n")
                        for (folder in folderRanking) {
                            append("${folder.folderPath}: ${formatFileSize(folder.size)} (${folder.fileCount} files)\n")
                        }

                        append("\n── Top 20 Large Files ──\n")
                        for (file in largeFiles) {
                            append("${file.filePath}: ${formatFileSize(file.fileSize)} [${file.category}]\n")
                        }

                        append("\n── Storage Trend ──\n")
                        if (storageTrendHistory.size >= 2) {
                            val oldest = storageTrendHistory.first()
                            val newest = storageTrendHistory.last()
                            val bytesDiff = newest.usedBytes - oldest.usedBytes
                            val timeDiffHours = (newest.timestamp - oldest.timestamp) / 3_600_000
                            val ratePerDay = if (timeDiffHours > 0) (bytesDiff.toDouble() / timeDiffHours) * 24 else 0.0
                            append("Filling rate: ${formatFileSize(ratePerDay.toLong())}/day\n")
                        } else {
                            append("Insufficient trend data\n")
                        }

                        append("\n── Duplicate Files ──\n")
                        if (duplicateGroups.isEmpty()) {
                            append("No scan performed yet\n")
                        } else {
                            val totalWasted = duplicateGroups.sumOf { it.fileSize * (it.files.size - 1) }
                            append("${duplicateGroups.size} duplicate groups, ${formatFileSize(totalWasted)} wasted\n")
                        }
                    })
                    writer.close()

                    withContext(Dispatchers.Main) {
                        showToast("Report exported to ${reportFile.absolutePath}")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showToast("Export failed: ${e.message}")
                    }
                }
            }
        }
    }

    private fun categorizeFile(file: File): String {
        val ext = file.extension.lowercase(Locale.getDefault())
        return when (ext) {
            in imageExtensions -> "Images"
            in videoExtensions -> "Videos"
            in audioExtensions -> "Audio"
            in documentExtensions -> "Documents"
            "apk" -> "APKs"
            in archiveExtensions -> "Archives"
            else -> "Other"
        }
    }

    private fun showQuickCleanSuggestions() {
        val suggestions = mutableListOf<String>()
        var potentialSavings = 0L

        val cacheCat = categoryList.find { it.name == "Cache" }
        if (cacheCat != null && cacheCat.size > 50L * 1024 * 1024) {
            suggestions.add("Cache: ${formatFileSize(cacheCat.size)} (safe to clean)")
            potentialSavings += cacheCat.size
        }

        val thumbDir = File(Environment.getExternalStorageDirectory(), ".thumbnails")
        val thumbSize = analyzeDirectorySize(thumbDir)
        if (thumbSize > 10L * 1024 * 1024) {
            suggestions.add("Thumbnail cache: ${formatFileSize(thumbSize)}")
            potentialSavings += thumbSize
        }

        if (largeFiles.size > 5) {
            suggestions.add("${largeFiles.size} large files found - review and delete unneeded")
        }

        val apkCat = categoryList.find { it.name == "APKs" }
        if (apkCat != null && apkCat.size > 10L * 1024 * 1024) {
            suggestions.add("APK files: ${formatFileSize(apkCat.size)} (review and delete installed APKs)")
            potentialSavings += apkCat.size
        }

        if (duplicateGroups.isNotEmpty()) {
            val wastedSpace = duplicateGroups.sumOf { it.fileSize * (it.files.size - 1) }
            suggestions.add("Duplicate files: ${formatFileSize(wastedSpace)} wasted")
            potentialSavings += wastedSpace
        }

        if (suggestions.isEmpty()) {
            suggestions.add("No quick clean suggestions. Storage looks good!")
        }

        val message = suggestions.joinToString("\n") { "• $it" } +
                if (potentialSavings > 0) "\n\nPotential savings: ${formatFileSize(potentialSavings)}" else ""

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Quick Clean Suggestions")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
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
        analyzeJob?.cancel()
    }

    // --- StoragePieView ---

    class StoragePieView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
    ) : View(context, attrs, defStyleAttr) {

        private val categories = mutableListOf<StorageCategory>()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 28f
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
        private val rectF = RectF()

        fun setData(data: List<StorageCategory>) {
            categories.clear()
            categories.addAll(data)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            if (categories.isEmpty()) return

            val total = categories.sumOf { it.size }
            if (total <= 0) return

            val size = minOf(width, height).toFloat()
            val padding = 40f
            val left = (width - size) / 2f + padding
            val top = (height - size) / 2f + padding
            val right = left + size - padding * 2
            val bottom = top + size - padding * 2

            rectF.set(left, top, right, bottom)

            var startAngle = -90f

            for (category in categories) {
                if (category.size <= 0) continue
                val sweepAngle = (category.size.toDouble() / total.toDouble()) * 360f

                paint.color = category.color
                paint.style = Paint.Style.FILL
                canvas.drawArc(rectF, startAngle, sweepAngle.toFloat(), true, paint)

                // Draw label if slice is large enough
                if (sweepAngle > 15f) {
                    val midAngle = startAngle + sweepAngle.toFloat() / 2f
                    val labelRadius = (size - padding * 2) / 3f
                    val labelX = width / 2f + (labelRadius * Math.cos(Math.toRadians(midAngle.toDouble()))).toFloat()
                    val labelY = height / 2f + (labelRadius * Math.sin(Math.toRadians(midAngle.toDouble()))).toFloat()

                    val percent = ((category.size.toDouble() / total.toDouble()) * 100).toInt()
                    textPaint.textSize = if (sweepAngle > 30f) 28f else 20f
                    canvas.drawText("${category.name}\n$percent%", labelX, labelY, textPaint)
                }

                startAngle += sweepAngle.toFloat()
            }

            // Draw center circle for donut effect
            val innerRadius = (size - padding * 2) / 4f
            paint.color = Color.parseColor("#1A1A2E")
            paint.style = Paint.Style.FILL
            canvas.drawCircle(width / 2f, height / 2f, innerRadius, paint)

            // Center text
            val usedBytes = categories.sumOf { it.size }
            textPaint.textSize = 24f
            textPaint.color = Color.WHITE
            canvas.drawText(formatSize(usedBytes), width / 2f, height / 2f + 8f, textPaint)
            textPaint.textSize = 14f
            textPaint.color = Color.GRAY
            canvas.drawText("USED", width / 2f, height / 2f - 16f, textPaint)
        }

        private fun formatSize(bytes: Long): String {
            return when {
                bytes <= 0 -> "0 B"
                bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
                else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
            }
        }
    }

    // ========== UPGRADE: StorageBarView ==========
    class StorageBarView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
    ) : View(context, attrs, defStyleAttr) {

        private val categories = mutableListOf<StorageCategory>()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f
            color = Color.WHITE
        }

        fun setData(data: List<StorageCategory>) {
            categories.clear()
            categories.addAll(data)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            if (categories.isEmpty()) return
            val total = categories.sumOf { it.size }
            if (total <= 0) return

            val w = width.toFloat()
            val h = height.toFloat()
            val padding = 20f
            val barHeight = 40f
            val barTop = (h - barHeight) / 2f
            val barWidth = w - padding * 2

            var currentX = padding
            for (category in categories) {
                if (category.size <= 0) continue
                val segmentWidth = (category.size.toDouble() / total.toDouble()) * barWidth

                paint.color = category.color
                paint.style = Paint.Style.FILL
                canvas.drawRect(currentX, barTop, currentX + segmentWidth.toFloat(), barTop + barHeight, paint)

                currentX += segmentWidth.toFloat()
            }

            // Draw labels below
            var labelX = padding
            val labelY = barTop + barHeight + 20f
            textPaint.textSize = 14f
            for (category in categories) {
                if (category.size <= 0) continue
                val pct = ((category.size.toDouble() / total.toDouble()) * 100).toInt()
                val segmentWidth = (category.size.toDouble() / total.toDouble()) * barWidth

                if (pct >= 5) {
                    textPaint.color = category.color
                    canvas.drawText("${category.name} $pct%", labelX, labelY, textPaint)
                }

                labelX += segmentWidth.toFloat()
            }
        }
    }

    // --- Inner Adapters ---

    inner class StorageCategoryAdapter(
        private val items: List<StorageCategory>
    ) : RecyclerView.Adapter<StorageCategoryAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val textViewCategoryName: TextView = view.findViewById(R.id.tvCategoryName)
            val textViewCategorySize: TextView = view.findViewById(R.id.tvCategorySize)
            val textViewCategoryPercent: TextView = view.findViewById(R.id.tvCategoryPercent)
            val viewCategoryColor: View = view.findViewById(R.id.viewCategoryColor)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_storage_category, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val total = items.sumOf { it.size }
            val percent = if (total > 0) ((item.size.toDouble() / total.toDouble()) * 100).toInt() else 0

            holder.textViewCategoryName.text = item.name
            holder.textViewCategorySize.text = formatFileSize(item.size)
            holder.textViewCategoryPercent.text = "$percent%"
            holder.viewCategoryColor.setBackgroundColor(item.color)
        }

        override fun getItemCount() = items.size
    }

    inner class LargeFileAdapter(
        private val items: List<LargeFileInfo>
    ) : RecyclerView.Adapter<LargeFileAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val textViewFileName: TextView = view.findViewById(R.id.tvLargeFileName)
            val textViewFileSize: TextView = view.findViewById(R.id.tvLargeFileSize)
            val textViewFilePath: TextView = view.findViewById(R.id.tvLargeFilePath)
            val textViewFileCategory: TextView = view.findViewById(R.id.tvLargeFileCategory)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_large_file, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.textViewFileName.text = item.fileName
            holder.textViewFileSize.text = formatFileSize(item.fileSize)
            holder.textViewFilePath.text = item.filePath
            holder.textViewFileCategory.text = item.category
        }

        override fun getItemCount() = items.size
    }

    // ========== UPGRADE: Folder Adapter ==========
    inner class FolderAdapter(
        private val items: List<FolderInfo>
    ) : RecyclerView.Adapter<FolderAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val textViewFolderName: TextView = view.findViewById(R.id.tvLargeFileName)
            val textViewFolderSize: TextView = view.findViewById(R.id.tvLargeFileSize)
            val textViewFolderPath: TextView = view.findViewById(R.id.tvLargeFilePath)
            val textViewFolderCategory: TextView = view.findViewById(R.id.tvLargeFileCategory)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_large_file, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.textViewFolderName.text = item.folderName
            holder.textViewFolderSize.text = formatFileSize(item.size)
            holder.textViewFolderPath.text = item.folderPath
            holder.textViewFolderCategory.text = "${item.fileCount} files"
        }

        override fun getItemCount() = items.size
    }

    companion object {
        private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico", "tiff", "raw", "heic", "heif")
        private val videoExtensions = setOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "3gp", "m4v", "mpeg", "mpg")
        private val audioExtensions = setOf("mp3", "wav", "flac", "aac", "ogg", "wma", "m4a", "opus", "amr")
        private val documentExtensions = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "rtf", "html", "xml", "json")
        private val archiveExtensions = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz")
    }
}
