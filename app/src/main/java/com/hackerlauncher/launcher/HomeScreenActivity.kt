package com.hackerlauncher.launcher

import com.hackerlauncher.R

import com.hackerlauncher.utils.PreferencesManager

import android.animation.ObjectAnimator
import android.app.Activity
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.PagerSnapHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home Screen Activity — the default launcher.
 * Features: clock/date, pinned apps grid, dock bar, gestures, folders, page dots.
 */
class HomeScreenActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var prefsManager: PreferencesManager

    // Clock views
    private lateinit var clockTextView: TextView
    private lateinit var dateTextView: TextView

    // Pinned apps
    private lateinit var pinnedRecyclerView: RecyclerView
    private lateinit var pinnedAdapter: PinnedAppsAdapter

    // Dock
    private lateinit var dockRecyclerView: RecyclerView
    private lateinit var homeDockAdapter: HomeDockAdapter

    // Page dots
    private lateinit var dotsLayout: LinearLayout

    // Gesture
    private lateinit var gestureDetector: GestureDetector

    private var pinnedApps = mutableListOf<AppDrawerActivity.AppItem>()
    private var dockApps = mutableListOf<AppDrawerActivity.AppItem>()
    private val dockPackageNames = mutableListOf<String>()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd EEEE", Locale.getDefault())

    companion object {
        const val REQUEST_CODE_DEVICE_ADMIN = 1001
        const val REQUEST_CODE_WALLPAPER = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        prefsManager = PreferencesManager(this)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Top: Clock area
        val clockContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, 60, 0, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        clockTextView = TextView(this).apply {
            setTextColor(Color.parseColor("#00FF00"))
            typeface = Typeface.MONOSPACE
            textSize = 64f
            gravity = Gravity.CENTER
            text = "> 00:00:00"
            setShadowLayer(12f, 0f, 0f, Color.parseColor("#3300FF00"))
        }
        dateTextView = TextView(this).apply {
            setTextColor(Color.parseColor("#00AA00"))
            typeface = Typeface.MONOSPACE
            textSize = 16f
            gravity = Gravity.CENTER
            text = "> loading_date..."
            setPadding(0, 8, 0, 0)
        }
        clockContainer.addView(clockTextView)
        clockContainer.addView(dateTextView)
        rootLayout.addView(clockContainer)

        // Middle: Pinned apps grid
        pinnedRecyclerView = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            layoutManager = GridLayoutManager(this@HomeScreenActivity, 4)
            overScrollMode = RecyclerView.OVER_SCROLL_NEVER
        }
        rootLayout.addView(pinnedRecyclerView)

        // Page dots
        dotsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(dotsLayout)

        // Bottom: Dock bar
        dockRecyclerView = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutManager = LinearLayoutManager(this@HomeScreenActivity, LinearLayoutManager.HORIZONTAL, false)
            overScrollMode = RecyclerView.OVER_SCROLL_NEVER
            setPadding(16, 12, 16, 24)
            setBackgroundColor(Color.parseColor("#0A0A0A"))
        }
        rootLayout.addView(dockRecyclerView)

        setContentView(rootLayout)

        // Setup adapters
        pinnedAdapter = PinnedAppsAdapter(this, pinnedApps, ::onPinnedAppClick, ::onPinnedAppLongClick, ::onFolderClick)
        pinnedRecyclerView.adapter = pinnedAdapter

        homeDockAdapter = HomeDockAdapter(this, dockApps, ::onDockAppClick, ::onDockAppLongClick)
        dockRecyclerView.adapter = homeDockAdapter

        // Gesture detector
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent,
                velocityX: Float, velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y

                if (Math.abs(dy) > Math.abs(dx)) {
                    if (dy < -200 && Math.abs(velocityY) > 500) {
                        // Swipe up → App Drawer
                        openAppDrawer()
                        return true
                    } else if (dy > 200 && Math.abs(velocityY) > 500) {
                        // Swipe down → Notification panel
                        openNotificationPanel()
                        return true
                    }
                } else {
                    if (dx < -200 && Math.abs(velocityX) > 500) {
                        // Swipe left → Tools Panel
                        openToolsPanel()
                        return true
                    }
                }
                return false
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                lockDevice()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                // Long press empty area → wallpaper picker
                openWallpaperPicker()
            }
        })

        // Set root touch listener for gestures
        rootLayout.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        loadPinnedApps()
        loadDockApps()
        startClock()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    private fun startClock() {
        scope.launch {
            while (true) {
                val now = Date()
                clockTextView.text = "> ${timeFormat.format(now)}"
                dateTextView.text = "> ${dateFormat.format(now)}"
                delay(1000)
            }
        }
    }

    private fun loadPinnedApps() {
        val saved = prefsManager.getPinnedApps()
        pinnedApps.clear()
        val folders = prefsManager.getFolders()

        // Add folder items as special items
        for ((folderName, folderAppPkgs) in folders) {
            pinnedApps.add(
                AppDrawerActivity.AppItem(
                    label = "[ $folderName ]",
                    packageName = "folder:$folderName",
                    isHeader = false
                )
            )
        }

        // Add pinned apps (excluding those already in folders and dock)
        val dockPkgs = prefsManager.getDockApps()
        for (pkg in saved) {
            if (pkg !in dockPkgs) {
                try {
                    val appInfo = packageManager.getApplicationInfo(pkg, 0)
                    val label = appInfo.loadLabel(packageManager).toString()
                    val icon = appInfo.loadIcon(packageManager)
                    pinnedApps.add(
                        AppDrawerActivity.AppItem(
                            label = label,
                            packageName = pkg,
                            icon = icon
                        )
                    )
                } catch (e: Exception) {
                    // Package not found, skip
                }
            }
        }

        // If no pinned apps, add a placeholder
        if (pinnedApps.isEmpty()) {
            pinnedApps.add(
                AppDrawerActivity.AppItem(
                    label = "swipe_up_for_apps",
                    packageName = "",
                    isHeader = false
                )
            )
        }

        pinnedAdapter.notifyDataSetChanged()
        updatePageDots()
    }

    private fun loadDockApps() {
        dockPackageNames.clear()
        dockPackageNames.addAll(prefsManager.getDockApps())
        dockApps.clear()

        scope.launch {
            val loaded = mutableListOf<AppDrawerActivity.AppItem>()
            for (pkg in dockPackageNames) {
                try {
                    val appInfo = packageManager.getApplicationInfo(pkg, 0)
                    val label = appInfo.loadLabel(packageManager).toString()
                    val icon = appInfo.loadIcon(packageManager)
                    loaded.add(
                        AppDrawerActivity.AppItem(
                            label = label,
                            packageName = pkg,
                            icon = icon
                        )
                    )
                } catch (e: Exception) {
                    // Package not found, skip
                }
            }

            // Fill remaining dock slots up to 5
            while (loaded.size < 5) {
                loaded.add(
                    AppDrawerActivity.AppItem(
                        label = "+",
                        packageName = "",
                        isHeader = false
                    )
                )
            }

            dockApps.clear()
            dockApps.addAll(loaded.take(5))
            homeDockAdapter.notifyDataSetChanged()
        }
    }

    private fun updatePageDots() {
        dotsLayout.removeAllViews()
        val pageCount = maxOf(1, (pinnedApps.size + 15) / 16) // 16 apps per page
        for (i in 0 until pageCount) {
            val dot = View(this).apply {
                setBackgroundColor(
                    if (i == 0) Color.parseColor("#00FF00") else Color.parseColor("#004400")
                )
                layoutParams = LinearLayout.LayoutParams(16, 16).apply {
                    marginStart = 4
                    marginEnd = 4
                }
            }
            dotsLayout.addView(dot)
        }
    }

    private fun onPinnedAppClick(app: AppDrawerActivity.AppItem) {
        if (app.packageName.startsWith("folder:")) {
            onFolderClick(app.packageName.removePrefix("folder:"))
            return
        }
        if (app.packageName.isBlank()) {
            openAppDrawer()
            return
        }
        launchApp(app.packageName)
    }

    private fun onPinnedAppLongClick(app: AppDrawerActivity.AppItem): Boolean {
        if (app.packageName.startsWith("folder:")) return false
        if (app.packageName.isBlank()) return false

        val options = arrayOf("Remove from Home", "App Info", "Uninstall")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("> ${app.label}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val pinned = prefsManager.getPinnedApps().toMutableList()
                        pinned.removeAll { it == app.packageName }
                        prefsManager.setPinnedApps(pinned)
                        loadPinnedApps()
                        Toast.makeText(this, "> removed_from_home", Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
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

    private fun onFolderClick(folderName: String) {
        val intent = Intent(this, AppFolderActivity::class.java).apply {
            putExtra(AppFolderActivity.EXTRA_FOLDER_NAME, folderName)
        }
        startActivity(intent)
    }

    private fun onDockAppClick(app: AppDrawerActivity.AppItem) {
        if (app.packageName.isBlank()) {
            // Empty slot, open app drawer to pick
            openAppDrawer()
            return
        }
        launchApp(app.packageName)
    }

    private fun onDockAppLongClick(app: AppDrawerActivity.AppItem): Boolean {
        if (app.packageName.isBlank()) return false
        val options = arrayOf("Remove from Dock", "App Info")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("> ${app.label}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val dock = prefsManager.getDockApps().toMutableList()
                        dock.remove(app.packageName)
                        prefsManager.setDockApps(dock)
                        loadDockApps()
                    }
                    1 -> {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", app.packageName, null)
                        }
                        startActivity(intent)
                    }
                }
            }
            .show()
        return true
    }

    private fun launchApp(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            Toast.makeText(this, "> error: cannot_launch", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAppDrawer() {
        val intent = Intent(this, AppDrawerActivity::class.java)
        startActivity(intent)
        overridePendingTransition(android.R.anim.slide_in_left, 0)
    }

    private fun openNotificationPanel() {
        try {
            val intent = Intent("android.intent.action.SHOW_NOTIFICATION_PANEL")
            sendBroadcast(intent)
        } catch (e: Exception) {
            // Fallback: open settings
            val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
            }
            startActivity(intent)
        }
    }

    private fun openToolsPanel() {
        val intent = Intent(this, QuickSettingsActivity::class.java)
        startActivity(intent)
    }

    private fun lockDevice() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val compName = ComponentName(this, AdminReceiver::class.java)
        if (dpm.isAdminActive(compName)) {
            dpm.lockNow()
        } else {
            Toast.makeText(this, "> admin_required. enable_device_admin first.", Toast.LENGTH_LONG).show()
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "HackerLauncher needs device admin to lock screen on double tap.")
            }
            startActivityForResult(intent, REQUEST_CODE_DEVICE_ADMIN)
        }
    }

    private fun openWallpaperPicker() {
        val intent = Intent(Intent.ACTION_SET_WALLPAPER).apply {
            putExtra("com.android.wallpaper.LAUNCH_SOURCE", "launcher")
        }
        startActivityForResult(Intent.createChooser(intent, "> select_wallpaper"), REQUEST_CODE_WALLPAPER)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_DEVICE_ADMIN) {
            if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(this, "> device_admin_enabled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadPinnedApps()
        loadDockApps()
    }

    //region Pinned Apps Adapter

    class PinnedAppsAdapter(
        private val context: Context,
        private val items: List<AppDrawerActivity.AppItem>,
        private val onClick: (AppDrawerActivity.AppItem) -> Unit,
        private val onLongClick: (AppDrawerActivity.AppItem) -> Boolean,
        private val onFolderClick: (String) -> Unit
    ) : RecyclerView.Adapter<PinnedAppsAdapter.PinnedViewHolder>() {

        class PinnedViewHolder(itemView: View, val iconView: ImageView, val labelView: TextView) :
            RecyclerView.ViewHolder(itemView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PinnedViewHolder {
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
                maxLines = 1
                gravity = Gravity.CENTER
                setPadding(0, 6, 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            container.addView(iconView)
            container.addView(labelView)
            return PinnedViewHolder(container, iconView, labelView)
        }

        override fun onBindViewHolder(holder: PinnedViewHolder, position: Int) {
            val item = items[position]
            holder.labelView.text = item.label
            if (item.packageName.startsWith("folder:")) {
                holder.iconView.setImageDrawable(
                    ContextCompat.getDrawable(context, android.R.drawable.ic_menu_gallery)
                )
                holder.iconView.setColorFilter(Color.parseColor("#00FF00"))
            } else if (item.icon != null) {
                holder.iconView.setImageDrawable(item.icon)
                holder.iconView.clearColorFilter()
            } else {
                holder.iconView.setImageDrawable(
                    ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)
                )
                holder.iconView.setColorFilter(Color.parseColor("#00FF00"))
            }
            holder.itemView.setOnClickListener { onClick(item) }
            holder.itemView.setOnLongClickListener { onLongClick(item); true }
        }

        override fun getItemCount(): Int = items.size
    }

    //endregion

    //region Dock Adapter

    class HomeDockAdapter(
        private val context: Context,
        private val items: List<AppDrawerActivity.AppItem>,
        private val onClick: (AppDrawerActivity.AppItem) -> Unit,
        private val onLongClick: (AppDrawerActivity.AppItem) -> Boolean
    ) : RecyclerView.Adapter<HomeDockAdapter.DockViewHolder>() {

        class DockViewHolder(itemView: View, val iconView: ImageView, val labelView: TextView) :
            RecyclerView.ViewHolder(itemView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DockViewHolder {
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(16, 8, 16, 8)
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.WRAP_CONTENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                )
            }
            val iconView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(64, 64)
            }
            val labelView = TextView(context).apply {
                setTextColor(Color.parseColor("#00AA00"))
                typeface = Typeface.MONOSPACE
                textSize = 10f
                maxLines = 1
                gravity = Gravity.CENTER
                setPadding(0, 4, 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            container.addView(iconView)
            container.addView(labelView)
            return DockViewHolder(container, iconView, labelView)
        }

        override fun onBindViewHolder(holder: DockViewHolder, position: Int) {
            val item = items[position]
            holder.labelView.text = item.label
            if (item.icon != null) {
                holder.iconView.setImageDrawable(item.icon)
            } else {
                holder.iconView.setImageDrawable(
                    ContextCompat.getDrawable(context, android.R.drawable.ic_menu_add)
                )
                holder.iconView.setColorFilter(Color.parseColor("#00FF00"))
            }
            holder.itemView.setOnClickListener { onClick(item) }
            holder.itemView.setOnLongClickListener { onLongClick(item); true }
        }

        override fun getItemCount(): Int = items.size
    }

    //endregion

    /**
     * DeviceAdminReceiver for double-tap-to-lock functionality.
     */
    class AdminReceiver : DeviceAdminReceiver() {
        override fun onEnabled(context: Context, intent: Intent) {
            super.onEnabled(context, intent)
            Log.d("HackerLauncher", "Device admin enabled")
        }

        override fun onDisabled(context: Context, intent: Intent) {
            super.onDisabled(context, intent)
            Log.d("HackerLauncher", "Device admin disabled")
        }
    }
}
