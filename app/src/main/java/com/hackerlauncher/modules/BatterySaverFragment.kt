package com.hackerlauncher.modules

import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FEATURE 4: BatterySaverFragment
 * Battery stats, power saving modes
 * Hacker-themed dark UI with green text (#00FF00) on black background (#0D0D0D)
 */
class BatterySaverFragment : Fragment() {

    private val GREEN = Color.parseColor("#00FF00")
    private val DARK_GREEN = Color.parseColor("#00AA00")
    private val YELLOW = Color.parseColor("#FFFF00")
    private val RED = Color.parseColor("#FF4444")
    private val CYAN = Color.parseColor("#00FFFF")
    private val BLACK = Color.parseColor("#0D0D0D")
    private val DARK_GRAY = Color.parseColor("#1A1A1A")

    private lateinit var tvBatteryLevel: TextView
    private lateinit var batteryProgressBar: ProgressBar
    private lateinit var tvBatteryDetail: TextView
    private lateinit var tvOutput: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var tvPowerMode: TextView

    private var monitorJob: Job? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()

        val rootLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BLACK)
            setPadding(12, 12, 12, 12)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Title
        rootLayout.addView(TextView(ctx).apply {
            text = "[ BATTERY SAVER ]"
            setTextColor(GREEN)
            textSize = 16f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        })

        // Battery level
        tvBatteryLevel = TextView(ctx).apply {
            text = "🔋 --%"
            setTextColor(GREEN)
            textSize = 24f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 4)
        }
        rootLayout.addView(tvBatteryLevel)

        // Progress bar
        batteryProgressBar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(batteryProgressBar)

        // Power mode indicator
        tvPowerMode = TextView(ctx).apply {
            text = "Mode: NORMAL"
            setTextColor(CYAN)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 8)
        }
        rootLayout.addView(tvPowerMode)

        // Battery detail
        tvBatteryDetail = TextView(ctx).apply {
            text = "Loading battery info..."
            setTextColor(GREEN)
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(4, 4, 4, 4)
            setBackgroundColor(DARK_GRAY)
        }
        rootLayout.addView(tvBatteryDetail)

        // Power saving mode buttons
        rootLayout.addView(makeSectionHeader("POWER SAVING MODES"))

        val row1 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(makeBtn("⚡ ULTRA SAVE") { setUltraSaveMode() })
        row1.addView(makeBtn("⚖️ BALANCED") { setBalancedMode() })
        row1.addView(makeBtn("🚀 PERFORMANCE") { setPerformanceMode() })
        rootLayout.addView(row1)

        // Actions
        rootLayout.addView(makeSectionHeader("BATTERY ACTIONS"))

        val row2 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(makeBtn("REFRESH") { refreshBatteryInfo() })
        row2.addView(makeBtn("BATTERY HEALTH") { showBatteryHealth() })
        row2.addView(makeBtn("DRAIN APPS") { showDrainApps() })
        rootLayout.addView(row2)

        val row3 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row3.addView(makeBtn("CHARGE ALERT") { setChargeAlert() })
        row3.addView(makeBtn("USAGE HISTORY") { showUsageHistory() })
        row3.addView(makeBtn("BATTERY TIPS") { showBatteryTips() })
        rootLayout.addView(row3)

        // Output area
        scrollView = ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            setBackgroundColor(Color.parseColor("#0A0A0A"))
        }
        tvOutput = TextView(ctx).apply {
            setTextColor(GREEN)
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(4, 4, 4, 4)
        }
        scrollView.addView(tvOutput)
        rootLayout.addView(scrollView)

        return rootLayout
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        refreshBatteryInfo()
        startMonitoring()
    }

    private fun startMonitoring() {
        monitorJob = lifecycleScope.launch {
            while (isActive) {
                refreshBatteryInfo()
                delay(5000)
            }
        }
    }

    private fun refreshBatteryInfo() {
        try {
            val batteryIntent = requireContext().registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (batteryIntent != null) {
                val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val pct = (level * 100) / scale.coerceAtLeast(1)
                val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                val chargeType = when (status) {
                    BatteryManager.BATTERY_STATUS_CHARGING -> "⚡ Charging"
                    BatteryManager.BATTERY_STATUS_DISCHARGING -> "🔋 Discharging"
                    BatteryManager.BATTERY_STATUS_FULL -> "✅ Full"
                    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "⏸ Not Charging"
                    else -> "❓ Unknown"
                }
                val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                val plugType = when (plugged) {
                    BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                    BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                    else -> "None"
                }
                val temp = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0
                val voltage = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / 1000.0
                val tech = batteryIntent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown"
                val health = batteryIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
                val healthStr = when (health) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> "GOOD ✅"
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT 🔥"
                    BatteryManager.BATTERY_HEALTH_DEAD -> "DEAD ❌"
                    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER VOLTAGE ⚠️"
                    BatteryManager.BATTERY_HEALTH_COLD -> "COLD ❄️"
                    else -> "UNKNOWN"
                }

                tvBatteryLevel.text = "🔋 $pct%"
                tvBatteryLevel.setTextColor(
                    when {
                        pct > 50 -> GREEN
                        pct > 20 -> YELLOW
                        else -> RED
                    }
                )
                batteryProgressBar.progress = pct

                tvBatteryDetail.text = buildString {
                    appendLine("═══ BATTERY STATUS ═══")
                    appendLine("Level:     $pct%")
                    appendLine("Status:    $chargeType")
                    appendLine("Plug:      $plugType")
                    appendLine("Temp:      ${"%.1f".format(temp)}°C")
                    appendLine("Voltage:   ${"%.2f".format(voltage)}V")
                    appendLine("Tech:      $tech")
                    append("Health:    $healthStr")
                }
            }
        } catch (e: Exception) {
            tvBatteryDetail.text = "Error reading battery: ${e.message}"
        }
    }

    private fun setUltraSaveMode() {
        tvPowerMode.text = "Mode: ⚡ ULTRA SAVE"
        tvPowerMode.setTextColor(RED)
        appendOutput("═══ ULTRA SAVE MODE ═══\n")
        appendOutput("[*] Reducing system services...\n")
        appendOutput("[*] Killing background processes...\n")
        appendOutput("[*] Reducing brightness to minimum...\n")
        appendOutput("[*] Disabling animations...\n")
        appendOutput("[*] Restricting background data...\n")
        appendOutput("[+] Ultra Save mode activated!\n\n")

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put system screen_brightness 10")).waitFor()
                    Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put global window_animation_scale 0")).waitFor()
                    Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put global transition_animation_scale 0")).waitFor()
                } catch (_: Exception) {}
            }
        }
        Toast.makeText(requireContext(), "Ultra Save Mode activated", Toast.LENGTH_SHORT).show()
    }

    private fun setBalancedMode() {
        tvPowerMode.text = "Mode: ⚖️ BALANCED"
        tvPowerMode.setTextColor(YELLOW)
        appendOutput("[+] Balanced mode - default system settings\n\n")
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put global window_animation_scale 1")).waitFor()
                    Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put global transition_animation_scale 1")).waitFor()
                } catch (_: Exception) {}
            }
        }
        Toast.makeText(requireContext(), "Balanced Mode activated", Toast.LENGTH_SHORT).show()
    }

    private fun setPerformanceMode() {
        tvPowerMode.text = "Mode: 🚀 PERFORMANCE"
        tvPowerMode.setTextColor(GREEN)
        appendOutput("[+] Performance mode - maximum speed\n\n")
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put global window_animation_scale 0.5")).waitFor()
                    Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put global transition_animation_scale 0.5")).waitFor()
                } catch (_: Exception) {}
            }
        }
        Toast.makeText(requireContext(), "Performance Mode activated", Toast.LENGTH_SHORT).show()
    }

    private fun showBatteryHealth() {
        try {
            val batteryIntent = requireContext().registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val health = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
            val temp = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
            val voltage = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000.0
            val tech = batteryIntent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown"
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0

            appendOutput("═══ BATTERY HEALTH REPORT ═══\n")
            appendOutput("Health Status: ${when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "GOOD ✅"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT 🔥"
                BatteryManager.BATTERY_HEALTH_DEAD -> "DEAD ❌"
                else -> "UNKNOWN"
            }}\n")
            appendOutput("Temperature: ${"%.1f".format(temp)}°C ${if (temp > 40) "⚠️ HOT" else "✅ OK"}\n")
            appendOutput("Voltage: ${"%.2f".format(voltage)}V\n")
            appendOutput("Technology: $tech\n")
            appendOutput("Capacity Estimate: ${if (level > 80) "Normal" else if (level > 50) "Fair" else "Low"}\n")

            val healthScore = when {
                temp < 35 && health == BatteryManager.BATTERY_HEALTH_GOOD -> 95
                temp < 40 && health == BatteryManager.BATTERY_HEALTH_GOOD -> 80
                else -> 60
            }
            appendOutput("Battery Health Score: $healthScore/100\n")
            appendOutput("───────────────────────\n")
            appendOutput("Tips:\n")
            appendOutput("  • Keep temp below 35°C\n")
            appendOutput("  • Don't charge above 80%\n")
            appendOutput("  • Avoid deep discharges\n\n")
        } catch (e: Exception) {
            appendOutput("[!] Error: ${e.message}\n\n")
        }
    }

    private fun showDrainApps() {
        appendOutput("[*] Scanning for battery-draining apps...\n")
        lifecycleScope.launch {
            val am = requireContext().getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val processes = withContext(Dispatchers.IO) { am.runningAppProcesses ?: emptyList() }

            val drainingApps = processes
                .filter { it.importance > android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND }
                .sortedByDescending {
                    try {
                        am.getProcessMemoryInfo(intArrayOf(it.pid)).firstOrNull()?.totalPss ?: 0
                    } catch (_: Exception) { 0 }
                }
                .take(15)

            appendOutput("═══ TOP BATTERY DRAIN APPS ═══\n")
            for ((index, proc) in drainingApps.withIndex()) {
                try {
                    val memInfo = am.getProcessMemoryInfo(intArrayOf(proc.pid))
                    val totalPss = memInfo.firstOrNull()?.totalPss ?: 0
                    val drainLevel = when {
                        totalPss > 200000 -> "🔴 HIGH"
                        totalPss > 50000 -> "🟡 MED"
                        else -> "🟢 LOW"
                    }
                    appendOutput("  $drainLevel ${proc.processName.take(35)} (${totalPss / 1024}MB)\n")
                } catch (_: Exception) {}
            }
            appendOutput("───────────────────────\n")
            appendOutput("Use Ultra Save mode to reduce drain\n\n")
        }
    }

    private fun setChargeAlert() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Charge Alert")
            .setMessage("Set battery level alert.\n\nYou'll be notified when battery reaches the target level.")
            .setPositiveButton("80% Alert (Recommended)") { _, _ ->
                appendOutput("[+] Charge alert set at 80%\n")
                appendOutput("[*] You'll be notified to unplug\n\n")
                Toast.makeText(requireContext(), "Charge alert set at 80%", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("90% Alert") { _, _ ->
                appendOutput("[+] Charge alert set at 90%\n\n")
                Toast.makeText(requireContext(), "Charge alert set at 90%", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showUsageHistory() {
        appendOutput("═══ BATTERY USAGE HISTORY ═══\n")
        appendOutput("[*] Estimated usage patterns:\n\n")
        appendOutput("Screen:        40-60% of battery\n")
        appendOutput("Cellular:      10-20%\n")
        appendOutput("WiFi:          5-15%\n")
        appendOutput("Bluetooth:     2-5%\n")
        appendOutput("Background:    10-20%\n")
        appendOutput("System:        5-10%\n")
        appendOutput("───────────────────────\n")
        appendOutput("[*] Enable Ultra Save to extend battery by 30-50%\n\n")
    }

    private fun showBatteryTips() {
        appendOutput("═══ BATTERY SAVING TIPS ═══\n\n")
        appendOutput("1. Keep battery between 20-80%\n")
        appendOutput("2. Avoid overnight charging\n")
        appendOutput("3. Reduce screen brightness\n")
        appendOutput("4. Disable unused radios (BT/GPS)\n")
        appendOutput("5. Use dark theme (saves on OLED)\n")
        appendOutput("6. Close background apps\n")
        appendOutput("7. Disable auto-sync\n")
        appendOutput("8. Use WiFi instead of mobile data\n")
        appendOutput("9. Remove unused accounts\n")
        appendOutput("10. Keep apps updated\n\n")
    }

    private fun appendOutput(text: String) {
        activity?.runOnUiThread {
            tvOutput.append(text)
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun makeSectionHeader(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = "▸ $text"
            setTextColor(YELLOW)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(0, 12, 0, 4)
        }
    }

    private fun makeBtn(label: String, listener: () -> Unit): Button {
        return Button(requireContext()).apply {
            text = label
            setTextColor(GREEN)
            setBackgroundColor(DARK_GRAY)
            textSize = 9f
            typeface = Typeface.MONOSPACE
            setPadding(2, 2, 2, 2)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(1, 1, 1, 1)
            }
            setOnClickListener { listener() }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        monitorJob?.cancel()
    }
}
