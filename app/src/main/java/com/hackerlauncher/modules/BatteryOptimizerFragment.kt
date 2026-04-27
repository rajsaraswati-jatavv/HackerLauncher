package com.hackerlauncher.modules

import com.hackerlauncher.R
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

    // ========== UPGRADE: New view references ==========
    private lateinit var buttonChargingProtection: Button
    private lateinit var textViewChargingProtectionStatus: TextView
    private lateinit var buttonBatteryHealth: Button
    private lateinit var textViewBatteryHealth: TextView
    private lateinit var buttonPowerProfile: Button
    private lateinit var textViewPowerProfile: TextView
    private lateinit var buttonBatteryHistory: Button
    private lateinit var textViewTempAlert: TextView
    private lateinit var editTextTempThreshold: EditText
    private lateinit var buttonSetTempAlert: Button
    private lateinit var buttonOvernightProtection: Button
    private lateinit var textViewOvernightStatus: TextView

    private var batteryAlarmJob: Job? = null
    private var alarmThreshold = 80
    private var isAlarmSet = false
    private var hasAlarmTriggered = false

    // ========== UPGRADE: New state ==========
    private var isChargingProtectionEnabled = false
    private var chargingProtectionThreshold = 80
    private var trickleChargeMode = false
    private var chargeCycleCount = 0
    private var estimatedCapacityDegradation = 0.0f
    private var tempAlertThreshold = 40f
    private var isTempAlertEnabled = false
    private var isOvernightProtectionEnabled = false
    private var currentPowerProfile = "BALANCED"  // ULTRA_SAVE, BALANCED, PERFORMANCE
    private var lastChargeLevel = -1
    private var lastChargingState = false
    private var tempAlertJob: Job? = null
    private var overnightJob: Job? = null

    // Battery usage tracking
    private val batteryUsageHistory = mutableListOf<BatteryUsageEntry>()
    // Charge cycle tracking
    private val chargeCycleLog = mutableListOf<ChargeCycleEntry>()

    data class BatteryUsageEntry(
        val timestamp: Long,
        val level: Int,
        val isCharging: Boolean,
        val temp: Float,
        val voltage: Float
    )

    data class ChargeCycleEntry(
        val timestamp: Long,
        val startLevel: Int,
        val endLevel: Int,
        val durationMinutes: Long
    )

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
        loadUpgradeSettings()

        // ========== UPGRADE: Add upgrade UI dynamically ==========
        addUpgradeViews(view)
    }

    // ========== UPGRADE: Dynamically add new feature views ==========
    private fun addUpgradeViews(view: View) {
        try {
            val container = view.findViewById<LinearLayout>(R.id.layoutBatteryInfo)
            if (container != null) {
                // Charging Protection
                textViewChargingProtectionStatus = TextView(requireContext()).apply {
                    text = "Charging Protection: OFF"
                    setTextColor(0xFF888888.toInt())
                    textSize = 13f
                }
                container.addView(textViewChargingProtectionStatus)

                buttonChargingProtection = Button(requireContext()).apply {
                    text = "🔋 Charging Protection"
                    setTextColor(0xFF00FF00.toInt())
                    textSize = 12f
                    setOnClickListener { showChargingProtectionDialog() }
                }
                container.addView(buttonChargingProtection)

                // Battery Health
                textViewBatteryHealth = TextView(requireContext()).apply {
                    text = "Battery Health: Checking..."
                    setTextColor(0xFFFFFF00.toInt())
                    textSize = 13f
                }
                container.addView(textViewBatteryHealth)

                buttonBatteryHealth = Button(requireContext()).apply {
                    text = "💊 Battery Health Monitor"
                    setTextColor(0xFF00FFFF.toInt())
                    textSize = 12f
                    setOnClickListener { showBatteryHealthDialog() }
                }
                container.addView(buttonBatteryHealth)

                // Power Profiles
                textViewPowerProfile = TextView(requireContext()).apply {
                    text = "Profile: BALANCED"
                    setTextColor(0xFFFFFF00.toInt())
                    textSize = 13f
                }
                container.addView(textViewPowerProfile)

                buttonPowerProfile = Button(requireContext()).apply {
                    text = "⚡ Power Saving Profiles"
                    setTextColor(0xFFFF9800.toInt())
                    textSize = 12f
                    setOnClickListener { showPowerProfileDialog() }
                }
                container.addView(buttonPowerProfile)

                // Battery Usage History
                buttonBatteryHistory = Button(requireContext()).apply {
                    text = "📊 Battery Usage History"
                    setTextColor(0xFF2196F3.toInt())
                    textSize = 12f
                    setOnClickListener { showBatteryHistoryDialog() }
                }
                container.addView(buttonBatteryHistory)

                // Temperature Alert
                textViewTempAlert = TextView(requireContext()).apply {
                    text = "Temp Alert: OFF"
                    setTextColor(0xFF888888.toInt())
                    textSize = 13f
                }
                container.addView(textViewTempAlert)

                val tempLayout = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                editTextTempThreshold = EditText(requireContext()).apply {
                    hint = "°C (e.g. 40)"
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                    setText("40")
                    setTextColor(0xFF00FF00.toInt())
                    setHintTextColor(0xFF888888.toInt())
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                buttonSetTempAlert = Button(requireContext()).apply {
                    text = "Set"
                    setTextColor(0xFFFF4444.toInt())
                    textSize = 12f
                    setOnClickListener { setTempAlert() }
                }
                tempLayout.addView(editTextTempThreshold)
                tempLayout.addView(buttonSetTempAlert)
                container.addView(tempLayout)

                // Overnight Protection
                textViewOvernightStatus = TextView(requireContext()).apply {
                    text = "Overnight Protection: OFF"
                    setTextColor(0xFF888888.toInt())
                    textSize = 13f
                }
                container.addView(textViewOvernightStatus)

                buttonOvernightProtection = Button(requireContext()).apply {
                    text = "🌙 Overnight Charging Protection"
                    setTextColor(0xFF9C27B0.toInt())
                    textSize = 12f
                    setOnClickListener { toggleOvernightProtection() }
                }
                container.addView(buttonOvernightProtection)
            }
        } catch (_: Exception) {
            // Views not available in layout
        }
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

        // ========== UPGRADE: Track battery usage ==========
        recordBatteryUsage(batteryPct, isCharging, temp, voltage)

        // ========== UPGRADE: Track charge cycles ==========
        trackChargeCycle(batteryPct, isCharging)

        // ========== UPGRADE: Charging protection check ==========
        checkChargingProtection(batteryPct, isCharging)

        // ========== UPGRADE: Temperature alert check ==========
        checkTempAlert(temp)

        // ========== UPGRADE: Update battery health display ==========
        updateBatteryHealthDisplay(batteryPct, health, temp)
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

        // UPGRADE: Additional recommendations based on profiles
        if (currentPowerProfile == "PERFORMANCE" && level < 30) {
            recommendations.add(
                BatteryRecommendation(
                    "PROFILE MISMATCH",
                    "Performance profile active with low battery. Switch to Balanced or Ultra Save.",
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

    // ========== UPGRADE: Load/Save upgrade settings ==========
    private fun loadUpgradeSettings() {
        val prefs = requireContext().getSharedPreferences("battery_optimizer_prefs", Context.MODE_PRIVATE)
        isChargingProtectionEnabled = prefs.getBoolean("charging_protection", false)
        chargingProtectionThreshold = prefs.getInt("charging_protection_threshold", 80)
        trickleChargeMode = prefs.getBoolean("trickle_charge", false)
        chargeCycleCount = prefs.getInt("charge_cycle_count", 0)
        estimatedCapacityDegradation = prefs.getFloat("capacity_degradation", 0f)
        tempAlertThreshold = prefs.getFloat("temp_alert_threshold", 40f)
        isTempAlertEnabled = prefs.getBoolean("temp_alert_enabled", false)
        isOvernightProtectionEnabled = prefs.getBoolean("overnight_protection", false)
        currentPowerProfile = prefs.getString("power_profile", "BALANCED") ?: "BALANCED"
        lastChargeLevel = prefs.getInt("last_charge_level", -1)

        if (isTempAlertEnabled) startTempAlertMonitor()
        if (isOvernightProtectionEnabled) startOvernightProtection()

        // Load battery usage history
        loadBatteryUsageHistory()
        loadChargeCycleLog()
    }

    private fun saveUpgradeSettings() {
        val prefs = requireContext().getSharedPreferences("battery_optimizer_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("charging_protection", isChargingProtectionEnabled)
            .putInt("charging_protection_threshold", chargingProtectionThreshold)
            .putBoolean("trickle_charge", trickleChargeMode)
            .putInt("charge_cycle_count", chargeCycleCount)
            .putFloat("capacity_degradation", estimatedCapacityDegradation)
            .putFloat("temp_alert_threshold", tempAlertThreshold)
            .putBoolean("temp_alert_enabled", isTempAlertEnabled)
            .putBoolean("overnight_protection", isOvernightProtectionEnabled)
            .putString("power_profile", currentPowerProfile)
            .putInt("last_charge_level", lastChargeLevel)
            .apply()
    }

    // ========== UPGRADE: Charging Protection ==========
    private fun showChargingProtectionDialog() {
        val options = arrayOf(
            "Enable 80% Charging Protection (alert to unplug)",
            "Enable Trickle Charge Mode (reduce charge speed above 80%)",
            "Set Custom Threshold",
            "Disable Charging Protection"
        )
        val checkedItems = booleanArrayOf(
            isChargingProtectionEnabled,
            trickleChargeMode,
            false,
            false
        )

        AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("🔋 Charging Protection")
            .setMessage("Protect your battery from overcharging.\n80% rule: Keep battery between 20-80% for best lifespan.")
            .setMultiChoiceItems(options, checkedItems) { _, which, isChecked ->
                when (which) {
                    0 -> {
                        isChargingProtectionEnabled = isChecked
                        chargingProtectionThreshold = 80
                    }
                    1 -> trickleChargeMode = isChecked
                    3 -> {
                        isChargingProtectionEnabled = false
                        trickleChargeMode = false
                    }
                }
            }
            .setPositiveButton("Save") { _, _ ->
                saveUpgradeSettings()
                updateChargingProtectionUI()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkChargingProtection(level: Int, isCharging: Boolean) {
        if (!isChargingProtectionEnabled || !isCharging) return
        if (level >= chargingProtectionThreshold && !hasAlarmTriggered) {
            hasAlarmTriggered = true
            sendChargingProtectionNotification(level)
            Toast.makeText(
                requireContext(),
                "🔋 CHARGING PROTECTION: Battery at $level%! Unplug to protect battery.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun sendChargingProtectionNotification(level: Int) {
        try {
            val nm = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "charging_protection"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(channelId, "Charging Protection", NotificationManager.IMPORTANCE_HIGH)
                )
            }

            NotificationCompat.Builder(requireContext(), channelId)
                .setContentTitle("🔋 Unplug Charger!")
                .setContentText("Battery at $level%. Unplug to extend battery lifespan.")
                .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
                .also { nm.notify(5001, it) }
        } catch (_: Exception) {}
    }

    private fun updateChargingProtectionUI() {
        try {
            if (isChargingProtectionEnabled) {
                textViewChargingProtectionStatus.text = "Charging Protection: ON (${chargingProtectionThreshold}%)" +
                        if (trickleChargeMode) " + Trickle" else ""
                textViewChargingProtectionStatus.setTextColor(0xFF00FF00.toInt())
            } else {
                textViewChargingProtectionStatus.text = "Charging Protection: OFF"
                textViewChargingProtectionStatus.setTextColor(0xFF888888.toInt())
            }
        } catch (_: Exception) {}
    }

    // ========== UPGRADE: Battery Health Monitor ==========
    private fun trackChargeCycle(level: Int, isCharging: Boolean) {
        // Detect charge cycle: charging -> full -> discharging
        if (lastChargeLevel == -1) {
            lastChargeLevel = level
            lastChargingState = isCharging
            return
        }

        // A charge cycle is counted when going from below 20% to above 80%
        if (lastChargingState && !isCharging && lastChargeLevel >= 80 && level < lastChargeLevel) {
            // Stopped charging at high level
            chargeCycleCount++
            chargeCycleLog.add(ChargeCycleEntry(
                timestamp = System.currentTimeMillis(),
                startLevel = lastChargeLevel.coerceAtMost(level),
                endLevel = lastChargeLevel,
                durationMinutes = 0
            ))
            if (chargeCycleLog.size > 100) chargeCycleLog.removeAt(0)

            // Estimate degradation: ~0.5% per 100 cycles for Li-ion
            estimatedCapacityDegradation = chargeCycleCount * 0.005f
            saveUpgradeSettings()
        }

        lastChargeLevel = level
        lastChargingState = isCharging
    }

    private fun updateBatteryHealthDisplay(level: Int, health: Int, temp: Float) {
        try {
            val estimatedHealth = (100f - estimatedCapacityDegradation).coerceIn(0f, 100f)
            val healthGrade = when {
                estimatedHealth >= 90 -> "EXCELLENT"
                estimatedHealth >= 75 -> "GOOD"
                estimatedHealth >= 50 -> "FAIR"
                else -> "POOR"
            }
            val color = when {
                estimatedHealth >= 90 -> 0xFF00FF00.toInt()
                estimatedHealth >= 75 -> 0xFFFFFF00.toInt()
                else -> 0xFFFF4444.toInt()
            }

            textViewBatteryHealth.text = "Health: ${"%.1f".format(estimatedHealth)}% [$healthGrade] | Cycles: $chargeCycleCount"
            textViewBatteryHealth.setTextColor(color)
        } catch (_: Exception) {}
    }

    private fun showBatteryHealthDialog() {
        val estimatedHealth = (100f - estimatedCapacityDegradation).coerceIn(0f, 100f)

        val message = buildString {
            append("═══ BATTERY HEALTH REPORT ═══\n\n")
            append("Estimated Health: ${"%.1f".format(estimatedHealth)}%\n")
            append("Charge Cycles: $chargeCycleCount\n")
            append("Estimated Degradation: ${"%.2f".format(estimatedCapacityDegradation)}%\n\n")
            append("── Recent Charge Cycles ──\n")
            if (chargeCycleLog.isEmpty()) {
                append("No charge cycles recorded yet.\n")
            } else {
                for (entry in chargeCycleLog.takeLast(5).reversed()) {
                    val date = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(entry.timestamp))
                    append("[$date] ${entry.startLevel}% → ${entry.endLevel}%\n")
                }
            }
            append("\n── Tips ──\n")
            append("• Keep battery between 20-80%\n")
            append("• Avoid overnight charging\n")
            append("• Reduce heat exposure\n")
        }

        AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("💊 Battery Health Monitor")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    // ========== UPGRADE: Power Saving Profiles ==========
    private fun showPowerProfileDialog() {
        val profiles = arrayOf(
            "🔋 Ultra Save (max savings, reduced functionality)",
            "⚖️ Balanced (default, moderate savings)",
            "🚀 Performance (full speed, more battery usage)"
        )
        val currentIndex = when (currentPowerProfile) {
            "ULTRA_SAVE" -> 0
            "PERFORMANCE" -> 2
            else -> 1
        }

        AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("⚡ Power Saving Profiles")
            .setSingleChoiceItems(profiles, currentIndex) { _, which ->
                currentPowerProfile = when (which) {
                    0 -> "ULTRA_SAVE"
                    2 -> "PERFORMANCE"
                    else -> "BALANCED"
                }
            }
            .setPositiveButton("Apply") { _, _ ->
                applyPowerProfile()
                saveUpgradeSettings()
                updatePowerProfileUI()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyPowerProfile() {
        try {
            val powerManager = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager

            when (currentPowerProfile) {
                "ULTRA_SAVE" -> {
                    // Enable battery saver
                    if (!powerManager.isPowerSaveMode) {
                        try {
                            Settings.Global.putInt(requireContext().contentResolver, Settings.Global.LOW_POWER_MODE, 1)
                        } catch (_: Exception) {}
                    }
                    // Reduce brightness
                    try {
                        Settings.System.putInt(requireContext().contentResolver, Settings.System.SCREEN_BRIGHTNESS, 30)
                    } catch (_: Exception) {}
                    // Reduce screen timeout
                    try {
                        Settings.System.putInt(requireContext().contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 15000)
                    } catch (_: Exception) {}
                    // Kill background processes
                    killBackgroundProcesses()
                    Toast.makeText(requireContext(), "🔋 Ultra Save activated!", Toast.LENGTH_SHORT).show()
                }
                "BALANCED" -> {
                    try {
                        Settings.System.putInt(requireContext().contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
                    } catch (_: Exception) {}
                    try {
                        Settings.System.putInt(requireContext().contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 30000)
                    } catch (_: Exception) {}
                    Toast.makeText(requireContext(), "⚖️ Balanced profile applied", Toast.LENGTH_SHORT).show()
                }
                "PERFORMANCE" -> {
                    try {
                        Settings.System.putInt(requireContext().contentResolver, Settings.System.SCREEN_BRIGHTNESS, 255)
                    } catch (_: Exception) {}
                    try {
                        Settings.System.putInt(requireContext().contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 60000)
                    } catch (_: Exception) {}
                    Toast.makeText(requireContext(), "🚀 Performance mode!", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (_: Exception) {
            Toast.makeText(requireContext(), "Some settings require system permission", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePowerProfileUI() {
        try {
            val (label, color) = when (currentPowerProfile) {
                "ULTRA_SAVE" -> "🔋 Ultra Save" to 0xFFFF4444.toInt()
                "PERFORMANCE" -> "🚀 Performance" to 0xFF00FF00.toInt()
                else -> "⚖️ Balanced" to 0xFFFFFF00.toInt()
            }
            textViewPowerProfile.text = "Profile: $label"
            textViewPowerProfile.setTextColor(color)
        } catch (_: Exception) {}
    }

    // ========== UPGRADE: Battery Usage History ==========
    private fun recordBatteryUsage(level: Int, isCharging: Boolean, temp: Float, voltage: Float) {
        val now = System.currentTimeMillis()
        // Record at most once per minute
        val lastEntry = batteryUsageHistory.lastOrNull()
        if (lastEntry != null && now - lastEntry.timestamp < 60_000) return

        batteryUsageHistory.add(BatteryUsageEntry(now, level, isCharging, temp, voltage))
        if (batteryUsageHistory.size > 10_080) { // 7 days at 1/min
            batteryUsageHistory.removeAt(0)
        }
    }

    private fun showBatteryHistoryDialog() {
        val message = buildString {
            append("═══ BATTERY USAGE HISTORY ═══\n\n")

            val now = System.currentTimeMillis()
            val dayMs = 86_400_000L

            // 24h summary
            val last24h = batteryUsageHistory.filter { now - it.timestamp < dayMs }
            append("── Last 24 Hours ──\n")
            if (last24h.isEmpty()) {
                append("No data recorded yet.\n")
            } else {
                val drainEvents = last24h.zipWithNext().filter { !it.second.isCharging && it.second.level < it.first.level }
                val totalDrain = drainEvents.sumOf { (it.first.level - it.second.level).toLong() }
                append("Battery drain: $totalDrain%\n")
                append("Average temp: ${"%.1f".format(last24h.map { it.temp }.average())}°C\n")
                append("Charging time: ${last24h.count { it.isCharging }} minutes\n")
                append("Data points: ${last24h.size}\n")
            }

            // 7d summary
            val last7d = batteryUsageHistory.filter { now - it.timestamp < 7 * dayMs }
            append("\n── Last 7 Days ──\n")
            if (last7d.isEmpty()) {
                append("No data recorded yet.\n")
            } else {
                val drainEvents7d = last7d.zipWithNext().filter { !it.second.isCharging && it.second.level < it.first.level }
                val totalDrain7d = drainEvents7d.sumOf { (it.first.level - it.second.level).toLong() }
                append("Total drain: $totalDrain7d%\n")
                append("Max temp: ${"%.1f".format(last7d.maxOf { it.temp })}°C\n")
                append("Data points: ${last7d.size}\n")
            }

            // Top battery consumers (by process memory)
            append("\n── Top Battery Consumers (by memory) ──\n")
            val topConsumers = processList.take(5)
            for ((index, proc) in topConsumers.withIndex()) {
                val mb = proc.memoryUsage / (1024.0 * 1024.0)
                append("${index + 1}. ${proc.processName} (${ "%.1f".format(mb)} MB)\n")
            }
        }

        AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("📊 Battery Usage History")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun loadBatteryUsageHistory() {
        // In a real app, this would load from a database
        // For now, start with empty history
    }

    private fun loadChargeCycleLog() {
        val prefs = requireContext().getSharedPreferences("battery_optimizer_prefs", Context.MODE_PRIVATE)
        val logStr = prefs.getString("charge_cycle_log", "") ?: ""
        chargeCycleLog.clear()
        if (logStr.isNotEmpty()) {
            logStr.split(";").forEach { entryStr ->
                val parts = entryStr.split(",")
                if (parts.size == 4) {
                    try {
                        chargeCycleLog.add(ChargeCycleEntry(
                            timestamp = parts[0].toLong(),
                            startLevel = parts[1].toInt(),
                            endLevel = parts[2].toInt(),
                            durationMinutes = parts[3].toLong()
                        ))
                    } catch (_: Exception) {}
                }
            }
        }
    }

    // ========== UPGRADE: Temperature Alert ==========
    private fun setTempAlert() {
        try {
            val thresholdStr = editTextTempThreshold.text.toString()
            val threshold = thresholdStr.toFloatOrNull()
            if (threshold == null || threshold < 20 || threshold > 60) {
                Toast.makeText(requireContext(), "Enter 20-60°C", Toast.LENGTH_SHORT).show()
                return
            }
            tempAlertThreshold = threshold
            isTempAlertEnabled = true
            saveUpgradeSettings()
            startTempAlertMonitor()
            try {
                textViewTempAlert.text = "Temp Alert: ON (${threshold}°C)"
                textViewTempAlert.setTextColor(0xFFFF4444.toInt())
            } catch (_: Exception) {}
            Toast.makeText(requireContext(), "Temperature alert set at ${threshold}°C", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    private fun checkTempAlert(temp: Float) {
        if (!isTempAlertEnabled) return
        if (temp >= tempAlertThreshold) {
            sendTempAlertNotification(temp)
        }
    }

    private fun startTempAlertMonitor() {
        tempAlertJob?.cancel()
        tempAlertJob = lifecycleScope.launch {
            while (isActive && isTempAlertEnabled) {
                try {
                    val batteryIntent = requireContext().registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    batteryIntent?.let {
                        val temp = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10.0f
                        if (temp >= tempAlertThreshold) {
                            withContext(Dispatchers.Main) {
                                sendTempAlertNotification(temp)
                            }
                        }
                    }
                } catch (_: Exception) {}
                delay(30000)
            }
        }
    }

    private fun sendTempAlertNotification(temp: Float) {
        try {
            val nm = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "temp_alert"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(channelId, "Temperature Alerts", NotificationManager.IMPORTANCE_HIGH)
                )
            }

            NotificationCompat.Builder(requireContext(), channelId)
                .setContentTitle("🌡️ HIGH TEMPERATURE ALERT")
                .setContentText("Battery at ${"%.1f".format(temp)}°C (threshold: ${tempAlertThreshold}°C)")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
                .also { nm.notify(5002, it) }
        } catch (_: Exception) {}
    }

    // ========== UPGRADE: Overnight Charging Protection ==========
    private fun toggleOvernightProtection() {
        isOvernightProtectionEnabled = !isOvernightProtectionEnabled
        saveUpgradeSettings()

        if (isOvernightProtectionEnabled) {
            startOvernightProtection()
        } else {
            overnightJob?.cancel()
        }

        try {
            textViewOvernightStatus.text = if (isOvernightProtectionEnabled) {
                "Overnight Protection: ON"
            } else {
                "Overnight Protection: OFF"
            }
            textViewOvernightStatus.setTextColor(if (isOvernightProtectionEnabled) 0xFF9C27B0.toInt() else 0xFF888888.toInt())
        } catch (_: Exception) {}

        Toast.makeText(
            requireContext(),
            if (isOvernightProtectionEnabled) "🌙 Overnight protection enabled - will auto-optimize during night charging" else "Overnight protection disabled",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun startOvernightProtection() {
        overnightJob?.cancel()
        overnightJob = lifecycleScope.launch {
            while (isActive && isOvernightProtectionEnabled) {
                // Check if it's nighttime (10PM - 6AM)
                val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                val isNightTime = hour >= 22 || hour < 6

                if (isNightTime) {
                    // Check if charging
                    val batteryIntent = requireContext().registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    batteryIntent?.let {
                        val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING

                        if (isCharging) {
                            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                            val pct = (level * 100) / scale.coerceAtLeast(1)

                            if (pct >= 80) {
                                // Notify user to unplug
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(requireContext(), "🌙 Overnight: Battery at $pct%. Consider unplugging.", Toast.LENGTH_SHORT).show()
                                }
                            }

                            // Kill battery-draining background processes during overnight
                            withContext(Dispatchers.Main) {
                                killBackgroundProcesses()
                            }
                        }
                    }
                }

                delay(30 * 60 * 1000L) // Check every 30 minutes
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        // ACTION_BATTERY_CHANGED is a system broadcast, needs RECEIVER_EXPORTED on Android 14+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(batteryReceiver, filter, android.content.Context.RECEIVER_EXPORTED)
        } else {
            requireContext().registerReceiver(batteryReceiver, filter)
        }
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
        tempAlertJob?.cancel()
        overnightJob?.cancel()
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
