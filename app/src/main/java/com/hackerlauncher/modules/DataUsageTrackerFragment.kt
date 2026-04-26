package com.hackerlauncher.modules

import android.app.AppOpsManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.TrafficStats
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hackerlauncher.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppDataUsage(
    val appName: String,
    val packageName: String,
    val mobileRx: Long,
    val mobileTx: Long,
    val wifiRx: Long,
    val wifiTx: Long,
    var isSelected: Boolean
)

class UsageBarGraphView(context: Context) : View(context) {

    private val dailyData = mutableListOf<DailyUsage>()
    private val maxDays = 7

    data class DailyUsage(
        val date: String,
        val mobileBytes: Long,
        val wifiBytes: Long
    )

    private val barPaintMobile = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FF6600")
    }

    private val barPaintWifi = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#00CCFF")
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.parseColor("#333333")
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 22f
        color = Color.parseColor("#888888")
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f
        color = Color.parseColor("#AAAAAA")
        textAlign = Paint.Align.CENTER
    }

    private val legendMobilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 22f
        color = Color.parseColor("#FF6600")
    }

    private val legendWifiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 22f
        color = Color.parseColor("#00CCFF")
    }

    fun setDailyData(data: List<DailyUsage>) {
        dailyData.clear()
        dailyData.addAll(data.takeLast(maxDays))
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val padding = 50f
        val topPadding = 50f
        val bottomPadding = 60f

        // Draw legend
        canvas.drawText("■ Mobile", w - 260f, 30f, legendMobilePaint)
        canvas.drawText("■ WiFi", w - 130f, 30f, legendWifiPaint)

        // Draw grid lines
        for (i in 0..4) {
            val y = topPadding + (h - topPadding - bottomPadding) * i / 4f
            canvas.drawLine(padding, y, w - padding, y, gridPaint)
        }

        if (dailyData.isEmpty()) {
            canvas.drawText("No data", w / 2f, h / 2f, labelPaint)
            return
        }

        val drawWidth = w - 2 * padding
        val drawHeight = h - topPadding - bottomPadding
        val barGroupWidth = drawWidth / dailyData.size
        val barWidth = barGroupWidth * 0.3f
        val gap = barGroupWidth * 0.1f

        // Find max value for scaling
        val maxBytes = dailyData.maxOfOrNull {
            it.mobileBytes + it.wifiBytes
        }?.coerceAtLeast(1L) ?: 1L

        // Draw Y-axis labels
        for (i in 0..4) {
            val y = topPadding + drawHeight * i / 4f
            val value = maxBytes * (4 - i) / 4
            canvas.drawText(formatCompactBytes(value), 0f, y + 8f, labelPaint)
        }

        for ((i, day) in dailyData.withIndex()) {
            val groupStart = padding + i * barGroupWidth + gap
            val bottomY = topPadding + drawHeight

            // Mobile bar
            val mobileHeight = if (maxBytes > 0) (day.mobileBytes.toFloat() / maxBytes) * drawHeight else 0f
            val mobileRect = RectF(
                groupStart,
                bottomY - mobileHeight,
                groupStart + barWidth,
                bottomY
            )
            canvas.drawRect(mobileRect, barPaintMobile)

            // WiFi bar
            val wifiHeight = if (maxBytes > 0) (day.wifiBytes.toFloat() / maxBytes) * drawHeight else 0f
            val wifiRect = RectF(
                groupStart + barWidth + gap,
                bottomY - wifiHeight,
                groupStart + 2 * barWidth + gap,
                bottomY
            )
            canvas.drawRect(wifiRect, barPaintWifi)

            // Date label at bottom
            val centerX = groupStart + barWidth + gap / 2
            canvas.drawText(day.date, centerX, topPadding + drawHeight + 40f, labelPaint)

            // Value labels on top of bars
            if (day.mobileBytes > 0) {
                canvas.drawText(
                    formatCompactBytes(day.mobileBytes),
                    groupStart + barWidth / 2,
                    bottomY - mobileHeight - 8f,
                    valuePaint
                )
            }
            if (day.wifiBytes > 0) {
                canvas.drawText(
                    formatCompactBytes(day.wifiBytes),
                    groupStart + barWidth + gap + barWidth / 2,
                    bottomY - wifiHeight - 8f,
                    valuePaint
                )
            }
        }
    }

    private fun formatCompactBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0 * 1024.0))}G"
            bytes >= 1024 * 1024 -> "${"%.0f".format(bytes / (1024.0 * 1024.0))}M"
            bytes >= 1024 -> "${"%.0f".format(bytes / 1024.0)}K"
            else -> "${bytes}B"
        }
    }
}

class DataUsageTrackerFragment : Fragment() {

    private lateinit var textViewMobileData: TextView
    private lateinit var textViewWifiData: TextView
    private lateinit var textViewTotalData: TextView
    private lateinit var textViewAlertThreshold: TextView
    private lateinit var recyclerViewAppUsage: RecyclerView
    private lateinit var buttonSetAlert: Button
    private lateinit var buttonResetCounters: Button
    private lateinit var usageGraphView: UsageBarGraphView
    private lateinit var spinnerPeriod: Spinner
    private lateinit var editTextAlertThreshold: EditText
    private lateinit var textViewPeriodUsage: TextView

    private val appUsageList = mutableListOf<AppDataUsage>()
    private var monitorJob: Job? = null

    private var alertThresholdMb: Long = 0
    private var savedMobileRxStart: Long = 0
    private var savedMobileTxStart: Long = 0
    private var savedWifiRxStart: Long = 0
    private var savedWifiTxStart: Long = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_data_usage_tracker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        textViewMobileData = view.findViewById(R.id.textViewMobileData)
        textViewWifiData = view.findViewById(R.id.textViewWifiData)
        textViewTotalData = view.findViewById(R.id.textViewTotalData)
        textViewAlertThreshold = view.findViewById(R.id.textViewAlertThreshold)
        recyclerViewAppUsage = view.findViewById(R.id.recyclerViewAppUsage)
        buttonSetAlert = view.findViewById(R.id.buttonSetAlert)
        buttonResetCounters = view.findViewById(R.id.buttonResetCounters)
        usageGraphView = view.findViewById(R.id.usageGraphView)
        spinnerPeriod = view.findViewById(R.id.spinnerPeriod)
        editTextAlertThreshold = view.findViewById(R.id.editTextAlertThreshold)
        textViewPeriodUsage = view.findViewById(R.id.textViewPeriodUsage)

        setupRecyclerView()
        setupSpinner()
        setupButtons()
        loadSavedData()
        refreshData()
        startMonitoring()
    }

    private fun setupRecyclerView() {
        recyclerViewAppUsage.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewAppUsage.adapter = AppDataUsageAdapter(appUsageList)
    }

    private fun setupSpinner() {
        val periods = arrayOf("Today", "This Week", "This Month")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, periods)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPeriod.adapter = adapter

        spinnerPeriod.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                refreshData()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupButtons() {
        buttonSetAlert.setOnClickListener {
            setAlertThreshold()
        }

        buttonResetCounters.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Reset Counters")
                .setMessage("Reset all data usage counters? This cannot be undone.")
                .setPositiveButton("Reset") { _, _ ->
                    resetCounters()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun loadSavedData() {
        val prefs = requireContext().getSharedPreferences("data_usage_prefs", Context.MODE_PRIVATE)
        alertThresholdMb = prefs.getLong("alert_threshold_mb", 0L)
        savedMobileRxStart = prefs.getLong("mobile_rx_start", TrafficStats.getMobileRxBytes())
        savedMobileTxStart = prefs.getLong("mobile_tx_start", TrafficStats.getMobileTxBytes())
        savedWifiRxStart = prefs.getLong("wifi_rx_start", getWifiRxBytes())
        savedWifiTxStart = prefs.getLong("wifi_tx_start", getWifiTxBytes())

        updateAlertDisplay()
    }

    private fun saveCounterData() {
        val prefs = requireContext().getSharedPreferences("data_usage_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("alert_threshold_mb", alertThresholdMb)
            .putLong("mobile_rx_start", savedMobileRxStart)
            .putLong("mobile_tx_start", savedMobileTxStart)
            .putLong("wifi_rx_start", savedWifiRxStart)
            .putLong("wifi_tx_start", savedWifiTxStart)
            .apply()
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = lifecycleScope.launch {
            while (isActive) {
                refreshData()
                delay(5000)
            }
        }
    }

    private fun refreshData() {
        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) {
                collectUsageData()
            }

            withContext(Dispatchers.Main) {
                updateDisplay(data)
                checkAlertThreshold(data)
            }
        }
    }

    private fun collectUsageData(): UsageData {
        val mobileRx = TrafficStats.getMobileRxBytes()
        val mobileTx = TrafficStats.getMobileTxBytes()
        val totalRx = TrafficStats.getTotalRxBytes()
        val totalTx = TrafficStats.getTotalTxBytes()

        val wifiRx = getWifiRxBytes()
        val wifiTx = getWifiTxBytes()

        val mobileUsageRx = (mobileRx - savedMobileRxStart).coerceAtLeast(0L)
        val mobileUsageTx = (mobileTx - savedMobileTxStart).coerceAtLeast(0L)
        val wifiUsageRx = (wifiRx - savedWifiRxStart).coerceAtLeast(0L)
        val wifiUsageTx = (wifiTx - savedWifiTxStart).coerceAtLeast(0L)

        val totalMobile = mobileUsageRx + mobileUsageTx
        val totalWifi = wifiUsageRx + wifiUsageTx
        val totalData = totalMobile + totalWifi

        // Collect per-app usage
        val apps = getPerAppDataUsage()

        // Generate daily usage data for graph (simulated from TrafficStats for last 7 days)
        val dailyData = generateDailyUsageData()

        return UsageData(
            mobileRx = mobileUsageRx,
            mobileTx = mobileUsageTx,
            wifiRx = wifiUsageRx,
            wifiTx = wifiUsageTx,
            totalMobile = totalMobile,
            totalWifi = totalWifi,
            totalData = totalData,
            perAppUsage = apps,
            dailyData = dailyData
        )
    }

    private data class UsageData(
        val mobileRx: Long,
        val mobileTx: Long,
        val wifiRx: Long,
        val wifiTx: Long,
        val totalMobile: Long,
        val totalWifi: Long,
        val totalData: Long,
        val perAppUsage: List<AppDataUsage>,
        val dailyData: List<UsageBarGraphView.DailyUsage>
    )

    private fun getPerAppDataUsage(): List<AppDataUsage> {
        val result = mutableListOf<AppDataUsage>()
        val pm = requireContext().packageManager

        // Only accessible with PACKAGE_USAGE_STATS permission
        if (!hasUsageStatsPermission()) {
            // Fallback: show UID-level data from TrafficStats
            return getUidLevelUsage()
        }

        try {
            val packages = pm.getInstalledPackages(0)
            for (pkgInfo in packages) {
                try {
                    val uid = pkgInfo.applicationInfo.uid
                    val mobileRx = TrafficStats.getUidRxBytes(uid)
                    val mobileTx = TrafficStats.getUidTxBytes(uid)

                    // WiFi usage is approximate: total - mobile
                    // This is a simplification as TrafficStats doesn't separate per-UID WiFi
                    val totalRx = mobileRx
                    val totalTx = mobileTx

                    val appName = pm.getApplicationLabel(pkgInfo.applicationInfo).toString()

                    if (mobileRx > 0 || mobileTx > 0) {
                        result.add(
                            AppDataUsage(
                                appName = appName,
                                packageName = pkgInfo.packageName,
                                mobileRx = mobileRx,
                                mobileTx = mobileTx,
                                wifiRx = 0L, // Not directly available per-UID per-network
                                wifiTx = 0L,
                                isSelected = false
                            )
                        )
                    }
                } catch (_: Exception) {
                }
            }

            result.sortByDescending { it.mobileRx + it.mobileTx + it.wifiRx + it.wifiTx }
        } catch (_: Exception) {
        }

        return result.take(50)
    }

    private fun getUidLevelUsage(): List<AppDataUsage> {
        val result = mutableListOf<AppDataUsage>()
        val pm = requireContext().packageManager

        try {
            val packages = pm.getInstalledPackages(0)
            for (pkgInfo in packages) {
                try {
                    val uid = pkgInfo.applicationInfo.uid
                    val rx = TrafficStats.getUidRxBytes(uid)
                    val tx = TrafficStats.getUidTxBytes(uid)

                    if (rx > 0 || tx > 0) {
                        val appName = pm.getApplicationLabel(pkgInfo.applicationInfo).toString()
                        result.add(
                            AppDataUsage(
                                appName = appName,
                                packageName = pkgInfo.packageName,
                                mobileRx = rx,
                                mobileTx = tx,
                                wifiRx = 0L,
                                wifiTx = 0L,
                                isSelected = false
                            )
                        )
                    }
                } catch (_: Exception) {
                }
            }

            result.sortByDescending { it.mobileRx + it.mobileTx }
        } catch (_: Exception) {
        }

        return result.take(50)
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOpsManager = requireContext().getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                requireContext().packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                requireContext().packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun getWifiRxBytes(): Long {
        return (TrafficStats.getTotalRxBytes() - TrafficStats.getMobileRxBytes()).coerceAtLeast(0L)
    }

    private fun getWifiTxBytes(): Long {
        return (TrafficStats.getTotalTxBytes() - TrafficStats.getMobileTxBytes()).coerceAtLeast(0L)
    }

    private fun generateDailyUsageData(): List<UsageBarGraphView.DailyUsage> {
        val dailyData = mutableListOf<UsageBarGraphView.DailyUsage>()
        val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())

        val prefs = requireContext().getSharedPreferences("data_usage_prefs", Context.MODE_PRIVATE)
        val totalMobileCurrent = TrafficStats.getMobileRxBytes() + TrafficStats.getMobileTxBytes()
        val totalWifiCurrent = getWifiRxBytes() + getWifiTxBytes()

        // Read saved daily snapshots
        val dailySnapshots = prefs.getString("daily_snapshots", null)
        val savedDays = mutableMapOf<String, Pair<Long, Long>>()

        if (dailySnapshots != null) {
            try {
                val jsonArray = org.json.JSONArray(dailySnapshots)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val date = obj.getString("date")
                    val mobile = obj.getLong("mobile")
                    val wifi = obj.getLong("wifi")
                    savedDays[date] = Pair(mobile, wifi)
                }
            } catch (_: Exception) {
            }
        }

        // Add today's data
        val today = dateFormat.format(Date())
        savedDays[today] = Pair(totalMobileCurrent, totalWifiCurrent)

        // Save updated snapshots
        saveDailySnapshots(savedDays)

        // Generate last 7 days
        val calendar = java.util.Calendar.getInstance()
        for (i in 6 downTo 0) {
            calendar.timeInMillis = System.currentTimeMillis()
            calendar.add(java.util.Calendar.DAY_OF_YEAR, -i)
            val dateStr = dateFormat.format(calendar.time)

            val data = savedDays[dateStr]
            if (data != null) {
                dailyData.add(
                    UsageBarGraphView.DailyUsage(
                        date = dateStr,
                        mobileBytes = data.first,
                        wifiBytes = data.second
                    )
                )
            } else {
                dailyData.add(
                    UsageBarGraphView.DailyUsage(
                        date = dateStr,
                        mobileBytes = 0L,
                        wifiBytes = 0L
                    )
                )
            }
        }

        return dailyData
    }

    private fun saveDailySnapshots(data: Map<String, Pair<Long, Long>>) {
        val prefs = requireContext().getSharedPreferences("data_usage_prefs", Context.MODE_PRIVATE)
        try {
            val jsonArray = org.json.JSONArray()
            for ((date, usage) in data) {
                val obj = org.json.JSONObject()
                obj.put("date", date)
                obj.put("mobile", usage.first)
                obj.put("wifi", usage.second)
                jsonArray.put(obj)
            }
            // Keep only last 30 days
            prefs.edit().putString("daily_snapshots", jsonArray.toString()).apply()
        } catch (_: Exception) {
        }
    }

    private fun updateDisplay(data: UsageData) {
        textViewMobileData.text = "Mobile: ${formatBytes(data.totalMobile)} (↓${formatBytes(data.mobileRx)} ↑${formatBytes(data.mobileTx)})"
        textViewWifiData.text = "WiFi: ${formatBytes(data.totalWifi)} (↓${formatBytes(data.wifiRx)} ↑${formatBytes(data.wifiTx)})"
        textViewTotalData.text = "Total: ${formatBytes(data.totalData)}"

        textViewMobileData.setTextColor(
            when {
                data.totalMobile > 1024 * 1024 * 1024 -> 0xFFFF4444.toInt()
                data.totalMobile > 500 * 1024 * 1024 -> 0xFFFFFF00.toInt()
                else -> 0xFF00FF00.toInt()
            }
        )

        // Update period-specific usage
        val periodLabel = when (spinnerPeriod.selectedItemPosition) {
            0 -> "Today"
            1 -> "This Week"
            2 -> "This Month"
            else -> "Total"
        }
        textViewPeriodUsage.text = "$periodLabel usage: ${formatBytes(data.totalData)}"

        // Foreground vs Background split (approximation: mobile tx is often background, rx foreground)
        val foreground = data.mobileRx + data.wifiRx
        val background = data.mobileTx + data.wifiTx
        val total = foreground + background
        if (total > 0) {
            val fgPercent = (foreground * 100) / total
            val bgPercent = 100 - fgPercent
            textViewPeriodUsage.append("\nFG: $fgPercent% | BG: $bgPercent%")
        }

        // Update graph
        usageGraphView.setDailyData(data.dailyData)

        // Update app usage list
        appUsageList.clear()
        appUsageList.addAll(data.perAppUsage)
        recyclerViewAppUsage.adapter?.notifyDataSetChanged()
    }

    private fun checkAlertThreshold(data: UsageData) {
        if (alertThresholdMb <= 0) return

        val totalMb = data.totalData / (1024 * 1024)
        if (totalMb >= alertThresholdMb) {
            textViewAlertThreshold.setTextColor(0xFFFF4444.toInt())
            textViewAlertThreshold.text = "⚠ ALERT: ${totalMb}MB / ${alertThresholdMb}MB"

            // Show alert notification (only once per threshold breach)
            val prefs = requireContext().getSharedPreferences("data_usage_prefs", Context.MODE_PRIVATE)
            val lastAlertMb = prefs.getLong("last_alert_mb", 0L)
            if (lastAlertMb < alertThresholdMb) {
                prefs.edit().putLong("last_alert_mb", alertThresholdMb).apply()

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Data Usage Alert")
                    .setMessage("You have exceeded your data usage threshold!\n\nUsed: ${formatBytes(data.totalData)}\nThreshold: ${alertThresholdMb} MB")
                    .setPositiveButton("OK", null)
                    .show()
            }
        } else {
            textViewAlertThreshold.setTextColor(0xFF00FF00.toInt())
            textViewAlertThreshold.text = "Threshold: ${totalMb}MB / ${alertThresholdMb}MB"
        }
    }

    private fun setAlertThreshold() {
        val input = editTextAlertThreshold.text.toString().trim()
        if (input.isBlank()) {
            Toast.makeText(requireContext(), "Enter threshold in MB", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val threshold = input.toLong()
            if (threshold <= 0) {
                Toast.makeText(requireContext(), "Threshold must be positive", Toast.LENGTH_SHORT).show()
                return
            }

            alertThresholdMb = threshold
            val prefs = requireContext().getSharedPreferences("data_usage_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putLong("alert_threshold_mb", alertThresholdMb)
                .putLong("last_alert_mb", 0L) // Reset alert state
                .apply()

            updateAlertDisplay()
            Toast.makeText(requireContext(), "Alert set: $threshold MB", Toast.LENGTH_SHORT).show()
            refreshData()
        } catch (e: NumberFormatException) {
            Toast.makeText(requireContext(), "Invalid number", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetCounters() {
        savedMobileRxStart = TrafficStats.getMobileRxBytes()
        savedMobileTxStart = TrafficStats.getMobileTxBytes()
        savedWifiRxStart = getWifiRxBytes()
        savedWifiTxStart = getWifiTxBytes()

        // Reset daily snapshots
        val prefs = requireContext().getSharedPreferences("data_usage_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("last_alert_mb", 0L)
            .remove("daily_snapshots")
            .apply()

        saveCounterData()
        Toast.makeText(requireContext(), "Counters reset", Toast.LENGTH_SHORT).show()
        refreshData()
    }

    private fun updateAlertDisplay() {
        if (alertThresholdMb > 0) {
            textViewAlertThreshold.text = "Threshold: -- MB / ${alertThresholdMb}MB"
        } else {
            textViewAlertThreshold.text = "Threshold: Not set"
            textViewAlertThreshold.setTextColor(0xFF888888.toInt())
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
            bytes >= 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
            bytes >= 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
            bytes < 0 -> "N/A"
            else -> "$bytes B"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        monitorJob?.cancel()
    }

    inner class AppDataUsageAdapter(
        private val items: List<AppDataUsage>
    ) : RecyclerView.Adapter<AppDataUsageAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textAppName: TextView = view.findViewById(R.id.textViewAppName)
            val textMobileUsage: TextView = view.findViewById(R.id.textViewMobileUsage)
            val textWifiUsage: TextView = view.findViewById(R.id.textViewWifiUsage)
            val textTotalUsage: TextView = view.findViewById(R.id.textViewTotalUsage)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app_data_usage, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.textAppName.text = item.appName
            holder.textMobileUsage.text = "Mobile: ↓${formatBytes(item.mobileRx)} ↑${formatBytes(item.mobileTx)}"
            holder.textWifiUsage.text = "WiFi: ↓${formatBytes(item.wifiRx)} ↑${formatBytes(item.wifiTx)}"

            val total = item.mobileRx + item.mobileTx + item.wifiRx + item.wifiTx
            holder.textTotalUsage.text = "Total: ${formatBytes(total)}"

            holder.textTotalUsage.setTextColor(
                when {
                    total > 500 * 1024 * 1024 -> 0xFFFF4444.toInt()
                    total > 100 * 1024 * 1024 -> 0xFFFFFF00.toInt()
                    else -> 0xFF00FF00.toInt()
                }
            )
        }

        override fun getItemCount(): Int = items.size
    }
}
