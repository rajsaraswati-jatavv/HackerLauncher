package com.hackerlauncher.modules

import com.hackerlauncher.R

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BigFileFinderFragment : Fragment() {

    private lateinit var recyclerViewBigFiles: RecyclerView
    private lateinit var textViewBigFileCount: TextView
    private lateinit var textViewBigFileTotalSize: TextView
    private lateinit var buttonScan: MaterialButton
    private lateinit var buttonDeleteSelected: MaterialButton
    private lateinit var progressBarScan: ProgressBar
    private lateinit var spinnerSizeThreshold: Spinner
    private lateinit var spinnerSortBy: Spinner
    private lateinit var spinnerCategoryFilter: Spinner

    private val bigFiles = mutableListOf<BigFileInfo>()
    private val allBigFiles = mutableListOf<BigFileInfo>()
    private lateinit var bigFileAdapter: BigFileAdapter

    private var scanJob: Job? = null
    private var isScanning = false
    private var sizeThresholdBytes = 50L * 1024 * 1024 // 50MB default
    private var sortBy = "size" // size, date, type
    private var categoryFilter = "All"

    private val sizeThresholds = listOf("10 MB", "50 MB", "100 MB", "500 MB", "1 GB")
    private val sortOptions = listOf("Size (largest)", "Date (newest)", "Type")
    private val categoryFilters = listOf("All", "Videos", "Archives", "Images", "Audio", "APKs", "Other")

    data class BigFileInfo(
        val fileName: String,
        val filePath: String,
        val fileSize: Long,
        val fileType: String,
        val lastModified: Long,
        var isSelected: Boolean = false
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_big_file_finder, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerViewBigFiles = view.findViewById(R.id.recyclerViewBigFiles)
        textViewBigFileCount = view.findViewById(R.id.textViewBigFileCount)
        textViewBigFileTotalSize = view.findViewById(R.id.textViewBigFileTotalSize)
        buttonScan = view.findViewById(R.id.buttonScan)
        buttonDeleteSelected = view.findViewById(R.id.buttonDeleteSelected)
        progressBarScan = view.findViewById(R.id.progressBarScan)
        spinnerSizeThreshold = view.findViewById(R.id.spinnerSizeThreshold)
        spinnerSortBy = view.findViewById(R.id.spinnerSortBy)
        spinnerCategoryFilter = view.findViewById(R.id.spinnerCategoryFilter)

        bigFileAdapter = BigFileAdapter(bigFiles) { info, isChecked ->
            val original = allBigFiles.find { it.filePath == info.filePath }
            original?.isSelected = isChecked
            updateSummary()
        }
        recyclerViewBigFiles.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewBigFiles.adapter = bigFileAdapter

        // Size threshold spinner
        val thresholdAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, sizeThresholds)
        thresholdAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSizeThreshold.adapter = thresholdAdapter
        spinnerSizeThreshold.setSelection(1) // 50MB default
        spinnerSizeThreshold.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                sizeThresholdBytes = when (position) {
                    0 -> 10L * 1024 * 1024
                    1 -> 50L * 1024 * 1024
                    2 -> 100L * 1024 * 1024
                    3 -> 500L * 1024 * 1024
                    4 -> 1024L * 1024 * 1024
                    else -> 50L * 1024 * 1024
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Sort by spinner
        val sortAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, sortOptions)
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSortBy.adapter = sortAdapter
        spinnerSortBy.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                sortBy = when (position) {
                    0 -> "size"
                    1 -> "date"
                    2 -> "type"
                    else -> "size"
                }
                applySortAndFilter()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Category filter spinner
        val categoryAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categoryFilters)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategoryFilter.adapter = categoryAdapter
        spinnerCategoryFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                categoryFilter = categoryFilters[position]
                applySortAndFilter()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        buttonScan.setOnClickListener {
            if (!isScanning) {
                checkPermissionAndScan()
            }
        }

        buttonDeleteSelected.setOnClickListener { deleteSelectedFiles() }

        checkStoragePermission()
        updateSummary()
    }

    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                showManageStorageDialog()
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun showManageStorageDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Storage Access")
            .setMessage("This app needs MANAGE_EXTERNAL_STORAGE permission to scan for large files. Grant the permission in settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = android.net.Uri.parse("package:${requireContext().packageName}")
                    }
                    manageStorageLauncher.launch(intent)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                showToast("Permission granted")
            }
        }
    }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) showToast("Storage permission required")
    }

    private fun checkPermissionAndScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                showManageStorageDialog()
                return
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }
        startScan()
    }

    private fun startScan() {
        isScanning = true
        allBigFiles.clear()
        bigFiles.clear()
        bigFileAdapter.notifyDataSetChanged()
        progressBarScan.visibility = View.VISIBLE
        buttonScan.text = "Scanning..."
        buttonScan.isEnabled = false

        scanJob = lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val scanRoots = mutableListOf<File>()
                    scanRoots.add(Environment.getExternalStorageDirectory())

                    // Also scan secondary storage if available
                    val externalDirs = ContextCompat.getExternalFilesDirs(requireContext(), null)
                    for (dir in externalDirs) {
                        dir?.let {
                            // Walk up to the root of the external storage
                            var root = it
                            while (root.parentFile != null && root.parentFile?.name != "Android") {
                                root = root.parentFile!!
                            }
                            if (root.parentFile != null) {
                                scanRoots.add(root.parentFile!!)
                            }
                        }
                    }

                    for (root in scanRoots) {
                        if (root.exists() && root.isDirectory) {
                            scanForBigFiles(root)
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showToast("Scan error: ${e.message}")
                    }
                }
            }

            isScanning = false
            progressBarScan.visibility = View.GONE
            buttonScan.text = "Re-Scan"
            buttonScan.isEnabled = true

            applySortAndFilter()
            updateSummary()
            showToast("Scan complete: ${allBigFiles.size} big files found")
        }
    }

    private suspend fun scanForBigFiles(directory: File) {
        if (!directory.exists() || !directory.isDirectory) return
        val files = directory.listFiles() ?: return

        for (file in files) {
            coroutineContext.ensureActive()
            if (file.isDirectory) {
                scanForBigFiles(file)
            } else if (file.length() >= sizeThresholdBytes) {
                val fileType = categorizeFile(file)
                val info = BigFileInfo(
                    fileName = file.name,
                    filePath = file.absolutePath,
                    fileSize = file.length(),
                    fileType = fileType,
                    lastModified = file.lastModified(),
                    isSelected = false
                )
                allBigFiles.add(info)

                withContext(Dispatchers.Main) {
                    applySortAndFilter()
                    updateSummary()
                }
            }
        }
    }

    private fun categorizeFile(file: File): String {
        val ext = file.extension.lowercase(Locale.getDefault())
        return when (ext) {
            in videoExtensions -> "Videos"
            in archiveExtensions -> "Archives"
            in imageExtensions -> "Images"
            in audioExtensions -> "Audio"
            "apk" -> "APKs"
            else -> "Other"
        }
    }

    private fun applySortAndFilter() {
        // Filter
        val filtered = if (categoryFilter == "All") {
            allBigFiles.toList()
        } else {
            allBigFiles.filter { it.fileType == categoryFilter }
        }

        // Sort
        val sorted = when (sortBy) {
            "size" -> filtered.sortedByDescending { it.fileSize }
            "date" -> filtered.sortedByDescending { it.lastModified }
            "type" -> filtered.sortedBy { it.fileType }
            else -> filtered.sortedByDescending { it.fileSize }
        }

        bigFiles.clear()
        bigFiles.addAll(sorted)
        bigFileAdapter.notifyDataSetChanged()
    }

    private fun updateSummary() {
        val fileCount = bigFiles.size
        val totalSize = bigFiles.sumOf { it.fileSize }
        val selectedCount = bigFiles.count { it.isSelected }
        val selectedSize = bigFiles.filter { it.isSelected }.sumOf { it.fileSize }

        textViewBigFileCount.text = "$fileCount files found"
        textViewBigFileTotalSize.text = "Total: ${formatFileSize(totalSize)}" +
                if (selectedCount > 0) " | Selected: ${formatFileSize(selectedSize)}" else ""

        buttonDeleteSelected.text = "Delete Selected ($selectedCount)"
        buttonDeleteSelected.isEnabled = selectedCount > 0
    }

    private fun deleteSelectedFiles() {
        val selectedFiles = bigFiles.filter { it.isSelected }
        if (selectedFiles.isEmpty()) {
            showToast("No files selected")
            return
        }

        val totalSize = selectedFiles.sumOf { it.fileSize }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Big Files")
            .setMessage("Permanently delete ${selectedFiles.size} files (${formatFileSize(totalSize)})?\n\nThis action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                performDelete(selectedFiles)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performDelete(filesToDelete: List<BigFileInfo>) {
        lifecycleScope.launch {
            var deletedCount = 0
            var freedBytes = 0L
            var failedCount = 0

            withContext(Dispatchers.IO) {
                for (info in filesToDelete) {
                    try {
                        val file = File(info.filePath)
                        if (file.exists()) {
                            val size = file.length()
                            if (file.delete()) {
                                deletedCount++
                                freedBytes += size
                            } else {
                                failedCount++
                            }
                        } else {
                            failedCount++
                        }
                    } catch (e: Exception) {
                        failedCount++
                    }
                }
            }

            showToast("Deleted $deletedCount files, freed ${formatFileSize(freedBytes)}" +
                    if (failedCount > 0) " ($failedCount failed)" else "")

            // Remove deleted files from lists
            val deletedPaths = filesToDelete.map { it.filePath }.toSet()
            allBigFiles.removeAll { it.filePath in deletedPaths }
            applySortAndFilter()
            updateSummary()
        }
    }

    private fun showFileInfo(info: BigFileInfo) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val dateStr = dateFormat.format(Date(info.lastModified))

        val message = buildString {
            append("═══ FILE INFO ═══\n\n")
            append("Name: ${info.fileName}\n")
            append("Path: ${info.filePath}\n")
            append("Size: ${formatFileSize(info.fileSize)}\n")
            append("Type: ${info.fileType}\n")
            append("Modified: $dateStr\n")
            append("Extension: ${File(info.filePath).extension}\n")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(info.fileName)
            .setMessage(message)
            .setPositiveButton("Delete") { _, _ ->
                performDelete(listOf(info))
            }
            .setNegativeButton("Close", null)
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
        scanJob?.cancel()
    }

    // --- Inner Adapter ---

    inner class BigFileAdapter(
        private val items: List<BigFileInfo>,
        private val onCheckChanged: (BigFileInfo, Boolean) -> Unit
    ) : RecyclerView.Adapter<BigFileAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val checkBox: CheckBox = view.findViewById(R.id.cbBigFileSelect)
            val imageViewTypeIcon: ImageView = view.findViewById(R.id.ivFileTypeIcon)
            val textViewFileName: TextView = view.findViewById(R.id.tvBigFileName)
            val textViewFileSize: TextView = view.findViewById(R.id.tvBigFileSize)
            val textViewFilePath: TextView = view.findViewById(R.id.tvBigFilePath)
            val textViewFileType: TextView = view.findViewById(R.id.tvBigFileType)
            val textViewFileDate: TextView = view.findViewById(R.id.tvBigFileDate)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_big_file, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.checkBox.setOnCheckedChangeListener(null)
            holder.checkBox.isChecked = item.isSelected

            holder.textViewFileName.text = item.fileName
            holder.textViewFileSize.text = formatFileSize(item.fileSize)
            holder.textViewFilePath.text = item.filePath
            holder.textViewFileType.text = item.fileType

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            holder.textViewFileDate.text = dateFormat.format(Date(item.lastModified))

            // Size color coding
            holder.textViewFileSize.setTextColor(
                when {
                    item.fileSize >= 1024L * 1024 * 1024 -> 0xFFFF4444.toInt()  // >= 1GB red
                    item.fileSize >= 500L * 1024 * 1024 -> 0xFFFF8800.toInt()   // >= 500MB orange
                    item.fileSize >= 100L * 1024 * 1024 -> 0xFFFFFF00.toInt()   // >= 100MB yellow
                    else -> 0xFF00FF00.toInt()                                     // green
                }
            )

            // Type icon color
            val typeColor = when (item.fileType) {
                "Videos" -> 0xFFFF5722.toInt()
                "Archives" -> 0xFFFF9800.toInt()
                "Images" -> 0xFF4CAF50.toInt()
                "Audio" -> 0xFF9C27B0.toInt()
                "APKs" -> 0xFF2196F3.toInt()
                else -> 0xFF888888.toInt()
            }
            holder.textViewFileType.setTextColor(typeColor)

            // Set type icon drawable tint
            holder.imageViewTypeIcon.setColorFilter(typeColor)
            holder.imageViewTypeIcon.setImageResource(
                when (item.fileType) {
                    "Videos" -> android.R.drawable.ic_media_play
                    "Archives" -> android.R.drawable.ic_menu_upload
                    "Images" -> android.R.drawable.ic_menu_gallery
                    "Audio" -> android.R.drawable.ic_media_play
                    "APKs" -> android.R.drawable.ic_menu_manage
                    else -> android.R.drawable.ic_menu_info_details
                }
            )

            holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
                onCheckChanged(item, isChecked)
            }

            holder.itemView.setOnClickListener {
                showFileInfo(item)
            }

            // Long press to toggle selection
            holder.itemView.setOnLongClickListener {
                holder.checkBox.isChecked = !holder.checkBox.isChecked
                true
            }
        }

        override fun getItemCount() = items.size
    }

    companion object {
        private val videoExtensions = setOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "3gp", "m4v", "mpeg", "mpg", "ts", "vob")
        private val archiveExtensions = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz", "zst", "lz4", "cab", "iso")
        private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico", "tiff", "tif", "raw", "heic", "heif", "psd")
        private val audioExtensions = setOf("mp3", "wav", "flac", "aac", "ogg", "wma", "m4a", "opus", "amr", "mid", "midi", "aiff")
    }
}
