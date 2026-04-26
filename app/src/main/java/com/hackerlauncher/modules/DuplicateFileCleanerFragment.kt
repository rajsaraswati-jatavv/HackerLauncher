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
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
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
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale

class DuplicateFileCleanerFragment : Fragment() {

    private lateinit var recyclerViewDuplicates: RecyclerView
    private lateinit var textViewDuplicateCount: TextView
    private lateinit var textViewDuplicateSize: TextView
    private lateinit var buttonScan: MaterialButton
    private lateinit var buttonCleanSelected: MaterialButton
    private lateinit var progressBarScan: ProgressBar
    private lateinit var spinnerCategory: Spinner
    private lateinit var textViewScanPath: TextView

    private val duplicateGroups = mutableListOf<DuplicateGroup>()
    private lateinit var duplicateAdapter: DuplicateGroupAdapter

    private var scanJob: Job? = null
    private var isScanning = false
    private var currentCategory = "All"

    private val categories = listOf("All", "Images", "Videos", "Audio", "Documents")

    data class DuplicateGroup(
        val fileName: String,
        val fileSize: Long,
        val filePaths: List<String>,
        val category: String,
        var selectedIndex: Int = 0
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_duplicate_file_cleaner, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerViewDuplicates = view.findViewById(R.id.recyclerViewDuplicates)
        textViewDuplicateCount = view.findViewById(R.id.textViewDuplicateCount)
        textViewDuplicateSize = view.findViewById(R.id.textViewDuplicateSize)
        buttonScan = view.findViewById(R.id.buttonScan)
        buttonCleanSelected = view.findViewById(R.id.buttonCleanSelected)
        progressBarScan = view.findViewById(R.id.progressBarScan)
        spinnerCategory = view.findViewById(R.id.spinnerCategory)
        textViewScanPath = view.findViewById(R.id.textViewScanPath)

        duplicateAdapter = DuplicateGroupAdapter(duplicateGroups) { group, newIndex ->
            group.selectedIndex = newIndex
            updateSummary()
        }
        recyclerViewDuplicates.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewDuplicates.adapter = duplicateAdapter

        val categoryAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categories)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = categoryAdapter
        spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentCategory = categories[position]
                filterAndDisplay()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val scanPath = loadScanPath()
        textViewScanPath.text = "Scan: $scanPath"

        buttonScan.setOnClickListener {
            if (!isScanning) {
                checkPermissionAndScan()
            }
        }

        buttonCleanSelected.setOnClickListener { cleanSelectedDuplicates() }

        textViewScanPath.setOnClickListener {
            showScanPathDialog()
        }

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
            .setMessage("This app needs access to all files to scan for duplicates. Grant the permission in settings.")
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

    private fun loadScanPath(): String {
        val prefs = requireContext().getSharedPreferences("duplicate_cleaner_prefs", Context.MODE_PRIVATE)
        return prefs.getString("scan_path", Environment.getExternalStorageDirectory().absolutePath)
            ?: Environment.getExternalStorageDirectory().absolutePath
    }

    private fun saveScanPath(path: String) {
        val prefs = requireContext().getSharedPreferences("duplicate_cleaner_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("scan_path", path).apply()
    }

    private fun showScanPathDialog() {
        val currentPath = loadScanPath()
        val editText = android.widget.EditText(requireContext()).apply {
            setText(currentPath)
            setSingleLine()
            setPadding(40, 24, 40, 24)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Scan Path")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val newPath = editText.text.toString().trim()
                if (newPath.isNotEmpty() && File(newPath).isDirectory) {
                    saveScanPath(newPath)
                    textViewScanPath.text = "Scan: $newPath"
                } else {
                    showToast("Invalid directory path")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startScan() {
        isScanning = true
        duplicateGroups.clear()
        duplicateAdapter.notifyDataSetChanged()
        progressBarScan.visibility = View.VISIBLE
        buttonScan.text = "Scanning..."
        buttonScan.isEnabled = false

        val scanPath = loadScanPath()

        scanJob = lifecycleScope.launch {
            val allGroups = mutableListOf<DuplicateGroup>()

            withContext(Dispatchers.IO) {
                try {
                    val scanDir = File(scanPath)
                    if (!scanDir.exists() || !scanDir.isDirectory) {
                        withContext(Dispatchers.Main) {
                            showToast("Scan directory does not exist")
                        }
                        return@withContext
                    }

                    // Phase 1: Collect files by name and size
                    val filesByNameSize = mutableMapOf<String, MutableList<File>>()
                    collectFiles(scanDir, filesByNameSize)

                    // Phase 2: For groups with same name+size, verify by hash
                    for ((key, files) in filesByNameSize) {
                        coroutineContext.ensureActive()
                        if (files.size < 2) continue

                        // Further group by content hash
                        val filesByHash = mutableMapOf<String, MutableList<File>>()
                        for (file in files) {
                            val hash = computeFileHash(file)
                            filesByHash.getOrPut(hash) { mutableListOf() }.add(file)
                        }

                        for ((hash, duplicateFiles) in filesByHash) {
                            if (duplicateFiles.size < 2) continue

                            val category = categorizeFile(duplicateFiles.first())
                            val group = DuplicateGroup(
                                fileName = duplicateFiles.first().name,
                                fileSize = duplicateFiles.first().length(),
                                filePaths = duplicateFiles.map { it.absolutePath },
                                category = category,
                                selectedIndex = 0
                            )
                            allGroups.add(group)

                            withContext(Dispatchers.Main) {
                                duplicateGroups.add(group)
                                duplicateAdapter.notifyItemInserted(duplicateGroups.size - 1)
                                updateSummary()
                            }
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

            filterAndDisplay()
            updateSummary()
            showToast("Scan complete: ${allGroups.size} duplicate groups found")
        }
    }

    private suspend fun collectFiles(
        directory: File,
        filesByNameSize: MutableMap<String, MutableList<File>>
    ) {
        if (!directory.exists() || !directory.isDirectory) return
        val files = directory.listFiles() ?: return

        for (file in files) {
            coroutineContext.ensureActive()
            if (file.isDirectory) {
                collectFiles(file, filesByNameSize)
            } else if (file.length() > 0) {
                val key = "${file.name.lowercase(Locale.getDefault())}_${file.length()}"
                filesByNameSize.getOrPut(key) { mutableListOf() }.add(file)
            }
        }
    }

    private fun computeFileHash(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val fis = FileInputStream(file)
            val buffer = ByteArray(8192)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
            fis.close()
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            file.absolutePath.hashCode().toString()
        }
    }

    private fun categorizeFile(file: File): String {
        val ext = file.extension.lowercase(Locale.getDefault())
        return when (ext) {
            in imageExtensions -> "Images"
            in videoExtensions -> "Videos"
            in audioExtensions -> "Audio"
            in documentExtensions -> "Documents"
            else -> "Other"
        }
    }

    private fun filterAndDisplay() {
        val filtered = if (currentCategory == "All") {
            duplicateGroups.toList()
        } else {
            duplicateGroups.filter { it.category == currentCategory }
        }
        duplicateAdapter.updateItems(filtered)
        updateSummary()
    }

    private fun updateSummary() {
        val filtered = if (currentCategory == "All") duplicateGroups else duplicateGroups.filter { it.category == currentCategory }
        val groupCount = filtered.size
        val duplicateCount = filtered.sumOf { it.filePaths.size - 1 }
        val duplicateSize = filtered.sumOf { group ->
            (group.filePaths.size - 1) * group.fileSize
        }

        textViewDuplicateCount.text = "$groupCount groups | $duplicateCount duplicates"
        textViewDuplicateSize.text = "Recoverable: ${formatFileSize(duplicateSize)}"

        val selectedForDeletion = filtered.sumOf { group ->
            val toDelete = group.filePaths.filterIndexed { index, _ -> index != group.selectedIndex }
            toDelete.size
        }
        buttonCleanSelected.text = "Clean Selected ($selectedForDeletion)"
        buttonCleanSelected.isEnabled = selectedForDeletion > 0
    }

    private fun cleanSelectedDuplicates() {
        val filtered = if (currentCategory == "All") duplicateGroups.toList() else duplicateGroups.filter { it.category == currentCategory }
        val filesToDelete = mutableListOf<String>()

        for (group in filtered) {
            group.filePaths.filterIndexed { index, _ -> index != group.selectedIndex }.forEach {
                filesToDelete.add(it)
            }
        }

        if (filesToDelete.isEmpty()) {
            showToast("No duplicates to clean")
            return
        }

        val totalSize = filesToDelete.map { File(it).length() }.sum()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Clean Duplicates")
            .setMessage("Delete ${filesToDelete.size} duplicate files (${formatFileSize(totalSize)})?\n\nOne copy of each group will be kept.")
            .setPositiveButton("Clean") { _, _ ->
                performClean(filesToDelete)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performClean(filesToDelete: List<String>) {
        lifecycleScope.launch {
            var cleanedCount = 0
            var freedBytes = 0L
            var failedCount = 0

            withContext(Dispatchers.IO) {
                for (path in filesToDelete) {
                    try {
                        val file = File(path)
                        if (file.exists()) {
                            val size = file.length()
                            if (file.delete()) {
                                cleanedCount++
                                freedBytes += size
                            } else {
                                failedCount++
                            }
                        }
                    } catch (e: Exception) {
                        failedCount++
                    }
                }
            }

            showToast("Cleaned $cleanedCount duplicates, freed ${formatFileSize(freedBytes)}" +
                    if (failedCount > 0) " ($failedCount failed)" else "")

            // Remove cleaned paths from groups and remove empty groups
            val deletedSet = filesToDelete.toSet()
            val iter = duplicateGroups.iterator()
            while (iter.hasNext()) {
                val group = iter.next()
                val remainingPaths = group.filePaths.filter { it !in deletedSet }
                if (remainingPaths.size <= 1) {
                    iter.remove()
                }
            }
            duplicateAdapter.notifyDataSetChanged()
            updateSummary()
        }
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

    inner class DuplicateGroupAdapter(
        private var items: List<DuplicateGroup>,
        private val onSelectChanged: (DuplicateGroup, Int) -> Unit
    ) : RecyclerView.Adapter<DuplicateGroupAdapter.VH>() {

        private val expandedPositions = mutableSetOf<Int>()

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val textViewFileName: TextView = view.findViewById(R.id.tvDuplicateFileName)
            val textViewFileSize: TextView = view.findViewById(R.id.tvDuplicateFileSize)
            val textViewCategory: TextView = view.findViewById(R.id.tvDuplicateCategory)
            val textViewCopyCount: TextView = view.findViewById(R.id.tvDuplicateCopyCount)
            val layoutPaths: LinearLayout = view.findViewById(R.id.layoutDuplicatePaths)
            val layoutHeader: View = view.findViewById(R.id.layoutDuplicateHeader)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_duplicate_group, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val group = items[position]
            holder.textViewFileName.text = group.fileName
            holder.textViewFileSize.text = formatFileSize(group.fileSize)
            holder.textViewCategory.text = group.category
            holder.textViewCopyCount.text = "${group.filePaths.size} copies"

            val categoryColor = when (group.category) {
                "Images" -> 0xFF2196F3.toInt()
                "Videos" -> 0xFFFF5722.toInt()
                "Audio" -> 0xFF9C27B0.toInt()
                "Documents" -> 0xFF4CAF50.toInt()
                else -> 0xFF888888.toInt()
            }
            holder.textViewCategory.setTextColor(categoryColor)

            val isExpanded = expandedPositions.contains(holder.adapterPosition)

            holder.layoutHeader.setOnClickListener {
                val pos = holder.adapterPosition
                if (expandedPositions.contains(pos)) {
                    expandedPositions.remove(pos)
                } else {
                    expandedPositions.add(pos)
                }
                notifyItemChanged(pos)
            }

            holder.layoutPaths.removeAllViews()
            if (isExpanded) {
                holder.layoutPaths.visibility = View.VISIBLE
                val radioGroup = RadioGroup(requireContext()).apply {
                    orientation = RadioGroup.VERTICAL
                }

                group.filePaths.forEachIndexed { index, path ->
                    val radioLayout = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, 8, 0, 8)
                    }

                    val radioButton = RadioButton(requireContext()).apply {
                        id = View.generateViewId()
                        text = ""
                        isChecked = index == group.selectedIndex
                        setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked) {
                                onSelectChanged(group, index)
                                notifyDataSetChanged()
                            }
                        }
                    }

                    val pathText = TextView(requireContext()).apply {
                        text = "${if (index == group.selectedIndex) "KEEP" else "DEL"} $path"
                        textSize = 12f
                        setTextColor(
                            if (index == group.selectedIndex) 0xFF00FF00.toInt() else 0xFFFF4444.toInt()
                        )
                        setPadding(8, 0, 0, 0)
                    }

                    radioLayout.addView(radioButton)
                    radioLayout.addView(pathText)
                    radioGroup.addView(radioLayout)
                }

                holder.layoutPaths.addView(radioGroup)
            } else {
                holder.layoutPaths.visibility = View.GONE
            }
        }

        override fun getItemCount() = items.size

        fun updateItems(newItems: List<DuplicateGroup>) {
            items = newItems
            expandedPositions.clear()
            notifyDataSetChanged()
        }
    }

    companion object {
        private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico", "tiff", "tif", "raw", "heic", "heif")
        private val videoExtensions = setOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "3gp", "m4v", "mpeg", "mpg")
        private val audioExtensions = setOf("mp3", "wav", "flac", "aac", "ogg", "wma", "m4a", "opus", "amr", "mid", "midi")
        private val documentExtensions = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "rtf", "odt", "ods", "odp", "html", "xml", "json")
    }
}
