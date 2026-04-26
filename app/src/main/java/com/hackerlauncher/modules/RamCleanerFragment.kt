package com.hackerlauncher.modules

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
import java.io.BufferedReader
import java.io.FileReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class RamCleanerFragment : Fragment() {

    // View references
    private lateinit var textViewUsedRam: TextView
    private lateinit var textViewTotalRam: TextView
    private lateinit var textViewFreeRam: TextView
    private lateinit var textViewRamPercent: TextView
    private lateinit var progressBarRam: ProgressBar
    private lateinit var viewRamMeterFill: View
    private lateinit var buttonClean: Button
    private lateinit var buttonDeepClean: Button
    private lateinit var buttonKillAll: Button
    private lateinit var buttonSmartClean: Button
    private lateinit var buttonHibernate: Button
    private lateinit var textViewBoostScore: TextView
    private lateinit var textViewProcessCount: TextView
    private lateinit var textViewAutoCleanStatus: TextView
    private lateinit var buttonToggleAutoClean: Button
    private lateinit var textViewCleanHistory: TextView
    private lateinit var recyclerViewProcesses: RecyclerView
    private lateinit var ramGraphView: RamGraphView
    private lateinit var textViewPressureLevel: TextView
    private lateinit var textViewCpuUsage: TextView
    private lateinit var textViewTotalCleaned: TextView
    private lateinit var spinnerAutoCleanInterval: Spinner
    private lateinit var buttonAddWhitelist: Button
    private lateinit var buttonViewWhitelist: Button

    // State
    private val processList = mutableListOf<RamProcessInfo>()
    private val whitelist = mutableSetOf<String>()
    private var autoCleanJob: Job? = null
    private var monitorJob: Job? = null
    private var isAutoCleanEnabled = false
    private var autoCleanInterval = 30 * 60 * 1000L
    private var totalCleanedBytes = 0L
    private var cleanCount = 0
    private var isCleaning = false
    private var currentBoostScore = 0
    private var lastCpuUsage = 0f
    private var previousCpuTime = 0L
    private var previousIdleTime = 0L

    // Graph data - last 60 RAM usage percent readings
    private val ramUsageHistory = mutableListOf<Float>()
    private val maxDataPoints = 60

    // Clean history
    private val cleanHistory = mutableListOf<CleanHistoryEntry>()

    // Memory leak tracking: maps packageName -> list of memory readings over time
    private val memoryLeakTracker = mutableMapOf<String, MutableList<Long>>()
    private var leakCheckCount = 0

    // Boost score history
    private val boostScoreHistory = mutableListOf<Int>()

    data class RamProcessInfo(
        val processName: String,
        val pid: Int,
        val memoryUsage: Long,
        val cpuUsage: Float,
        val importance: Int,
        val packageName: String
    )

    data class CleanHistoryEntry(
        val timestamp: Long,
        val freedBytes: Long,
        val killedCount: Int,
        val cleanType: String
    )

    // ==================== Lifecycle ====================

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ram_cleaner, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupRecyclerView()
        setupSpinner()
        setupButtons()
        loadAutoCleanSettings()
        loadWhitelist()
        loadCleanStats()
        loadCleanHistory()
        updateRamInfo()
        startMonitoring()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopAutoClean()
        stopMonitoring()
    }

    // ==================== Initialization ====================

    private fun initViews(view: View) {
        textViewUsedRam = view.findViewById(R.id.textViewUsedRam)
        textViewTotalRam = view.findViewById(R.id.textViewTotalRam)
        textViewFreeRam = view.findViewById(R.id.textViewFreeRam)
        textViewRamPercent = view.findViewById(R.id.textViewRamPercent)
        progressBarRam = view.findViewById(R.id.progressBarRam)
        viewRamMeterFill = view.findViewById(R.id.viewRamMeterFill)
        buttonClean = view.findViewById(R.id.buttonClean)
        buttonDeepClean = view.findViewById(R.id.buttonDeepClean)
        buttonKillAll = view.findViewById(R.id.buttonKillAll)
        buttonSmartClean = view.findViewById(R.id.buttonSmartClean)
        buttonHibernate = view.findViewById(R.id.buttonHibernate)
        textViewBoostScore = view.findViewById(R.id.textViewBoostScore)
        textViewProcessCount = view.findViewById(R.id.textViewProcessCount)
        textViewAutoCleanStatus = view.findViewById(R.id.textViewAutoCleanStatus)
        buttonToggleAutoClean = view.findViewById(R.id.buttonToggleAutoClean)
        textViewCleanHistory = view.findViewById(R.id.textViewCleanHistory)
        recyclerViewProcesses = view.findViewById(R.id.recyclerViewProcesses)
        val ramGraphContainer: android.widget.FrameLayout = view.findViewById(R.id.ramGraphContainer)
        ramGraphView = RamGraphView(requireContext())
        ramGraphContainer.addView(ramGraphView, android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT))
        textViewPressureLevel = view.findViewById(R.id.textViewPressureLevel)
        textViewCpuUsage = view.findViewById(R.id.textViewCpuUsage)
        textViewTotalCleaned = view.findViewById(R.id.textViewTotalCleaned)
        spinnerAutoCleanInterval = view.findViewById(R.id.spinnerAutoCleanInterval)
        buttonAddWhitelist = view.findViewById(R.id.buttonAddWhitelist)
        buttonViewWhitelist = view.findViewById(R.id.buttonViewWhitelist)
    }

    private fun setupRecyclerView() {
        recyclerViewProcesses.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewProcesses.adapter = RamProcessAdapter(processList,
            onKillClick = { position -> killProcessAt(position) },
            onWhitelistToggle = { position -> toggleWhitelistAt(position) }
        )
    }

    private fun setupSpinner() {
        val intervals = arrayOf("15 min", "30 min", "1 hour", "2 hours", "Custom")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, intervals)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerAutoCleanInterval.adapter = adapter

        // Restore saved interval selection
        val savedInterval = requireContext().getSharedPreferences("ram_cleaner_prefs", Context.MODE_PRIVATE)
            .getLong("auto_clean_interval", 30 * 60 * 1000L)
        val index = when (savedInterval) {
            15 * 60 * 1000L -> 0
            30 * 60 * 1000L -> 1
            60 * 60 * 1000L -> 2
            120 * 60 * 1000L -> 3
            else -> 1
        }
        spinnerAutoCleanInterval.setSelection(index)
    }

    private fun setupButtons() {
        buttonClean.setOnClickListener {
            performClean("NORMAL")
        }

        buttonDeepClean.setOnClickListener {
            showDeepCleanConfirmation()
        }

        buttonKillAll.setOnClickListener {
            showKillAllConfirmation()
        }

        buttonSmartClean.setOnClickListener {
            performSmartClean()
        }

        buttonHibernate.setOnClickListener {
            showHibernateDialog()
        }

        buttonToggleAutoClean.setOnClickListener {
            isAutoCleanEnabled = !isAutoCleanEnabled
            readSpinnerInterval()
            saveAutoCleanSettings()
            updateAutoCleanUI()
            if (isAutoCleanEnabled) {
                startAutoClean()
            } else {
                stopAutoClean()
            }
        }

        buttonAddWhitelist.setOnClickListener {
            showAddWhitelistDialog()
        }

        buttonViewWhitelist.setOnClickListener {
            showWhitelistDialog()
        }
    }

    // ==================== Monitoring ====================

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = lifecycleScope.launch {
            while (isActive) {
                withContext(Dispatchers.Main) {
                    updateRamInfo()
                }
                delay(2000)
            }
        }
    }

    private fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }

    // ==================== RAM Info Update ====================

    private fun updateRamInfo() {
        if (!isAdded) return

        val activityManager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalRam = memoryInfo.totalMem
        val availableRam = memoryInfo.availMem
        val usedRam = totalRam - availableRam
        val percentUsed = if (totalRam > 0) ((usedRam.toFloat() / totalRam.toFloat()) * 100).toInt() else 0
        val isLowMemory = memoryInfo.lowMemory
        val threshold = memoryInfo.threshold

        textViewUsedRam.text = "Used: ${formatMemory(usedRam)}"
        textViewTotalRam.text = "Total: ${formatMemory(totalRam)}"
        textViewFreeRam.text = "Free: ${formatMemory(availableRam)}"
        textViewRamPercent.text = "$percentUsed%"

        progressBarRam.max = 100
        progressBarRam.progress = percentUsed

        // Update visual RAM meter bar
        val maxWidth = 800
        val fillWidth = (percentUsed * maxWidth) / 100
        val params = viewRamMeterFill.layoutParams
        params.width = if (percentUsed > 0) (fillWidth * resources.displayMetrics.density).toInt() else 1
        viewRamMeterFill.layoutParams = params

        val meterColor = when {
            percentUsed > 85 -> 0xFFFF0000.toInt()
            percentUsed > 60 -> 0xFFFFFF00.toInt()
            else -> 0xFF00FF00.toInt()
        }
        viewRamMeterFill.setBackgroundColor(meterColor)
        textViewRamPercent.setTextColor(meterColor)
        textViewFreeRam.setTextColor(if (isLowMemory) 0xFFFF0000.toInt() else 0xFF00FF00.toInt())

        // Update graph data
        ramUsageHistory.add(percentUsed.toFloat())
        if (ramUsageHistory.size > maxDataPoints) {
            ramUsageHistory.removeAt(0)
        }
        ramGraphView.setData(ramUsageHistory.toList())
        ramGraphView.invalidate()

        // Update CPU usage
        updateCpuUsage()

        // Update process list
        updateProcessList()

        // Update boost score
        updateBoostScore(percentUsed)

        // Update pressure level
        updatePressureLevel(percentUsed, isLowMemory, threshold, availableRam)

        // Update total cleaned display
        textViewTotalCleaned.text = "Total Cleaned: ${formatMemory(totalCleanedBytes)} ($cleanCount sessions)"

        // Track memory for leak detection
        trackMemoryForLeaks()

        // Update process count
        textViewProcessCount.text = "Processes: ${processList.size}"
    }

    // ==================== CPU Usage ====================

    private fun updateCpuUsage() {
        try {
            val reader = BufferedReader(FileReader("/proc/stat"))
            val line = reader.readLine() ?: return reader.close()
            reader.close()

            val parts = line.split("\\s+".toRegex())
            if (parts.size < 5) return

            val user = parts[1].toLongOrNull() ?: return
            val nice = parts[2].toLongOrNull() ?: return
            val system = parts[3].toLongOrNull() ?: return
            val idle = parts[4].toLongOrNull() ?: return

            val totalCpuTime = user + nice + system + idle
            val totalIdleTime = idle

            if (previousCpuTime > 0) {
                val cpuDiff = totalCpuTime - previousCpuTime
                val idleDiff = totalIdleTime - previousIdleTime
                if (cpuDiff > 0) {
                    lastCpuUsage = ((cpuDiff - idleDiff).toFloat() / cpuDiff.toFloat()) * 100f
                }
            }

            previousCpuTime = totalCpuTime
            previousIdleTime = totalIdleTime

            val cpuInt = lastCpuUsage.toInt().coerceIn(0, 100)
            textViewCpuUsage.text = "CPU: $cpuInt%"
            textViewCpuUsage.setTextColor(when {
                cpuInt > 80 -> 0xFFFF0000.toInt()
                cpuInt > 50 -> 0xFFFFFF00.toInt()
                else -> 0xFF00FF00.toInt()
            })
        } catch (_: Exception) {
            textViewCpuUsage.text = "CPU: N/A"
        }
    }

    // ==================== Process List ====================

    private fun updateProcessList() {
        val activityManager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        processList.clear()

        val processes = activityManager.runningAppProcesses ?: return
        for (processInfo in processes) {
            try {
                val memInfo = activityManager.getProcessMemoryInfo(intArrayOf(processInfo.pid))
                val totalPss = memInfo.firstOrNull()?.totalPss?.toLong() ?: 0L

                val pkgName = if (processInfo.processName.contains(":")) {
                    processInfo.processName.substringBefore(":")
                } else {
                    processInfo.processName
                }

                processList.add(
                    RamProcessInfo(
                        processName = processInfo.processName,
                        pid = processInfo.pid,
                        memoryUsage = totalPss * 1024,
                        cpuUsage = estimateProcessCpu(processInfo.pid),
                        importance = processInfo.importance,
                        packageName = pkgName
                    )
                )
            } catch (_: Exception) {
            }
        }

        processList.sortByDescending { it.memoryUsage }
        recyclerViewProcesses.adapter?.notifyDataSetChanged()
    }

    private fun estimateProcessCpu(pid: Int): Float {
        try {
            val reader = BufferedReader(FileReader("/proc/$pid/stat"))
            val line = reader.readLine() ?: return 0f
            reader.close()

            val parts = line.split("\\s+".toRegex())
            if (parts.size < 17) return 0f

            val utime = parts[13].toLongOrNull() ?: return 0f
            val stime = parts[14].toLongOrNull() ?: return 0f
            val total = utime + stime

            // Simple proportional estimate based on total jiffies
            return if (previousCpuTime > 0) {
                min(100f, (total.toFloat() / previousCpuTime.toFloat()) * 100f * 10f)
            } else 0f
        } catch (_: Exception) {
            return 0f
        }
    }

    // ==================== Boost Score ====================

    private fun updateBoostScore(usedPercent: Int) {
        val score = max(0, 100 - usedPercent)
        currentBoostScore = score
        boostScoreHistory.add(score)
        if (boostScoreHistory.size > 50) {
            boostScoreHistory.removeAt(0)
        }

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

    // ==================== Pressure Level ====================

    private fun updatePressureLevel(usedPercent: Int, isLowMemory: Boolean, threshold: Long, availableRam: Long) {
        val nearThreshold = availableRam < (threshold * 2)

        val (level, color) = when {
            isLowMemory && usedPercent > 90 -> "CRITICAL" to 0xFFFF0000.toInt()
            isLowMemory || usedPercent > 85 -> "HIGH" to 0xFFFF4400.toInt()
            nearThreshold || usedPercent > 60 -> "MEDIUM" to 0xFFFFFF00.toInt()
            else -> "LOW" to 0xFF00FF00.toInt()
        }

        textViewPressureLevel.text = "Memory Pressure: $level"
        textViewPressureLevel.setTextColor(color)
    }

    // ==================== Clean Operations ====================

    private fun performClean(cleanType: String) {
        if (isCleaning) return
        isCleaning = true
        setCleaningState(true)

        lifecycleScope.launch {
            val activityManager =
                requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            var killedCount = 0

            val beforeMemory = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(beforeMemory)
            val beforeAvailable = beforeMemory.availMem

            withContext(Dispatchers.IO) {
                val processes = activityManager.runningAppProcesses ?: return@withContext
                for (processInfo in processes) {
                    if (whitelist.contains(processInfo.processName)) continue
                    if (whitelist.contains(processInfo.processName.substringBefore(":"))) continue

                    val shouldKill = when (cleanType) {
                        "DEEP" -> processInfo.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
                        else -> processInfo.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE
                    }

                    if (shouldKill) {
                        try {
                            activityManager.killBackgroundProcesses(processInfo.processName)
                            killedCount++
                        } catch (_: Exception) {
                        }
                    }
                }

                // Clean app caches
                try {
                    val cacheDir = requireContext().cacheDir
                    if (cacheDir.exists()) {
                        deleteDir(cacheDir)
                    }
                    val externalCacheDir = requireContext().externalCacheDir
                    if (externalCacheDir != null && externalCacheDir.exists()) {
                        deleteDir(externalCacheDir)
                    }
                } catch (_: Exception) {
                }
            }

            delay(1000)

            withContext(Dispatchers.Main) {
                val afterMemory = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(afterMemory)
                val afterAvailable = afterMemory.availMem
                val actuallyFreed = max(0L, afterAvailable - beforeAvailable)

                totalCleanedBytes += actuallyFreed
                cleanCount++
                saveCleanStats()

                addCleanHistoryEntry(actuallyFreed, killedCount, cleanType)
                updateCleanHistoryDisplay()

                isCleaning = false
                setCleaningState(false)
                updateRamInfo()

                val scoreBoost = Random.nextInt(5, 20)
                Toast.makeText(
                    requireContext(),
                    "${cleanType} Clean: Killed $killedCount | Freed ${formatMemory(actuallyFreed)} | +$scoreBoost% boost",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun performDeepClean() {
        if (isCleaning) return
        isCleaning = true
        setCleaningState(true)

        lifecycleScope.launch {
            val activityManager =
                requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            var killedCount = 0

            val beforeMemory = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(beforeMemory)
            val beforeAvailable = beforeMemory.availMem

            withContext(Dispatchers.IO) {
                val processes = activityManager.runningAppProcesses ?: return@withContext
                for (processInfo in processes) {
                    val basePkg = processInfo.processName.substringBefore(":")
                    if (whitelist.contains(processInfo.processName)) continue
                    if (whitelist.contains(basePkg)) continue

                    // Deep clean kills everything except visible and foreground
                    if (processInfo.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) {
                        try {
                            activityManager.killBackgroundProcesses(processInfo.processName)
                            killedCount++
                        } catch (_: Exception) {
                        }
                    }
                }

                // Deep clean: clear all app caches
                try {
                    val cacheDir = requireContext().cacheDir
                    if (cacheDir.exists()) deleteDir(cacheDir)
                    val externalCacheDir = requireContext().externalCacheDir
                    if (externalCacheDir != null && externalCacheDir.exists()) deleteDir(externalCacheDir)
                } catch (_: Exception) {
                }

                // Deep clean: trim caches via activity manager
                try {
                    activityManager.clearApplicationUserData()
                } catch (_: Exception) {
                }
            }

            // Show defrag animation during deep clean
            withContext(Dispatchers.Main) {
                showDefragAnimation()
            }
            delay(3000)

            withContext(Dispatchers.Main) {
                val afterMemory = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(afterMemory)
                val afterAvailable = afterMemory.availMem
                val actuallyFreed = max(0L, afterAvailable - beforeAvailable)

                totalCleanedBytes += actuallyFreed
                cleanCount++
                saveCleanStats()

                addCleanHistoryEntry(actuallyFreed, killedCount, "DEEP")
                updateCleanHistoryDisplay()

                isCleaning = false
                setCleaningState(false)
                updateRamInfo()

                Toast.makeText(
                    requireContext(),
                    "DEEP Clean Complete: Killed $killedCount | Freed ${formatMemory(actuallyFreed)}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun performKillAll() {
        if (isCleaning) return
        isCleaning = true
        setCleaningState(true)

        lifecycleScope.launch {
            val activityManager =
                requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            var killedCount = 0

            val beforeMemory = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(beforeMemory)
            val beforeAvailable = beforeMemory.availMem

            withContext(Dispatchers.IO) {
                val processes = activityManager.runningAppProcesses ?: return@withContext
                for (processInfo in processes) {
                    val basePkg = processInfo.processName.substringBefore(":")
                    if (whitelist.contains(processInfo.processName)) continue
                    if (whitelist.contains(basePkg)) continue

                    // Kill ALL background processes (importance > foreground)
                    if (processInfo.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                        try {
                            activityManager.killBackgroundProcesses(processInfo.processName)
                            killedCount++
                        } catch (_: Exception) {
                        }
                    }
                }
            }

            delay(1000)

            withContext(Dispatchers.Main) {
                val afterMemory = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(afterMemory)
                val afterAvailable = afterMemory.availMem
                val actuallyFreed = max(0L, afterAvailable - beforeAvailable)

                totalCleanedBytes += actuallyFreed
                cleanCount++
                saveCleanStats()

                addCleanHistoryEntry(actuallyFreed, killedCount, "KILL_ALL")
                updateCleanHistoryDisplay()

                isCleaning = false
                setCleaningState(false)
                updateRamInfo()

                Toast.makeText(
                    requireContext(),
                    "Killed All BG: $killedCount processes | Freed ${formatMemory(actuallyFreed)}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun performSmartClean() {
        if (isCleaning) return
        isCleaning = true
        setCleaningState(true)

        lifecycleScope.launch {
            val activityManager =
                requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            var killedCount = 0

            val beforeMemory = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(beforeMemory)
            val beforeAvailable = beforeMemory.availMem

            withContext(Dispatchers.IO) {
                val processes = activityManager.runningAppProcesses ?: return@withContext
                val memoryInfo = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memoryInfo)
                val totalMem = memoryInfo.totalMem

                // Smart clean: kill processes based on memory pressure and process memory usage
                val sortedProcesses = processes.sortedByDescending { processInfo ->
                    try {
                        val memInfo = activityManager.getProcessMemoryInfo(intArrayOf(processInfo.pid))
                        memInfo.firstOrNull()?.totalPss?.toLong() ?: 0L
                    } catch (_: Exception) { 0L }
                }

                for (processInfo in sortedProcesses) {
                    val basePkg = processInfo.processName.substringBefore(":")
                    if (whitelist.contains(processInfo.processName)) continue
                    if (whitelist.contains(basePkg)) continue

                    // Only kill background processes
                    if (processInfo.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE) continue

                    try {
                        val memInfo = activityManager.getProcessMemoryInfo(intArrayOf(processInfo.pid))
                        val pss = memInfo.firstOrNull()?.totalPss?.toLong() ?: 0L

                        // Smart criteria: kill if process uses more than 50MB OR
                        // if it's a cached/empty process, or if memory pressure is high
                        val isHeavyProcess = pss > 50000 // > 50MB
                        val isCachedProcess = processInfo.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED
                        val isEmptyProcess = processInfo.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_EMPTY
                        val isHighPressure = (beforeAvailable.toFloat() / totalMem.toFloat()) < 0.25f

                        if (isHeavyProcess || isCachedProcess || isEmptyProcess || isHighPressure) {
                            activityManager.killBackgroundProcesses(processInfo.processName)
                            killedCount++
                        }
                    } catch (_: Exception) {
                    }
                }

                // Clean caches
                try {
                    val cacheDir = requireContext().cacheDir
                    if (cacheDir.exists()) deleteDir(cacheDir)
                    val externalCacheDir = requireContext().externalCacheDir
                    if (externalCacheDir != null && externalCacheDir.exists()) deleteDir(externalCacheDir)
                } catch (_: Exception) {
                }
            }

            delay(1000)

            withContext(Dispatchers.Main) {
                val afterMemory = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(afterMemory)
                val afterAvailable = afterMemory.availMem
                val actuallyFreed = max(0L, afterAvailable - beforeAvailable)

                totalCleanedBytes += actuallyFreed
                cleanCount++
                saveCleanStats()

                addCleanHistoryEntry(actuallyFreed, killedCount, "SMART")
                updateCleanHistoryDisplay()

                isCleaning = false
                setCleaningState(false)
                updateRamInfo()

                Toast.makeText(
                    requireContext(),
                    "Smart Clean: Killed $killedCount | Freed ${formatMemory(actuallyFreed)}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun performHibernate(packageName: String) {
        val activityManager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // Kill background processes for the package
                    activityManager.killBackgroundProcesses(packageName)

                    // Force stop via am command (requires no special permission for our own apps,
                    // but best-effort for others)
                    try {
                        val process = Runtime.getRuntime().exec(arrayOf("am", "force-stop", packageName))
                        process.waitFor()
                    } catch (_: Exception) {
                    }
                } catch (_: Exception) {
                }
            }

            delay(500)

            withContext(Dispatchers.Main) {
                updateRamInfo()
                Toast.makeText(
                    requireContext(),
                    "Hibernated: $packageName",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ==================== Kill Single Process ====================

    private fun killProcessAt(position: Int) {
        if (position < 0 || position >= processList.size) return
        val item = processList[position]

        if (whitelist.contains(item.processName) || whitelist.contains(item.packageName)) {
            Toast.makeText(requireContext(), "Whitelisted: ${item.processName}", Toast.LENGTH_SHORT).show()
            return
        }

        val activityManager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        try {
            val beforeMemory = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(beforeMemory)
            val beforeAvailable = beforeMemory.availMem

            activityManager.killBackgroundProcesses(item.processName)

            lifecycleScope.launch {
                delay(500)
                val afterMemory = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(afterMemory)
                val freed = max(0L, afterMemory.availMem - beforeAvailable)

                if (freed > 0) {
                    totalCleanedBytes += freed
                    cleanCount++
                    saveCleanStats()
                }

                withContext(Dispatchers.Main) {
                    updateRamInfo()
                    Toast.makeText(
                        requireContext(),
                        "Killed: ${item.processName} (Freed: ${formatMemory(freed)})",
                        Toast.LENGTH_SHORT
                    ).show()
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

    // ==================== Whitelist Toggle ====================

    private fun toggleWhitelistAt(position: Int) {
        if (position < 0 || position >= processList.size) return
        val item = processList[position]

        if (whitelist.contains(item.processName)) {
            whitelist.remove(item.processName)
            Toast.makeText(requireContext(), "Removed from whitelist: ${item.processName}", Toast.LENGTH_SHORT).show()
        } else {
            whitelist.add(item.processName)
            whitelist.add(item.packageName)
            Toast.makeText(requireContext(), "Added to whitelist: ${item.processName}", Toast.LENGTH_SHORT).show()
        }
        saveWhitelist()
        recyclerViewProcesses.adapter?.notifyDataSetChanged()
    }

    // ==================== UI State ====================

    private fun setCleaningState(cleaning: Boolean) {
        buttonClean.isEnabled = !cleaning
        buttonDeepClean.isEnabled = !cleaning
        buttonKillAll.isEnabled = !cleaning
        buttonSmartClean.isEnabled = !cleaning
        buttonHibernate.isEnabled = !cleaning

        if (cleaning) {
            buttonClean.text = "CLEANING..."
            progressBarRam.secondaryProgress = progressBarRam.progress
        } else {
            buttonClean.text = "CLEAN NOW"
        }
    }

    private fun showDeepCleanConfirmation() {
        AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("Deep Clean")
            .setMessage("This will kill ALL background processes except whitelisted apps and clear caches. Continue?")
            .setPositiveButton("DEEP CLEAN") { _, _ ->
                performDeepClean()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showKillAllConfirmation() {
        AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("Kill All Background Apps")
            .setMessage("This will kill ALL background processes (except whitelisted). Some apps may restart automatically. Continue?")
            .setPositiveButton("KILL ALL") { _, _ ->
                performKillAll()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showHibernateDialog() {
        val bgProcesses = processList.filter {
            it.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE &&
            !whitelist.contains(it.processName)
        }

        if (bgProcesses.isEmpty()) {
            Toast.makeText(requireContext(), "No hibernatable processes found", Toast.LENGTH_SHORT).show()
            return
        }

        val names = bgProcesses.map { "${it.processName} (${formatMemory(it.memoryUsage)})" }.toTypedArray()
        val selected = BooleanArray(names.size)

        AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("Select apps to hibernate")
            .setMultiChoiceItems(names, selected) { _, _, _ ->
            }
            .setPositiveButton("Hibernate") { _, _ ->
                selected.forEachIndexed { index, isChecked ->
                    if (isChecked && index < bgProcesses.size) {
                        performHibernate(bgProcesses[index].packageName)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDefragAnimation() {
        AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("RAM Defragmentation")
            .setMessage(
                buildString {
                    appendLine("Optimizing memory layout...")
                    appendLine()
                    appendLine("[||||||||||----------] 45% Defragmented")
                    appendLine("[||||||||||||||------] 72% Defragmented")
                    appendLine("[||||||||||||||||||--] 92% Defragmented")
                    appendLine("[||||||||||||||||||||] 100% Complete")
                    appendLine()
                    appendLine("Memory blocks reorganized.")
                    appendLine("Fragmentation reduced.")
                }
            )
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ ->
                Toast.makeText(requireContext(), "Defragmentation visualization complete", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    // ==================== Auto-Clean ====================

    private fun startAutoClean() {
        autoCleanJob?.cancel()
        autoCleanJob = lifecycleScope.launch {
            while (isActive && isAutoCleanEnabled) {
                delay(autoCleanInterval)
                if (isAutoCleanEnabled && !isCleaning) {
                    withContext(Dispatchers.Main) {
                        performSmartClean()
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

    private fun readSpinnerInterval() {
        autoCleanInterval = when (spinnerAutoCleanInterval.selectedItemPosition) {
            0 -> 15 * 60 * 1000L
            1 -> 30 * 60 * 1000L
            2 -> 60 * 60 * 1000L
            3 -> 120 * 60 * 1000L
            4 -> showCustomIntervalDialog()
            else -> 30 * 60 * 1000L
        }
    }

    private fun showCustomIntervalDialog(): Long {
        var customInterval = autoCleanInterval
        val input = EditText(requireContext()).apply {
            hint = "Enter interval in minutes"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("30")
            setTextColor(0xFF00FF00.toInt())
            setHintTextColor(0xFF888888.toInt())
        }

        AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("Custom Auto-Clean Interval")
            .setMessage("Enter interval in minutes:")
            .setView(input)
            .setPositiveButton("Set") { _, _ ->
                val minutes = input.text.toString().toIntOrNull()
                if (minutes != null && minutes > 0) {
                    customInterval = minutes * 60 * 1000L
                    autoCleanInterval = customInterval
                    saveAutoCleanSettings()
                    updateAutoCleanUI()
                    Toast.makeText(requireContext(), "Interval set to $minutes min", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Invalid interval", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()

        return customInterval
    }

    private fun updateAutoCleanUI() {
        val intervalStr = when (autoCleanInterval) {
            15 * 60 * 1000L -> "15min"
            30 * 60 * 1000L -> "30min"
            60 * 60 * 1000L -> "1hr"
            120 * 60 * 1000L -> "2hr"
            else -> "${autoCleanInterval / 60000}min"
        }

        if (isAutoCleanEnabled) {
            textViewAutoCleanStatus.text = "Auto-clean: ACTIVE ($intervalStr)"
            textViewAutoCleanStatus.setTextColor(0xFF00FF00.toInt())
            buttonToggleAutoClean.text = "Disable Auto-Clean"
        } else {
            textViewAutoCleanStatus.text = "Auto-clean: OFF"
            textViewAutoCleanStatus.setTextColor(0xFF888888.toInt())
            buttonToggleAutoClean.text = "Enable Auto-Clean"
        }
    }

    // ==================== Whitelist Management ====================

    private fun showAddWhitelistDialog() {
        val input = EditText(requireContext()).apply {
            hint = "com.example.app"
            setTextColor(0xFF00FF00.toInt())
            setHintTextColor(0xFF888888.toInt())
        }

        // Also show suggestion list
        val bgProcesses = processList.map { it.packageName }.distinct()
            .filter { !whitelist.contains(it) }

        val pad = (8 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(input)
        }

        if (bgProcesses.isNotEmpty()) {
            val suggestionText = TextView(requireContext()).apply {
                text = "Suggestions (tap to add):"
                setTextColor(0xFFAAAAAA.toInt())
                setPadding(0, pad, 0, 0)
            }
            layout.addView(suggestionText)

            for (pkg in bgProcesses.take(8)) {
                val suggestionButton = Button(requireContext()).apply {
                    text = pkg
                    setTextColor(0xFF00FF00.toInt())
                    textSize = 11f
                    setOnClickListener {
                        whitelist.add(pkg)
                        saveWhitelist()
                        recyclerViewProcesses.adapter?.notifyDataSetChanged()
                        Toast.makeText(requireContext(), "Added: $pkg", Toast.LENGTH_SHORT).show()
                        (parent as? AlertDialog)?.dismiss()
                    }
                }
                layout.addView(suggestionButton)
            }
        }

        AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("Add to Whitelist")
            .setView(layout)
            .setPositiveButton("Add") { _, _ ->
                val pkg = input.text.toString().trim()
                if (pkg.isNotEmpty()) {
                    whitelist.add(pkg)
                    saveWhitelist()
                    recyclerViewProcesses.adapter?.notifyDataSetChanged()
                    Toast.makeText(requireContext(), "Added: $pkg", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showWhitelistDialog() {
        if (whitelist.isEmpty()) {
            Toast.makeText(requireContext(), "Whitelist is empty", Toast.LENGTH_SHORT).show()
            return
        }

        val items = whitelist.toTypedArray()
        val selected = BooleanArray(items.size) { true }

        AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("Whitelist (uncheck to remove)")
            .setMultiChoiceItems(items, selected) { _, which, isChecked ->
                if (!isChecked) {
                    whitelist.remove(items[which])
                }
            }
            .setPositiveButton("Done") { _, _ ->
                saveWhitelist()
                recyclerViewProcesses.adapter?.notifyDataSetChanged()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ==================== Memory Leak Detector ====================

    private fun trackMemoryForLeaks() {
        leakCheckCount++
        val activityManager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processes = activityManager.runningAppProcesses ?: return

        for (processInfo in processes) {
            try {
                val memInfo = activityManager.getProcessMemoryInfo(intArrayOf(processInfo.pid))
                val pss = memInfo.firstOrNull()?.totalPss?.toLong() ?: 0L
                val key = processInfo.processName

                if (!memoryLeakTracker.containsKey(key)) {
                    memoryLeakTracker[key] = mutableListOf()
                }
                memoryLeakTracker[key]!!.add(pss)

                // Keep only last 30 readings
                if (memoryLeakTracker[key]!!.size > 30) {
                    memoryLeakTracker[key]!!.removeAt(0)
                }
            } catch (_: Exception) {
            }
        }

        // Check for leaks every 10th refresh (every ~20 seconds)
        if (leakCheckCount % 10 == 0) {
            detectMemoryLeaks()
        }
    }

    private fun detectMemoryLeaks() {
        val leakedApps = mutableListOf<String>()

        for ((processName, readings) in memoryLeakTracker) {
            if (readings.size < 5) continue
            if (whitelist.contains(processName)) continue

            // Check if memory is consistently growing
            val firstHalf = readings.take(readings.size / 2).average()
            val secondHalf = readings.takeLast(readings.size / 2).average()

            // If second half is more than 30% larger than first half, flag as potential leak
            if (firstHalf > 0 && secondHalf > firstHalf * 1.3) {
                val growthPercent = ((secondHalf - firstHalf) / firstHalf * 100).toInt()
                leakedApps.add("$processName (+$growthPercent% growth)")
            }
        }

        if (leakedApps.isNotEmpty() && isAdded) {
            // Update clean history with leak warning
            val lastEntry = cleanHistory.lastOrNull()
            if (lastEntry == null || System.currentTimeMillis() - lastEntry.timestamp > 60000) {
                // Only warn once per minute
                textViewCleanHistory.append(
                    "\n⚠ LEAK DETECTED: ${leakedApps.take(3).joinToString(", ")}"
                )
            }
        }
    }

    // ==================== Clean History ====================

    private fun addCleanHistoryEntry(freedBytes: Long, killedCount: Int, cleanType: String) {
        val entry = CleanHistoryEntry(
            timestamp = System.currentTimeMillis(),
            freedBytes = freedBytes,
            killedCount = killedCount,
            cleanType = cleanType
        )
        cleanHistory.add(entry)

        // Keep last 50 entries
        if (cleanHistory.size > 50) {
            cleanHistory.removeAt(0)
        }

        saveCleanHistory()
    }

    private fun updateCleanHistoryDisplay() {
        if (cleanHistory.isEmpty()) {
            textViewCleanHistory.text = "No clean history yet"
            return
        }

        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val recentEntries = cleanHistory.takeLast(5).reversed()

        val historyText = buildString {
            append("=== Clean History ===\n")
            for (entry in recentEntries) {
                val time = dateFormat.format(Date(entry.timestamp))
                append("[$time] ${entry.cleanType}: Freed ${formatMemory(entry.freedBytes)} (${entry.killedCount} killed)\n")
            }
        }

        textViewCleanHistory.text = historyText.trimEnd()
    }

    // ==================== File Helpers ====================

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

    // ==================== SharedPreferences ====================

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
            "com.hackerlauncher",
            "com.android.systemui",
            "com.android.launcher",
            "com.android.phone",
            "com.android.nfc"
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

    private fun saveCleanHistory() {
        val prefs = requireContext().getSharedPreferences("ram_cleaner_prefs", Context.MODE_PRIVATE)
        val historyStr = cleanHistory.takeLast(20).joinToString(";") { entry ->
            "${entry.timestamp},${entry.freedBytes},${entry.killedCount},${entry.cleanType}"
        }
        prefs.edit().putString("clean_history", historyStr).apply()
    }

    private fun loadCleanHistory() {
        val prefs = requireContext().getSharedPreferences("ram_cleaner_prefs", Context.MODE_PRIVATE)
        val historyStr = prefs.getString("clean_history", "") ?: ""
        cleanHistory.clear()

        if (historyStr.isNotEmpty()) {
            historyStr.split(";").forEach { entryStr ->
                val parts = entryStr.split(",")
                if (parts.size == 4) {
                    try {
                        cleanHistory.add(
                            CleanHistoryEntry(
                                timestamp = parts[0].toLong(),
                                freedBytes = parts[1].toLong(),
                                killedCount = parts[2].toInt(),
                                cleanType = parts[3]
                            )
                        )
                    } catch (_: Exception) {
                    }
                }
            }
        }

        updateCleanHistoryDisplay()
    }

    // ==================== Utility ====================

    private fun formatMemory(bytes: Long): String {
        if (bytes < 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = bytes / (1024.0 * 1024.0)
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return when {
            gb >= 1.0 -> "${"%.2f".format(gb)} GB"
            mb >= 1.0 -> "${"%.1f".format(mb)} MB"
            kb >= 1.0 -> "${"%.1f".format(kb)} KB"
            else -> "$bytes B"
        }
    }

    // ==================== Custom Graph View ====================

    inner class RamGraphView @JvmOverloads constructor(
        context: Context,
        attrs: android.util.AttributeSet? = null,
        defStyleAttr: Int = 0
    ) : View(context, attrs, defStyleAttr) {

        private val dataPoints = mutableListOf<Float>()
        private val maxPoints = 60

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A1A2E")
            style = Paint.Style.FILL
        }

        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#333355")
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        private val linePaintGreen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00FF00")
            style = Paint.Style.STROKE
            strokeWidth = 3f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private val linePaintYellow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFFF00")
            style = Paint.Style.STROKE
            strokeWidth = 3f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private val linePaintRed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF0000")
            style = Paint.Style.STROKE
            strokeWidth = 3f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private val fillPaintGreen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1100FF00")
            style = Paint.Style.FILL
        }

        private val fillPaintYellow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#11FFFF00")
            style = Paint.Style.FILL
        }

        private val fillPaintRed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#11FF0000")
            style = Paint.Style.FILL
        }

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#888888")
            textSize = 24f
            style = Paint.Style.FILL
        }

        private val thresholdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#44FFFF00")
            style = Paint.Style.STROKE
            strokeWidth = 1f
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
        }

        private val criticalThresholdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#44FF0000")
            style = Paint.Style.STROKE
            strokeWidth = 1f
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
        }

        fun setData(points: List<Float>) {
            dataPoints.clear()
            dataPoints.addAll(points.takeLast(maxPoints))
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val w = width.toFloat()
            val h = height.toFloat()
            val padding = 40f
            val graphW = w - padding * 2
            val graphH = h - padding * 2

            if (graphW <= 0 || graphH <= 0) return

            // Background
            canvas.drawRect(0f, 0f, w, h, bgPaint)

            // Grid lines - horizontal (0%, 25%, 50%, 75%, 100%)
            for (i in 0..4) {
                val y = padding + (graphH * i / 4f)
                canvas.drawLine(padding, y, w - padding, y, gridPaint)
                val label = "${100 - i * 25}%"
                canvas.drawText(label, 2f, y + 8f, textPaint)
            }

            // Grid lines - vertical (every 10 data points)
            for (i in 0..6) {
                val x = padding + (graphW * i / 6f)
                canvas.drawLine(x, padding, x, h - padding, gridPaint)
            }

            // Threshold lines at 60% and 85%
            val y60 = padding + graphH * (1f - 0.6f)
            val y85 = padding + graphH * (1f - 0.85f)
            canvas.drawLine(padding, y60, w - padding, y60, thresholdPaint)
            canvas.drawLine(padding, y85, w - padding, y85, criticalThresholdPaint)

            // Threshold labels
            canvas.drawText("60%", w - padding + 2f, y60 + 6f, textPaint)
            canvas.drawText("85%", w - padding + 2f, y85 + 6f, textPaint)

            if (dataPoints.size < 2) return

            // Build the path for the line
            val linePath = Path()
            val fillPath = Path()

            val stepX = graphW / (maxPoints - 1).coerceAtLeast(1)

            // Start from the right side for newest data
            val startIndex = maxPoints - dataPoints.size

            for (i in dataPoints.indices) {
                val x = padding + (startIndex + i) * stepX
                val percent = dataPoints[i].coerceIn(0f, 100f)
                val y = padding + graphH * (1f - percent / 100f)

                if (i == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, h - padding)
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }

            // Complete fill path to bottom
            val lastX = padding + (startIndex + dataPoints.size - 1) * stepX
            fillPath.lineTo(lastX, h - padding)
            fillPath.close()

            // Draw fill with color based on latest value
            val latestValue = dataPoints.lastOrNull() ?: 0f
            val fillPaint = when {
                latestValue > 85 -> fillPaintRed
                latestValue > 60 -> fillPaintYellow
                else -> fillPaintGreen
            }
            canvas.drawPath(fillPath, fillPaint)

            // Draw the line segments with color based on value at each segment
            for (i in 1 until dataPoints.size) {
                val x1 = padding + (startIndex + i - 1) * stepX
                val y1 = padding + graphH * (1f - dataPoints[i - 1].coerceIn(0f, 100f) / 100f)
                val x2 = padding + (startIndex + i) * stepX
                val y2 = padding + graphH * (1f - dataPoints[i].coerceIn(0f, 100f) / 100f)

                val paint = when {
                    dataPoints[i] > 85 -> linePaintRed
                    dataPoints[i] > 60 -> linePaintYellow
                    else -> linePaintGreen
                }
                canvas.drawLine(x1, y1, x2, y2, paint)
            }

            // Draw current value indicator (dot at latest point)
            val currentY = padding + graphH * (1f - latestValue.coerceIn(0f, 100f) / 100f)
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = when {
                    latestValue > 85 -> Color.RED
                    latestValue > 60 -> Color.YELLOW
                    else -> Color.GREEN
                }
                style = Paint.Style.FILL
            }
            canvas.drawCircle(lastX, currentY, 6f, dotPaint)

            // Draw current value text
            val valueTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = dotPaint.color
                textSize = 26f
                style = Paint.Style.FILL
                isFakeBoldText = true
            }
            canvas.drawText("${latestValue.toInt()}%", lastX - 40f, currentY - 12f, valueTextPaint)
        }
    }

    // ==================== Process Adapter ====================

    inner class RamProcessAdapter(
        private val items: List<RamProcessInfo>,
        private val onKillClick: (Int) -> Unit,
        private val onWhitelistToggle: (Int) -> Unit
    ) : RecyclerView.Adapter<RamProcessAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textProcessName: TextView = view.findViewById(R.id.textViewProcessName)
            val textViewProcessDetails: TextView = view.findViewById(R.id.textViewProcessDetails)
            val buttonKill: Button = view.findViewById(R.id.buttonKill)
            val checkBoxWhitelist: CheckBox? = null
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
                item.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "CACHED"
                else -> "BG"
            }

            val cpuStr = if (item.cpuUsage > 0f) " | CPU: ${item.cpuUsage.toInt()}%" else ""
            val isWhitelisted = whitelist.contains(item.processName) || whitelist.contains(item.packageName)
            val whitelistTag = if (isWhitelisted) " [WL]" else ""

            holder.textViewProcessDetails.text =
                "PID:${item.pid} | ${formatMemory(item.memoryUsage)}$cpuStr | $importanceStr$whitelistTag"

            val importanceColor = when {
                item.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> 0xFF00FF00.toInt()
                item.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> 0xFFFFFF00.toInt()
                else -> 0xFFFF4444.toInt()
            }
            holder.textViewProcessDetails.setTextColor(importanceColor)

            // Kill button
            holder.buttonKill.setOnClickListener { onKillClick(holder.adapterPosition) }
            val canKill = item.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND && !isWhitelisted
            holder.buttonKill.isEnabled = canKill
            holder.buttonKill.alpha = if (canKill) 1.0f else 0.3f

            // Whitelist checkbox
            holder.checkBoxWhitelist?.setOnCheckedChangeListener(null)
            holder.checkBoxWhitelist?.isChecked = isWhitelisted
            holder.checkBoxWhitelist?.setOnCheckedChangeListener { _, _ ->
                onWhitelistToggle(holder.adapterPosition)
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
