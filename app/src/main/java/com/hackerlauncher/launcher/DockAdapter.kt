package com.hackerlauncher.launcher

import com.hackerlauncher.R
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray

// ─── Dock App Data ────────────────────────────────────────────────────────────

data class DockApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: Drawable,
    var notificationCount: Int = 0
)

// ─── Dock ViewHolder ──────────────────────────────────────────────────────────

class DockViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val iconView: ImageView = itemView.findViewById(R.id.dock_icon)
    val badgeView: TextView = itemView.findViewById(R.id.dock_badge)
}

// ─── Dock Adapter ─────────────────────────────────────────────────────────────

class DockAdapter(
    private val context: Context,
    private val onAppClick: (DockApp) -> Unit,
    private val onAppLongClick: (DockApp) -> Boolean,
    private val onReorder: (List<DockApp>) -> Unit
) : RecyclerView.Adapter<DockViewHolder>() {

    companion object {
        private const val PREFS_NAME = "dock_prefs"
        private const val KEY_DOCK_APPS = "dock_apps"
        private const val MAX_DOCK_ITEMS = 5
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var dockApps = mutableListOf<DockApp>()
    private val iconPackManager = IconPackManager.getInstance(context)

    // ─── Drag & Drop ───────────────────────────────────────────────────────

    private val itemTouchHelper by lazy {
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
        ) {
            override fun onMove(
                rv: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) return false
                if (fromPos < toPos) {
                    for (i in fromPos until toPos) {
                        dockApps[i] = dockApps[i + 1].also { dockApps[i + 1] = dockApps[i] }
                    }
                } else {
                    for (i in fromPos downTo toPos + 1) {
                        dockApps[i] = dockApps[i - 1].also { dockApps[i - 1] = dockApps[i] }
                    }
                }
                notifyItemMoved(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Swipe to remove from dock
                val pos = viewHolder.adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    removeApp(pos)
                }
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.alpha = 0.7f
                    viewHolder?.itemView?.scaleX = 1.1f
                    viewHolder?.itemView?.scaleY = 1.1f
                }
            }

            override fun clearView(rv: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(rv, viewHolder)
                viewHolder.itemView.alpha = 1f
                viewHolder.itemView.scaleX = 1f
                viewHolder.itemView.scaleY = 1f
                onReorder(dockApps.toList())
                saveDockApps()
            }

            override fun isLongPressDragEnabled(): Boolean = true
        })
    }

    fun attachToRecyclerView(recyclerView: RecyclerView) {
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    // ─── Set / Get Apps ────────────────────────────────────────────────────

    fun setDockApps(apps: List<DockApp>) {
        dockApps.clear()
        dockApps.addAll(apps.take(MAX_DOCK_ITEMS))
        notifyDataSetChanged()
    }

    fun getDockApps(): List<DockApp> = dockApps.toList()

    fun addApp(app: DockApp) {
        if (dockApps.size >= MAX_DOCK_ITEMS) {
            Toast.makeText(context, "> DOCK_FULL [MAX=$MAX_DOCK_ITEMS]", Toast.LENGTH_SHORT).show()
            return
        }
        if (dockApps.any { it.packageName == app.packageName }) {
            Toast.makeText(context, "> ALREADY_IN_DOCK", Toast.LENGTH_SHORT).show()
            return
        }
        dockApps.add(app)
        notifyItemInserted(dockApps.size - 1)
        saveDockApps()
    }

    fun removeApp(position: Int) {
        if (position in dockApps.indices) {
            val removed = dockApps.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, dockApps.size)
            saveDockApps()
            Toast.makeText(context, "> REMOVED: ${removed.label}", Toast.LENGTH_SHORT).show()
        }
    }

    fun removeAppByPackage(packageName: String) {
        val index = dockApps.indexOfFirst { it.packageName == packageName }
        if (index >= 0) {
            removeApp(index)
        }
    }

    // ─── Notification Badges ───────────────────────────────────────────────

    fun updateBadge(packageName: String, count: Int) {
        val index = dockApps.indexOfFirst { it.packageName == packageName }
        if (index >= 0) {
            dockApps[index].notificationCount = count
            notifyItemChanged(index)
        }
    }

    // ─── Adapter Overrides ─────────────────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DockViewHolder {
        val itemView = createDockItemView()
        return DockViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: DockViewHolder, position: Int) {
        val app = dockApps[position]

        // Load icon from icon pack or use default
        val packIcon = iconPackManager.getIconForApp(app.packageName, app.activityName)
        holder.iconView.setImageDrawable(packIcon ?: app.icon)

        // Notification badge
        if (app.notificationCount > 0) {
            holder.badgeView.visibility = View.VISIBLE
            holder.badgeView.text = if (app.notificationCount > 9) "9+" else app.notificationCount.toString()
        } else {
            holder.badgeView.visibility = View.GONE
        }

        // Click to launch
        holder.itemView.setOnClickListener {
            onAppClick(app)
        }

        // Long click to remove
        holder.itemView.setOnLongClickListener {
            onAppLongClick(app)
        }
    }

    override fun getItemCount() = dockApps.size

    // ─── View Creation ─────────────────────────────────────────────────────

    private fun createDockItemView(): View {
        val ctx = context

        val frameLayout = FrameLayout(ctx).apply {
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
            setPadding(8, 8, 8, 8)
        }

        // Icon container with background
        val iconContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val iconView = ImageView(ctx).apply {
            id = R.id.dock_icon
            layoutParams = LinearLayout.LayoutParams(80, 80)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setPadding(4, 4, 4, 4)
            setBackgroundColor(Color.parseColor("#0A1A0A")) // Subtle dark green bg
        }
        iconContainer.addView(iconView)

        frameLayout.addView(iconContainer)

        // Badge overlay
        val badgeView = TextView(ctx).apply {
            id = R.id.dock_badge
            text = "0"
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#00FF00"))
            textSize = 8f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(4, 1, 4, 1)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.END or Gravity.TOP
                topMargin = 4
                marginEnd = 4
            }

            // Make badge circular
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor("#00FF00"))
            }
        }
        frameLayout.addView(badgeView)

        return frameLayout
    }

    // ─── Launch App ────────────────────────────────────────────────────────

    fun launchApp(app: DockApp) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                component = android.content.ComponentName(app.packageName, app.activityName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent != null) context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "> LAUNCH_FAILED: ${app.label}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ─── Persistence ───────────────────────────────────────────────────────

    fun saveDockApps() {
        val arr = JSONArray()
        dockApps.forEach { app ->
            arr.put(org.json.JSONObject().apply {
                put("packageName", app.packageName)
                put("activityName", app.activityName)
                put("label", app.label)
            })
        }
        prefs.edit().putString(KEY_DOCK_APPS, arr.toString()).apply()
    }

    fun loadDockApps(): List<DockApp> {
        val json = prefs.getString(KEY_DOCK_APPS, null) ?: return emptyList()
        val apps = mutableListOf<DockApp>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val pkg = obj.getString("packageName")
                val activity = obj.getString("activityName")
                val label = obj.optString("label", pkg)

                try {
                    val appInfo = context.packageManager.getApplicationInfo(pkg, 0)
                    val icon = context.packageManager.getApplicationIcon(appInfo)
                    apps.add(DockApp(pkg, activity, label, icon))
                } catch (_: Exception) {
                    // App might be uninstalled, skip
                }
            }
        } catch (_: Exception) { }
        return apps
    }

    // ─── Default Dock Apps ─────────────────────────────────────────────────

    fun setDefaultDockApps() {
        val pm = context.packageManager
        val defaultPackages = listOf(
            "com.google.android.dialer",
            "com.google.android.apps.messaging",
            "com.google.android.chrome",
            "com.google.android.apps.photos",
            "com.google.android.apps.maps"
        )

        val apps = mutableListOf<DockApp>()
        for (pkg in defaultPackages) {
            try {
                val launchIntent = pm.getLaunchIntentForPackage(pkg) ?: continue
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(appInfo).toString()
                val icon = pm.getApplicationIcon(appInfo)
                val activityName = launchIntent.component?.className ?: ""
                apps.add(DockApp(pkg, activityName, label, icon))
            } catch (_: Exception) { continue }
            if (apps.size >= MAX_DOCK_ITEMS) break
        }

        // If not enough default apps found, add any launchable apps
        if (apps.size < MAX_DOCK_ITEMS) {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(intent, 0)
            for (info in resolveInfos) {
                if (apps.size >= MAX_DOCK_ITEMS) break
                val pkg = info.activityInfo.packageName
                if (apps.none { it.packageName == pkg } && pkg != context.packageName) {
                    apps.add(DockApp(
                        packageName = pkg,
                        activityName = info.activityInfo.name,
                        label = info.loadLabel(pm).toString(),
                        icon = info.loadIcon(pm)
                    ))
                }
            }
        }

        setDockApps(apps)
        saveDockApps()
    }
}
