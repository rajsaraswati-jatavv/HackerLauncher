package com.hackerlauncher.modules

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.hackerlauncher.R
import com.hackerlauncher.utils.Logger
import com.hackerlauncher.utils.ShellExecutor
import kotlinx.coroutines.*

class RootToolsFragment : Fragment() {

    private lateinit var tvOutput: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var etCommand: EditText
    private val logger = Logger
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isRooted = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_root_tools, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvOutput = view.findViewById(R.id.tvRootOutput)
        etCommand = view.findViewById(R.id.etRootCommand)
        scrollView = view.findViewById(R.id.scrollViewRoot)

        val btnCheckRoot = view.findViewById<Button>(R.id.btnCheckRoot)
        val btnRootShell = view.findViewById<Button>(R.id.btnRootShell)
        val btnRootExecute = view.findViewById<Button>(R.id.btnRootExecute)
        val tvRootStatus = view.findViewById<TextView>(R.id.tvRootStatus)

        appendOutput("═══ Root Tools Module v3.0 ═══\n")
        appendOutput("Type root commands below.\n\n")

        // Auto-check root
        checkRootStatus()

        btnCheckRoot.setOnClickListener { checkRootStatus() }
        btnRootShell.setOnClickListener { openRootShell() }
        btnRootExecute.setOnClickListener {
            val cmd = etCommand.text.toString().trim()
            if (cmd.isNotEmpty()) {
                executeRootCommand(cmd)
                etCommand.text.clear()
            }
        }

        etCommand.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                btnRootExecute.performClick()
                true
            } else false
        }
    }

    private fun openRootShell() {
        appendOutput("[*] Opening root shell via Termux...\n")
        try {
            val intent = android.content.Intent()
            intent.setClassName("com.termux", "com.termux.app.TermuxActivity")
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        } catch (e: Exception) {
            appendOutput("[E] Termux not available: ${e.message}\n")
            appendOutput("[*] Install Termux for root shell access\n")
        }
    }

    private fun checkRootStatus() {
        scope.launch {
            appendOutput("[*] Checking root access...\n")
            val rooted = withContext(Dispatchers.IO) { ShellExecutor.isRootAvailable() }
            isRooted = rooted
            activity?.runOnUiThread {
                try {
                    val tvRootStatus = view?.findViewById<TextView>(R.id.tvRootStatus)
                    if (rooted) {
                        tvRootStatus?.text = "[+] ROOT ACCESS GRANTED"
                        tvRootStatus?.setTextColor(0xFF00FF00.toInt())
                    } else {
                        tvRootStatus?.text = "[-] ROOT NOT AVAILABLE"
                        tvRootStatus?.setTextColor(0xFFFF4444.toInt())
                    }
                } catch (_: Exception) {}
            }
            if (rooted) {
                appendOutput("[+] ROOT ACCESS GRANTED\n")
                appendOutput("[*] SU binary found and working\n\n")
                val suInfo = withContext(Dispatchers.IO) { ShellExecutor.executeRoot("su -v") }
                if (suInfo.output.isNotEmpty()) {
                    appendOutput("[*] SU Version: ${suInfo.output}\n")
                }
                checkMagisk()
            } else {
                appendOutput("[-] ROOT NOT AVAILABLE\n")
                appendOutput("[*] Some features require root access\n")
                appendOutput("[*] Install Magisk for root access\n\n")
            }
        }
    }

    private fun checkMagisk() {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                ShellExecutor.executeRoot("which magisk && magisk -v")
            }
            if (result.exitCode == 0) {
                appendOutput("[+] Magisk detected: ${result.output}\n")
            } else {
                appendOutput("[-] Magisk not found\n")
            }

            // Check for other root indicators
            val checks = withContext(Dispatchers.IO) {
                ShellExecutor.execute("ls /system/app/Superuser.apk 2>/dev/null; ls /system/xbin/su 2>/dev/null; ls /system/bin/su 2>/dev/null; which su 2>/dev/null")
            }
            if (checks.output.isNotEmpty()) {
                appendOutput("[*] Root binaries found:\n${checks.output}\n")
            }
        }
    }

    private fun executeRootCommand(command: String) {
        appendOutput("root@launcher:~# $command\n")

        when {
            command == "help" -> {
                appendOutput("""
                    Root Commands:
                    help          - Show this help
                    check         - Check root status
                    sysinfo       - Detailed system info
                    mounts        - List mounted filesystems
                    iptables      - Show firewall rules
                    hosts         - Show/edit hosts file
                    props         - System properties
                    selinux       - Check SELinux status
                    bootloader    - Bootloader info
                    battery       - Battery info
                    sensors       - List sensors
                    disks         - Disk partition info
                    kernel        - Kernel version
                    meminfo       - Memory details
                    tcpdump       - Packet capture (root)
                    reboot        - Reboot device
                    shutdown      - Shutdown device

                """.trimIndent() + "\n")
            }
            command == "check" -> checkRootStatus()
            command == "sysinfo" -> runRootCommand("uname -a && cat /proc/version && cat /proc/cpuinfo | head -20 && getprop ro.build.version.release && getprop ro.build.display.id")
            command == "mounts" -> runRootCommand("mount")
            command == "iptables" -> runRootCommand("iptables -L -n -v")
            command == "hosts" -> runRootCommand("cat /etc/hosts")
            command == "props" -> runRootCommand("getprop")
            command == "selinux" -> runRootCommand("getenforce && cat /proc/filesystems | grep selinux")
            command == "bootloader" -> runRootCommand("getprop ro.bootloader && getprop ro.boot.verifiedbootstate")
            command == "battery" -> runRootCommand("cat /sys/class/power_supply/battery/*")
            command == "sensors" -> runRootCommand("cat /proc/bus/input/devices")
            command == "disks" -> runRootCommand("cat /proc/partitions && df -h")
            command == "kernel" -> runRootCommand("uname -r && cat /proc/cmdline")
            command == "meminfo" -> runRootCommand("cat /proc/meminfo")
            command == "tcpdump" -> {
                appendOutput("[!] tcpdump requires manual Ctrl+C to stop\n")
                appendOutput("[*] Use: tcpdump -i any -c 100 -nn for 100 packets\n")
                runRootCommand("which tcpdump")
            }
            command == "reboot" -> runRootCommand("reboot")
            command == "shutdown" -> runRootCommand("reboot -p")
            command.startsWith("install ") -> {
                val path = command.removePrefix("install ")
                runRootCommand("pm install $path")
            }
            command.startsWith("uninstall ") -> {
                val pkg = command.removePrefix("uninstall ")
                runRootCommand("pm uninstall $pkg")
            }
            else -> runRootCommand(command)
        }
    }

    private fun runRootCommand(cmd: String) {
        scope.launch {
            val result = if (isRooted) {
                withContext(Dispatchers.IO) { ShellExecutor.executeRoot(cmd) }
            } else {
                withContext(Dispatchers.IO) { ShellExecutor.execute(cmd) }
            }
            if (result.output.isNotEmpty()) {
                appendOutput(result.output.take(3000) + "\n")
            }
            if (result.error.isNotEmpty()) {
                appendOutput("[E] ${result.error.take(500)}\n")
            }
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
        scope.cancel()
    }
}
