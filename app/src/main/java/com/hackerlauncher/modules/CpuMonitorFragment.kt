package com.hackerlauncher.modules

import android.app.ActivityManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
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
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

data class CpuProcessInfo(
    val processName: String,
    val pid: Int,
    val cpuUsage: Float,
    val memoryUsage: Long
)

class CpuGraphView(context: Context) : View(context) {

    private val dataPoints = mutableListOf<Float>()
    private val maxPoints = 60

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        alpha = 40
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.parseColor("#333333")
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        color = Color.parseColor("#888888")
    }

    private val path = Path()
    private val fillPath = Path()

    fun addDataPoint(cpuPercent: Float) {
        dataPoints.add(cpuPercent.coerceIn(0f, 100f))
        if (dataPoints.size > maxPoints) {
            dataPoints.removeAt(0)
        }
        invalidate()
    }

    fun clearData() {
        dataPoints.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val padding = 40f

        // Draw grid lines
        for (i in 0..4) {
            val y = padding + (h - 2 * padding) * i / 4f
            canvas.drawLine(padding, y, w - padding, y, gridPaint)
            val label = "${100 - i * 25}%"
            canvas.drawText(label, 2f, y + 8f, labelPaint)
        }

        // Draw bottom axis line
        canvas.drawLine(padding, h - padding, w - padding, h - padding, gridPaint)

        if (dataPoints.size < 2) return

        val drawWidth = w - 2 * padding
        val drawHeight = h - 2 * padding
        val step = drawWidth / (maxPoints - 1)

        // Determine color based on latest value
        val latestCpu = dataPoints.last()
        val lineColor = when {
            latestCpu < 50f -> Color.parseColor("#00FF00")
            latestCpu < 80f -> Color.parseColor("#FFFF00")
            else -> Color.parseColor("#FF4444")
        }
        linePaint.color = lineColor
        fillPaint.color = lineColor

        path.reset()
        fillPath.reset()

        val startX = padding + (maxPoints - dataPoints.size) * step
        val firstY = h - padding - (dataPoints[0] / 100f) * drawHeight
        path.moveTo(startX, firstY)
        fillPath.moveTo(startX, h - padding)
        fillPath.lineTo(startX, firstY)

        for (i in 1 until dataPoints.size) {
            val x = startX + i * step
            val y = h - padding - (dataPoints[i] / 100f) * drawHeight
            path.lineTo(x, y)
            fillPath.lineTo(x, y)
        }

        // Close fill path
        fillPath.lineTo(startX + (dataPoints.size - 1) * step, h - padding)
        fillPath.close()

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(path, linePaint)
    }
}

class CpuMonitorFragment : Fragment() {

    private lateinit var textViewCpuUsage: TextView
    private lateinit var textViewCpuFreq: TextView
    private lateinit var textViewCpuTemp: TextView
    private lateinit var textViewCpuCores: TextView
    private lateinit var textViewCpuGovernor: TextView
    private lateinit var cpuGraphView: CpuGraphView
    private lateinit var recyclerViewCpuProcesses: RecyclerView
    private lateinit var progressBarCpu: ProgressBar
    private lateinit var buttonKillHighCpu: Button

    private val processList = mutableListOf<CpuProcessInfo>()
    private var monitorJob: Job? = null
    private var prevCpuTime: Long = 0
    private var prevIdleTime: Long = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_cpu_monitor, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        textViewCpuUsage = view.findViewById(R.id.textViewCpuUsage)
        textViewCpuFreq = view.findViewById(R.id.textViewCpuFreq)
        textViewCpuTemp = view.findViewById(R.id.textViewCpuTemp)
        textViewCpuCores = view.findViewById(R.id.textViewCpuCores)
        textViewCpuGovernor = view.findViewById(R.id.textViewCpuGovernor)
        cpuGraphView = view.findViewById(R.id.cpuGraphView)
        recyclerViewCpuProcesses = view.findViewById(R.id.recyclerViewCpuProcesses)
        progressBarCpu = view.findViewById(R.id.progressBarCpu)
        buttonKillHighCpu = view.findViewById(R.id.buttonKillHighCpu)

        setupRecyclerView()
        setupButtons()
        displayStaticInfo()
        startMonitoring()
    }

    private fun setupRecyclerView() {
        recyclerViewCpuProcesses.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewCpuProcesses.adapter = CpuProcessAdapter(processList) { position ->
            showKillConfirmDialog(position)
        }
    }

    private fun setupButtons() {
        buttonKillHighCpu.setOnClickListener {
            showKillHighCpuDialog()
        }
    }

    private fun displayStaticInfo() {
        val cores = Runtime.getRuntime().availableProcessors()
        textViewCpuCores.text = "Cores: $cores"
        textViewCpuGovernor.text = "Governor: ${readCpuGovernor()}"
        textViewCpuFreq.text = "Freq: ${readCpuFrequencies()}"
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = lifecycleScope.launch {
            while (isActive) {
                refreshCpuData()
                delay(2000)
            }
        }
    }

    private suspend fun refreshCpuData() {
        withContext(Dispatchers.IO) {
            val cpuUsage = readOverallCpuUsage()
            val temp = readCpuTemperature()
            val freq = readCpuFrequencies()
            val processes = readPerProcessCpuUsage()

            withContext(Dispatchers.Main) {
                textViewCpuUsage.text = "CPU Usage: ${"%.1f".format(cpuUsage)}%"
                textViewCpuUsage.setTextColor(
                    when {
                        cpuUsage < 50f -> 0xFF00FF00.toInt()
                        cpuUsage < 80f -> 0xFFFFFF00.toInt()
                        else -> 0xFFFF4444.toInt()
                    }
                )
                textViewCpuTemp.text = "Temp: $temp"
                textViewCpuFreq.text = "Freq: $freq"

                progressBarCpu.progress = cpuUsage.toInt().coerceIn(0, 100)

                cpuGraphView.addDataPoint(cpuUsage)

                processList.clear()
                processList.addAll(processes)
                recyclerViewCpuProcesses.adapter?.notifyDataSetChanged()
            }
        }
    }

    private fun readOverallCpuUsage(): Float {
        try {
            val statFile = File("/proc/stat")
            if (!statFile.exists()) return 0f
            val line = BufferedReader(FileReader(statFile)).use { it.readLine() } ?: return 0f
            val parts = line.split("\\s+".toRegex())
            if (parts.size < 5) return 0f

            val user = parts[1].toLongOrNull() ?: 0L
            val nice = parts[2].toLongOrNull() ?: 0L
            val system = parts[3].toLongOrNull() ?: 0L
            val idle = parts[4].toLongOrNull() ?: 0L

            val totalCpuTime = user + nice + system + idle
            val totalIdle = idle

            val diffTotal = totalCpuTime - prevCpuTime
            val diffIdle = totalIdle - prevIdleTime

            prevCpuTime = totalCpuTime
            prevIdleTime = totalIdle

            if (diffTotal == 0L) return 0f

            val usage = ((diffTotal - diffIdle).toFloat() / diffTotal.toFloat()) * 100f
            return usage.coerceIn(0f, 100f)
        } catch (_: Exception) {
            return 0f
        }
    }

    private fun readCpuFrequencies(): String {
        try {
            val cpuDir = File("/sys/devices/system/cpu/")
            val freqList = mutableListOf<Pair<Long, Long>>()

            val cpuDirs = cpuDir.listFiles { file -> file.name.matches(Regex("cpu\\d+")) }
                ?: return "N/A"

            for (dir in cpuDirs) {
                val curFreqFile = File(dir, "cpufreq/scaling_cur_freq")
                val minFreqFile = File(dir, "cpufreq/scaling_min_freq")
                val maxFreqFile = File(dir, "cpufreq/scaling_max_freq")

                val curFreq = readFileAsLong(curFreqFile)
                val minFreq = readFileAsLong(minFreqFile)
                val maxFreq = readFileAsLong(maxFreqFile)

                if (curFreq > 0 || maxFreq > 0) {
                    freqList.add(Pair(minFreq, maxFreq))
                }
            }

            if (freqList.isEmpty()) return "N/A"

            val globalMin = freqList.minOfOrNull { it.first } ?: 0L
            val globalMax = freqList.maxOfOrNull { it.second } ?: 0L

            val minMhz = globalMin / 1000
            val maxMhz = globalMax / 1000

            return "${minMhz}-${maxMhz} MHz"
        } catch (_: Exception) {
            return "N/A"
        }
    }

    private fun readCpuTemperature(): String {
        try {
            val thermalDir = File("/sys/class/thermal/")
            val thermalZones = thermalDir.listFiles { file -> file.name.startsWith("thermal_zone") }
                ?: return "N/A"

            for (zone in thermalZones) {
                val typeFile = File(zone, "type")
                val type = readFileAsString(typeFile).lowercase()

                if (type.contains("cpu") || type.contains("soc") || type.contains("pkg")) {
                    val tempFile = File(zone, "temp")
                    val tempValue = readFileAsLong(tempFile)
                    if (tempValue > 0) {
                        val tempC = if (tempValue > 1000) tempValue / 1000.0 else tempValue.toDouble()
                        return "${"%.1f".format(tempC)}°C"
                    }
                }
            }

            // Fallback: try first thermal zone with valid temp
            for (zone in thermalZones) {
                val tempFile = File(zone, "temp")
                val tempValue = readFileAsLong(tempFile)
                if (tempValue > 0) {
                    val tempC = if (tempValue > 1000) tempValue / 1000.0 else tempValue.toDouble()
                    return "${"%.1f".format(tempC)}°C"
                }
            }

            return "N/A"
        } catch (_: Exception) {
            return "N/A"
        }
    }

    private fun readCpuGovernor(): String {
        try {
            val cpuDir = File("/sys/devices/system/cpu/")
            val cpuDirs = cpuDir.listFiles { file -> file.name.matches(Regex("cpu\\d+")) }
                ?: return "N/A"

            for (dir in cpuDirs) {
                val govFile = File(dir, "cpufreq/scaling_governor")
                val governor = readFileAsString(govFile)
                if (governor.isNotBlank()) return governor
            }
            return "N/A"
        } catch (_: Exception) {
            return "N/A"
        }
    }

    private fun readPerProcessCpuUsage(): List<CpuProcessInfo> {
        val result = mutableListOf<CpuProcessInfo>()
        try {
            val activityManager =
                requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val processes = activityManager.runningAppProcesses ?: return emptyList()

            for (processInfo in processes) {
                try {
                    val pid = processInfo.pid
                    val statFile = File("/proc/$pid/stat")
                    if (!statFile.exists()) continue

                    val statLine = BufferedReader(FileReader(statFile)).use { it.readLine() } ?: continue
                    val parts = statLine.split("\\s+".toRegex())
                    if (parts.size < 17) continue

                    val utime = parts[13].toLongOrNull() ?: 0L
                    val stime = parts[14].toLongOrNull() ?: 0L
                    val totalTime = utime + stime

                    val memInfo = activityManager.getProcessMemoryInfo(intArrayOf(pid))
                    val totalPss = memInfo.firstOrNull()?.totalPss?.toLong() ?: 0L

                    // Calculate approximate CPU usage as percentage of total
                    val cpuPercent = if (prevCpuTime > 0) {
                        ((totalTime.toFloat() / prevCpuTime.toFloat()) * 100f).coerceIn(0f, 100f)
                    } else {
                        0f
                    }

                    result.add(
                        CpuProcessInfo(
                            processName = processInfo.processName,
                            pid = pid,
                            cpuUsage = cpuPercent,
                            memoryUsage = totalPss * 1024L
                        )
                    )
                } catch (_: Exception) {
                    // Skip processes we can't read
                }
            }

            result.sortByDescending { it.cpuUsage }
        } catch (_: Exception) {
        }
        return result
    }

    private fun showKillConfirmDialog(position: Int) {
        if (position < 0 || position >= processList.size) return
        val process = processList[position]

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Kill Process")
            .setMessage("Kill ${process.processName} (PID: ${process.pid})?\nCPU: ${"%.1f".format(process.cpuUsage)}%")
            .setPositiveButton("Kill") { _, _ ->
                killProcess(process)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showKillHighCpuDialog() {
        val highCpuProcesses = processList.filter { it.cpuUsage > 10f }
        if (highCpuProcesses.isEmpty()) {
            Toast.makeText(requireContext(), "No high CPU processes found", Toast.LENGTH_SHORT).show()
            return
        }

        val names = highCpuProcesses.map {
            "${it.processName} (${it.pid}) - ${"%.1f".format(it.cpuUsage)}%"
        }.toTypedArray()

        val selected = BooleanArray(names.size) { false }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Kill High CPU Processes (>10%)")
            .setMultiChoiceItems(names, selected) { _, _, _ -> }
            .setPositiveButton("Kill Selected") { _, _ ->
                var killedCount = 0
                for (i in selected.indices) {
                    if (selected[i]) {
                        killProcess(highCpuProcesses[i])
                        killedCount++
                    }
                }
                Toast.makeText(requireContext(), "Killed $killedCount process(es)", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun killProcess(process: CpuProcessInfo) {
        val activityManager =
            requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        try {
            activityManager.killBackgroundProcesses(process.processName)
            try {
                Runtime.getRuntime().exec("kill ${process.pid}").waitFor()
            } catch (_: Exception) {
            }
            Toast.makeText(requireContext(), "Killed: ${process.processName}", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                delay(800)
                refreshCpuData()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun readFileAsLong(file: File): Long {
        return try {
            if (file.exists()) {
                val content = BufferedReader(FileReader(file)).use { it.readLine() }
                content?.trim()?.toLongOrNull() ?: 0L
            } else {
                0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    private fun readFileAsString(file: File): String {
        return try {
            if (file.exists()) {
                BufferedReader(FileReader(file)).use { it.readLine() }?.trim() ?: ""
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        monitorJob?.cancel()
    }

    inner class CpuProcessAdapter(
        private val items: List<CpuProcessInfo>,
        private val onKillClick: (Int) -> Unit
    ) : RecyclerView.Adapter<CpuProcessAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textProcessName: TextView = view.findViewById(R.id.textViewProcessName)
            val textPid: TextView = view.findViewById(R.id.textViewPid)
            val textCpu: TextView = view.findViewById(R.id.textViewCpu)
            val textMemory: TextView = view.findViewById(R.id.textViewMemory)
            val buttonKill: Button = view.findViewById(R.id.buttonKill)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_cpu_process, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.textProcessName.text = item.processName
            holder.textPid.text = "PID: ${item.pid}"
            holder.textCpu.text = "CPU: ${"%.1f".format(item.cpuUsage)}%"
            holder.textMemory.text = formatMemory(item.memoryUsage)

            holder.textCpu.setTextColor(
                when {
                    item.cpuUsage > 50f -> 0xFFFF4444.toInt()
                    item.cpuUsage > 10f -> 0xFFFFFF00.toInt()
                    else -> 0xFF00FF00.toInt()
                }
            )

            holder.buttonKill.setOnClickListener {
                onKillClick(holder.adapterPosition)
            }
        }

        override fun getItemCount(): Int = items.size

        private fun formatMemory(bytes: Long): String {
            val mb = bytes / (1024.0 * 1024.0)
            return "${"%.1f".format(mb)} MB"
        }
    }
}
