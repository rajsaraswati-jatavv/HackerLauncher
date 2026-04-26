package com.hackerlauncher.modules

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.hackerlauncher.R
import com.hackerlauncher.utils.Logger
import com.hackerlauncher.utils.ShellExecutor
import kotlinx.coroutines.*

class BluetoothScannerFragment : Fragment() {

    private lateinit var tvOutput: TextView
    private lateinit var scrollView: ScrollView
    private val logger = Logger()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isScanning = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_network, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvOutput = view.findViewById(R.id.tvNetOutput)
        scrollView = view.findViewById(R.id.scrollViewNetwork)

        val btnWifiInfo = view.findViewById<Button>(R.id.btnWifiInfo)
        val btnScanWifi = view.findViewById<Button>(R.id.btnScanWifi)
        val btnArpTable = view.findViewById<Button>(R.id.btnArpTable)
        val btnDnsLookup = view.findViewById<Button>(R.id.btnDnsLookup)
        val btnPing = view.findViewById<Button>(R.id.btnPing)
        val btnPortScan = view.findViewById<Button>(R.id.btnPortScan)
        val btnIfconfig = view.findViewById<Button>(R.id.btnIfconfig)
        val btnNetstat = view.findViewById<Button>(R.id.btnNetstat)

        btnWifiInfo.text = "BT Status"
        btnScanWifi.text = "BT Scan"
        btnArpTable.text = "BLE Scan"
        btnDnsLookup.text = "BT Paired"
        btnPing.text = "BT Fingerprint"
        btnPortScan.text = "BT Logs"
        btnIfconfig.text = "NFC Check"
        btnNetstat.text = "BT Config"

        btnWifiInfo.setOnClickListener { checkBluetoothStatus() }
        btnScanWifi.setOnClickListener { scanBluetooth() }
        btnArpTable.setOnClickListener { scanBLE() }
        btnDnsLookup.setOnClickListener { listPairedDevices() }
        btnPing.setOnClickListener { fingerprintDevice() }
        btnPortScan.setOnClickListener { showBtLogs() }
        btnIfconfig.setOnClickListener { checkNfc() }
        btnNetstat.setOnClickListener { openBtSettings() }

        checkBluetoothStatus()
    }

    private fun checkBluetoothStatus() {
        val sb = StringBuilder("╔══════════════════════════════════╗\n")
        sb.append("║   Bluetooth Status              ║\n")
        sb.append("╠══════════════════════════════════╣\n")

        try {
            val btManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = btManager?.adapter

            if (adapter == null) {
                sb.append("║ Bluetooth: NOT SUPPORTED\n")
            } else {
                sb.append("║ Bluetooth: ${if (adapter.isEnabled) "ENABLED" else "DISABLED"}\n")
                sb.append("║ Name: ${adapter.name ?: "Unknown"}\n")
                sb.append("║ Address: ${adapter.address ?: "Unavailable"}\n")
                sb.append("║ Scan Mode: ${adapter.scanMode}\n")
                sb.append("║ Discoverable: ${adapter.scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE}\n")
                sb.append("║ State: ${adapter.state}\n")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val leScanner = adapter.bluetoothLeScanner
                    sb.append("║ BLE Scanner: ${if (leScanner != null) "Available" else "Not Available"}\n")
                }

                // Check BLE support
                sb.append("║ BLE Support: ${requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)}\n")
                sb.append("║ BT Classic: ${requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)}\n")

                // Connected devices
                try {
                    val connected = btManager.getConnectedDevices(BluetoothProfile.GATT)
                    sb.append("║ Connected Devices: ${connected.size}\n")
                    for (device in connected) {
                        sb.append("║   - ${device.name} (${device.address})\n")
                    }
                } catch (e: SecurityException) {
                    sb.append("║ Connected: Permission denied\n")
                }
            }
        } catch (e: Exception) {
            sb.append("║ Error: ${e.message}\n")
        }

        sb.append("╚══════════════════════════════════╝\n")
        appendOutput(sb.toString())
    }

    private fun scanBluetooth() {
        if (isScanning) {
            appendOutput("[*] Scan already in progress\n")
            return
        }

        scope.launch {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║   Bluetooth Classic Scan        ║\n")
            appendOutput("╠══════════════════════════════════╣\n")

            val result = withContext(Dispatchers.IO) {
                val sb = StringBuilder()

                // Try using shell commands for discovery
                val btResult = ShellExecutor.executeWithTimeout("hcitool scan 2>/dev/null", 15000)
                if (btResult.output.isNotEmpty() && !btResult.output.contains("not found")) {
                    sb.append("[*] hcitool scan results:\n${btResult.output}\n\n")
                } else {
                    sb.append("[*] hcitool not available\n")
                }

                // Try btmgmt
                val mgmtResult = ShellExecutor.executeWithTimeout("btmgmt find 2>/dev/null", 10000)
                if (mgmtResult.output.isNotEmpty() && !mgmtResult.output.contains("not found")) {
                    sb.append("[*] btmgmt results:\n${mgmtResult.output}\n\n")
                }

                // List from system
                val inquireResult = ShellExecutor.executeWithTimeout("hcitool inq 2>/dev/null", 10000)
                if (inquireResult.output.isNotEmpty()) {
                    sb.append("[*] Inquiry results:\n${inquireResult.output}\n")
                }

                if (sb.isEmpty()) {
                    sb.append("[*] No Bluetooth scanning tools available\n")
                    sb.append("[*] Install via Termux: pkg install bluez\n")
                    sb.append("[*] Or grant Bluetooth permissions and use system scan\n")
                }

                sb.toString()
            }
            appendOutput(result)
            appendOutput("╚══════════════════════════════════╝\n")
        }
    }

    private fun scanBLE() {
        appendOutput("╔══════════════════════════════════╗\n")
        appendOutput("║   BLE Scanner                   ║\n")
        appendOutput("╠══════════════════════════════════╣\n\n")

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            appendOutput("[E] BLE requires Android 5.0+\n")
            appendOutput("╚══════════════════════════════════╝\n")
            return
        }

        try {
            val btManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = btManager?.adapter
            if (adapter == null || !adapter.isEnabled) {
                appendOutput("[E] Bluetooth not enabled\n")
                appendOutput("╚══════════════════════════════════╝\n")
                return
            }

            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                appendOutput("[E] Bluetooth and Location permissions required\n")
                appendOutput("╚══════════════════════════════════╝\n")
                return
            }

            val leScanner = adapter.bluetoothLeScanner
            if (leScanner == null) {
                appendOutput("[E] BLE Scanner not available\n")
                appendOutput("╚══════════════════════════════════╝\n")
                return
            }

            isScanning = true
            appendOutput("[*] BLE scan started (10 seconds)...\n\n")

            val scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val device = result.device
                    val name = if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        device.name ?: "Unknown"
                    } else "Unknown"
                    val rssi = result.rssi
                    val type = when (result.device.type) {
                        android.bluetooth.BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic"
                        android.bluetooth.BluetoothDevice.DEVICE_TYPE_LE -> "BLE"
                        android.bluetooth.BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual"
                        else -> "Unknown"
                    }
                    appendOutput("  [FOUND] $name (${device.address})\n")
                    appendOutput("    RSSI: ${rssi}dBm | Type: $type\n\n")
                }

                override fun onScanFailed(errorCode: Int) {
                    appendOutput("[E] BLE scan failed: error $errorCode\n")
                    isScanning = false
                }
            }

            leScanner.startScan(scanCallback)

            // Stop after 10 seconds
            scope.launch {
                delay(10000)
                try {
                    leScanner.stopScan(scanCallback)
                } catch (_: Exception) {}
                isScanning = false
                appendOutput("[*] BLE scan completed\n")
                appendOutput("╚══════════════════════════════════╝\n")
            }
        } catch (e: Exception) {
            appendOutput("[E] BLE scan error: ${e.message}\n")
            appendOutput("╚══════════════════════════════════╝\n")
            isScanning = false
        }
    }

    private fun listPairedDevices() {
        val sb = StringBuilder("╔══════════════════════════════════╗\n")
        sb.append("║   Paired Devices                ║\n")
        sb.append("╠══════════════════════════════════╣\n\n")

        try {
            val btManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = btManager?.adapter
            if (adapter != null && adapter.isEnabled) {
                if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    val bonded = adapter.bondedDevices
                    if (bonded.isNullOrEmpty()) {
                        sb.append("  No paired devices\n")
                    } else {
                        for ((idx, device) in bonded.withIndex()) {
                            val type = when (device.type) {
                                android.bluetooth.BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic"
                                android.bluetooth.BluetoothDevice.DEVICE_TYPE_LE -> "BLE"
                                android.bluetooth.BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual"
                                else -> "Unknown"
                            }
                            sb.append("  ${idx + 1}. ${device.name ?: "Unknown"}\n")
                            sb.append("     Address: ${device.address}\n")
                            sb.append("     Type: $type\n")
                            sb.append("     Bond: ${device.bondState}\n\n")
                        }
                    }
                } else {
                    sb.append("  [!] Bluetooth permission required\n")
                }
            } else {
                sb.append("  Bluetooth not enabled\n")
            }
        } catch (e: Exception) {
            sb.append("  [E] ${e.message}\n")
        }

        sb.append("╚══════════════════════════════════╝\n")
        appendOutput(sb.toString())
    }

    private fun fingerprintDevice() {
        appendOutput("╔══════════════════════════════════╗\n")
        appendOutput("║   Bluetooth Fingerprinting      ║\n")
        appendOutput("╠══════════════════════════════════╣\n\n")
        appendOutput("[*] Device fingerprinting techniques:\n\n")
        appendOutput("  1. Service enumeration\n")
        appendOutput("     sdptool records <mac_address>\n\n")
        appendOutput("  2. Device class analysis\n")
        appendOutput("     Identifies device type (phone, audio, etc.)\n\n")
        appendOutput("  3. Manufacturer identification\n")
        appendOutput("     OUI lookup from MAC address\n\n")
        appendOutput("  4. Protocol analysis\n")
        appendOutput("     Supported profiles: A2DP, HFP, SPP, etc.\n\n")
        appendOutput("[*] Install bluez tools via Termux:\n")
        appendOutput("    pkg install bluez\n")
        appendOutput("    sdptool browse <mac>\n")
        appendOutput("    hcitool info <mac>\n\n")
        appendOutput("╚══════════════════════════════════╝\n")
    }

    private fun showBtLogs() {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                ShellExecutor.executeWithTimeout("dmesg | grep -i bluetooth | tail -20; logcat -d -s BluetoothAdapter BluetoothDevice | tail -20", 5000)
            }
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║   Bluetooth Logs                ║\n")
            appendOutput("╠══════════════════════════════════╣\n\n")
            if (result.output.isNotEmpty()) {
                appendOutput(result.output.take(2000) + "\n")
            } else {
                appendOutput("[*] No Bluetooth logs available\n")
            }
            appendOutput("\n╚══════════════════════════════════╝\n")
        }
    }

    private fun checkNfc() {
        val sb = StringBuilder("╔══════════════════════════════════╗\n")
        sb.append("║   NFC Status                    ║\n")
        sb.append("╠══════════════════════════════════╣\n\n")

        val hasNfc = requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)
        sb.append("  NFC Support: ${if (hasNfc) "YES" else "NO"}\n")

        if (hasNfc) {
            val nfcAdapter = android.nfc.NfcAdapter.getDefaultAdapter(requireContext())
            if (nfcAdapter != null) {
                sb.append("  NFC Enabled: ${nfcAdapter.isEnabled}\n")
                sb.append("  NFC Mode: ${if (nfcAdapter.isEnabled) "Active" else "Disabled"}\n")
            }
            sb.append("\n  [*] NFC Tools available:\n")
            sb.append("    - Read NFC tags\n")
            sb.append("    - Write NFC tags (root)\n")
            sb.append("    - Clone NFC tags (root)\n")
            sb.append("    - Emulate NFC cards (root + NXP chip)\n")
        }

        sb.append("\n╚══════════════════════════════════╝\n")
        appendOutput(sb.toString())
    }

    private fun openBtSettings() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
            startActivity(intent)
            appendOutput("[*] Opened Bluetooth settings\n")
        } catch (e: Exception) {
            appendOutput("[E] Could not open settings: ${e.message}\n")
        }
    }

    private fun appendOutput(text: String) {
        activity?.runOnUiThread {
            tvOutput.append(text)
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isScanning = false
        scope.cancel()
    }
}
