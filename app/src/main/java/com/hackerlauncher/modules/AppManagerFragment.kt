package com.hackerlauncher.modules

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hackerlauncher.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppManagerInfo(
    val packageName: String,
    val appName: String,
    val appSize: Long,
    val versionName: String,
    val versionCode: Long,
    val installDate: Long,
    val updateDate: Long,
    val isSystemApp: Boolean,
    var isSelected: Boolean,
    val targetSdkVersion: Int
)

class AppManagerFragment : Fragment() {

    private lateinit var recyclerViewApps: RecyclerView
    private lateinit var editTextSearch: EditText
    private lateinit var spinnerSortBy: Spinner
    private lateinit var spinnerFilter: Spinner
    private lateinit var buttonBatchUninstall: Button
    private lateinit var buttonExportList: Button
    private lateinit var textViewAppCount: TextView
    private lateinit var progressBarLoading: ProgressBar

    private val allApps = mutableListOf<AppManagerInfo>()
    private val filteredApps = mutableListOf<AppManagerInfo>()
    private lateinit var adapter: AppManagerAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_app_manager, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerViewApps = view.findViewById(R.id.recyclerViewApps)
        editTextSearch = view.findViewById(R.id.editTextSearch)
        spinnerSortBy = view.findViewById(R.id.spinnerSortBy)
        spinnerFilter = view.findViewById(R.id.spinnerFilter)
        buttonBatchUninstall = view.findViewById(R.id.buttonBatchUninstall)
        buttonExportList = view.findViewById(R.id.buttonExportList)
        textViewAppCount = view.findViewById(R.id.textViewAppCount)
        progressBarLoading = view.findViewById(R.id.progressBarLoading)

        setupRecyclerView()
        setupSpinners()
        setupSearch()
        setupButtons()
        loadApps()
    }

    private fun setupRecyclerView() {
        adapter = AppManagerAdapter(
            filteredApps,
            onAppClick = { position -> showAppDetails(position) },
            onCheckboxChanged = { position, isChecked ->
                if (position in filteredApps.indices) {
                    filteredApps[position].isSelected = isChecked
                    updateBatchButton()
                }
            }
        )
        recyclerViewApps.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewApps.adapter = adapter
    }

    private fun setupSpinners() {
        val sortOptions = arrayOf("Name A-Z", "Name Z-A", "Size (High→Low)", "Size (Low→High)", "Install Date (New)", "Install Date (Old)", "Update Date (New)", "Update Date (Old)")
        val sortAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, sortOptions)
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSortBy.adapter = sortAdapter

        val filterOptions = arrayOf("All Apps", "User Apps", "System Apps")
        val filterAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, filterOptions)
        filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFilter.adapter = filterAdapter

        val spinnerListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                applyFilterAndSort()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        spinnerSortBy.onItemSelectedListener = spinnerListener
        spinnerFilter.onItemSelectedListener = spinnerListener
    }

    private fun setupSearch() {
        editTextSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilterAndSort()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupButtons() {
        buttonBatchUninstall.setOnClickListener {
            showBatchUninstallDialog()
        }

        buttonExportList.setOnClickListener {
            exportAppList()
        }

        updateBatchButton()
    }

    private fun loadApps() {
        progressBarLoading.visibility = View.VISIBLE

        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                getInstalledApps()
            }

            allApps.clear()
            allApps.addAll(apps)
            applyFilterAndSort()
            progressBarLoading.visibility = View.GONE
        }
    }

    private fun getInstalledApps(): List<AppManagerInfo> {
        val pm = requireContext().packageManager
        val packages = pm.getInstalledPackages(
            PackageManager.GET_META_DATA or PackageManager.GET_PERMISSIONS
        )

        val result = mutableListOf<AppManagerInfo>()

        for (pkgInfo in packages) {
            try {
                val appInfo = pkgInfo.applicationInfo
                val appName = pm.getApplicationLabel(appInfo).toString()
                val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                val apkFile = File(appInfo.sourceDir)
                val appSize = if (apkFile.exists()) apkFile.length() else 0L

                val versionName = pkgInfo.versionName ?: "N/A"
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkgInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pkgInfo.versionCode.toLong()
                }

                val installDate = pkgInfo.firstInstallTime
                val updateDate = pkgInfo.lastUpdateTime

                val targetSdk = appInfo.targetSdkVersion

                result.add(
                    AppManagerInfo(
                        packageName = pkgInfo.packageName,
                        appName = appName,
                        appSize = appSize,
                        versionName = versionName,
                        versionCode = versionCode,
                        installDate = installDate,
                        updateDate = updateDate,
                        isSystemApp = isSystemApp,
                        isSelected = false,
                        targetSdkVersion = targetSdk
                    )
                )
            } catch (_: Exception) {
                // Skip packages that cause errors
            }
        }

        return result.sortedBy { it.appName.lowercase() }
    }

    private fun applyFilterAndSort() {
        val query = editTextSearch.text.toString().lowercase()

        val filtered = when (spinnerFilter.selectedItemPosition) {
            1 -> allApps.filter { !it.isSystemApp }
            2 -> allApps.filter { it.isSystemApp }
            else -> allApps.toList()
        }

        val searched = if (query.isBlank()) {
            filtered
        } else {
            filtered.filter {
                it.appName.lowercase().contains(query) ||
                it.packageName.lowercase().contains(query)
            }
        }

        val sorted = when (spinnerSortBy.selectedItemPosition) {
            0 -> searched.sortedBy { it.appName.lowercase() }
            1 -> searched.sortedByDescending { it.appName.lowercase() }
            2 -> searched.sortedByDescending { it.appSize }
            3 -> searched.sortedBy { it.appSize }
            4 -> searched.sortedByDescending { it.installDate }
            5 -> searched.sortedBy { it.installDate }
            6 -> searched.sortedByDescending { it.updateDate }
            7 -> searched.sortedBy { it.updateDate }
            else -> searched
        }

        filteredApps.clear()
        filteredApps.addAll(sorted)
        adapter.notifyDataSetChanged()
        textViewAppCount.text = "Apps: ${filteredApps.size}"
    }

    private fun showAppDetails(position: Int) {
        if (position < 0 || position >= filteredApps.size) return
        val app = filteredApps[position]

        val pm = requireContext().packageManager
        val details = StringBuilder()
        details.appendLine("Package: ${app.packageName}")
        details.appendLine("Version: ${app.versionName} (${app.versionCode})")
        details.appendLine("Target SDK: ${app.targetSdkVersion}")
        details.appendLine("Size: ${formatSize(app.appSize)}")
        details.appendLine("Type: ${if (app.isSystemApp) "System" else "User"}")
        details.appendLine("Installed: ${formatDate(app.installDate)}")
        details.appendLine("Updated: ${formatDate(app.updateDate)}")

        // Get permissions
        try {
            val pkgInfo = pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
            val permissions = pkgInfo.requestedPermissions
            if (permissions != null && permissions.isNotEmpty()) {
                details.appendLine("\nPermissions (${permissions.size}):")
                permissions.take(20).forEach { perm ->
                    details.appendLine("  • $perm")
                }
                if (permissions.size > 20) {
                    details.appendLine("  ... and ${permissions.size - 20} more")
                }
            } else {
                details.appendLine("\nPermissions: None requested")
            }
        } catch (_: Exception) {
            details.appendLine("\nPermissions: Unable to retrieve")
        }

        // Get activities
        try {
            val pkgInfo = pm.getPackageInfo(app.packageName, PackageManager.GET_ACTIVITIES)
            val activities = pkgInfo.activities
            if (activities != null && activities.isNotEmpty()) {
                details.appendLine("\nActivities (${activities.size}):")
                activities.take(10).forEach { act ->
                    details.appendLine("  • ${act.name}")
                }
                if (activities.size > 10) {
                    details.appendLine("  ... and ${activities.size - 10} more")
                }
            }
        } catch (_: Exception) {
        }

        // Get services
        try {
            val pkgInfo = pm.getPackageInfo(app.packageName, PackageManager.GET_SERVICES)
            val services = pkgInfo.services
            if (services != null && services.isNotEmpty()) {
                details.appendLine("\nServices (${services.size}):")
                services.take(10).forEach { svc ->
                    details.appendLine("  • ${svc.name}")
                }
                if (services.size > 10) {
                    details.appendLine("  ... and ${services.size - 10} more")
                }
            }
        } catch (_: Exception) {
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(app.appName)
            .setMessage(details.toString())
            .setPositiveButton("OK", null)
            .setNeutralButton("App Info") { _, _ ->
                openAppSettings(app.packageName)
            }
            .show()
    }

    private fun openAppSettings(packageName: String) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Cannot open settings: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showBatchUninstallDialog() {
        val selectedApps = filteredApps.filter { it.isSelected }
        if (selectedApps.isEmpty()) {
            Toast.makeText(requireContext(), "No apps selected", Toast.LENGTH_SHORT).show()
            return
        }

        val names = selectedApps.map {
            "${it.appName} (${it.packageName})"
        }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Uninstall ${selectedApps.size} App(s)?")
            .setItems(names, null)
            .setPositiveButton("Uninstall All") { _, _ ->
                batchUninstall(selectedApps)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun batchUninstall(apps: List<AppManagerInfo>) {
        var uninstalledCount = 0
        for (app in apps) {
            try {
                val intent = Intent(Intent.ACTION_DELETE)
                intent.data = Uri.parse("package:${app.packageName}")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                uninstalledCount++
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to uninstall ${app.appName}: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        if (uninstalledCount > 0) {
            Toast.makeText(requireContext(), "Initiated uninstall for $uninstalledCount app(s)", Toast.LENGTH_SHORT).show()
        }

        // Reset selections
        for (app in allApps) {
            app.isSelected = false
        }
        updateBatchButton()
        adapter.notifyDataSetChanged()
    }

    private fun exportAppList() {
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                writeAppListToFile()
            }

            if (success) {
                Toast.makeText(requireContext(), "App list exported to Downloads", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), "Failed to export app list", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun writeAppListToFile(): Boolean {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val fileName = "app_list_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.txt"
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)

            val writer = FileWriter(file)
            writer.write("=== HackerLauncher App List ===\n")
            writer.write("Generated: ${dateFormat.format(Date())}\n")
            writer.write("Total Apps: ${allApps.size}\n")
            writer.write("User Apps: ${allApps.count { !it.isSystemApp }}\n")
            writer.write("System Apps: ${allApps.count { it.isSystemApp }}\n")
            writer.write("\n")

            writer.write("--- User Apps ---\n")
            for (app in allApps.filter { !it.isSystemApp }.sortedBy { it.appName.lowercase() }) {
                writer.write("${app.appName} | ${app.packageName} | v${app.versionName} | ${formatSize(app.appSize)} | SDK:${app.targetSdkVersion} | Installed:${dateFormat.format(Date(app.installDate))}\n")
            }

            writer.write("\n--- System Apps ---\n")
            for (app in allApps.filter { it.isSystemApp }.sortedBy { it.appName.lowercase() }) {
                writer.write("${app.appName} | ${app.packageName} | v${app.versionName} | ${formatSize(app.appSize)} | SDK:${app.targetSdkVersion}\n")
            }

            writer.flush()
            writer.close()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    private fun updateBatchButton() {
        val selectedCount = filteredApps.count { it.isSelected }
        buttonBatchUninstall.text = if (selectedCount > 0) {
            "Uninstall ($selectedCount)"
        } else {
            "Batch Uninstall"
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
            bytes >= 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
            bytes >= 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
            else -> "$bytes B"
        }
    }

    private fun formatDate(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    inner class AppManagerAdapter(
        private val items: List<AppManagerInfo>,
        private val onAppClick: (Int) -> Unit,
        private val onCheckboxChanged: (Int, Boolean) -> Unit
    ) : RecyclerView.Adapter<AppManagerAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val checkBox: CheckBox = view.findViewById(R.id.checkBoxApp)
            val imageIcon: ImageView = view.findViewById(R.id.imageViewAppIcon)
            val textAppName: TextView = view.findViewById(R.id.textViewAppName)
            val textPackageName: TextView = view.findViewById(R.id.textViewPackageName)
            val textSize: TextView = view.findViewById(R.id.textViewAppSize)
            val textVersion: TextView = view.findViewById(R.id.textViewAppVersion)
            val textType: TextView = view.findViewById(R.id.textViewAppType)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app_manager, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]

            holder.checkBox.setOnCheckedChangeListener(null)
            holder.checkBox.isChecked = item.isSelected
            holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
                onCheckboxChanged(holder.adapterPosition, isChecked)
            }

            holder.textAppName.text = item.appName
            holder.textPackageName.text = item.packageName
            holder.textSize.text = formatSize(item.appSize)
            holder.textVersion.text = "v${item.versionName} (SDK ${item.targetSdkVersion})"
            holder.textType.text = if (item.isSystemApp) "SYSTEM" else "USER"
            holder.textType.setTextColor(
                if (item.isSystemApp) 0xFFFF8800.toInt() else 0xFF00FF00.toInt()
            )

            // Load app icon
            lifecycleScope.launch {
                val icon = withContext(Dispatchers.IO) {
                    getAppIcon(item.packageName)
                }
                holder.imageIcon.setImageDrawable(icon)
            }

            holder.itemView.setOnClickListener {
                onAppClick(holder.adapterPosition)
            }
        }

        override fun getItemCount(): Int = items.size

        private fun getAppIcon(packageName: String): Drawable? {
            return try {
                requireContext().packageManager.getApplicationIcon(packageName)
            } catch (_: Exception) {
                null
            }
        }
    }
}
