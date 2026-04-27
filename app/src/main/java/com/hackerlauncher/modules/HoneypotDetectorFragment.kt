package com.hackerlauncher.modules

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

class HoneypotDetectorFragment : Fragment() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var scrollView: ScrollView
    private lateinit var tvOutput: TextView
    private lateinit var tvRiskScore: TextView
    private lateinit var progressRisk: ProgressBar

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
            setPadding(12, 12, 12, 12)
        }

        // Title
        root.addView(TextView(context).apply {
            text = ">> HONEYPOT DETECTOR v1.1"
            setTextColor(0xFF00FF00.toInt())
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setPadding(0, 8, 0, 8)
        })

        // Risk score display
        val scoreLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 8)
        }
        tvRiskScore = TextView(context).apply {
            text = "RISK: --/100"
            setTextColor(0xFF00FF00.toInt())
            textSize = 16f
            typeface = Typeface.MONOSPACE
            setPadding(8, 8, 16, 8)
        }
        scoreLayout.addView(tvRiskScore)
        progressRisk = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        scoreLayout.addView(progressRisk)
        root.addView(scoreLayout)

        // Buttons
        val btnRow1 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow1.addView(makeBtn("Full Scan") { runFullScan() })
        btnRow1.addView(makeBtn("Emulator") { checkEmulator() })
        btnRow1.addView(makeBtn("Root") { checkRoot() })
        root.addView(btnRow1)

        val btnRow2 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow2.addView(makeBtn("Debugger") { checkDebugger() })
        btnRow2.addView(makeBtn("Xposed") { checkXposed() })
        btnRow2.addView(makeBtn("Frida") { checkFrida() })
        root.addView(btnRow2)

        val btnRow3 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow3.addView(makeBtn("Sandbox") { checkSandbox() })
        btnRow3.addView(makeBtn("Build Info") { showBuildInfo() })
        root.addView(btnRow3)

        // Output
        scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        tvOutput = TextView(context).apply {
            setTextColor(0xFF00FF00.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(4, 4, 4, 4)
        }
        scrollView.addView(tvOutput)
        root.addView(scrollView)

        appendOutput("╔══════════════════════════════════╗\n")
        appendOutput("║  HONEYPOT DETECTOR v1.1         ║\n")
        appendOutput("║  Detect sandbox/emulator/root   ║\n")
        appendOutput("║  Risk score: 0 (safe) - 100     ║\n")
        appendOutput("╚══════════════════════════════════╝\n\n")

        return root
    }

    private fun makeBtn(label: String, onClick: () -> Unit): Button {
        return Button(context).apply {
            text = label
            setTextColor(0xFF00FF00.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(6, 2, 6, 2)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(2, 2, 2, 2)
            }
        }
    }

    private fun updateRiskScore(score: Int) {
        val color = when {
            score >= 70 -> 0xFFFF0000.toInt()
            score >= 40 -> 0xFFFFFF00.toInt()
            else -> 0xFF00FF00.toInt()
        }
        tvRiskScore.text = "RISK: $score/100"
        tvRiskScore.setTextColor(color)
        progressRisk.progress = score
    }

    private fun runFullScan() {
        scope.launch {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║     FULL SECURITY SCAN          ║\n")
            appendOutput("╠══════════════════════════════════╣\n\n")

            var totalRisk = 0

            val emuScore = withContext(Dispatchers.IO) { detectEmulator(true) }
            totalRisk += emuScore
            appendOutput("[Emulator] Score: +$emuScore\n")

            val rootScore = withContext(Dispatchers.IO) { detectRoot(true) }
            totalRisk += rootScore
            appendOutput("[Root] Score: +$rootScore\n")

            val debugScore = detectDebugger(true)
            totalRisk += debugScore
            appendOutput("[Debugger] Score: +$debugScore\n")

            val xposedScore = withContext(Dispatchers.IO) { detectXposed(true) }
            totalRisk += xposedScore
            appendOutput("[Xposed] Score: +$xposedScore\n")

            val fridaScore = withContext(Dispatchers.IO) { detectFrida(true) }
            totalRisk += fridaScore
            appendOutput("[Frida] Score: +$fridaScore\n")

            val sandboxScore = withContext(Dispatchers.IO) { detectSandbox(true) }
            totalRisk += sandboxScore
            appendOutput("[Sandbox] Score: +$sandboxScore\n")

            val finalScore = totalRisk.coerceIn(0, 100)
            appendOutput("\n")
            appendOutput("╠══════════════════════════════════╣\n")
            appendOutput("║  TOTAL RISK: $finalScore/100\n")

            when {
                finalScore >= 70 -> appendOutput("║  STATUS: !! DANGEROUS !!\n")
                finalScore >= 40 -> appendOutput("║  STATUS: ! SUSPICIOUS !\n")
                finalScore >= 20 -> appendOutput("║  STATUS: ~ LOW RISK ~\n")
                else -> appendOutput("║  STATUS: CLEAN\n")
            }
            appendOutput("╚══════════════════════════════════╝\n\n")

            updateRiskScore(finalScore)
        }
    }

    private fun checkEmulator() {
        scope.launch {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║     Emulator Detection         ║\n")
            appendOutput("╠══════════════════════════════════╣\n\n")
            val score = withContext(Dispatchers.IO) { detectEmulator(true) }
            appendOutput("\n  Score: +$score\n")
            appendOutput("╚══════════════════════════════════╝\n\n")
        }
    }

    private fun detectEmulator(verbose: Boolean = false): Int {
        var score = 0

        // Build.FINGERPRINT.contains("generic")
        val fingerprint = Build.FINGERPRINT
        if (fingerprint.contains("generic", ignoreCase = true) ||
            fingerprint.contains("emulator", ignoreCase = true) ||
            fingerprint.contains("sdk", ignoreCase = true)) {
            score += 15
            if (verbose) appendOutput("  [!] Fingerprint: $fingerprint\n")
        } else {
            if (verbose) appendOutput("  [+] Fingerprint: clean\n")
        }

        // Build.MODEL.contains("sdk")
        val model = Build.MODEL
        if (model.contains("sdk", ignoreCase = true) ||
            model.contains("Emulator", ignoreCase = true) ||
            model.contains("Android SDK", ignoreCase = true) ||
            model.contains("simulator", ignoreCase = true)) {
            score += 10
            if (verbose) appendOutput("  [!] Model: $model\n")
        } else {
            if (verbose) appendOutput("  [+] Model: $model\n")
        }

        // Build.MANUFACTURER
        val manufacturer = Build.MANUFACTURER
        if (manufacturer.contains("Genymotion", ignoreCase = true) ||
            manufacturer.contains("unknown", ignoreCase = true)) {
            score += 5
            if (verbose) appendOutput("  [!] Manufacturer: $manufacturer\n")
        } else {
            if (verbose) appendOutput("  [+] Manufacturer: $manufacturer\n")
        }

        // Build.BRAND
        val brand = Build.BRAND
        if (brand.contains("generic", ignoreCase = true)) {
            score += 5
            if (verbose) appendOutput("  [!] Brand: $brand\n")
        } else {
            if (verbose) appendOutput("  [+] Brand: $brand\n")
        }

        // Build.DEVICE
        val device = Build.DEVICE
        if (device.contains("generic", ignoreCase = true) ||
            device.contains("goldfish", ignoreCase = true) ||
            device.contains("sdk", ignoreCase = true)) {
            score += 10
            if (verbose) appendOutput("  [!] Device: $device\n")
        } else {
            if (verbose) appendOutput("  [+] Device: $device\n")
        }

        // Build.PRODUCT
        val product = Build.PRODUCT
        if (product.contains("sdk", ignoreCase = true) ||
            product.contains("google_sdk", ignoreCase = true) ||
            product.contains("simulator", ignoreCase = true)) {
            score += 10
            if (verbose) appendOutput("  [!] Product: $product\n")
        } else {
            if (verbose) appendOutput("  [+] Product: $product\n")
        }

        // "goldfish" in Build.HARDWARE
        val hardware = Build.HARDWARE
        if (hardware.contains("goldfish", ignoreCase = true) ||
            hardware.contains("ranchu", ignoreCase = true) ||
            hardware.contains("vbox", ignoreCase = true)) {
            score += 10
            if (verbose) appendOutput("  [!] Hardware: $hardware (goldfish check)\n")
        } else {
            if (verbose) appendOutput("  [+] Hardware: $hardware\n")
        }

        // Check for emulator files
        val emuFiles = listOf(
            "/dev/goldfish_pipe", "/dev/qemu_pipe",
            "/system/lib/libc_malloc_debug_qemu.so",
            "/sys/qemu_trace", "/system/bin/qemu-props"
        )
        for (f in emuFiles) {
            if (File(f).exists()) {
                score += 5
                if (verbose) appendOutput("  [!] Emulator file: $f\n")
            }
        }

        // Check qemu properties
        try {
            val qemu = System.getProperty("ro.kernel.qemu")
            if (qemu == "1") {
                score += 5
                if (verbose) appendOutput("  [!] ro.kernel.qemu = 1\n")
            }
        } catch (_: Exception) {}

        return score.coerceAtMost(30)
    }

    private fun checkRoot() {
        scope.launch {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║     Root Detection              ║\n")
            appendOutput("╠══════════════════════════════════╣\n\n")
            val score = withContext(Dispatchers.IO) { detectRoot(true) }
            appendOutput("\n  Score: +$score\n")
            appendOutput("╚══════════════════════════════════╝\n\n")
        }
    }

    private fun detectRoot(verbose: Boolean = false): Int {
        var score = 0

        // Check su binary
        val suPaths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/data/local/xbin/su", "/data/local/bin/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su",
            "/su/bin/su", "/magisk/.core/bin/su"
        )
        for (path in suPaths) {
            if (File(path).exists()) {
                score += 5
                if (verbose) appendOutput("  [!] su found: $path\n")
            }
        }
        if (score == 0 && verbose) appendOutput("  [+] No su binary found\n")

        // Check Magisk
        val magiskPaths = listOf(
            "/sbin/.magisk", "/cache/.disable_magisk",
            "/data/adb/magisk", "/data/adb/magisk.db"
        )
        for (path in magiskPaths) {
            if (File(path).exists()) {
                score += 5
                if (verbose) appendOutput("  [!] Magisk: $path\n")
            }
        }

        // Check Superuser.apk
        val superuserPaths = listOf(
            "/system/app/Superuser.apk", "/system/app/SuperSU",
            "/data/data/com.noshufou.android.su",
            "/data/data/eu.chainfire.supersu",
            "/data/data/com.topjohnwu.magisk"
        )
        for (path in superuserPaths) {
            if (File(path).exists()) {
                score += 3
                if (verbose) appendOutput("  [!] Superuser app: $path\n")
            }
        }

        // Check for root management tags
        try {
            val tags = Build.TAGS
            if (tags != null && tags.contains("test-keys")) {
                score += 5
                if (verbose) appendOutput("  [!] Build tags: test-keys\n")
            }
        } catch (_: Exception) {}

        // Check for busybox
        val busyboxPaths = listOf("/system/xbin/busybox", "/system/bin/busybox")
        for (path in busyboxPaths) {
            if (File(path).exists()) {
                score += 3
                if (verbose) appendOutput("  [!] busybox: $path\n")
            }
        }

        // Check for writable system paths
        if (File("/system").canWrite()) {
            score += 3
            if (verbose) appendOutput("  [!] /system is writable\n")
        }

        if (score == 0 && verbose) appendOutput("  [+] No root indicators found\n")
        return score.coerceAtMost(30)
    }

    private fun checkDebugger() {
        val score = detectDebugger(true)
        appendOutput("╔══════════════════════════════════╗\n")
        appendOutput("║     Debugger Detection          ║\n")
        appendOutput("╠══════════════════════════════════╣\n\n")
        appendOutput("  Score: +$score\n")
        appendOutput("╚══════════════════════════════════╝\n\n")
    }

    private fun detectDebugger(verbose: Boolean = false): Int {
        var score = 0

        // Debug.isDebuggerConnected()
        val debuggerConnected = android.os.Debug.isDebuggerConnected()
        if (debuggerConnected) {
            score += 10
            if (verbose) appendOutput("  [!] Debugger connected!\n")
        } else {
            if (verbose) appendOutput("  [+] No debugger connected\n")
        }

        // Check debug flag in application
        try {
            val appInfo = requireContext().applicationInfo
            if ((appInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                score += 5
                if (verbose) appendOutput("  [!] App is debuggable\n")
            } else {
                if (verbose) appendOutput("  [+] App not debuggable\n")
            }
        } catch (e: Exception) {
            if (verbose) appendOutput("  [?] Debuggable check: ${e.message}\n")
        }

        // Check for debugging in settings
        try {
            val waitForDebugger = Settings.System.getInt(requireContext().contentResolver, "wait_for_debugger", 0)
            if (waitForDebugger != 0) {
                score += 3
                if (verbose) appendOutput("  [!] Wait for debugger enabled\n")
            }
        } catch (_: Exception) {}

        // Check for ptrace via /proc/self/status
        try {
            val file = File("/proc/self/status")
            if (file.exists()) {
                val content = file.readText()
                val tracerPidLine = content.lines().find { it.startsWith("TracerPid:") }
                if (tracerPidLine != null) {
                    val pid = tracerPidLine.substringAfter(":").trim()
                    if (pid != "0") {
                        score += 5
                        if (verbose) appendOutput("  [!] Tracer PID: $pid (being traced!)\n")
                    } else {
                        if (verbose) appendOutput("  [+] Not being traced\n")
                    }
                }
            }
        } catch (_: Exception) {}

        return score.coerceAtMost(20)
    }

    private fun checkXposed() {
        scope.launch {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║     Xposed Detection            ║\n")
            appendOutput("╠══════════════════════════════════╣\n\n")
            val score = withContext(Dispatchers.IO) { detectXposed(true) }
            appendOutput("\n  Score: +$score\n")
            appendOutput("╚══════════════════════════════════╝\n\n")
        }
    }

    private fun detectXposed(verbose: Boolean = false): Int {
        var score = 0

        // Check for Xposed classes (de.robv.android.xposed)
        try {
            Class.forName("de.robv.android.xposed.XposedBridge")
            score += 10
            if (verbose) appendOutput("  [!] XposedBridge class found!\n")
        } catch (_: ClassNotFoundException) {
            if (verbose) appendOutput("  [+] No XposedBridge class\n")
        }

        try {
            Class.forName("de.robv.android.xposed.XposedHelpers")
            score += 5
            if (verbose) appendOutput("  [!] XposedHelpers class found!\n")
        } catch (_: ClassNotFoundException) {
            if (verbose) appendOutput("  [+] No XposedHelpers class\n")
        }

        // Check for Xposed files
        val xposedPaths = listOf(
            "/system/framework/XposedBridge.jar",
            "/system/lib/libxposed_art.so",
            "/data/dalvik-cache/system@framework@XposedBridge.jar@classes.dex",
            "/system/xposed.prop",
            "/cache/xposed/XposedBridge.jar"
        )
        for (path in xposedPaths) {
            if (File(path).exists()) {
                score += 5
                if (verbose) appendOutput("  [!] Xposed file: $path\n")
            }
        }

        // Check for EdXposed / LSPosed
        val lsposedPaths = listOf(
            "/data/adb/lspd", "/data/adb/modules/edxposed",
            "/data/adb/modules/riru_edxposed", "/data/adb/modules/zygisk_lsposed"
        )
        for (path in lsposedPaths) {
            if (File(path).exists()) {
                score += 5
                if (verbose) appendOutput("  [!] LSPosed/EdXposed: $path\n")
            }
        }

        // Check stack trace for Xposed
        try {
            throw Exception("stacktrace_check")
        } catch (e: Exception) {
            val stackTrace = e.stackTraceToString()
            if (stackTrace.contains("de.robv.android.xposed")) {
                score += 5
                if (verbose) appendOutput("  [!] Xposed in stack trace!\n")
            }
        }

        if (score == 0 && verbose) appendOutput("  [+] No Xposed indicators\n")
        return score.coerceAtMost(20)
    }

    private fun checkFrida() {
        scope.launch {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║     Frida Detection             ║\n")
            appendOutput("╠══════════════════════════════════╣\n\n")
            val score = withContext(Dispatchers.IO) { detectFrida(true) }
            appendOutput("\n  Score: +$score\n")
            appendOutput("╚══════════════════════════════════╝\n\n")
        }
    }

    private fun detectFrida(verbose: Boolean = false): Int {
        var score = 0

        // Check for Frida server on port 27042
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress("127.0.0.1", 27042), 500)
            socket.close()
            score += 15
            if (verbose) appendOutput("  [!] Frida server on port 27042!\n")
        } catch (_: Exception) {
            if (verbose) appendOutput("  [+] No Frida on port 27042\n")
        }

        // Check alternate port
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress("127.0.0.1", 27043), 500)
            socket.close()
            score += 10
            if (verbose) appendOutput("  [!] Frida on port 27043!\n")
        } catch (_: Exception) {}

        // Check for frida-related files
        val fridaPaths = listOf(
            "/data/local/tmp/frida-server",
            "/data/local/tmp/frida",
            "/data/local/tmp/re.frida.server",
            "/sdcard/frida-server"
        )
        for (path in fridaPaths) {
            if (File(path).exists()) {
                score += 5
                if (verbose) appendOutput("  [!] Frida file: $path\n")
            }
        }

        // Check for frida libraries in memory maps
        try {
            val maps = File("/proc/self/maps")
            if (maps.exists()) {
                val content = maps.readText()
                if (content.contains("frida", ignoreCase = true)) {
                    score += 10
                    if (verbose) appendOutput("  [!] Frida in memory maps!\n")
                } else {
                    if (verbose) appendOutput("  [+] No Frida in memory maps\n")
                }
                if (content.contains("frida-agent", ignoreCase = true)) {
                    score += 10
                    if (verbose) appendOutput("  [!] frida-agent in maps!\n")
                }
            }
        } catch (_: Exception) {}

        // Check for frida package
        try {
            val pm = requireContext().packageManager
            pm.getPackageInfo("re.frida.server", 0)
            score += 5
            if (verbose) appendOutput("  [!] re.frida.server package!\n")
        } catch (_: Exception) {}

        if (score == 0 && verbose) appendOutput("  [+] No Frida indicators\n")
        return score.coerceAtMost(20)
    }

    private fun checkSandbox() {
        scope.launch {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║     Sandbox Detection           ║\n")
            appendOutput("╠══════════════════════════════════╣\n\n")
            val score = withContext(Dispatchers.IO) { detectSandbox(true) }
            appendOutput("\n  Score: +$score\n")
            appendOutput("╚══════════════════════════════════╝\n\n")
        }
    }

    private fun detectSandbox(verbose: Boolean = false): Int {
        var score = 0

        // Check for analysis apps
        val analysisPackages = listOf(
            "com.crowdshield.client", "org.cert.android",
            "com.lookout", "com.zscaler.zscaler"
        )
        val pm = requireContext().packageManager
        for (pkg in analysisPackages) {
            try {
                pm.getPackageInfo(pkg, 0)
                score += 3
                if (verbose) appendOutput("  [!] Analysis app: $pkg\n")
            } catch (_: Exception) {}
        }

        // Check for VPN transport
        try {
            val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork
                val caps = connectivityManager.getNetworkCapabilities(network)
                if (caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true) {
                    score += 3
                    if (verbose) appendOutput("  [!] VPN transport detected\n")
                }
            }
        } catch (_: Exception) {}

        // Check sensor availability (emulators often lack sensors)
        try {
            val sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
            val sensors = sensorManager.getSensorList(android.hardware.Sensor.TYPE_ALL)
            if (sensors.size < 5) {
                score += 5
                if (verbose) appendOutput("  [!] Few sensors: ${sensors.size} (suspicious)\n")
            } else {
                if (verbose) appendOutput("  [+] Sensors: ${sensors.size} (normal)\n")
            }
        } catch (e: Exception) {
            if (verbose) appendOutput("  [?] Sensor check: ${e.message}\n")
        }

        // Check battery (emulators often report charging always)
        try {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = requireContext().registerReceiver(null, intentFilter)
            val level = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            if (verbose) appendOutput("  [i] Battery level: $level\n")
        } catch (_: Exception) {}

        // Check for known sandbox indicators in system properties
        try {
            val proc = Runtime.getRuntime().exec("getprop")
            proc.inputStream.bufferedReader().use { reader ->
                val lines = reader.readLines()
                for (line in lines) {
                    if (line.contains("sandbox", ignoreCase = true) ||
                        line.contains("genymotion", ignoreCase = true) ||
                        line.contains("bluestacks", ignoreCase = true) ||
                        line.contains("nox", ignoreCase = true) ||
                        line.contains("memu", ignoreCase = true)) {
                        score += 5
                        if (verbose) appendOutput("  [!] Sandbox prop: ${line.take(60)}\n")
                    }
                }
            }
        } catch (_: Exception) {}

        if (score == 0 && verbose) appendOutput("  [+] No sandbox indicators\n")
        return score.coerceAtMost(15)
    }

    private fun showBuildInfo() {
        appendOutput("╔══════════════════════════════════╗\n")
        appendOutput("║     Build Information           ║\n")
        appendOutput("╠══════════════════════════════════╣\n")
        try {
            appendOutput("  FINGERPRINT: ${Build.FINGERPRINT}\n")
            appendOutput("  MODEL: ${Build.MODEL}\n")
            appendOutput("  MANUFACTURER: ${Build.MANUFACTURER}\n")
            appendOutput("  BRAND: ${Build.BRAND}\n")
            appendOutput("  DEVICE: ${Build.DEVICE}\n")
            appendOutput("  PRODUCT: ${Build.PRODUCT}\n")
            appendOutput("  HARDWARE: ${Build.HARDWARE}\n")
            appendOutput("  BOARD: ${Build.BOARD}\n")
            appendOutput("  BOOTLOADER: ${Build.BOOTLOADER}\n")
            appendOutput("  DISPLAY: ${Build.DISPLAY}\n")
            appendOutput("  HOST: ${Build.HOST}\n")
            appendOutput("  ID: ${Build.ID}\n")
            appendOutput("  TAGS: ${Build.TAGS}\n")
            appendOutput("  TYPE: ${Build.TYPE}\n")
            appendOutput("  USER: ${Build.USER}\n")
            appendOutput("  SDK: ${Build.VERSION.SDK_INT}\n")
            appendOutput("  RELEASE: ${Build.VERSION.RELEASE}\n")
            appendOutput("  CODENAME: ${Build.VERSION.CODENAME}\n")
            appendOutput("  INCREMENTAL: ${Build.VERSION.INCREMENTAL}\n")
            appendOutput("  SECURITY_PATCH: ${Build.VERSION.SECURITY_PATCH}\n")
        } catch (e: Exception) {
            appendOutput("  [E] ${e.message}\n")
        }
        appendOutput("╚══════════════════════════════════╝\n\n")
    }

    private fun appendOutput(text: String) {
        activity?.runOnUiThread {
            tvOutput.append(text)
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scope.cancel()
    }
}
