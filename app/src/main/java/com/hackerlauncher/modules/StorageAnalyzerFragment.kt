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
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
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
import java.util.Locale

class StorageAnalyzerFragment : Fragment() {

    private lateinit var storagePieChart: StoragePieView
    private lateinit var recyclerViewCategories: RecyclerView
    private lateinit var recyclerViewLargeFiles: RecyclerView
    private lateinit var textViewTotalStorage: TextView
    private lateinit var textViewUsedStorage: TextView
    private lateinit var textViewFreeStorage: TextView
    private lateinit var storageProgressBar: ProgressBar
    private lateinit var buttonQuickClean: MaterialButton
    private lateinit var tabStorage: TabLayout

    private val categoryList = mutableListOf<StorageCategory>()
    private val largeFiles = mutableListOf<LargeFileInfo>()
    private lateinit var categoryAdapter: StorageCategoryAdapter
    private lateinit var largeFileAdapter: LargeFileAdapter

    private var analyzeJob: Job? = null
    private var isInternal = true

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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_storage_analyzer, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        storagePieChart = view.findViewById(R.id.storagePieView)
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

        startAnalysis()
    }

    private fun startAnalysis() {
        analyzeJob?.cancel()
        categoryList.clear()
        largeFiles.clear()
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

                    val accountedSize = imagesSize + videosSize + audioSize + documentsSize + cacheSize + appsSize
                    val otherSize = maxOf(0L, usedBytes - accountedSize)

                    val categories = listOf(
                        StorageCategory("Apps", appsSize, 0xFF2196F3.toInt()),
                        StorageCategory("Images", imagesSize, 0xFF4CAF50.toInt()),
                        StorageCategory("Videos", videosSize, 0xFFFF5722.toInt()),
                        StorageCategory("Audio", audioSize, 0xFF9C27B0.toInt()),
                        StorageCategory("Documents", documentsSize, 0xFFFF9800.toInt()),
                        StorageCategory("Cache", cacheSize, 0xFFFFEB3B.toInt()),
                        StorageCategory("Other", otherSize, 0xFF888888.toInt())
                    )

                    withContext(Dispatchers.Main) {
                        categoryList.clear()
                        categoryList.addAll(categories)
                        categoryAdapter.notifyDataSetChanged()
                        storagePieChart.setData(categories)
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

        val downloadsDir = File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DOWNLOADS)
        val apkFiles = findApkFiles(downloadsDir)
        if (apkFiles.isNotEmpty()) {
            val apkSize = apkFiles.sumOf { it.length() }
            suggestions.add("APK files in Downloads: ${formatFileSize(apkSize)} (${apkFiles.size} files)")
            potentialSavings += apkSize
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

    companion object {
        private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico", "tiff", "raw", "heic", "heif")
        private val videoExtensions = setOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "3gp", "m4v", "mpeg", "mpg")
        private val audioExtensions = setOf("mp3", "wav", "flac", "aac", "ogg", "wma", "m4a", "opus", "amr")
        private val documentExtensions = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "rtf", "html", "xml", "json")
        private val archiveExtensions = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz")
    }
}
