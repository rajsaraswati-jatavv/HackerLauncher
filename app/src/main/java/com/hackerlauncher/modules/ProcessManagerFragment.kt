package com.hackerlauncher.modules

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
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
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

class ProcessManagerFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var editTextSearch: EditText
    private lateinit var spinnerSort: Spinner
    private lateinit var buttonRefresh: ImageButton
    private lateinit var textViewProcessCount: TextView
    private lateinit var textViewCpuUsage: TextView
    private lateinit var emptyStateText: TextView

    private val processList = mutableListOf<ProcessDetailInfo>()
    private val filteredList = mutableListOf<ProcessDetailInfo>()
    private val blacklist = mutableSetOf<String>()
    private var autoKillJob: Job? = false.let { null }
    private var isAutoKillEnabled = false

    data class ProcessDetailInfo(
        val processName: String,
        val pid: Int,
        val uid: Int,
        val memoryUsage: Long,
        val importance: Int,
        val cpuUsage: Float
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_process_manager, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerViewProcesses)
        editTextSearch = view.findViewById(R.id.editTextSearch)
        spinnerSort = view.findViewById(R.id.spinnerSort)
        buttonRefresh = view.findViewById(R.id.buttonRefresh)
        textViewProcessCount = view.findViewById(R.id.textViewProcessCount)
        textViewCpuUsage = view.findViewById(R.id.textViewCpuUsage)
        emptyStateText = view.findViewById(R.id.textViewEmptyState)

        setupRecyclerView()
        setupSearch()
        setupSort()
        setupButtons()
        loadBlacklist()
        loadAutoKillSetting()
        refreshProcessList()
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = ProcessDetailAdapter(
            filteredList,
            onKillClick = { position -> killProcessAt(position) },
            onBlacklistClick = { position -> toggleBlacklistAt(position) }
        )
    }

    private fun setupSearch() {
        editTextSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilterAndSort()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupSort() {
        val sortOptions = arrayOf("Memory (High→Low)", "Memory (Low→High)", "PID", "CPU Usage", "Name A-Z")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, sortOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSort.adapter = adapter

        spinnerSort.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                applyFilterAndSort()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupButtons() {
        buttonRefresh.setOnClickListener {
            refreshProcessList()
        }
    }

    private fun refreshProcessList() {
        lifecycleScope.launch {
            val processes = withContext(Dispatchers.IO) {
                getRunningProcesses()
            }
            processList.clear()
            processList.addAll(processes)
            applyFilterAndSort()

            val totalCpu = processList.sumOf { it.cpuUsage.toDouble() }.toFloat()
            textViewCpuUsage.text = "Total CPU: ${"%.1f".format(totalCpu)}%"
            textViewProcessCount.text = "Processes: ${processList.size}"

            // Check blacklist for auto-kill
            if (isAutoKillEnabled) {
                autoKillBlacklisted()
            }
        }
    }

    private fun getRunningProcesses(): List<ProcessDetailInfo> {
        val activityManager =
            requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val result = mutableListOf<ProcessDetailInfo>()

        val processes = activityManager.runningAppProcesses ?: return emptyList()
        for (processInfo in processes) {
            try {
                val memInfo = activityManager.getProcessMemoryInfo(intArrayOf(processInfo.pid))
                val totalPss = memInfo.firstOrNull()?.totalPss?.toLong() ?: 0L
                val cpuUsage = readCpuUsage(processInfo.pid)

                result.add(
                    ProcessDetailInfo(
                        processName = processInfo.processName,
                        pid = processInfo.pid,
                        uid = processInfo.uid,
                        memoryUsage = totalPss * 1024,
                        importance = processInfo.importance,
                        cpuUsage = cpuUsage
                    )
                )
            } catch (_: Exception) {
            }
        }

        return result
    }

    private fun readCpuUsage(pid: Int): Float {
        try {
            val statFile = File("/proc/$pid/stat")
            if (!statFile.exists()) return 0f

            val statLine = BufferedReader(FileReader(statFile)).use { it.readLine() } ?: return 0f
            val parts = statLine.split("\\s+".toRegex())
            if (parts.size < 17) return 0f

            val utime = parts[13].toLongOrNull() ?: 0L
            val stime = parts[14].toLongOrNull() ?: 0L
            val totalTime = utime + stime

            // Read system uptime
            val uptimeFile = File("/proc/uptime")
            if (!uptimeFile.exists()) return 0f
            val uptimeLine = BufferedReader(FileReader(uptimeFile)).use { it.readLine() } ?: return 0f
            val uptime = uptimeLine.split("\\s+".toRegex())[0].toDoubleOrNull() ?: 0.0

            // Read total CPU time from /proc/stat
            val procStatFile = File("/proc/stat")
            if (!procStatFile.exists()) return 0f
            val procStatLine = BufferedReader(FileReader(procStatFile)).use { it.readLine() } ?: return 0f
            val cpuParts = procStatLine.split("\\s+".toRegex())
            var totalCpuTime = 0L
            for (i in 1 until cpuParts.size) {
                totalCpuTime += cpuParts[i].toLongOrNull() ?: 0L
            }

            if (totalCpuTime == 0L) return 0f

            val hz = java.lang.Math.max(1L, totalTime)
            return ((hz * 100.0) / totalCpuTime).toFloat().coerceIn(0f, 100f)
        } catch (_: Exception) {
            return 0f
        }
    }

    private fun applyFilterAndSort() {
        val query = editTextSearch.text.toString().lowercase()
        filteredList.clear()

        val filtered = if (query.isBlank()) {
            processList.toList()
        } else {
            processList.filter {
                it.processName.lowercase().contains(query) ||
                        it.pid.toString().contains(query)
            }
        }

        val sorted = when (spinnerSort.selectedItemPosition) {
            0 -> filtered.sortedByDescending { it.memoryUsage }
            1 -> filtered.sortedBy { it.memoryUsage }
            2 -> filtered.sortedBy { it.pid }
            3 -> filtered.sortedByDescending { it.cpuUsage }
            4 -> filtered.sortedBy { it.processName }
            else -> filtered
        }

        filteredList.addAll(sorted)
        recyclerView.adapter?.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun killProcessAt(position: Int) {
        if (position < 0 || position >= filteredList.size) return
        val item = filteredList[position]

        val activityManager =
            requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        try {
            // Using killBackgroundProcesses for safety
            activityManager.killBackgroundProcesses(item.processName)

            // Try force-stop via Process
            try {
                val process = Runtime.getRuntime().exec("kill ${item.pid}")
                process.waitFor()
            } catch (_: Exception) {
            }

            Toast.makeText(requireContext(), "Killed: ${item.processName}", Toast.LENGTH_SHORT).show()

            lifecycleScope.launch {
                delay(800)
                withContext(Dispatchers.Main) {
                    refreshProcessList()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleBlacklistAt(position: Int) {
        if (position < 0 || position >= filteredList.size) return
        val item = filteredList[position]

        if (blacklist.contains(item.processName)) {
            blacklist.remove(item.processName)
            Toast.makeText(requireContext(), "Removed from blacklist: ${item.processName}", Toast.LENGTH_SHORT).show()
        } else {
            blacklist.add(item.processName)
            Toast.makeText(
                requireContext(),
                "Added to auto-kill blacklist: ${item.processName}",
                Toast.LENGTH_SHORT
            ).show()
        }
        saveBlacklist()
        recyclerView.adapter?.notifyItemChanged(position)
    }

    private fun autoKillBlacklisted() {
        if (blacklist.isEmpty()) return
        val activityManager =
            requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        lifecycleScope.launch(Dispatchers.IO) {
            val processes = activityManager.runningAppProcesses ?: return@launch
            for (processInfo in processes) {
                if (blacklist.contains(processInfo.processName)) {
                    try {
                        activityManager.killBackgroundProcesses(processInfo.processName)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private fun startAutoKillMonitor() {
        autoKillJob?.cancel()
        autoKillJob = lifecycleScope.launch {
            while (isActive && isAutoKillEnabled) {
                delay(15_000) // Check every 15 seconds
                if (isAutoKillEnabled && blacklist.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        autoKillBlacklisted()
                    }
                    withContext(Dispatchers.Main) {
                        refreshProcessList()
                    }
                }
            }
        }
    }

    private fun stopAutoKillMonitor() {
        autoKillJob?.cancel()
        autoKillJob = null
    }

    private fun saveBlacklist() {
        val prefs = requireContext().getSharedPreferences("process_manager_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("blacklist", blacklist).apply()
    }

    private fun loadBlacklist() {
        val prefs = requireContext().getSharedPreferences("process_manager_prefs", Context.MODE_PRIVATE)
        blacklist.clear()
        blacklist.addAll(prefs.getStringSet("blacklist", emptySet()) ?: emptySet())
    }

    private fun loadAutoKillSetting() {
        val prefs = requireContext().getSharedPreferences("process_manager_prefs", Context.MODE_PRIVATE)
        isAutoKillEnabled = prefs.getBoolean("auto_kill_enabled", false)
        if (isAutoKillEnabled) {
            startAutoKillMonitor()
        }
    }

    private fun saveAutoKillSetting() {
        val prefs = requireContext().getSharedPreferences("process_manager_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("auto_kill_enabled", isAutoKillEnabled).apply()
    }

    private fun updateEmptyState() {
        emptyStateText.visibility = if (filteredList.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopAutoKillMonitor()
    }

    inner class ProcessDetailAdapter(
        private val items: List<ProcessDetailInfo>,
        private val onKillClick: (Int) -> Unit,
        private val onBlacklistClick: (Int) -> Unit
    ) : RecyclerView.Adapter<ProcessDetailAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textProcessName: TextView = view.findViewById(R.id.textViewProcessName)
            val textPid: TextView = view.findViewById(R.id.textViewPid)
            val textUid: TextView = view.findViewById(R.id.textViewUid)
            val textMemory: TextView = view.findViewById(R.id.textViewMemory)
            val textCpu: TextView = view.findViewById(R.id.textViewCpu)
            val textImportance: TextView = view.findViewById(R.id.textViewImportance)
            val buttonKill: Button = view.findViewById(R.id.buttonKill)
            val buttonBlacklist: Button = view.findViewById(R.id.buttonBlacklist)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_process_detail, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.textProcessName.text = item.processName
            holder.textPid.text = "PID: ${item.pid}"
            holder.textUid.text = "UID: ${item.uid}"
            holder.textMemory.text = formatMemory(item.memoryUsage)
            holder.textCpu.text = "CPU: ${"%.1f".format(item.cpuUsage)}%"

            val importanceStr = when {
                item.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "FOREGROUND"
                item.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "VISIBLE"
                item.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> "PERCEPTIBLE"
                item.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "SERVICE"
                else -> "BACKGROUND"
            }
            holder.textImportance.text = importanceStr

            val importanceColor = when {
                item.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> 0xFF00FF00.toInt()
                item.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> 0xFFFFFF00.toInt()
                else -> 0xFFFF4444.toInt()
            }
            holder.textImportance.setTextColor(importanceColor)
            holder.textCpu.setTextColor(
                if (item.cpuUsage > 10f) 0xFFFF4444.toInt() else 0xFF00FF00.toInt()
            )

            holder.buttonKill.setOnClickListener { onKillClick(holder.adapterPosition) }

            val isBlacklisted = blacklist.contains(item.processName)
            holder.buttonBlacklist.text = if (isBlacklisted) "✓ BL" else "+ BL"
            holder.buttonBlacklist.setOnClickListener { onBlacklistClick(holder.adapterPosition) }
        }

        override fun getItemCount(): Int = items.size

        private fun formatMemory(bytes: Long): String {
            val mb = bytes / (1024.0 * 1024.0)
            return "${"%.1f".format(mb)} MB"
        }
    }
}
