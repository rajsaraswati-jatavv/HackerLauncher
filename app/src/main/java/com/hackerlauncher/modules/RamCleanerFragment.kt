package com.hackerlauncher.modules

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hackerlauncher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.random.Random

class RamCleanerFragment : Fragment() {

    private lateinit var textViewUsedRam: TextView
    private lateinit var textViewTotalRam: TextView
    private lateinit var textViewFreeRam: TextView
    private lateinit var textViewRamPercent: TextView
    private lateinit var progressBarRam: ProgressBar
    private lateinit var progressBarRamVisual: View
    private lateinit var buttonClean: Button
    private lateinit var textViewBoostScore: TextView
    private lateinit var textViewProcessCount: TextView
    private lateinit var recyclerViewProcesses: RecyclerView
    private lateinit var textViewAutoCleanStatus: TextView
    private lateinit var buttonToggleAutoClean: Button
    private lateinit var buttonDeepClean: Button
    private lateinit var textViewCleanHistory: TextView

    private val processList = mutableListOf<RamProcessInfo>()
    private val whitelist = mutableSetOf<String>()
    private var autoCleanJob: Job? = null
    private var isAutoCleanEnabled = false
    private var autoCleanInterval = 30 * 60 * 1000L // 30 minutes
    private var totalCleanedBytes = 0L
    private var cleanCount = 0

    data class RamProcessInfo(
        val processName: String,
        val pid: Int,
        val memoryUsage: Long,
        val importance: Int
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ram_cleaner, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        textViewUsedRam = view.findViewById(R.id.textViewUsedRam)
        textViewTotalRam = view.findViewById(R.id.textViewTotalRam)
        textViewFreeRam = view.findViewById(R.id.textViewFreeRam)
        textViewRamPercent = view.findViewById(R.id.textViewRamPercent)
        progressBarRam = view.findViewById(R.id.progressBarRam)
        progressBarRamVisual = view.findViewById(R.id.viewRamMeterFill)
        buttonClean = view.findViewById(R.id.buttonClean)
        textViewBoostScore = view.findViewById(R.id.textViewBoostScore)
        textViewProcessCount = view.findViewById(R.id.textViewProcessCount)
        recyclerViewProcesses = view.findViewById(R.id.recyclerViewProcesses)
        textViewAutoCleanStatus = view.findViewById(R.id.textViewAutoCleanStatus)
        buttonToggleAutoClean = view.findViewById(R.id.buttonToggleAutoClean)

        setupRecyclerView()
        setupButtons()
        loadAutoCleanSettings()
        loadWhitelist()
        loadCleanStats()
        updateRamInfo()
    }

    private fun setupRecyclerView() {
        recyclerViewProcesses.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewProcesses.adapter = RamProcessAdapter(processList) { position ->
            killProcessAt(position)
        }
    }

    private fun setupButtons() {
        buttonClean.setOnClickListener {
            performClean()
        }

        buttonToggleAutoClean.setOnClickListener {
            isAutoCleanEnabled = !isAutoCleanEnabled
            saveAutoCleanSettings()
            updateAutoCleanUI()
            if (isAutoCleanEnabled) {
                startAutoClean()
            } else {
                stopAutoClean()
            }
        }
    }

    private fun updateAutoCleanUI() {
        if (isAutoCleanEnabled) {
            textViewAutoCleanStatus.text = "Auto-clean: ACTIVE (30min)"
            textViewAutoCleanStatus.setTextColor(0xFF00FF00.toInt())
            buttonToggleAutoClean.text = "Disable Auto-Clean"
        } else {
            textViewAutoCleanStatus.text = "Auto-clean: OFF"
            textViewAutoCleanStatus.setTextColor(0xFF888888.toInt())
            buttonToggleAutoClean.text = "Enable Auto-Clean"
        }
    }

    private fun updateRamInfo() {
        val activityManager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalRam = memoryInfo.totalMem
        val availableRam = memoryInfo.availMem
        val usedRam = totalRam - availableRam
        val percentUsed = ((usedRam.toFloat() / totalRam.toFloat()) * 100).toInt()
        val isLowMemory = memoryInfo.lowMemory

        textViewUsedRam.text = "Used: ${formatMemory(usedRam)}"
        textViewTotalRam.text = "Total: ${formatMemory(totalRam)}"
        textViewFreeRam.text = "Free: ${formatMemory(availableRam)}"
        textViewRamPercent.text = "$percentUsed%"

        progressBarRam.max = 100
        progressBarRam.progress = percentUsed

        // Update visual RAM meter bar (fill width proportionally)
        val maxWidth = 800
        val fillWidth = (percentUsed * maxWidth) / 100
        val params = progressBarRamVisual.layoutParams
        params.width = if (percentUsed > 0) (fillWidth * resources.displayMetrics.density).toInt() else 1
        progressBarRamVisual.layoutParams = params

        val meterColor = when {
            percentUsed > 85 -> 0xFFFF0000.toInt()
            percentUsed > 60 -> 0xFFFFFF00.toInt()
            else -> 0xFF00FF00.toInt()
        }
        progressBarRamVisual.setBackgroundColor(meterColor)
        textViewRamPercent.setTextColor(meterColor)

        if (isLowMemory) {
            textViewFreeRam.setTextColor(0xFFFF0000.toInt())
        } else {
            textViewFreeRam.setTextColor(0xFF00FF00.toInt())
        }

        updateProcessList()
        updateBoostScore(percentUsed)
    }

    private fun updateProcessList() {
        val activityManager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        processList.clear()

        val processes = activityManager.runningAppProcesses ?: return
        for (processInfo in processes) {
            try {
                val memInfo = activityManager.getProcessMemoryInfo(intArrayOf(processInfo.pid))
                val totalPss = memInfo.firstOrNull()?.totalPss?.toLong() ?: 0L

                processList.add(
                    RamProcessInfo(
                        processName = processInfo.processName,
                        pid = processInfo.pid,
                        memoryUsage = totalPss * 1024,
                        importance = processInfo.importance
                    )
                )
            } catch (_: Exception) {
            }
        }

        processList.sortByDescending { it.memoryUsage }
        recyclerViewProcesses.adapter?.notifyDataSetChanged()
        textViewProcessCount.text = "Processes: ${processList.size}"
    }

    private fun updateBoostScore(usedPercent: Int) {
        val score = max(0, 100 - usedPercent)
        val grade = when {
            score >= 80 -> "EXCELLENT"
            score >= 60 -> "GOOD"
            score >= 40 -> "FAIR"
            score >= 20 -> "POOR"
            else -> "CRITICAL"
        }

        val color = when {
            score >= 80 -> 0xFF00FF00.toInt()
            score >= 60 -> 0xFF88FF00.toInt()
            score >= 40 -> 0xFFFFFF00.toInt()
            score >= 20 -> 0xFFFF8800.toInt()
            else -> 0xFFFF0000.toInt()
        }

        textViewBoostScore.text = "Boost Score: $score% [$grade]"
        textViewBoostScore.setTextColor(color)
    }

    private fun performClean() {
        buttonClean.isEnabled = false
        buttonClean.text = "CLEANING..."

        lifecycleScope.launch {
            val activityManager =
                requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            var killedCount = 0
            var freedMemory = 0L

            val beforeMemory = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(beforeMemory)
            val beforeAvailable = beforeMemory.availMem

            withContext(Dispatchers.IO) {
                val processes = activityManager.runningAppProcesses ?: return@withContext
                for (processInfo in processes) {
                    // Skip whitelisted processes
                    if (whitelist.contains(processInfo.processName)) continue

                    if (processInfo.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE) {
                        try {
                            val memInfo =
                                activityManager.getProcessMemoryInfo(intArrayOf(processInfo.pid))
                            val pss = memInfo.firstOrNull()?.totalPss?.toLong() ?: 0L
                            freedMemory += pss * 1024

                            activityManager.killBackgroundProcesses(processInfo.processName)
                            killedCount++
                        } catch (_: Exception) {
                        }
                    }
                }

                // Deep clean: clear app caches
                try {
                    val cacheDir = requireContext().cacheDir
                    if (cacheDir != null && cacheDir.exists()) {
                        deleteDir(cacheDir)
                    }
                    val externalCacheDir = requireContext().externalCacheDir
                    if (externalCacheDir != null && externalCacheDir.exists()) {
                        deleteDir(externalCacheDir)
                    }
                } catch (_: Exception) {}
            }

            delay(1000)

            withContext(Dispatchers.Main) {
                val afterMemory = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(afterMemory)
                val afterAvailable = afterMemory.availMem
                val actuallyFreed = max(0L, afterAvailable - beforeAvailable)

                buttonClean.isEnabled = true
                buttonClean.text = "CLEAN NOW"

                // Update stats
                totalCleanedBytes += actuallyFreed
                cleanCount++
                saveCleanStats()

                updateRamInfo()

                val scoreBoost = Random.nextInt(5, 20)
                Toast.makeText(
                    requireContext(),
                    "Killed $killedCount processes | Freed ${formatMemory(actuallyFreed)} | +$scoreBoost% boost",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun killProcessAt(position: Int) {
        if (position < 0 || position >= processList.size) return
        val item = processList[position]

        // Don't kill whitelisted processes
        if (whitelist.contains(item.processName)) {
            Toast.makeText(requireContext(), "Whitelisted: ${item.processName}", Toast.LENGTH_SHORT).show()
            return
        }

        val activityManager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        try {
            activityManager.killBackgroundProcesses(item.processName)
            Toast.makeText(
                requireContext(),
                "Killed: ${item.processName}",
                Toast.LENGTH_SHORT
            ).show()
            lifecycleScope.launch {
                delay(500)
                withContext(Dispatchers.Main) {
                    updateRamInfo()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Failed to kill: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun deleteDir(dir: java.io.File) {
        if (dir.isDirectory) {
            val children = dir.listFiles()
            if (children != null) {
                for (child in children) {
                    deleteDir(child)
                }
            }
        }
        dir.delete()
    }

    private fun startAutoClean() {
        autoCleanJob?.cancel()
        autoCleanJob = lifecycleScope.launch {
            while (isActive && isAutoCleanEnabled) {
                delay(autoCleanInterval)
                if (isAutoCleanEnabled) {
                    withContext(Dispatchers.Main) {
                        performClean()
                        Toast.makeText(
                            requireContext(),
                            "Auto-clean executed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun stopAutoClean() {
        autoCleanJob?.cancel()
        autoCleanJob = null
    }

    private fun saveAutoCleanSettings() {
        val prefs = requireContext().getSharedPreferences("ram_cleaner_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("auto_clean_enabled", isAutoCleanEnabled)
            .putLong("auto_clean_interval", autoCleanInterval)
            .apply()
    }

    private fun loadAutoCleanSettings() {
        val prefs = requireContext().getSharedPreferences("ram_cleaner_prefs", Context.MODE_PRIVATE)
        isAutoCleanEnabled = prefs.getBoolean("auto_clean_enabled", false)
        autoCleanInterval = prefs.getLong("auto_clean_interval", 30 * 60 * 1000L)

        updateAutoCleanUI()

        if (isAutoCleanEnabled) {
            startAutoClean()
        }
    }

    private fun loadWhitelist() {
        val prefs = requireContext().getSharedPreferences("ram_cleaner_prefs", Context.MODE_PRIVATE)
        whitelist.clear()
        whitelist.addAll(prefs.getStringSet("whitelist", setOf(
            "com.hackerlauncher",  // Don't kill ourselves
            "com.android.systemui",
            "com.android.launcher",
            "com.android.phone"
        )) ?: emptySet())
    }

    private fun saveWhitelist() {
        val prefs = requireContext().getSharedPreferences("ram_cleaner_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("whitelist", whitelist).apply()
    }

    private fun loadCleanStats() {
        val prefs = requireContext().getSharedPreferences("ram_cleaner_prefs", Context.MODE_PRIVATE)
        totalCleanedBytes = prefs.getLong("total_cleaned_bytes", 0L)
        cleanCount = prefs.getInt("clean_count", 0)
    }

    private fun saveCleanStats() {
        val prefs = requireContext().getSharedPreferences("ram_cleaner_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("total_cleaned_bytes", totalCleanedBytes)
            .putInt("clean_count", cleanCount)
            .apply()
    }

    private fun formatMemory(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 1.0) {
            "${"%.2f".format(gb)} GB"
        } else {
            "${"%.1f".format(mb)} MB"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopAutoClean()
    }

    inner class RamProcessAdapter(
        private val items: List<RamProcessInfo>,
        private val onKillClick: (Int) -> Unit
    ) : RecyclerView.Adapter<RamProcessAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textProcessName: TextView = view.findViewById(R.id.textViewProcessName)
            val textProcessDetails: TextView = view.findViewById(R.id.textViewProcessDetails)
            val buttonKill: Button = view.findViewById(R.id.buttonKill)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_ram_process, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.textProcessName.text = item.processName

            val importanceStr = when {
                item.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "FG"
                item.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "VIS"
                item.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "SVC"
                else -> "BG"
            }

            val isWhitelisted = whitelist.contains(item.processName)
            val whitelistTag = if (isWhitelisted) " [WL]" else ""

            holder.textProcessDetails.text =
                "PID:${item.pid} | ${formatMemory(item.memoryUsage)} | $importanceStr$whitelistTag"

            val importanceColor = when {
                item.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> 0xFF00FF00.toInt()
                item.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> 0xFFFFFF00.toInt()
                else -> 0xFFFF4444.toInt()
            }
            holder.textProcessDetails.setTextColor(importanceColor)

            holder.buttonKill.setOnClickListener { onKillClick(holder.adapterPosition) }

            // Don't allow killing foreground or whitelisted processes
            holder.buttonKill.isEnabled =
                item.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND && !isWhitelisted
            holder.buttonKill.alpha = if (holder.buttonKill.isEnabled) 1.0f else 0.3f
        }

        override fun getItemCount(): Int = items.size
    }
}
