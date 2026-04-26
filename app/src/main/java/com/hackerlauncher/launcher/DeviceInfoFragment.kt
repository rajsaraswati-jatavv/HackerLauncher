package com.hackerlauncher.launcher

import android.app.ActivityManager
import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.StatFs
import android.os.Environment
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.NetworkInterface
import java.text.DecimalFormat

class DeviceInfoFragment : Fragment() {

    private lateinit var container: LinearLayout
    private val df = DecimalFormat("#,###")
    private val df1 = DecimalFormat("#,##0.0")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val scrollView = ScrollView(requireContext()).apply {
            setBackgroundColor(Color.BLACK)
        }
        this.container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        scrollView.addView(this.container)

        val header = makeHeader("> SYSTEM_SCAN_INIT...")
        this.container.addView(header)

        lifecycleScope.launch {
            loadAllInfo()
        }

        return scrollView
    }

    private suspend fun loadAllInfo() {
        withContext(Dispatchers.IO) {
            val sections = mutableListOf<Pair<String, List<Pair<String, String>>>>()

            sections.add(loadBatteryInfo())
            sections.add(loadCpuInfo())
            sections.add(loadRamInfo())
            sections.add(loadStorageInfo())
            sections.add(loadScreenInfo())
            sections.add(loadNetworkInfo())
            sections.add(loadOsInfo())
            sections.add(loadDeviceInfo())
            sections.add(loadSensorInfo())
            sections.add(loadGpuInfo())

            withContext(Dispatchers.Main) {
                container.removeAllViews()
                container.addView(makeHeader("> SCAN_COMPLETE // ${sections.size} MODULES"))
                sections.forEach { (title, entries) ->
                    container.addView(makeSection(title, entries))
                }
            }
        }
    }

    // ─── Battery ───────────────────────────────────────────────────────────

    private fun loadBatteryInfo(): Pair<String, List<Pair<String, String>>> {
        val ctx = requireContext()
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val intentFilter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        val batteryIntent = ctx.registerReceiver(null, intentFilter)

        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val health = when (batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "GOOD"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT"
            BatteryManager.BATTERY_HEALTH_DEAD -> "DEAD"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER_VOLTAGE"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "FAILURE"
            else -> "UNKNOWN"
        }
        val status = when (batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "DISCHARGING"
            BatteryManager.BATTERY_STATUS_FULL -> "FULL"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "NOT_CHARGING"
            else -> "UNKNOWN"
        }
        val temp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.div(10.0f) ?: 0f
        val voltage = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)?.div(1000.0f) ?: 0f
        val technology = batteryIntent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "N/A"

        return "⚡ BATTERY" to listOf(
            "level" to "$level%",
            "health" to health,
            "status" to status,
            "temperature" to "${df1.format(temp)}°C",
            "voltage" to "${df1.format(voltage)}V",
            "technology" to technology
        )
    }

    // ─── CPU ───────────────────────────────────────────────────────────────

    private fun loadCpuInfo(): Pair<String, List<Pair<String, String>>> {
        val abis = Build.SUPPORTED_ABIS.joinToString(", ")
        val cores = Runtime.getRuntime().availableProcessors()

        var maxFreq = "N/A"
        try {
            val freqFile = File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
            if (freqFile.exists()) {
                val freq = freqFile.readText().trim().toLong()
                maxFreq = "${df.format(freq / 1000)} MHz"
            }
        } catch (_: Exception) { }

        var cpuModel = "N/A"
        try {
            val cpuInfo = File("/proc/cpuinfo").readText()
            val modelLine = cpuInfo.lines().find { it.startsWith("Hardware") || it.startsWith("model name") }
            cpuModel = modelLine?.substringAfter(":")?.trim() ?: Build.HARDWARE
        } catch (_: Exception) {
            cpuModel = Build.HARDWARE
        }

        return "🧠 CPU" to listOf(
            "model" to cpuModel,
            "abi" to abis,
            "cores" to cores.toString(),
            "max_freq" to maxFreq
        )
    }

    // ─── RAM ───────────────────────────────────────────────────────────────

    private fun loadRamInfo(): Pair<String, List<Pair<String, String>>> {
        val ctx = requireContext()
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val total = memInfo.totalMem
        val available = memInfo.availMem
        val threshold = memInfo.threshold
        val lowMem = memInfo.lowMemory

        return "💾 RAM" to listOf(
            "total" to formatBytes(total),
            "available" to formatBytes(available),
            "threshold" to formatBytes(threshold),
            "low_memory" to lowMem.toString().uppercase()
        )
    }

    // ─── Storage ───────────────────────────────────────────────────────────

    private fun loadStorageInfo(): Pair<String, List<Pair<String, String>>> {
        val entries = mutableListOf<Pair<String, String>>()

        // Internal
        val internalStat = StatFs(Environment.getDataDirectory().path)
        val internalTotal = internalStat.totalBytes
        val internalFree = internalStat.availableBytes
        entries.add("internal_total" to formatBytes(internalTotal))
        entries.add("internal_free" to formatBytes(internalFree))
        entries.add("internal_used" to formatBytes(internalTotal - internalFree))

        // External
        if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            try {
                val externalStat = StatFs(Environment.getExternalStorageDirectory().path)
                val externalTotal = externalStat.totalBytes
                val externalFree = externalStat.availableBytes
                entries.add("external_total" to formatBytes(externalTotal))
                entries.add("external_free" to formatBytes(externalFree))
                entries.add("external_used" to formatBytes(externalTotal - externalFree))
            } catch (_: Exception) {
                entries.add("external" to "UNAVAILABLE")
            }
        } else {
            entries.add("external" to "NOT_MOUNTED")
        }

        return "💿 STORAGE" to entries
    }

    // ─── Screen ────────────────────────────────────────────────────────────

    private fun loadScreenInfo(): Pair<String, List<Pair<String, String>>> {
        val ctx = requireContext()
        val dm = DisplayMetrics()
        requireActivity().windowManager.defaultDisplay.getMetrics(dm)

        val widthPx = dm.widthPixels
        val heightPx = dm.heightPixels
        val density = dm.density
        val densityDpi = dm.densityDpi
        val xdpi = dm.xdpi
        val ydpi = dm.ydpi

        var refreshRate = 60.0
        try {
            refreshRate = requireActivity().windowManager.defaultDisplay.refreshRate.toDouble()
        } catch (_: Exception) { }

        val widthInch = widthPx / xdpi
        val heightInch = heightPx / ydpi
        val screenSize = Math.sqrt(widthInch * widthInch + heightInch * heightInch)

        return "📱 SCREEN" to listOf(
            "resolution" to "${widthPx}x${heightPx}",
            "density" to "$density (${getDensityName(densityDpi)})",
            "dpi" to densityDpi.toString(),
            "xdpi" to df1.format(xdpi),
            "ydpi" to df1.format(ydpi),
            "refresh_rate" to "${df1.format(refreshRate)} Hz",
            "size" to "${df1.format(screenSize)} inches"
        )
    }

    // ─── Network ───────────────────────────────────────────────────────────

    private fun loadNetworkInfo(): Pair<String, List<Pair<String, String>>> {
        val entries = mutableListOf<Pair<String, String>>()
        val ctx = requireContext()

        @Suppress("DEPRECATION")
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val activeNetwork = cm.activeNetworkInfo
        entries.add("type" to (activeNetwork?.typeName?.uppercase() ?: "DISCONNECTED"))

        // WiFi info
        try {
            val wifiMgr = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val wifiInfo = wifiMgr.connectionInfo
            @Suppress("DEPRECATION")
            val ssid = wifiInfo.ssid?.removeSurrounding("\"") ?: "N/A"
            entries.add("wifi_ssid" to ssid)
            val ipInt = wifiInfo.ipAddress
            val ip = if (ipInt != 0) {
                "${ipInt and 0xFF}.${(ipInt shr 8) and 0xFF}.${(ipInt shr 16) and 0xFF}.${(ipInt shr 24) and 0xFF}"
            } else "N/A"
            entries.add("wifi_ip" to ip)
            @Suppress("DEPRECATION")
            entries.add("wifi_mac" to wifiInfo.macAddress ?: "N/A")
        } catch (_: Exception) {
            entries.add("wifi" to "PERMISSION_DENIED")
        }

        // Get IP from network interfaces
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        entries.add("ipv4" to addr.hostAddress ?: "N/A")
                    }
                }
            }
        } catch (_: Exception) { }

        return "🌐 NETWORK" to entries
    }

    // ─── OS ────────────────────────────────────────────────────────────────

    private fun loadOsInfo(): Pair<String, List<Pair<String, String>>> {
        val codename = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Build.VERSION.RELEASE_OR_CODENAME
        } else {
            Build.VERSION.RELEASE
        }

        return "🐧 OS" to listOf(
            "version" to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "codename" to codename,
            "security_patch" to (Build.VERSION.SECURITY_PATCH.ifEmpty { "N/A" }),
            "build_number" to Build.DISPLAY,
            "incremental" to Build.VERSION.INCREMENTAL,
            "base_os" to (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Build.VERSION.BASE_OS else "N/A")
        )
    }

    // ─── Device ────────────────────────────────────────────────────────────

    private fun loadDeviceInfo(): Pair<String, List<Pair<String, String>>> {
        return "🔧 DEVICE" to listOf(
            "model" to Build.MODEL,
            "manufacturer" to Build.MANUFACTURER,
            "brand" to Build.BRAND,
            "device" to Build.DEVICE,
            "hardware" to Build.HARDWARE,
            "product" to Build.PRODUCT,
            "board" to Build.BOARD,
            "bootloader" to Build.BOOTLOADER,
            "serial" to try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    "RESTRICTED"
                } else {
                    @Suppress("DEPRECATION")
                    Build.SERIAL
                }
            } catch (_: Exception) { "N/A" }
        )
    }

    // ─── Sensors ───────────────────────────────────────────────────────────

    private fun loadSensorInfo(): Pair<String, List<Pair<String, String>>> {
        val ctx = requireContext()
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensors = sm.getSensorList(Sensor.TYPE_ALL)

        val entries = mutableListOf<Pair<String, String>>()
        entries.add("count" to sensors.size.toString())

        val typeMap = mutableMapOf<String, Int>()
        sensors.forEach { sensor ->
            val typeName = getSensorTypeName(sensor.type)
            typeMap[typeName] = (typeMap[typeName] ?: 0) + 1
        }
        typeMap.forEach { (name, count) ->
            entries.add(name to count.toString())
        }

        return "📡 SENSORS" to entries
    }

    // ─── GPU ───────────────────────────────────────────────────────────────

    private fun loadGpuInfo(): Pair<String, List<Pair<String, String>>> {
        val entries = mutableListOf<Pair<String, String>>()

        try {
            val extensions = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_EXTENSIONS) ?: ""
            val renderer = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_RENDERER) ?: "N/A"
            val vendor = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_VENDOR) ?: "N/A"
            val version = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_VERSION) ?: "N/A"
            entries.add("renderer" to renderer)
            entries.add("vendor" to vendor)
            entries.add("gl_version" to version)
            entries.add("extensions_count" to extensions.split(" ").filter { it.isNotBlank() }.size.toString())
        } catch (_: Exception) {
            entries.add("gpu" to "UNAVAILABLE")
        }

        return "🎮 GPU" to entries
    }

    // ─── UI Builders ───────────────────────────────────────────────────────

    private fun makeHeader(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setTextColor(Color.parseColor("#00FF00"))
            textSize = 16f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 16, 0, 16)
        }
    }

    private fun makeSection(title: String, entries: List<Pair<String, String>>): LinearLayout {
        val ctx = requireContext()
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12, 0, 4)

            // Section title
            addView(TextView(ctx).apply {
                text = "┌── $title ──"
                setTextColor(Color.parseColor("#00FF00"))
                textSize = 14f
                typeface = android.graphics.Typeface.MONOSPACE
                setPadding(0, 8, 0, 4)
            })

            entries.forEachIndexed { index, (key, value) ->
                addView(TextView(ctx).apply {
                    val prefix = if (index == entries.size - 1) "└──" else "├──"
                    text = "$prefix $key: $value"
                    setTextColor(Color.parseColor("#00CC00"))
                    textSize = 12f
                    typeface = android.graphics.Typeface.MONOSPACE
                    setPadding(16, 2, 0, 2)
                })
            }
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private fun formatBytes(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var unitIndex = 0
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        return "${df1.format(size)} ${units[unitIndex]}"
    }

    private fun getDensityName(densityDpi: Int): String = when {
        densityDpi <= 120 -> "ldpi"
        densityDpi <= 160 -> "mdpi"
        densityDpi <= 240 -> "hdpi"
        densityDpi <= 320 -> "xhdpi"
        densityDpi <= 480 -> "xxhdpi"
        densityDpi <= 640 -> "xxxhdpi"
        else -> "${densityDpi}dpi"
    }

    private fun getSensorTypeName(type: Int): String = when (type) {
        Sensor.TYPE_ACCELEROMETER -> "accelerometer"
        Sensor.TYPE_MAGNETIC_FIELD -> "magnetic_field"
        Sensor.TYPE_GYROSCOPE -> "gyroscope"
        Sensor.TYPE_LIGHT -> "light"
        Sensor.TYPE_PRESSURE -> "pressure"
        Sensor.TYPE_PROXIMITY -> "proximity"
        Sensor.TYPE_GRAVITY -> "gravity"
        Sensor.TYPE_LINEAR_ACCELERATION -> "linear_accel"
        Sensor.TYPE_ROTATION_VECTOR -> "rotation_vector"
        Sensor.TYPE_RELATIVE_HUMIDITY -> "humidity"
        Sensor.TYPE_AMBIENT_TEMPERATURE -> "ambient_temp"
        Sensor.TYPE_STEP_COUNTER -> "step_counter"
        Sensor.TYPE_STEP_DETECTOR -> "step_detector"
        Sensor.TYPE_GAME_ROTATION_VECTOR -> "game_rotation"
        Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "gyro_uncalib"
        Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "mag_uncalib"
        else -> "type_$type"
    }
}
