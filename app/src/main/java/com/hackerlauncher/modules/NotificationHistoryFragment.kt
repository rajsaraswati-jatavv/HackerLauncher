package com.hackerlauncher.modules

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NotificationEntry(
    val appName: String,
    val packageName: String,
    val title: String,
    val text: String,
    val category: String,
    val timestamp: Long,
    val id: Int
)

class NotificationHistoryFragment : Fragment() {

    private val GREEN = Color.parseColor("#00FF00")
    private val DARK_GREEN = Color.parseColor("#00AA00")
    private val BLACK = Color.parseColor("#000000")
    private val DARK_GRAY = Color.parseColor("#1A1A1A")
    private val MED_GRAY = Color.parseColor("#333333")

    private lateinit var prefs: SharedPreferences
    private val notifications = mutableListOf<NotificationEntry>()
    private val filteredNotifications = mutableListOf<NotificationEntry>()
    private var currentFilter = ""

    private lateinit var mainLayout: LinearLayout
    private lateinit var searchEdit: EditText
    private lateinit var countText: TextView
    private lateinit var statsText: TextView
    private lateinit var notifListContainer: LinearLayout
    private lateinit var statusText: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        prefs = requireContext().getSharedPreferences("notification_history", Context.MODE_PRIVATE)

        val scrollView = ScrollView(requireContext()).apply {
            setBackgroundColor(BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        mainLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Title
        mainLayout.addView(makeTitle("[>] NOTIFICATION HISTORY"))

        // Listener check
        mainLayout.addView(makeButton("CHECK NOTIFICATION ACCESS") { checkNotificationAccess() })

        // Status
        statusText = makeLabel("[~] Loading...")
        mainLayout.addView(statusText)

        // Search
        searchEdit = EditText(requireContext()).apply {
            hint = "Search by app or text..."
            setTextColor(GREEN)
            setHintTextColor(DARK_GREEN)
            setBackgroundColor(MED_GRAY)
            textSize = 13f
            setTypeface(Typeface.MONOSPACE)
            setPadding(12, 8, 12, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { applyFilter() }
            override fun afterTextChanged(s: Editable?) {}
        })
        mainLayout.addView(searchEdit)

        // Count & Stats
        countText = makeLabel("Notifications: 0")
        mainLayout.addView(countText)

        statsText = makeLabel("")
        mainLayout.addView(statsText)

        // Buttons row
        val btnRow1 = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        btnRow1.addView(makeHalfButton("REFRESH") { refreshNotifications() })
        btnRow1.addView(makeHalfButton("DELETE ALL") { deleteAllNotifications() })
        mainLayout.addView(btnRow1)

        val btnRow2 = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        btnRow2.addView(makeHalfButton("EXPORT") { exportNotifications() })
        btnRow2.addView(makeHalfButton("STATS") { showStats() })
        mainLayout.addView(btnRow2)

        // Notification list
        notifListContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        mainLayout.addView(notifListContainer)

        scrollView.addView(mainLayout)
        return scrollView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkNotificationAccess()
        refreshNotifications()
    }

    private fun checkNotificationAccess() {
        try {
            val cn = ComponentName(requireContext(), "com.hackerlauncher.NotificationListener")
            val flat = Settings.Secure.getString(
                requireContext().contentResolver,
                "enabled_notification_listeners"
            )
            val enabled = flat != null && flat.contains(cn.flattenToString())

            if (enabled) {
                statusText.text = "[>] Notification access: ENABLED"
            } else {
                statusText.text = "[!] Notification access: DISABLED - Tap button to enable"
                // Add button to open settings
                mainLayout.addView(makeButton("OPEN NOTIFICATION SETTINGS") {
                    try {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Cannot open settings: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }, 4) // Insert after status text
            }
        } catch (e: Exception) {
            statusText.text = "[!] Error checking access: ${e.message}"
        }
    }

    private fun refreshNotifications() {
        try {
            notifications.clear()
            val json = prefs.getString("notifications", "[]") ?: "[]"
            val arr = JSONArray(json)

            for (i in 0 until arr.length()) {
                try {
                    val obj = arr.getJSONObject(i)
                    notifications.add(NotificationEntry(
                        appName = obj.optString("appName", "Unknown"),
                        packageName = obj.optString("packageName", ""),
                        title = obj.optString("title", ""),
                        text = obj.optString("text", ""),
                        category = obj.optString("category", ""),
                        timestamp = obj.optLong("timestamp", 0),
                        id = obj.optInt("id", 0)
                    ))
                } catch (_: Exception) { }
            }

            // Sort by timestamp descending
            notifications.sortByDescending { it.timestamp }
            applyFilter()
            statusText.text = "[>] Loaded ${notifications.size} notifications"
        } catch (e: Exception) {
            statusText.text = "[!] Error loading: ${e.message}"
        }
    }

    private fun applyFilter() {
        currentFilter = searchEdit.text.toString().lowercase()
        filteredNotifications.clear()

        filteredNotifications.addAll(
            if (currentFilter.isBlank()) notifications
            else notifications.filter {
                it.appName.lowercase().contains(currentFilter) ||
                it.title.lowercase().contains(currentFilter) ||
                it.text.lowercase().contains(currentFilter) ||
                it.packageName.lowercase().contains(currentFilter)
            }
        )

        countText.text = "Notifications: ${filteredNotifications.size}/${notifications.size}"
        renderNotifications()
    }

    private fun renderNotifications() {
        notifListContainer.removeAllViews()

        if (filteredNotifications.isEmpty()) {
            notifListContainer.addView(TextView(requireContext()).apply {
                text = "[~] No notifications recorded"
                setTextColor(DARK_GREEN)
                textSize = 12f
                setTypeface(Typeface.MONOSPACE)
                setPadding(0, 24, 0, 0)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
            return
        }

        // Show most recent 100
        val display = filteredNotifications.take(100)
        val df = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

        for ((index, notif) in display.withIndex()) {
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(if (index % 2 == 0) DARK_GRAY else BLACK)
                setPadding(12, 8, 12, 8)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            // App name and time
            val headerRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            headerRow.addView(TextView(requireContext()).apply {
                text = notif.appName
                setTextColor(GREEN)
                textSize = 12f
                setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            })

            headerRow.addView(TextView(requireContext()).apply {
                text = df.format(Date(notif.timestamp))
                setTextColor(Color.parseColor("#888888"))
                textSize = 10f
                setTypeface(Typeface.MONOSPACE)
            })

            row.addView(headerRow)

            // Title
            if (notif.title.isNotBlank()) {
                row.addView(TextView(requireContext()).apply {
                    text = notif.title
                    setTextColor(Color.parseColor("#CCCCCC"))
                    textSize = 12f
                    setTypeface(Typeface.MONOSPACE)
                })
            }

            // Text
            if (notif.text.isNotBlank()) {
                row.addView(TextView(requireContext()).apply {
                    text = notif.text.take(200)
                    setTextColor(Color.parseColor("#AAAAAA"))
                    textSize = 11f
                    setTypeface(Typeface.MONOSPACE)
                })
            }

            // Category & package
            val metaRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            if (notif.category.isNotBlank()) {
                metaRow.addView(TextView(requireContext()).apply {
                    text = "[${notif.category}]"
                    setTextColor(Color.parseColor("#FFFF00"))
                    textSize = 9f
                    setTypeface(Typeface.MONOSPACE)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = 8 }
                })
            }

            metaRow.addView(TextView(requireContext()).apply {
                text = notif.packageName
                setTextColor(Color.parseColor("#666666"))
                textSize = 9f
                setTypeface(Typeface.MONOSPACE)
            })

            row.addView(metaRow)

            notifListContainer.addView(row)
        }

        if (filteredNotifications.size > 100) {
            notifListContainer.addView(TextView(requireContext()).apply {
                text = "... and ${filteredNotifications.size - 100} more"
                setTextColor(DARK_GREEN)
                textSize = 12f
                setTypeface(Typeface.MONOSPACE)
                setPadding(0, 8, 0, 0)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
        }
    }

    private fun deleteAllNotifications() {
        try {
            notifications.clear()
            filteredNotifications.clear()
            prefs.edit().remove("notifications").apply()
            countText.text = "Notifications: 0"
            notifListContainer.removeAllViews()
            notifListContainer.addView(TextView(requireContext()).apply {
                text = "[~] History cleared"
                setTextColor(DARK_GREEN)
                textSize = 12f
                setTypeface(Typeface.MONOSPACE)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
            statusText.text = "[+] All notifications deleted"
            Toast.makeText(requireContext(), "History cleared", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            statusText.text = "[!] Error deleting: ${e.message}"
        }
    }

    private fun exportNotifications() {
        try {
            val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val fileName = "notification_history_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.txt"
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)

            val writer = FileWriter(file)
            writer.write("=== HackerLauncher Notification History ===\n")
            writer.write("Exported: ${df.format(Date())}\n")
            writer.write("Total: ${notifications.size} notifications\n\n")

            for (notif in notifications) {
                writer.write("---\n")
                writer.write("App: ${notif.appName}\n")
                writer.write("Package: ${notif.packageName}\n")
                writer.write("Title: ${notif.title}\n")
                writer.write("Text: ${notif.text}\n")
                writer.write("Category: ${notif.category}\n")
                writer.write("Time: ${df.format(Date(notif.timestamp))}\n")
            }

            writer.flush()
            writer.close()
            statusText.text = "[+] Exported to ${file.absolutePath}"
            Toast.makeText(requireContext(), "Exported to Downloads", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            statusText.text = "[!] Export error: ${e.message}"
            Toast.makeText(requireContext(), "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showStats() {
        try {
            val sb = StringBuilder()
            sb.appendLine("=== NOTIFICATION STATS ===")
            sb.appendLine("Total notifications: ${notifications.size}")

            // Most notifying app
            val appCounts = notifications.groupingBy { it.appName }.eachCount()
            val topApp = appCounts.maxByOrNull { it.value }
            sb.appendLine("\nMost notifying app: ${topApp?.key} (${topApp?.value})")

            // Top 10 apps
            sb.appendLine("\n--- Top 10 Apps ---")
            appCounts.entries.sortedByDescending { it.value }.take(10).forEach { (app, count) ->
                sb.appendLine("  $app: $count")
            }

            // Daily count
            val dailyDf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dailyCounts = notifications.groupingBy { dailyDf.format(Date(it.timestamp)) }.eachCount()
            sb.appendLine("\n--- Daily Count ---")
            dailyCounts.entries.sortedByDescending { it.key }.take(14).forEach { (day, count) ->
                sb.appendLine("  $day: $count")
            }

            // Category breakdown
            val catCounts = notifications.filter { it.category.isNotBlank() }
                .groupingBy { it.category }.eachCount()
            if (catCounts.isNotEmpty()) {
                sb.appendLine("\n--- Categories ---")
                catCounts.entries.sortedByDescending { it.value }.forEach { (cat, count) ->
                    sb.appendLine("  $cat: $count")
                }
            }

            showInfoDialog("Notification Stats", sb.toString())
        } catch (e: Exception) {
            statusText.text = "[!] Stats error: ${e.message}"
        }
    }

    private fun showInfoDialog(title: String, message: String) {
        val scroll = ScrollView(requireContext()).apply { setBackgroundColor(BLACK) }
        scroll.addView(TextView(requireContext()).apply {
            text = message
            setTextColor(GREEN)
            textSize = 12f
            setTypeface(Typeface.MONOSPACE)
            setPadding(24, 24, 24, 24)
        })

        android.app.AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton("COPY") { _, _ ->
                val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Stats", message))
                Toast.makeText(requireContext(), "Copied", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("CLOSE", null)
            .show()
    }

    // Companion object for the NotificationListenerService to save notifications
    companion object {
        fun saveNotification(context: Context, entry: NotificationEntry) {
            try {
                val prefs = context.getSharedPreferences("notification_history", Context.MODE_PRIVATE)
                val json = prefs.getString("notifications", "[]") ?: "[]"
                val arr = JSONArray(json)

                // Add new notification
                val obj = JSONObject().apply {
                    put("appName", entry.appName)
                    put("packageName", entry.packageName)
                    put("title", entry.title)
                    put("text", entry.text)
                    put("category", entry.category)
                    put("timestamp", entry.timestamp)
                    put("id", entry.id)
                }
                arr.put(obj)

                // Keep last 500
                while (arr.length() > 500) {
                    arr.remove(0)
                }

                prefs.edit().putString("notifications", arr.toString()).apply()
            } catch (_: Exception) { }
        }
    }

    private fun makeTitle(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setTextColor(GREEN)
            textSize = 14f
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun makeLabel(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setTextColor(GREEN)
            textSize = 12f
            setTypeface(Typeface.MONOSPACE)
            setPadding(0, 8, 0, 4)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun makeButton(text: String, onClick: () -> Unit): Button {
        return Button(requireContext()).apply {
            this.text = text
            setTextColor(BLACK)
            setBackgroundColor(GREEN)
            textSize = 12f
            setTypeface(Typeface.MONOSPACE)
            setPadding(12, 8, 12, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4; bottomMargin = 4 }
            setOnClickListener { onClick() }
        }
    }

    private fun makeHalfButton(text: String, onClick: () -> Unit): Button {
        return Button(requireContext()).apply {
            this.text = text
            setTextColor(BLACK)
            setBackgroundColor(GREEN)
            textSize = 11f
            setTypeface(Typeface.MONOSPACE)
            setPadding(8, 6, 8, 6)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply { marginEnd = 4 }
            setOnClickListener { onClick() }
        }
    }
}
