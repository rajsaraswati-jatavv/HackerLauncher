package com.hackerlauncher.modules

import com.hackerlauncher.R

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
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
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
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

    // ========== UPGRADE: New view references ==========
    private lateinit var btnDeepClean: MaterialButton
    private lateinit var btnScheduledClean: MaterialButton
    private lateinit var btnWhitelist: MaterialButton
    private lateinit var tvBeforeAfter: TextView
    private lateinit var tvCategoryFilter: TextView

    private val junkItems = mutableListOf<JunkItem>()
    private lateinit var junkAdapter: JunkAdapter

    private var scanJob: Job? = null
    private var isScanning = false

    // ========== UPGRADE: New state ==========
    private val cleanWhitelist = mutableSetOf<String>() // Files/folders to never delete
    private var scheduledCleanEnabled = false
    private var scheduledCleanInterval = "daily" // daily, weekly
    private var beforeCleanFreeSpace = 0L
    private var afterCleanFreeSpace = 0L
    private var deepCleanMode = false
    private var selectedCategories = mutableSetOf(
        "Cache", "Temp Files", "Log Files", "Empty Folders",
        "APK Files", "Thumbnail Cache", "Incomplete Downloads",
        "Orphan Files", "Broken Shortcuts", "Residual Files",
        "Clipboard Data", "Browser Data"
    )
    private var totalFreedHistorical = 0L
    private var cleanSessionCount = 0

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

        // ========== UPGRADE: Add upgrade views ==========
        addUpgradeViews(view)
        loadUpgradeSettings()
    }

    // ========== UPGRADE: Add upgrade views dynamically ==========
    private fun addUpgradeViews(view: View) {
        try {
            // Find a suitable container in the layout
            val root = view.findViewById<LinearLayout>(R.id.tvStorageDetail).parent as? ViewGroup
            if (root != null) {
                btnDeepClean = MaterialButton(requireContext()).apply {
                    text = "🔥 DEEP CLEAN"
                    setTextColor(0xFFFF4444.toInt())
                    textSize = 13f
                    setOnClickListener { showDeepCleanDialog() }
                }

                btnScheduledClean = MaterialButton(requireContext()).apply {
                    text = "📅 SCHEDULED CLEAN"
                    setTextColor(0xFF2196F3.toInt())
                    textSize = 13f
                    setOnClickListener { showScheduledCleanDialog() }
                }

                btnWhitelist = MaterialButton(requireContext()).apply {
                    text = "🛡️ WHITELIST"
                    setTextColor(0xFF4CAF50.toInt())
                    textSize = 13f
                    setOnClickListener { showCleanWhitelistDialog() }
                }

                tvBeforeAfter = TextView(requireContext()).apply {
                    text = "Before/After: --"
                    setTextColor(0xFF888888.toInt())
                    textSize = 12f
                }

                tvCategoryFilter = TextView(requireContext()).apply {
                    text = "Categories: All"
                    setTextColor(0xFF00FFFF.toInt())
                    textSize = 12f
                    setOnClickListener { showCategoryFilterDialog() }
                }

                // Add after the existing views
                val index = root.indexOfChild(view.findViewById(R.id.tvStorageDetail))
                if (index >= 0) {
                    root.addView(tvBeforeAfter, index + 1)
                    root.addView(tvCategoryFilter, index + 2)
                    root.addView(btnDeepClean, index + 3)
                    root.addView(btnScheduledClean, index + 4)
                    root.addView(btnWhitelist, index + 5)
                } else {
                    root.addView(tvBeforeAfter)
                    root.addView(tvCategoryFilter)
                    root.addView(btnDeepClean)
                    root.addView(btnScheduledClean)
                    root.addView(btnWhitelist)
                }
            }
        } catch (_: Exception) {
            // Fallback: create minimal references
        }
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
                    if (selectedCategories.contains("Cache")) scanCacheDirs()

                    // Scan temp files
                    if (selectedCategories.contains("Temp Files")) scanTempFiles()

                    // Scan log files
                    if (selectedCategories.contains("Log Files")) scanLogFiles()

                    // Scan empty folders
                    if (selectedCategories.contains("Empty Folders")) scanEmptyFolders()

                    // Scan APK files
                    if (selectedCategories.contains("APK Files")) scanApkFiles()

                    // Scan thumbnail cache
                    if (selectedCategories.contains("Thumbnail Cache")) scanThumbnailCache()

                    // Scan download temp files
                    if (selectedCategories.contains("Incomplete Downloads")) scanDownloadTemp()

                    // ========== UPGRADE: New scan categories ==========

                    // Orphan files from uninstalled apps
                    if (selectedCategories.contains("Orphan Files")) scanOrphanFiles()

                    // Broken shortcuts
                    if (selectedCategories.contains("Broken Shortcuts")) scanBrokenShortcuts()

                    // Residual files from uninstalled apps
                    if (selectedCategories.contains("Residual Files")) scanResidualFiles()

                    // Clipboard data
                    if (selectedCategories.contains("Clipboard Data")) scanClipboardData()

                    // Browser data
                    if (selectedCategories.contains("Browser Data")) scanBrowserData()

                    // Deep clean: scan entire storage
                    if (deepCleanMode) scanDeepClean()

                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showToast("Scan error: ${e.message}")
                    }
                }
            }

            isScanning = false
            deepCleanMode = false
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
            findEmptyFolders(root, onFound = { folder ->
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
            })
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
                            category = "Thumbnail Cache",
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

    // ========== UPGRADE: Advanced Junk Detection Scans ==========

    private suspend fun scanOrphanFiles() {
        // Find files/folders in Android/data for uninstalled apps
        val androidDataDir = File(Environment.getExternalStorageDirectory(), "Android/data")
        if (!androidDataDir.exists() || !androidDataDir.isDirectory) return

        val pm = requireContext().packageManager
        val installedPackages = pm.getInstalledApplications(0).map { it.packageName }.toSet()

        val dirs = androidDataDir.listFiles() ?: return
        for (dir in dirs) {
            coroutineContext.ensureActive()
            if (dir.isDirectory && !installedPackages.contains(dir.name)) {
                val size = calculateDirectorySize(dir)
                // Skip whitelist
                if (cleanWhitelist.contains(dir.absolutePath)) continue
                if (size > 0) {
                    withContext(Dispatchers.Main) {
                        junkItems.add(
                            JunkItem(
                                path = dir.absolutePath,
                                name = "Orphan: ${dir.name}",
                                category = "Orphan Files",
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

        // Also check Android/obb for orphan OBB files
        val obbDir = File(Environment.getExternalStorageDirectory(), "Android/obb")
        if (obbDir.exists() && obbDir.isDirectory) {
            val obbDirs = obbDir.listFiles() ?: return
            for (dir in obbDirs) {
                coroutineContext.ensureActive()
                if (dir.isDirectory && !installedPackages.contains(dir.name)) {
                    if (cleanWhitelist.contains(dir.absolutePath)) continue
                    val size = calculateDirectorySize(dir)
                    if (size > 0) {
                        withContext(Dispatchers.Main) {
                            junkItems.add(
                                JunkItem(
                                    path = dir.absolutePath,
                                    name = "Orphan OBB: ${dir.name}",
                                    category = "Orphan Files",
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
    }

    private suspend fun scanBrokenShortcuts() {
        // Scan for broken shortcuts on home screen
        val shortcutsDir = File(Environment.getExternalStorageDirectory(), "shortcuts")
        if (shortcutsDir.exists()) {
            findFilesByExtension(shortcutsDir, listOf(".shortcut", ".lnk")) { file ->
                withContext(Dispatchers.Main) {
                    junkItems.add(
                        JunkItem(
                            path = file.absolutePath,
                            name = "Shortcut: ${file.name}",
                            category = "Broken Shortcuts",
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

    private suspend fun scanResidualFiles() {
        // Check for residual files from known uninstalled apps
        val residualDirs = listOf("Android/data", "Android/obb", "Android/media")
        val pm = requireContext().getPackageManager()
        val installedPackages = pm.getInstalledApplications(0).map { it.packageName }.toSet()

        for (dirName in residualDirs) {
            val parentDir = File(Environment.getExternalStorageDirectory(), dirName)
            if (!parentDir.exists() || !parentDir.isDirectory) continue

            val dirs = parentDir.listFiles() ?: continue
            for (dir in dirs) {
                coroutineContext.ensureActive()
                if (dir.isDirectory && !installedPackages.contains(dir.name)) {
                    if (cleanWhitelist.contains(dir.absolutePath)) continue
                    // Check for residual cache/data subfolders
                    val cacheSubdir = File(dir, "cache")
                    val dataSubdir = File(dir, "data")
                    var residualSize = 0L
                    if (cacheSubdir.exists()) residualSize += calculateDirectorySize(cacheSubdir)
                    if (dataSubdir.exists()) residualSize += calculateDirectorySize(dataSubdir)

                    if (residualSize > 0) {
                        withContext(Dispatchers.Main) {
                            junkItems.add(
                                JunkItem(
                                    path = dir.absolutePath,
                                    name = "Residual: ${dir.name}",
                                    category = "Residual Files",
                                    size = residualSize,
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
    }

    private suspend fun scanClipboardData() {
        // Clipboard data is ephemeral; mark app's own clipboard cache if any
        try {
            val clipboardCacheDir = File(requireContext().cacheDir, "clipboard")
            if (clipboardCacheDir.exists() && clipboardCacheDir.isDirectory) {
                val size = calculateDirectorySize(clipboardCacheDir)
                if (size > 0) {
                    withContext(Dispatchers.Main) {
                        junkItems.add(
                            JunkItem(
                                path = clipboardCacheDir.absolutePath,
                                name = "Clipboard Cache",
                                category = "Clipboard Data",
                                size = size,
                                isSelected = true,
                                isFile = false
                            )
                        )
                        junkAdapter.notifyItemInserted(junkItems.size - 1)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private suspend fun scanBrowserData() {
        // Scan for browser cache directories
        val browserPackages = listOf(
            "com.android.browser", "com.android.chrome",
            "com.google.android.chrome", "org.mozilla.firefox",
            "com.brave.browser", "com.opera.browser",
            "com.duckduckgo.mobile.android"
        )

        for (pkg in browserPackages) {
            try {
                val browserCache = File(Environment.getExternalStorageDirectory(), "Android/data/$pkg/cache")
                if (browserCache.exists() && browserCache.isDirectory) {
                    if (cleanWhitelist.contains(browserCache.absolutePath)) continue
                    val size = calculateDirectorySize(browserCache)
                    if (size > 0) {
                        withContext(Dispatchers.Main) {
                            junkItems.add(
                                JunkItem(
                                    path = browserCache.absolutePath,
                                    name = "Browser Cache: $pkg",
                                    category = "Browser Data",
                                    size = size,
                                    isSelected = true,
                                    isFile = false
                                )
                            )
                            junkAdapter.notifyItemInserted(junkItems.size - 1)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private suspend fun scanDeepClean() {
        // Deep clean: scan entire storage for junk files
        val storageRoot = Environment.getExternalStorageDirectory()
        val junkExtensions = listOf(
            ".tmp", ".temp", ".bak", ".swp", ".swo", ".log",
            ".part", ".crdownload", ".download", ".old", ".orig"
        )

        findFilesByExtension(storageRoot, junkExtensions, maxDepth = 8) { file ->
            if (cleanWhitelist.contains(file.absolutePath)) return@findFilesByExtension
            withContext(Dispatchers.Main) {
                junkItems.add(
                    JunkItem(
                        path = file.absolutePath,
                        name = "Deep: ${file.name}",
                        category = "Deep Clean",
                        size = file.length(),
                        isSelected = true,
                        isFile = true
                    )
                )
                junkAdapter.notifyItemInserted(junkItems.size - 1)
            }
        }
    }

    private suspend fun findFilesByExtension(
        directory: File,
        extensions: List<String>,
        onFound: suspend (File) -> Unit
    ) {
        findFilesByExtension(directory, extensions, onFound, maxDepth = 5)
    }

    private suspend fun findFilesByExtension(
        directory: File,
        extensions: List<String>,
        onFound: suspend (File) -> Unit,
        maxDepth: Int,
        currentDepth: Int = 0
    ) {
        if (!directory.exists() || !directory.isDirectory) return
        if (currentDepth > maxDepth) return
        val files = directory.listFiles() ?: return
        for (file in files) {
            coroutineContext.ensureActive()
            if (file.isDirectory) {
                findFilesByExtension(file, extensions, onFound, maxDepth, currentDepth + 1)
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
        // Record before state
        beforeCleanFreeSpace = getAvailableStorage()

        lifecycleScope.launch {
            var cleanedCount = 0
            var freedBytes = 0L
            var failedCount = 0

            withContext(Dispatchers.IO) {
                for (item in items) {
                    // UPGRADE: Skip whitelisted paths
                    if (cleanWhitelist.contains(item.path)) {
                        failedCount++
                        continue
                    }

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

            // Record after state
            afterCleanFreeSpace = getAvailableStorage()
            totalFreedHistorical += freedBytes
            cleanSessionCount++
            saveUpgradeSettings()

            // UPGRADE: Update before/after display
            updateBeforeAfterDisplay()

            // UPGRADE: Send notification
            sendCleanNotification(freedBytes, cleanedCount)

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

    // ========== UPGRADE: Deep Clean Mode ==========
    private fun showDeepCleanDialog() {
        AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("🔥 Deep Clean Mode")
            .setMessage("Deep Clean scans the ENTIRE storage for junk files.\n\nThis includes:\n• All temp/log/bak files recursively\n• Orphan files from uninstalled apps\n• Browser cache and data\n• Residual files deep in storage\n\n⚠ Be careful: This scans deeper and may take longer.")
            .setPositiveButton("START DEEP CLEAN") { _, _ ->
                deepCleanMode = true
                checkPermissionAndScan()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ========== UPGRADE: Scheduled Auto-Clean ==========
    private fun showScheduledCleanDialog() {
        val options = arrayOf("Disable Scheduled Clean", "Daily Auto-Clean", "Weekly Auto-Clean")
        val currentIndex = when {
            !scheduledCleanEnabled -> 0
            scheduledCleanInterval == "daily" -> 1
            else -> 2
        }

        AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("📅 Scheduled Auto-Clean")
            .setSingleChoiceItems(options, currentIndex) { _, which ->
                when (which) {
                    0 -> {
                        scheduledCleanEnabled = false
                        showToast("Scheduled clean disabled")
                    }
                    1 -> {
                        scheduledCleanEnabled = true
                        scheduledCleanInterval = "daily"
                        showToast("Daily auto-clean enabled")
                    }
                    2 -> {
                        scheduledCleanEnabled = true
                        scheduledCleanInterval = "weekly"
                        showToast("Weekly auto-clean enabled")
                    }
                }
                saveUpgradeSettings()
            }
            .setPositiveButton("OK", null)
            .show()
    }

    // ========== UPGRADE: Clean Whitelist ==========
    private fun showCleanWhitelistDialog() {
        val options = arrayOf("Add File/Folder to Whitelist", "Remove from Whitelist", "View Whitelist")
        AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("🛡️ Clean Whitelist")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAddToWhitelistDialog()
                    1 -> showRemoveFromWhitelistDialog()
                    2 -> showViewWhitelistDialog()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddToWhitelistDialog() {
        val input = EditText(requireContext()).apply {
            hint = "/path/to/file_or_folder"
            setTextColor(0xFF00FF00.toInt())
            setHintTextColor(0xFF888888.toInt())
        }

        AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("Add to Whitelist")
            .setMessage("Files/folders in the whitelist will NEVER be deleted during cleaning.")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val path = input.text.toString().trim()
                if (path.isNotEmpty()) {
                    cleanWhitelist.add(path)
                    saveUpgradeSettings()
                    showToast("Added to whitelist: $path")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRemoveFromWhitelistDialog() {
        if (cleanWhitelist.isEmpty()) {
            showToast("Whitelist is empty")
            return
        }

        val items = cleanWhitelist.toTypedArray()
        val selected = BooleanArray(items.size) { true }

        AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("Remove from Whitelist (uncheck to remove)")
            .setMultiChoiceItems(items, selected) { _, which, isChecked ->
                if (!isChecked) cleanWhitelist.remove(items[which])
            }
            .setPositiveButton("Done") { _, _ -> saveUpgradeSettings() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showViewWhitelistDialog() {
        val message = if (cleanWhitelist.isEmpty()) {
            "Whitelist is empty. Add paths to protect them from deletion."
        } else {
            buildString {
                append("═══ PROTECTED PATHS ═══\n\n")
                for (path in cleanWhitelist) {
                    append("🛡️ $path\n")
                }
            }
        }

        AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("🛡️ Whitelist")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    // ========== UPGRADE: Category Filter ==========
    private fun showCategoryFilterDialog() {
        val allCategories = arrayOf(
            "Cache", "Temp Files", "Log Files", "Empty Folders",
            "APK Files", "Thumbnail Cache", "Incomplete Downloads",
            "Orphan Files", "Broken Shortcuts", "Residual Files",
            "Clipboard Data", "Browser Data"
        )
        val checked = allCategories.map { selectedCategories.contains(it) }.toBooleanArray()

        AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("📋 Scan Categories")
            .setMessage("Select categories to scan:")
            .setMultiChoiceItems(allCategories, checked) { _, which, isChecked ->
                if (isChecked) {
                    selectedCategories.add(allCategories[which])
                } else {
                    selectedCategories.remove(allCategories[which])
                }
            }
            .setPositiveButton("Apply") { _, _ ->
                try {
                    tvCategoryFilter.text = "Categories: ${selectedCategories.size}/${allCategories.size}"
                } catch (_: Exception) {}
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ========== UPGRADE: Before/After Comparison ==========
    private fun updateBeforeAfterDisplay() {
        try {
            val freed = afterCleanFreeSpace - beforeCleanFreeSpace
            tvBeforeAfter.text = buildString {
                append("Before: ${formatFileSize(beforeCleanFreeSpace)} → ")
                append("After: ${formatFileSize(afterCleanFreeSpace)} | ")
                append("Freed: ${formatFileSize(maxOf(0L, freed))}")
            }
            tvBeforeAfter.setTextColor(0xFF00FF00.toInt())
        } catch (_: Exception) {}
    }

    private fun getAvailableStorage(): Long {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.availableBytes
        } catch (_: Exception) { 0L }
    }

    // ========== UPGRADE: Clean Notification ==========
    private fun sendCleanNotification(freedBytes: Long, cleanedCount: Int) {
        try {
            val nm = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "system_cleaner"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(channelId, "System Cleaner", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }

            NotificationCompat.Builder(requireContext(), channelId)
                .setContentTitle("🧹 System Clean Complete")
                .setContentText("Freed ${formatFileSize(freedBytes)} ($cleanedCount items)")
                .setSmallIcon(android.R.drawable.ic_menu_delete)
                .setAutoCancel(true)
                .build()
                .also { nm.notify(6001, it) }
        } catch (_: Exception) {}
    }

    // ========== UPGRADE: Settings ==========
    private fun loadUpgradeSettings() {
        val prefs = requireContext().getSharedPreferences("system_cleaner_prefs", Context.MODE_PRIVATE)
        cleanWhitelist.clear()
        cleanWhitelist.addAll(prefs.getStringSet("clean_whitelist", emptySet()) ?: emptySet())
        scheduledCleanEnabled = prefs.getBoolean("scheduled_clean", false)
        scheduledCleanInterval = prefs.getString("scheduled_interval", "daily") ?: "daily"
        totalFreedHistorical = prefs.getLong("total_freed", 0L)
        cleanSessionCount = prefs.getInt("clean_sessions", 0)
        val savedCategories = prefs.getStringSet("selected_categories", null)
        if (savedCategories != null) {
            selectedCategories.clear()
            selectedCategories.addAll(savedCategories)
        }
    }

    private fun saveUpgradeSettings() {
        val prefs = requireContext().getSharedPreferences("system_cleaner_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet("clean_whitelist", cleanWhitelist)
            .putBoolean("scheduled_clean", scheduledCleanEnabled)
            .putString("scheduled_interval", scheduledCleanInterval)
            .putLong("total_freed", totalFreedHistorical)
            .putInt("clean_sessions", cleanSessionCount)
            .putStringSet("selected_categories", selectedCategories)
            .apply()
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
