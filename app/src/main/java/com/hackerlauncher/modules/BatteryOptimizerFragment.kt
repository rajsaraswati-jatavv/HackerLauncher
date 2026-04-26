package com.hackerlauncher.modules

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BatteryOptimizerFragment : Fragment() {

    private lateinit var textViewLevel: TextView
    private lateinit var progressBarBattery: ProgressBar
    private lateinit var textViewHealth: TextView
    private lateinit var textViewTemp: TextView
    private lateinit var textViewVoltage: TextView
    private lateinit var textViewCharging: TextView
    private lateinit var textViewTechnology: TextView
    private lateinit var textViewCapacity: TextView
    private lateinit var buttonKillBackground: Button
    private lateinit var recyclerViewRecommendations: RecyclerView
    private lateinit var recyclerViewProcesses: RecyclerView
    private lateinit var editTextAlarmThreshold: EditText
    private lateinit var buttonSetAlarm: Button
    private lateinit var textViewAlarmStatus: TextView
    private lateinit var layoutBatteryInfo: LinearLayout

    private var batteryAlarmJob: Job? = null
    private var alarmThreshold = 80
    private var isAlarmSet = false
    private var hasAlarmTriggered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let { updateBatteryInfo(it) }
        }
    }

    private val recommendations = mutableListOf<BatteryRecommendation>()
    private val processList = mutableListOf<ProcessInfo>()

    data class BatteryRecommendation(
        val title: String,
        val description: String,
        val severity: String // "high", "medium", "low"
    )

    data class ProcessInfo(
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
        return inflater.inflate(R.layout.fragment_battery_optimizer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        textViewLevel = view.findViewById(R.id.textViewBatteryLevel)
        progressBarBattery = view.findViewById(R.id.progressBarBattery)
        textViewHealth = view.findViewById(R.id.textViewHealth)
        textViewTemp = view.findViewById(R.id.textViewTemp)
        textViewVoltage = view.findViewById(R.id.textViewVoltage)
        textViewCharging = view.findViewById(R.id.textViewCharging)
        textViewTechnology = view.findViewById(R.id.textViewTechnology)
        textViewCapacity = view.findViewById(R.id.textViewCapacity)
        buttonKillBackground = view.findViewById(R.id.buttonKillBackground)
        recyclerViewRecommendations = view.findViewById(R.id.recyclerViewRecommendations)
        recyclerViewProcesses = view.findViewById(R.id.recyclerViewProcesses)
        editTextAlarmThreshold = view.findViewById(R.id.editTextAlarmThreshold)
        buttonSetAlarm = view.findViewById(R.id.buttonSetAlarm)
        textViewAlarmStatus = view.findViewById(R.id.textViewAlarmStatus)
        layoutBatteryInfo = view.findViewById(R.id.layoutBatteryInfo)

        setupRecyclerViews()
        setupButtons()
        loadInitialBatteryInfo()
        loadAlarmSettings()
    }

    private fun setupRecyclerViews() {
        recyclerViewRecommendations.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewRecommendations.adapter = RecommendationAdapter(recommendations)

        recyclerViewProcesses.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewProcesses.adapter = ProcessInfoAdapter(processList)
    }

    private fun setupButtons() {
        buttonKillBackground.setOnClickListener {
            killBackgroundProcesses()
        }

        buttonSetAlarm.setOnClickListener {
            val thresholdStr = editTextAlarmThreshold.text.toString()
            val threshold = thresholdStr.toIntOrNull()
            if (threshold == null || threshold < 1 || threshold > 100) {
                Toast.makeText(requireContext(), "Enter 1-100", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            alarmThreshold = threshold
            isAlarmSet = true
            hasAlarmTriggered = false
            saveAlarmSettings()
            startAlarmMonitor()
            textViewAlarmStatus.text = "Alarm set: $threshold%"
            textViewAlarmStatus.setTextColor(0xFF00FF00.toInt())
            Toast.makeText(requireContext(), "Charging alarm at $threshold%", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadInitialBatteryInfo() {
        val batteryIntent = requireContext().registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryIntent?.let { updateBatteryInfo(it) }
    }

    private fun updateBatteryInfo(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val batteryPct = (level * 100) / scale.coerceAtLeast(1)

        val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        val healthStr = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "GOOD"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT"
            BatteryManager.BATTERY_HEALTH_DEAD -> "DEAD"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER_VOLTAGE"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "FAILURE"
            BatteryManager.BATTERY_HEALTH_COLD -> "COLD"
            else -> "UNKNOWN"
        }
        val healthColor = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> 0xFF00FF00.toInt()
            BatteryManager.BATTERY_HEALTH_OVERHEAT, BatteryManager.BATTERY_HEALTH_DEAD -> 0xFFFF0000.toInt()
            else -> 0xFFFFFF00.toInt()
        }

        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10.0f
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) / 1000.0f

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        val chargingStr = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "DISCHARGING"
            BatteryManager.BATTERY_STATUS_FULL -> "FULL"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "NOT_CHARGING"
            else -> "UNKNOWN"
        }

        val plugType = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val plugStr = when (plugType) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
            else -> if (isCharging) "UNKNOWN" else "NONE"
        }

        val technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "N/A"

        val bm = requireContext().getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val capacity = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        textViewLevel.text = "$batteryPct%"
        progressBarBattery.progress = batteryPct
        textViewHealth.text = "Health: $healthStr"
        textViewHealth.setTextColor(healthColor)
        textViewTemp.text = "Temp: ${"%.1f".format(temp)}°C"
        textViewTemp.setTextColor(
            if (temp > 40f) 0xFFFF0000.toInt() else 0xFF00FF00.toInt()
        )
        textViewVoltage.text = "Voltage: ${"%.2f".format(voltage)}V"
        textViewCharging.text = "Status: $chargingStr ($plugStr)"
        textViewCharging.setTextColor(
            if (isCharging) 0xFF00FF00.toInt() else 0xFFFFFF00.toInt()
        )
        textViewTechnology.text = "Technology: $technology"
        textViewCapacity.text = "Capacity: $capacity%"

        updateRecommendations(batteryPct, temp, isCharging, health)
        updateProcessList()

        // Check alarm
        if (isAlarmSet && !hasAlarmTriggered && isCharging && batteryPct >= alarmThreshold) {
            hasAlarmTriggered = true
            triggerAlarm(batteryPct)
        }
    }

    private fun updateRecommendations(level: Int, temp: Float, isCharging: Boolean, health: Int) {
        recommendations.clear()

        if (temp > 40f) {
            recommendations.add(
                BatteryRecommendation(
                    "HIGH TEMPERATURE",
                    "Battery temp ${"%.1f".format(temp)}°C is above safe range. Stop charging and close heavy apps.",
                    "high"
                )
            )
        }

        if (level <= 15 && !isCharging) {
            recommendations.add(
                BatteryRecommendation(
                    "CRITICAL BATTERY",
                    "Battery at $level%. Enable power save mode immediately.",
                    "high"
                )
            )
        } else if (level <= 30 && !isCharging) {
            recommendations.add(
                BatteryRecommendation(
                    "LOW BATTERY",
                    "Battery at $level%. Consider reducing background activity.",
                    "medium"
                )
            )
        }

        if (isCharging && level > 80) {
            recommendations.add(
                BatteryRecommendation(
                    "OVERCHARGING RISK",
                    "Battery at $level% while charging. Unplug to extend battery lifespan.",
                    "medium"
                )
            )
        }

        if (health != BatteryManager.BATTERY_HEALTH_GOOD && health != BatteryManager.BATTERY_HEALTH_UNKNOWN) {
            recommendations.add(
                BatteryRecommendation(
                    "BATTERY HEALTH ISSUE",
                    "Battery health is degraded. Consider replacement.",
                    "high"
                )
            )
        }

        val activityManager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningProcesses = activityManager.runningAppProcesses?.size ?: 0
        if (runningProcesses > 50) {
            recommendations.add(
                BatteryRecommendation(
                    "TOO MANY PROCESSES",
                    "$runningProcesses processes running. Kill background apps to save battery.",
                    "medium"
                )
            )
        }

        if (recommendations.isEmpty()) {
            recommendations.add(
                BatteryRecommendation(
                    "ALL CLEAR",
                    "Battery status is optimal. No actions needed.",
                    "low"
                )
            )
        }

        recyclerViewRecommendations.adapter?.notifyDataSetChanged()
    }

    private fun updateProcessList() {
        val activityManager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        processList.clear()

        val processes = activityManager.runningAppProcesses ?: return
        for (processInfo in processes) {
            val memInfo = activityManager.getProcessMemoryInfo(intArrayOf(processInfo.pid))
            val totalPss = memInfo.firstOrNull()?.totalPss?.toLong() ?: 0L

            processList.add(
                ProcessInfo(
                    processName = processInfo.processName,
                    pid = processInfo.pid,
                    memoryUsage = totalPss * 1024, // Convert KB to bytes
                    importance = processInfo.importance
                )
            )
        }

        processList.sortByDescending { it.memoryUsage }
        recyclerViewProcesses.adapter?.notifyDataSetChanged()
    }

    private fun killBackgroundProcesses() {
        val activityManager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        var killedCount = 0

        val processes = activityManager.runningAppProcesses ?: return
        for (processInfo in processes) {
            if (processInfo.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                try {
                    activityManager.killBackgroundProcesses(processInfo.processName)
                    killedCount++
                } catch (_: Exception) {
                }
            }
        }

        Toast.makeText(
            requireContext(),
            "Killed $killedCount background processes",
            Toast.LENGTH_SHORT
        ).show()

        lifecycleScope.launch {
            delay(1000)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                updateProcessList()
            }
        }
    }

    private fun triggerAlarm(level: Int) {
        Toast.makeText(
            requireContext(),
            "⚡ CHARGING ALARM: Battery at $level%!",
            Toast.LENGTH_LONG
        ).show()

        val dialog = android.app.AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("⚡ CHARGING ALARM")
            .setMessage("Battery has reached $level%. Unplug charger to preserve battery health.")
            .setPositiveButton("OK") { _, _ -> }
            .setNegativeButton("Dismiss Alarm") { _, _ ->
                isAlarmSet = false
                batteryAlarmJob?.cancel()
                textViewAlarmStatus.text = "Alarm dismissed"
                textViewAlarmStatus.setTextColor(0xFF888888.toInt())
            }
            .create()
        dialog.show()
    }

    private fun startAlarmMonitor() {
        batteryAlarmJob?.cancel()
        batteryAlarmJob = lifecycleScope.launch {
            while (isActive) {
                val batteryIntent = requireContext().registerReceiver(
                    null,
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                )
                batteryIntent?.let {
                    val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val pct = (level * 100) / scale.coerceAtLeast(1)
                    val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL

                    if (isAlarmSet && !hasAlarmTriggered && isCharging && pct >= alarmThreshold) {
                        hasAlarmTriggered = true
                        withContext(Dispatchers.Main) {
                            triggerAlarm(pct)
                        }
                    }
                }
                delay(5000)
            }
        }
    }

    private fun saveAlarmSettings() {
        val prefs = requireContext().getSharedPreferences("battery_optimizer_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("alarm_threshold", alarmThreshold)
            .putBoolean("alarm_set", isAlarmSet)
            .apply()
    }

    private fun loadAlarmSettings() {
        val prefs = requireContext().getSharedPreferences("battery_optimizer_prefs", Context.MODE_PRIVATE)
        alarmThreshold = prefs.getInt("alarm_threshold", 80)
        isAlarmSet = prefs.getBoolean("alarm_set", false)

        if (isAlarmSet) {
            editTextAlarmThreshold.setText(alarmThreshold.toString())
            textViewAlarmStatus.text = "Alarm active: $alarmThreshold%"
            textViewAlarmStatus.setTextColor(0xFF00FF00.toInt())
            startAlarmMonitor()
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        requireContext().registerReceiver(batteryReceiver, filter)
    }

    override fun onPause() {
        super.onPause()
        try {
            requireContext().unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        batteryAlarmJob?.cancel()
        try {
            requireContext().unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {
        }
    }

    inner class RecommendationAdapter(
        private val items: List<BatteryRecommendation>
    ) : RecyclerView.Adapter<RecommendationAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textTitle: TextView = view.findViewById(R.id.textViewRecTitle)
            val textDesc: TextView = view.findViewById(R.id.textViewRecDesc)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_battery_recommendation, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.textTitle.text = item.title
            holder.textDesc.text = item.description

            val color = when (item.severity) {
                "high" -> 0xFFFF0000.toInt()
                "medium" -> 0xFFFFFF00.toInt()
                else -> 0xFF00FF00.toInt()
            }
            holder.textTitle.setTextColor(color)
        }

        override fun getItemCount(): Int = items.size
    }

    inner class ProcessInfoAdapter(
        private val items: List<ProcessInfo>
    ) : RecyclerView.Adapter<ProcessInfoAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textProcessName: TextView = view.findViewById(R.id.textViewProcessName)
            val textProcessDetails: TextView = view.findViewById(R.id.textViewProcessDetails)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_battery_process, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.textProcessName.text = item.processName

            val importanceStr = when {
                item.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "FOREGROUND"
                item.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "VISIBLE"
                item.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "SERVICE"
                else -> "BACKGROUND"
            }

            holder.textProcessDetails.text = "PID: ${item.pid} | ${formatMemory(item.memoryUsage)} | $importanceStr"

            val color = when {
                item.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> 0xFF00FF00.toInt()
                item.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> 0xFFFFFF00.toInt()
                else -> 0xFFFF4444.toInt()
            }
            holder.textProcessDetails.setTextColor(color)
        }

        override fun getItemCount(): Int = items.size

        private fun formatMemory(bytes: Long): String {
            val mb = bytes / (1024.0 * 1024.0)
            return "${"%.1f".format(mb)} MB"
        }
    }
}
