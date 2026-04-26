package com.hackerlauncher.launcher

import com.hackerlauncher.R

import com.hackerlauncher.utils.PreferencesManager

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Folder Management Activity.
 * Shows apps within a folder, supports add/remove/rename/delete.
 */
class AppFolderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FOLDER_NAME = "extra_folder_name"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var prefsManager: PreferencesManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var folderTitleView: TextView
    private lateinit var adapter: FolderAppsAdapter

    private var folderName: String = ""
    private var folderApps = mutableListOf<AppDrawerActivity.AppItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        prefsManager = PreferencesManager(this)
        folderName = intent.getStringExtra(EXTRA_FOLDER_NAME) ?: ""

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(16, 16, 16, 16)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Top bar with folder name and actions
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 16, 0, 16)
        }

        folderTitleView = TextView(this).apply {
            text = "> folder: $folderName"
            setTextColor(Color.parseColor("#00FF00"))
            typeface = Typeface.MONOSPACE
            textSize = 20f
            setShadowLayer(8f, 0f, 0f, Color.parseColor("#3300FF00"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        topBar.addView(folderTitleView)

        // Menu button
        val menuBtn = TextView(this).apply {
            text = "[ ⋮ ]"
            setTextColor(Color.parseColor("#00FF00"))
            typeface = Typeface.MONOSPACE
            textSize = 24f
            setPadding(16, 8, 16, 8)
            setOnClickListener { showFolderMenu() }
        }
        topBar.addView(menuBtn)

        // Add button
        val addBtn = TextView(this).apply {
            text = "[ + ]"
            setTextColor(Color.parseColor("#00FF00"))
            typeface = Typeface.MONOSPACE
            textSize = 24f
            setPadding(16, 8, 16, 8)
            setOnClickListener { showAddAppDialog() }
        }
        topBar.addView(addBtn)

        rootLayout.addView(topBar)

        // Separator
        View(this).apply {
            setBackgroundColor(Color.parseColor("#003300"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                2
            )
        }.also { rootLayout.addView(it) }

        // App count
        val countView = TextView(this).apply {
            text = "> apps_in_folder: 0"
            setTextColor(Color.parseColor("#008800"))
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setPadding(0, 8, 0, 8)
        }
        rootLayout.addView(countView)

        // RecyclerView
        recyclerView = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@AppFolderActivity, 4)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        rootLayout.addView(recyclerView)

        adapter = FolderAppsAdapter(this, folderApps, ::onAppClick, ::onAppLongClick)
        recyclerView.adapter = adapter

        setContentView(rootLayout)

        if (folderName.isEmpty()) {
            showFolderPicker()
        } else {
            loadFolderApps()
        }
    }

    private fun loadFolderApps() {
        val folders = prefsManager.getFolders()
        val apps = folders[folderName] ?: emptyList()
        folderApps.clear()

        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val result = mutableListOf<AppDrawerActivity.AppItem>()
                for (app in apps) {
                    try {
                        val appInfo = packageManager.getApplicationInfo(app, 0)
                        val label = appInfo.loadLabel(packageManager).toString()
                        val icon = appInfo.loadIcon(packageManager)
                        result.add(
                            AppDrawerActivity.AppItem(
                                label = label,
                                packageName = app,
                                icon = icon
                            )
                        )
                    } catch (e: Exception) {
                        // App not found, skip
                    }
                }
                result
            }
            folderApps.clear()
            folderApps.addAll(loaded)
            adapter.notifyDataSetChanged()

            // Update count
            folderTitleView.text = "> folder: $folderName [${folderApps.size}]"
        }
    }

    private fun onAppClick(app: AppDrawerActivity.AppItem) {
        val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            Toast.makeText(this, "> error: cannot_launch", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onAppLongClick(app: AppDrawerActivity.AppItem): Boolean {
        val options = arrayOf("Remove from Folder", "App Info", "Uninstall")
        AlertDialog.Builder(this)
            .setTitle("> ${app.label}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> removeAppFromFolder(app)
                    1 -> {
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", app.packageName, null)
                        }
                        startActivity(intent)
                    }
                    2 -> {
                        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                            data = android.net.Uri.fromParts("package", app.packageName, null)
                        }
                        startActivity(intent)
                    }
                }
            }
            .show()
        return true
    }

    private fun removeAppFromFolder(app: AppDrawerActivity.AppItem) {
        val folders = prefsManager.getFolders().toMutableMap()
        val apps = folders[folderName]?.toMutableList() ?: return
        apps.removeAll { it == app.packageName }
        if (apps.isEmpty()) {
            folders.remove(folderName)
        } else {
            folders[folderName] = apps
        }
        prefsManager.setFolders(folders)
        loadFolderApps()
        Toast.makeText(this, "> removed: ${app.label}", Toast.LENGTH_SHORT).show()
    }

    private fun showAddAppDialog() {
        scope.launch {
            val allInstalled = withContext(Dispatchers.IO) {
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = packageManager.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
                resolveInfos.map { resolveInfo ->
                    AppDrawerActivity.AppItem(
                        label = resolveInfo.loadLabel(packageManager).toString(),
                        packageName = resolveInfo.activityInfo.packageName,
                        icon = resolveInfo.loadIcon(packageManager)
                    )
                }.sortedBy { it.label.lowercase() }
            }

            // Filter out apps already in folder
            val folderPkgs = folderApps.map { it.packageName }.toSet()
            val availableApps = allInstalled.filter { it.packageName !in folderPkgs }

            if (availableApps.isEmpty()) {
                Toast.makeText(this@AppFolderActivity, "> no_apps_available_to_add", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val names = availableApps.map { it.label }.toTypedArray()
            val selected = mutableListOf<AppDrawerActivity.AppItem>()

            AlertDialog.Builder(this@AppFolderActivity)
                .setTitle("> add_apps_to_folder")
                .setMultiChoiceItems(names, null) { _, which, isChecked ->
                    if (isChecked) {
                        selected.add(availableApps[which])
                    } else {
                        selected.removeAll { it.packageName == availableApps[which].packageName }
                    }
                }
                .setPositiveButton("add") { _, _ ->
                    addAppsToFolder(selected)
                }
                .setNegativeButton("cancel", null)
                .show()
        }
    }

    private fun addAppsToFolder(apps: List<AppDrawerActivity.AppItem>) {
        val folders = prefsManager.getFolders().toMutableMap()
        val existing = folders[folderName]?.toMutableList() ?: mutableListOf()
        for (app in apps) {
            if (existing.none { it == app.packageName }) {
                existing.add(app.packageName)
            }
        }
        folders[folderName] = existing
        prefsManager.setFolders(folders)
        loadFolderApps()
        Toast.makeText(this, "> added ${apps.size} app(s)", Toast.LENGTH_SHORT).show()
    }

    private fun showFolderMenu() {
        val options = arrayOf("Rename Folder", "Delete Folder", "Create New Folder")
        AlertDialog.Builder(this)
            .setTitle("> folder_options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameDialog()
                    1 -> showDeleteConfirmation()
                    2 -> showCreateFolderDialog()
                }
            }
            .show()
    }

    private fun showRenameDialog() {
        val input = EditText(this).apply {
            setTextColor(Color.parseColor("#00FF00"))
            typeface = Typeface.MONOSPACE
            setText(folderName)
            setPadding(24, 16, 24, 16)
        }
        AlertDialog.Builder(this)
            .setTitle("> rename_folder")
            .setView(input)
            .setPositiveButton("rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != folderName) {
                    val folders = prefsManager.getFolders().toMutableMap()
                    val apps = folders.remove(folderName)
                    if (apps != null) {
                        folders[newName] = apps
                        prefsManager.saveFolders(folders)
                        folderName = newName
                        folderTitleView.text = "> folder: $folderName"
                        Toast.makeText(this, "> renamed_to: $newName", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("cancel", null)
            .show()
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("> delete_folder?")
            .setMessage("Delete folder '$folderName'? Apps will not be uninstalled.")
            .setPositiveButton("delete") { _, _ ->
                val folders = prefsManager.getFolders().toMutableMap()
                folders.remove(folderName)
                prefsManager.saveFolders(folders)
                Toast.makeText(this, "> folder_deleted", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("cancel", null)
            .show()
    }

    private fun showCreateFolderDialog() {
        val input = EditText(this).apply {
            setTextColor(Color.parseColor("#00FF00"))
            typeface = Typeface.MONOSPACE
            hint = "> folder_name"
            setHintTextColor(0xFF00AA00.toInt())
            setPadding(24, 16, 24, 16)
        }
        AlertDialog.Builder(this)
            .setTitle("> create_folder")
            .setView(input)
            .setPositiveButton("create") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    val folders = prefsManager.getFolders().toMutableMap()
                    if (!folders.containsKey(newName)) {
                        folders[newName] = emptyList()
                        prefsManager.saveFolders(folders)
                        Toast.makeText(this, "> created: $newName", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "> folder_already_exists", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("cancel", null)
            .show()
    }

    private fun showFolderPicker() {
        val folders = prefsManager.getFolders()
        if (folders.isEmpty()) {
            // Create default folders
            val defaultFolders: MutableMap<String, List<String>> = mutableMapOf(
                "Games" to emptyList(),
                "Social" to emptyList(),
                "Tools" to emptyList()
            )
            prefsManager.saveFolders(defaultFolders)
            Toast.makeText(this, "> default_folders_created", Toast.LENGTH_SHORT).show()
        }

        val folderNames = prefsManager.getFolders().keys.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("> select_folder")
            .setItems(folderNames) { _, which ->
                folderName = folderNames[which]
                folderTitleView.text = "> folder: $folderName"
                loadFolderApps()
            }
            .setOnCancelListener { finish() }
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (folderName.isNotEmpty()) {
            loadFolderApps()
        }
    }

    //region FolderAppsAdapter

    class FolderAppsAdapter(
        private val context: AppFolderActivity,
        private val items: List<AppDrawerActivity.AppItem>,
        private val onClick: (AppDrawerActivity.AppItem) -> Unit,
        private val onLongClick: (AppDrawerActivity.AppItem) -> Boolean
    ) : RecyclerView.Adapter<FolderAppsAdapter.FolderAppViewHolder>() {

        class FolderAppViewHolder(
            itemView: View,
            val iconView: ImageView,
            val labelView: TextView
        ) : RecyclerView.ViewHolder(itemView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderAppViewHolder {
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(8, 16, 8, 16)
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                )
            }
            val iconView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(80, 80)
            }
            val labelView = TextView(context).apply {
                setTextColor(Color.parseColor("#00FF00"))
                typeface = Typeface.MONOSPACE
                textSize = 11f
                maxLines = 2
                gravity = Gravity.CENTER
                setPadding(0, 6, 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            container.addView(iconView)
            container.addView(labelView)
            return FolderAppViewHolder(container, iconView, labelView)
        }

        override fun onBindViewHolder(holder: FolderAppViewHolder, position: Int) {
            val item = items[position]
            holder.labelView.text = item.label
            if (item.icon != null) {
                holder.iconView.setImageDrawable(item.icon)
            } else {
                holder.iconView.setImageResource(android.R.drawable.sym_def_app_icon)
                holder.iconView.setColorFilter(Color.parseColor("#00FF00"))
            }
            holder.itemView.setOnClickListener { onClick(item) }
            holder.itemView.setOnLongClickListener { onLongClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }

    //endregion
}
