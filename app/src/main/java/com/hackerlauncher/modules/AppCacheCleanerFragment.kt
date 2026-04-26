package com.hackerlauncher.modules

import com.hackerlauncher.R

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class AppCacheCleanerFragment : Fragment() {

    private lateinit var recyclerViewApps: RecyclerView
    private lateinit var textViewTotalCache: TextView
    private lateinit var buttonCleanAll: MaterialButton
    private lateinit var buttonCleanSelected: MaterialButton
    private lateinit var progressBarScan: ProgressBar
    private lateinit var checkBoxSelectAll: CheckBox
    private lateinit var editTextSearch: EditText

    private val appCacheList = mutableListOf<AppCacheInfo>()
    private val filteredList = mutableListOf<AppCacheInfo>()
    private lateinit var appCacheAdapter: AppCacheAdapter

    private var scanJob: Job? = null
    private var searchQuery = ""

    data class AppCacheInfo(
        val packageName: String,
        val appName: String,
        val cacheSize: Long,
        val dataSize: Long,
        val appIcon: Drawable?,
        var isSelected: Boolean = false
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

        // Auto-scan on open
        startScan()
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

                            withContext(Dispatchers.Main) {
                                applyFilter()
                                updateTotalCache()
                            }
                        } catch (e: Exception) {
                            // Skip apps we can't read
                        }
                    }

                    // Sort by cache size descending
                    appCacheList.sortByDescending { it.cacheSize }

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
                        }
                    } catch (e: Exception) {
                        failedCount++
                    }
                }
            }

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
