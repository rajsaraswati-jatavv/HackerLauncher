package com.hackerlauncher.modules

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.coroutineContext

class SystemCleanerFragment : Fragment() {

    private lateinit var tvStorageUsage: TextView
    private lateinit var storageProgressBar: ProgressBar
    private lateinit var tvStorageDetail: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: TextView
    private lateinit var btnScan: MaterialButton
    private lateinit var btnCleanSelected: MaterialButton
    private lateinit var btnCleanAll: MaterialButton
    private lateinit var btnSelectAll: CheckBox
    private lateinit var tvTotalSize: TextView
    private lateinit var scanProgressBar: ProgressBar

    private val junkItems = mutableListOf<JunkItem>()
    private lateinit var junkAdapter: JunkAdapter

    private var scanJob: Job? = null
    private var isScanning = false

    data class JunkItem(
        val path: String,
        val name: String,
        val category: String,
        var size: Long = 0,
        var isSelected: Boolean = true,
        var isFile: Boolean = true
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_system_cleaner, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvStorageUsage = view.findViewById(R.id.tvStorageUsage)
        storageProgressBar = view.findViewById(R.id.storageProgressBar)
        tvStorageDetail = view.findViewById(R.id.tvStorageDetail)
        recyclerView = view.findViewById(R.id.junkRecycler)
        emptyState = view.findViewById(R.id.emptyState)
        btnScan = view.findViewById(R.id.btnScan)
        btnCleanSelected = view.findViewById(R.id.btnCleanSelected)
        btnCleanAll = view.findViewById(R.id.btnCleanAll)
        btnSelectAll = view.findViewById(R.id.btnSelectAll)
        tvTotalSize = view.findViewById(R.id.tvTotalSize)
        scanProgressBar = view.findViewById(R.id.scanProgressBar)

        junkAdapter = JunkAdapter(junkItems) { item, isChecked ->
            item.isSelected = isChecked
            updateTotalSize()
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = junkAdapter

        updateStorageInfo()

        btnScan.setOnClickListener {
            if (!isScanning) {
                checkPermissionAndScan()
            }
        }

        btnCleanSelected.setOnClickListener { cleanSelected() }
        btnCleanAll.setOnClickListener { cleanAll() }

        btnSelectAll.setOnCheckedChangeListener { _, isChecked ->
            junkItems.forEach { it.isSelected = isChecked }
            junkAdapter.notifyDataSetChanged()
            updateTotalSize()
        }

        checkStoragePermission()
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
            .setMessage("This app needs access to all files to scan for junk files. Grant the permission in settings.")
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
        junkItems.clear()
        junkAdapter.notifyDataSetChanged()
        scanProgressBar.visibility = View.VISIBLE
        btnScan.text = "Scanning..."
        btnScan.isEnabled = false

        scanJob = lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // Scan cache directories
                    scanCacheDirs()

                    // Scan temp files
                    scanTempFiles()

                    // Scan log files
                    scanLogFiles()

                    // Scan empty folders
                    scanEmptyFolders()

                    // Scan APK files
                    scanApkFiles()

                    // Scan thumbnail cache
                    scanThumbnailCache()

                    // Scan download temp files
                    scanDownloadTemp()

                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showToast("Scan error: ${e.message}")
                    }
                }
            }

            isScanning = false
            scanProgressBar.visibility = View.GONE
            btnScan.text = "Re-Scan"
            btnScan.isEnabled = true

            junkAdapter.notifyDataSetChanged()
            updateTotalSize()
            emptyState.visibility = if (junkItems.isEmpty()) View.VISIBLE else View.GONE
            updateStorageInfo()

            showToast("Scan complete: ${junkItems.size} items found")
        }
    }

    private suspend fun scanCacheDirs() {
        val paths = mutableListOf<String>()

        // App cache dir
        requireContext().cacheDir?.let { paths.add(it.absolutePath) }

        // External cache dir
        requireContext().externalCacheDir?.let { paths.add(it.absolutePath) }

        // All external cache dirs
        requireContext().externalCacheDirs?.forEach { dir ->
            paths.add(dir.absolutePath)
        }

        // Code cache
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            requireContext().codeCacheDir?.let { paths.add(it.absolutePath) }
        }

        for (path in paths) {
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) {
                val size = calculateDirectorySize(dir)
                if (size > 0) {
                    withContext(Dispatchers.Main) {
                        junkItems.add(
                            JunkItem(
                                path = dir.absolutePath,
                                name = "Cache: ${dir.name}",
                                category = "Cache",
                                size = size,
                                isSelected = true,
                                isFile = false
                            )
                        )
                        junkAdapter.notifyItemInserted(junkItems.size - 1)
                    }
                }
            }
        }
    }

    private suspend fun scanTempFiles() {
        val searchDirs = mutableListOf<File>()

        requireContext().cacheDir?.let { searchDirs.add(it) }
        requireContext().externalCacheDir?.let { searchDirs.add(it) }
        Environment.getExternalStorageDirectory().let { searchDirs.add(File(it, "tmp")) }
        Environment.getExternalStorageDirectory().let { searchDirs.add(File(it, "temp")) }

        for (dir in searchDirs) {
            if (!dir.exists()) continue
            findFilesByExtension(dir, listOf(".tmp", ".temp", ".bak", ".swp", ".swo")) { file ->
                withContext(Dispatchers.Main) {
                    junkItems.add(
                        JunkItem(
                            path = file.absolutePath,
                            name = file.name,
                            category = "Temp Files",
                            size = file.length(),
                            isSelected = true,
                            isFile = true
                        )
                    )
                    junkAdapter.notifyItemInserted(junkItems.size - 1)
                }
            }
        }
    }

    private suspend fun scanLogFiles() {
        val searchDirs = mutableListOf<File>()

        searchDirs.add(File(Environment.getExternalStorageDirectory(), "log"))
        searchDirs.add(File(Environment.getExternalStorageDirectory(), "logs"))
        requireContext().cacheDir?.let { searchDirs.add(it) }

        for (dir in searchDirs) {
            if (!dir.exists()) continue
            findFilesByExtension(dir, listOf(".log", ".logs")) { file ->
                withContext(Dispatchers.Main) {
                    junkItems.add(
                        JunkItem(
                            path = file.absolutePath,
                            name = file.name,
                            category = "Log Files",
                            size = file.length(),
                            isSelected = true,
                            isFile = true
                        )
                    )
                    junkAdapter.notifyItemInserted(junkItems.size - 1)
                }
            }
        }
    }

    private suspend fun scanEmptyFolders() {
        val searchRoots = mutableListOf<File>()
        searchRoots.add(Environment.getExternalStorageDirectory())

        for (root in searchRoots) {
            if (!root.exists() || !root.isDirectory) continue
            findEmptyFolders(root) { folder ->
                withContext(Dispatchers.Main) {
                    junkItems.add(
                        JunkItem(
                            path = folder.absolutePath,
                            name = folder.absolutePath,
                            category = "Empty Folders",
                            size = 0,
                            isSelected = false, // Don't auto-select empty folders
                            isFile = false
                        )
                    )
                    junkAdapter.notifyItemInserted(junkItems.size - 1)
                }
            }
        }
    }

    private suspend fun scanApkFiles() {
        val searchRoots = mutableListOf<File>()
        searchRoots.add(Environment.getExternalStorageDirectory())
        searchRoots.add(File(Environment.getExternalStorageDirectory(), "Download"))

        for (root in searchRoots) {
            if (!root.exists() || !root.isDirectory) continue
            findFilesByExtension(root, listOf(".apk")) { file ->
                withContext(Dispatchers.Main) {
                    junkItems.add(
                        JunkItem(
                            path = file.absolutePath,
                            name = "APK: ${file.name}",
                            category = "APK Files",
                            size = file.length(),
                            isSelected = false, // Don't auto-select APKs
                            isFile = true
                        )
                    )
                    junkAdapter.notifyItemInserted(junkItems.size - 1)
                }
            }
        }
    }

    private suspend fun scanThumbnailCache() {
        val thumbDir = File(Environment.getExternalStorageDirectory(), ".thumbnails")
        if (thumbDir.exists() && thumbDir.isDirectory) {
            val size = calculateDirectorySize(thumbDir)
            if (size > 0) {
                withContext(Dispatchers.Main) {
                    junkItems.add(
                        JunkItem(
                            path = thumbDir.absolutePath,
                            name = "Thumbnail Cache",
                            category = "Cache",
                            size = size,
                            isSelected = true,
                            isFile = false
                        )
                    )
                    junkAdapter.notifyItemInserted(junkItems.size - 1)
                }
            }
        }
    }

    private suspend fun scanDownloadTemp() {
        val downloadDir = File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DOWNLOADS)
        if (downloadDir.exists() && downloadDir.isDirectory) {
            findFilesByExtension(downloadDir, listOf(".part", ".crdownload", ".download")) { file ->
                withContext(Dispatchers.Main) {
                    junkItems.add(
                        JunkItem(
                            path = file.absolutePath,
                            name = "Partial: ${file.name}",
                            category = "Incomplete Downloads",
                            size = file.length(),
                            isSelected = true,
                            isFile = true
                        )
                    )
                    junkAdapter.notifyItemInserted(junkItems.size - 1)
                }
            }
        }
    }

    private suspend fun findFilesByExtension(
        directory: File,
        extensions: List<String>,
        onFound: suspend (File) -> Unit
    ) {
        if (!directory.exists() || !directory.isDirectory) return
        val files = directory.listFiles() ?: return
        for (file in files) {
            coroutineContext.ensureActive()
            if (file.isDirectory) {
                findFilesByExtension(file, extensions, onFound)
            } else {
                val name = file.name.lowercase(Locale.getDefault())
                if (extensions.any { ext -> name.endsWith(ext) }) {
                    onFound(file)
                }
            }
        }
    }

    private suspend fun findEmptyFolders(
        directory: File,
        onFound: suspend (File) -> Unit,
        depth: Int = 0
    ) {
        if (!directory.exists() || !directory.isDirectory) return
        if (depth > 5) return // Limit depth to avoid deep scanning
        val files = directory.listFiles() ?: return

        var isEmpty = true
        for (file in files) {
            coroutineContext.ensureActive()
            if (file.isDirectory) {
                findEmptyFolders(file, onFound, depth + 1)
            } else {
                isEmpty = false
            }
        }

        if (isEmpty && directory.listFiles()?.isEmpty() == true) {
            // Skip root and important system directories
            val skipNames = setOf("Android", "DCIM", "Pictures", "Music", "Videos", "Documents", "Download")
            if (directory.name !in skipNames && depth > 0) {
                onFound(directory)
            }
        }
    }

    private fun calculateDirectorySize(directory: File): Long {
        if (!directory.exists()) return 0
        if (directory.isFile) return directory.length()

        var size = 0L
        val files = directory.listFiles() ?: return 0
        for (file in files) {
            size += if (file.isDirectory) {
                calculateDirectorySize(file)
            } else {
                file.length()
            }
        }
        return size
    }

    private fun updateTotalSize() {
        val selectedSize = junkItems.filter { it.isSelected }.sumOf { it.size }
        val totalSize = junkItems.sumOf { it.size }
        tvTotalSize.text = "Selected: ${formatFileSize(selectedSize)} / Total: ${formatFileSize(totalSize)}"

        val selectedCount = junkItems.count { it.isSelected }
        btnCleanSelected.text = "Clean Selected ($selectedCount)"
        btnCleanSelected.isEnabled = selectedCount > 0
        btnCleanAll.isEnabled = junkItems.isNotEmpty()
    }

    private fun updateStorageInfo() {
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val totalBytes = stat.totalBytes
            val availableBytes = stat.availableBytes
            val usedBytes = totalBytes - availableBytes
            val usagePercent = ((usedBytes.toDouble() / totalBytes.toDouble()) * 100).toInt()

            tvStorageUsage.text = "Storage: $usagePercent% Used"
            storageProgressBar.progress = usagePercent
            tvStorageDetail.text = buildString {
                append("═══ STORAGE INFO ═══\n")
                append("Total: ${formatFileSize(totalBytes)}\n")
                append("Used:  ${formatFileSize(usedBytes)}\n")
                append("Free:  ${formatFileSize(availableBytes)}\n")
            }
        } catch (e: Exception) {
            tvStorageUsage.text = "Storage info unavailable"
            tvStorageDetail.text = "Cannot read storage stats"
        }
    }

    private fun cleanSelected() {
        val selectedItems = junkItems.filter { it.isSelected }
        if (selectedItems.isEmpty()) {
            showToast("No items selected")
            return
        }

        val totalSize = selectedItems.sumOf { it.size }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Clean Selected Items")
            .setMessage("Delete ${selectedItems.size} items (${formatFileSize(totalSize)})?")
            .setPositiveButton("Clean") { _, _ ->
                performClean(selectedItems)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun cleanAll() {
        if (junkItems.isEmpty()) {
            showToast("No items to clean")
            return
        }

        val totalSize = junkItems.sumOf { it.size }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Clean All Items")
            .setMessage("Delete ALL ${junkItems.size} items (${formatFileSize(totalSize)})?\n\n⚠ Some items may be in use.")
            .setPositiveButton("Clean All") { _, _ ->
                performClean(junkItems.toList())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performClean(items: List<JunkItem>) {
        lifecycleScope.launch {
            var cleanedCount = 0
            var freedBytes = 0L
            var failedCount = 0

            withContext(Dispatchers.IO) {
                for (item in items) {
                    try {
                        val file = File(item.path)
                        val wasSize = if (file.isFile) file.length() else calculateDirectorySize(file)

                        val deleted = if (file.isFile) {
                            file.delete()
                        } else {
                            deleteDirectory(file)
                        }

                        if (deleted) {
                            cleanedCount++
                            freedBytes += wasSize
                        } else {
                            failedCount++
                        }
                    } catch (e: Exception) {
                        failedCount++
                    }
                }
            }

            showToast("Cleaned $cleanedCount items, freed ${formatFileSize(freedBytes)}" +
                    if (failedCount > 0) " ($failedCount failed)" else "")

            // Remove cleaned items from list
            val cleanedPaths = items.map { it.path }.toSet()
            junkItems.removeAll { it.path in cleanedPaths }
            junkAdapter.notifyDataSetChanged()
            updateTotalSize()
            updateStorageInfo()
            emptyState.visibility = if (junkItems.isEmpty()) View.VISIBLE else View.GONE
        }
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

    // --- Inner Adapter ---

    inner class JunkAdapter(
        private val items: List<JunkItem>,
        private val onCheckChanged: (JunkItem, Boolean) -> Unit
    ) : RecyclerView.Adapter<JunkAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val cbSelect: CheckBox = view.findViewById(R.id.cbSelect)
            val tvName: TextView = view.findViewById(R.id.tvJunkName)
            val tvPath: TextView = view.findViewById(R.id.tvJunkPath)
            val tvSize: TextView = view.findViewById(R.id.tvJunkSize)
            val tvCategory: TextView = view.findViewById(R.id.tvJunkCategory)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_junk, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.cbSelect.setOnCheckedChangeListener(null) // Prevent recycled listener
            holder.cbSelect.isChecked = item.isSelected
            holder.tvName.text = item.name
            holder.tvPath.text = item.path
            holder.tvSize.text = if (item.size > 0) formatFileSize(item.size) else "Empty"
            holder.tvCategory.text = item.category

            holder.cbSelect.setOnCheckedChangeListener { _, isChecked ->
                onCheckChanged(item, isChecked)
            }
        }

        override fun getItemCount() = items.size
    }
}
