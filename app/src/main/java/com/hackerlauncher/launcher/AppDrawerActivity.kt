package com.hackerlauncher.launcher

import com.hackerlauncher.utils.PreferencesManager

import android.animation.ObjectAnimator
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen App Drawer Activity.
 * Displays all installed applications in a 4-column grid with sticky section headers,
 * search filtering, and context actions on long press.
 */
class AppDrawerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_HIDE_APP = "extra_hide_app"
        const val EXTRA_PIN_APP = "extra_pin_app"
        const val EXTRA_FOLDER_APP = "extra_folder_app"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchEditText: EditText
    private lateinit var adapter: AppDrawerAdapter
    private lateinit var prefsManager: PreferencesManager

    private var allApps = mutableListOf<AppItem>()
    private var filteredApps = mutableListOf<AppItem>()
    private var hiddenPackages = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        prefsManager = PreferencesManager(this)
        hiddenPackages = prefsManager.getHiddenApps().toMutableSet()

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Search bar
        searchEditText = EditText(this).apply {
            hint = "> search_apps..."
            setHintTextColor(0xFF00AA00.toInt())
            setTextColor(Color.parseColor("#00FF00"))
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            typeface = Typeface.MONOSPACE
            textSize = 16f
            setPadding(32, 24, 32, 24)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            singleLine = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = 16; marginEnd = 16; topMargin = 16 }
        }
        rootLayout.addView(searchEditText)

        // RecyclerView
        recyclerView = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            layoutManager = GridLayoutManager(this@AppDrawerActivity, 4)
            itemAnimator = null
        }
        rootLayout.addView(recyclerView)

        val gridLayoutManager = recyclerView.layoutManager as GridLayoutManager
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (adapter.getItemViewType(position) == AppDrawerAdapter.TYPE_HEADER) 4 else 1
            }
        }

        adapter = AppDrawerAdapter(this, filteredApps, ::onAppClick, ::onAppLongClick)
        recyclerView.adapter = adapter

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
                true
            } else false
        }

        setContentView(rootLayout)
        loadApps()

        // Open animation
        recyclerView.alpha = 0f
        recyclerView.translationY = 100f
        ObjectAnimator.ofFloat(recyclerView, "alpha", 0f, 1f).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            start()
        }
        ObjectAnimator.ofFloat(recyclerView, "translationY", 100f, 0f).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private fun loadApps() {
        scope.launch {
            allApps = withContext(Dispatchers.IO) {
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = packageManager.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
                val apps = resolveInfos.map { resolveInfo ->
                    AppItem(
                        label = resolveInfo.loadLabel(packageManager).toString(),
                        packageName = resolveInfo.activityInfo.packageName,
                        activityName = resolveInfo.activityInfo.name,
                        icon = resolveInfo.loadIcon(packageManager)
                    )
                }.filter { it.packageName != packageName }
                    .sortedBy { it.label.lowercase() }
                    .toMutableList()
                apps
            }
            filterApps(searchEditText.text.toString())
        }
    }

    private fun filterApps(query: String) {
        filteredApps.clear()
        val q = query.lowercase().trim()

        var currentSection = ""
        for (app in allApps) {
            if (app.packageName in hiddenPackages) continue
            if (q.isNotEmpty() && !app.label.lowercase().contains(q)) continue

            val section = if (app.label.isNotEmpty()) app.label[0].uppercaseChar().toString() else "#"
            if (section != currentSection) {
                currentSection = section
                filteredApps.add(AppItem(label = currentSection, packageName = "", isHeader = true))
            }
            filteredApps.add(app)
        }
        adapter.notifyDataSetChanged()
    }

    private fun onAppClick(app: AppItem) {
        if (app.isHeader) return
        val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (launchIntent != null) {
            startActivity(launchIntent)
            overridePendingTransition(0, 0)
            finish()
        } else {
            Toast.makeText(this, "> error: cannot_launch ${app.packageName}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onAppLongClick(app: AppItem): Boolean {
        if (app.isHeader) return false
        val options = arrayOf("App Info", "Uninstall", "Pin to Home", "Hide App", "Add to Folder")
        AlertDialog.Builder(this)
            .setTitle("> ${app.label}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openAppInfo(app.packageName)
                    1 -> uninstallApp(app.packageName)
                    2 -> pinToHome(app)
                    3 -> hideApp(app.packageName)
                    4 -> addToFolder(app)
                }
            }
            .setNegativeButton("cancel", null)
            .show()
        return true
    }

    private fun openAppInfo(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName)
        }
        startActivity(intent)
    }

    private fun uninstallApp(packageName: String) {
        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
            data = Uri.fromParts("package", packageName)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
        startActivity(intent)
    }

    private fun pinToHome(app: AppItem) {
        val pinned = prefsManager.getPinnedApps().toMutableList()
        if (pinned.none { it.packageName == app.packageName }) {
            pinned.add(app)
            prefsManager.savePinnedApps(pinned)
            Toast.makeText(this, "> pinned: ${app.label}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "> already_pinned: ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideApp(packageName: String) {
        hiddenPackages.add(packageName)
        prefsManager.saveHiddenApps(hiddenPackages.toList())
        filterApps(searchEditText.text.toString())
        Toast.makeText(this, "> hidden: $packageName", Toast.LENGTH_SHORT).show()
    }

    private fun addToFolder(app: AppItem) {
        val folders = prefsManager.getFolders()
        if (folders.isEmpty()) {
            Toast.makeText(this, "> no_folders_found. create one first.", Toast.LENGTH_SHORT).show()
            return
        }
        val folderNames = folders.keys.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("> add_to_folder")
            .setItems(folderNames) { _, which ->
                val folderName = folderNames[which]
                val folderApps = folders[folderName]?.toMutableList() ?: mutableListOf()
                if (folderApps.none { it.packageName == app.packageName }) {
                    folderApps.add(app)
                    folders[folderName] = folderApps
                    prefsManager.saveFolders(folders)
                    Toast.makeText(this, "> added_to: $folderName", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "> already_in: $folderName", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    override fun onBackPressed() {
        closeDrawer()
    }

    private fun closeDrawer() {
        ObjectAnimator.ofFloat(recyclerView, "alpha", 1f, 0f).apply {
            duration = 200
            start()
        }
        ObjectAnimator.ofFloat(recyclerView, "translationY", 0f, 100f).apply {
            duration = 200
            start()
        }
        recyclerView.postDelayed({ finish() }, 210)
    }

    override fun onResume() {
        super.onResume()
        if (hiddenPackages != prefsManager.getHiddenApps().toMutableSet()) {
            hiddenPackages = prefsManager.getHiddenApps().toMutableSet()
            filterApps(searchEditText.text.toString())
        }
    }

    //region Adapter & ViewHolders

    data class AppItem(
        val label: String,
        val packageName: String,
        val activityName: String = "",
        val icon: android.graphics.drawable.Drawable? = null,
        val isHeader: Boolean = false
    )

    class AppDrawerAdapter(
        private val context: Context,
        private val items: List<AppItem>,
        private val onClick: (AppItem) -> Unit,
        private val onLongClick: (AppItem) -> Boolean
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            const val TYPE_HEADER = 0
            const val TYPE_APP = 1
        }

        override fun getItemViewType(position: Int): Int {
            return if (items[position].isHeader) TYPE_HEADER else TYPE_APP
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == TYPE_HEADER) {
                val tv = TextView(context).apply {
                    setTextColor(Color.parseColor("#00FF00"))
                    typeface = Typeface.MONOSPACE
                    textSize = 18f
                    setPadding(16, 24, 16, 8)
                    setBackgroundColor(Color.parseColor("#050505"))
                    layoutParams = RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT,
                        RecyclerView.LayoutParams.WRAP_CONTENT
                    )
                }
                HeaderViewHolder(tv)
            } else {
                val container = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(8, 12, 8, 12)
                    layoutParams = RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT,
                        RecyclerView.LayoutParams.WRAP_CONTENT
                    )
                }
                val iconView = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(96, 96).apply {
                        gravity = Gravity.CENTER
                    }
                    setImageDrawable(ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon))
                }
                val labelView = TextView(context).apply {
                    setTextColor(Color.parseColor("#00FF00"))
                    typeface = Typeface.MONOSPACE
                    textSize = 11f
                    maxLines = 2
                    gravity = Gravity.CENTER
                    setPadding(0, 4, 0, 0)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                val badgeView = TextView(context).apply {
                    setTextColor(Color.BLACK)
                    setBackgroundColor(Color.parseColor("#00FF00"))
                    typeface = Typeface.MONOSPACE
                    textSize = 9f
                    gravity = Gravity.CENTER
                    setPadding(4, 2, 4, 2)
                    visibility = View.GONE
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { gravity = Gravity.CENTER }
                }
                container.addView(iconView)
                container.addView(labelView)
                container.addView(badgeView)
                AppViewHolder(container, iconView, labelView, badgeView)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            if (holder is HeaderViewHolder) {
                (holder.itemView as TextView).text = "[ ${item.label} ]"
            } else if (holder is AppViewHolder) {
                holder.labelView.text = item.label
                item.icon?.let { holder.iconView.setImageDrawable(it) }
                // Badge: show notification count from NotificationListener
                val count = NotificationListener.getBadgeCount(item.packageName)
                if (count > 0) {
                    holder.badgeView.text = count.toString()
                    holder.badgeView.visibility = View.VISIBLE
                } else {
                    holder.badgeView.visibility = View.GONE
                }
                holder.itemView.setOnClickListener { onClick(item) }
                holder.itemView.setOnLongClickListener { onLongClick(item) }
            }
        }

        override fun getItemCount(): Int = items.size

        class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
        class AppViewHolder(
            itemView: View,
            val iconView: ImageView,
            val labelView: TextView,
            val badgeView: TextView
        ) : RecyclerView.ViewHolder(itemView)
    }

    //endregion
}
