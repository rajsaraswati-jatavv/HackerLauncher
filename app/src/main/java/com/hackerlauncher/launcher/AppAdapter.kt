package com.hackerlauncher.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

// ─── App Adapter ──────────────────────────────────────────────────────────────

class AppAdapter(
    private val context: Context,
    private var viewMode: ViewMode = ViewMode.GRID,
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: (AppInfo, View) -> Unit,
    private val onAppSelected: (AppInfo, Boolean) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), Filterable {

    companion object {
        private const val TYPE_GRID = 0
        private const val TYPE_LIST = 1

        fun fromResolveInfo(context: Context, resolveInfos: List<ResolveInfo>): List<AppInfo> {
            val pm = context.packageManager
            return resolveInfos.mapNotNull { info ->
                try {
                    val activityInfo = info.activityInfo
                    AppInfo(
                        packageName = activityInfo.packageName,
                        activityName = activityInfo.name,
                        label = info.loadLabel(pm).toString(),
                        icon = info.loadIcon(pm),
                        isSystemApp = (activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    )
                } catch (_: Exception) { null }
            }
        }

        fun getInstalledApps(context: Context): List<AppInfo> {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(intent, PackageManager.GET_RESOLVED_FILTER)
            return fromResolveInfo(context, resolveInfos)
        }
    }

    private var apps = listOf<AppInfo>()
    private var filteredApps = listOf<AppInfo>()
    private var selectionMode = false
    private val selectedApps = mutableSetOf<String>()
    private val iconPackManager = IconPackManager.getInstance(context)
    private val themeManager = ThemeManager.getInstance(context)

    // ─── Set Apps ──────────────────────────────────────────────────────────

    fun setApps(newApps: List<AppInfo>) {
        val diffResult = DiffUtil.calculateDiff(AppDiffCallback(apps, newApps))
        apps = newApps
        filteredApps = newApps
        diffResult.dispatchUpdatesTo(this)
    }

    fun setViewMode(mode: ViewMode) {
        if (viewMode != mode) {
            viewMode = mode
            notifyDataSetChanged()
        }
    }

    // ─── Selection Mode ───────────────────────────────────────────────────

    fun setSelectionMode(enabled: Boolean) {
        selectionMode = enabled
        if (!enabled) {
            selectedApps.clear()
            apps.forEach { it.isSelected = false }
        }
        notifyDataSetChanged()
    }

    fun isSelected(packageName: String): Boolean = packageName in selectedApps

    fun getSelectedApps(): List<AppInfo> = apps.filter { it.isSelected }

    // ─── Notification Badges ───────────────────────────────────────────────

    fun updateBadge(packageName: String, count: Int) {
        val index = filteredApps.indexOfFirst { it.packageName == packageName }
        if (index >= 0) {
            filteredApps[index].notificationCount = count
            notifyItemChanged(index)
        }
    }

    // ─── Adapter Overrides ─────────────────────────────────────────────────

    override fun getItemViewType(position: Int): Int {
        return if (viewMode == ViewMode.GRID) TYPE_GRID else TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_GRID -> {
                val view = createGridView(inflater, parent)
                AppGridViewHolder(view)
            }
            else -> {
                val view = createListView(inflater, parent)
                AppListViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val app = filteredApps[position]
        when (holder) {
            is AppGridViewHolder -> bindGridView(holder, app)
            is AppListViewHolder -> bindListView(holder, app)
        }
    }

    override fun getItemCount() = filteredApps.size

    // ─── Grid View ─────────────────────────────────────────────────────────

    private fun createGridView(inflater: LayoutInflater, parent: ViewGroup): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(8, 12, 8, 12)
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
        }

        // Icon with badge overlay
        val iconFrame = android.widget.FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val icon = ImageView(context).apply {
            id = R.id.app_icon
            layoutParams = android.widget.FrameLayout.LayoutParams(96, 96)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        iconFrame.addView(icon)

        val badge = TextView(context).apply {
            id = R.id.notification_badge
            text = "0"
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#00FF00"))
            textSize = 9f
            typeface = Typeface.MONOSPACE
            gravity = android.view.Gravity.CENTER
            visibility = View.GONE
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.END or android.view.Gravity.TOP
                marginEnd = 0
            }
            setPadding(4, 2, 4, 2)
        }
        iconFrame.addView(badge)
        container.addView(iconFrame)

        // Checkbox (hidden by default)
        val checkbox = CheckBox(context).apply {
            id = R.id.app_checkbox
            visibility = View.GONE
            setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#00FF00")))
        }
        container.addView(checkbox)

        // Label
        val label = TextView(context).apply {
            id = R.id.app_label
            setTextColor(Color.parseColor("#00FF00"))
            textSize = 11f
            typeface = Typeface.MONOSPACE
            gravity = android.view.Gravity.CENTER
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(2, 4, 2, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(label)

        return container
    }

    private fun bindGridView(holder: AppGridViewHolder, app: AppInfo) {
        // Try icon pack first, then default
        val packIcon = iconPackManager.getIconForApp(app.packageName, app.activityName)
        holder.icon.setImageDrawable(packIcon ?: app.icon)

        holder.label.text = app.label

        // Badge
        if (app.notificationCount > 0) {
            holder.badge.text = if (app.notificationCount > 99) "99+" else app.notificationCount.toString()
            holder.badge.visibility = View.VISIBLE
        } else {
            holder.badge.visibility = View.GONE
        }

        // Checkbox
        if (selectionMode) {
            holder.checkbox.visibility = View.VISIBLE
            holder.checkbox.setOnCheckedChangeListener(null)
            holder.checkbox.isChecked = app.isSelected
            holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
                app.isSelected = isChecked
                if (isChecked) selectedApps.add(app.packageName) else selectedApps.remove(app.packageName)
                onAppSelected(app, isChecked)
            }
        } else {
            holder.checkbox.visibility = View.GONE
        }

        // Click
        holder.itemView.setOnClickListener {
            if (selectionMode) {
                holder.checkbox.isChecked = !holder.checkbox.isChecked
            } else {
                onAppClick(app)
            }
        }

        // Long click
        holder.itemView.setOnLongClickListener {
            onAppLongClick(app, holder.itemView)
            true
        }
    }

    // ─── List View ─────────────────────────────────────────────────────────

    private fun createListView(inflater: LayoutInflater, parent: ViewGroup): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(16, 8, 16, 8)
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
        }

        val icon = ImageView(context).apply {
            id = R.id.app_icon
            layoutParams = LinearLayout.LayoutParams(64, 64)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        container.addView(icon)

        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(16, 0, 8, 0)
        }

        val name = TextView(context).apply {
            id = R.id.app_name
            setTextColor(Color.parseColor("#00FF00"))
            textSize = 14f
            typeface = Typeface.MONOSPACE
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        textContainer.addView(name)

        val pkg = TextView(context).apply {
            id = R.id.app_package
            setTextColor(Color.GRAY)
            textSize = 11f
            typeface = Typeface.MONOSPACE
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        textContainer.addView(pkg)

        val size = TextView(context).apply {
            id = R.id.app_size
            setTextColor(Color.parseColor("#00AA00"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
        }
        textContainer.addView(size)

        container.addView(textContainer)

        // Badge
        val badge = TextView(context).apply {
            id = R.id.notification_badge
            text = "0"
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#00FF00"))
            textSize = 9f
            typeface = Typeface.MONOSPACE
            gravity = android.view.Gravity.CENTER
            visibility = View.GONE
            setPadding(4, 2, 4, 2)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(badge)

        // Checkbox
        val checkbox = CheckBox(context).apply {
            id = R.id.app_checkbox
            visibility = View.GONE
            setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#00FF00")))
        }
        container.addView(checkbox)

        return container
    }

    private fun bindListView(holder: AppListViewHolder, app: AppInfo) {
        val packIcon = iconPackManager.getIconForApp(app.packageName, app.activityName)
        holder.icon.setImageDrawable(packIcon ?: app.icon)

        holder.name.text = app.label
        holder.pkg.text = app.packageName

        // App size
        try {
            val appInfo = context.packageManager.getApplicationInfo(app.packageName, 0)
            val size = java.io.File(appInfo.sourceDir).length()
            holder.size.text = formatSize(size)
        } catch (_: Exception) {
            holder.size.text = ""
        }

        // Badge
        if (app.notificationCount > 0) {
            holder.badge.text = if (app.notificationCount > 99) "99+" else app.notificationCount.toString()
            holder.badge.visibility = View.VISIBLE
        } else {
            holder.badge.visibility = View.GONE
        }

        // Checkbox
        if (selectionMode) {
            holder.checkbox.visibility = View.VISIBLE
            holder.checkbox.setOnCheckedChangeListener(null)
            holder.checkbox.isChecked = app.isSelected
            holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
                app.isSelected = isChecked
                if (isChecked) selectedApps.add(app.packageName) else selectedApps.remove(app.packageName)
                onAppSelected(app, isChecked)
            }
        } else {
            holder.checkbox.visibility = View.GONE
        }

        // Click
        holder.itemView.setOnClickListener {
            if (selectionMode) {
                holder.checkbox.isChecked = !holder.checkbox.isChecked
            } else {
                onAppClick(app)
            }
        }

        // Long click
        holder.itemView.setOnLongClickListener {
            onAppLongClick(app, holder.itemView)
            true
        }
    }

    // ─── Filter ────────────────────────────────────────────────────────────

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val query = constraint?.toString()?.lowercase()?.trim() ?: ""
                val results = if (query.isEmpty()) {
                    apps
                } else {
                    apps.filter { app ->
                        app.label.lowercase().contains(query) ||
                        app.packageName.lowercase().contains(query)
                    }
                }
                return FilterResults().apply {
                    values = results
                    count = results.size
                }
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredApps = (results?.values as? List<AppInfo>) ?: apps
                notifyDataSetChanged()
            }
        }
    }

    fun filter(query: String) {
        getFilter().filter(query)
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private fun formatSize(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIndex = 0
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        return String.format("%.1f %s", size, units[unitIndex])
    }

    // ─── Launch App ────────────────────────────────────────────────────────

    fun launchApp(app: AppInfo) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                component = app.getComponentName()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback: try launch by package
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent != null) context.startActivity(intent)
            } catch (_: Exception) { }
        }
    }

}
