package com.hackerlauncher.modules

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.hackerlauncher.R
import com.hackerlauncher.services.HackerForegroundService
import com.hackerlauncher.utils.Logger
import com.hackerlauncher.utils.PreferencesManager
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class AutomationFragment : Fragment() {

    private lateinit var tvAutoOutput: TextView
    private lateinit var scrollView: ScrollView
    private val logger = Logger
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val macroActions = mutableListOf<String>()
    private val scheduledTasks = mutableListOf<ScheduledTask>()
    private var isRecording = false

    private data class ScheduledTask(
        val name: String,
        val time: String,
        val action: String,
        val isRecurring: Boolean
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_automation, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvAutoOutput = view.findViewById(R.id.tvAutoOutput)
        scrollView = view.findViewById(R.id.scrollViewAuto)
        val btnRecord = view.findViewById<Button>(R.id.btnRecord)
        val btnStopRecord = view.findViewById<Button>(R.id.btnStopRecord)
        val btnPlayMacro = view.findViewById<Button>(R.id.btnPlayMacro)
        val btnClearMacro = view.findViewById<Button>(R.id.btnClearMacro)
        val btnSchedule = view.findViewById<Button>(R.id.btnSchedule)
        val btnAutoRecon = view.findViewById<Button>(R.id.btnAutoRecon)
        val btnNotifLog = view.findViewById<Button>(R.id.btnNotifLog)
        val btnListTasks = view.findViewById<Button>(R.id.btnListTasks)
        val etTaskName = view.findViewById<EditText>(R.id.etTaskName)
        val etTaskDelay = view.findViewById<EditText>(R.id.etTaskDelay)

        btnRecord.setOnClickListener {
            isRecording = true
            macroActions.clear()
            appendOutput("[*] Macro recording started...\n")
            appendOutput("[*] Actions will be logged as you trigger them\n")
        }

        btnStopRecord.setOnClickListener {
            isRecording = false
            appendOutput("[*] Macro recording stopped. ${macroActions.size} actions captured.\n")
            for ((idx, action) in macroActions.withIndex()) {
                appendOutput("  ${idx + 1}. $action\n")
            }
        }

        btnPlayMacro.setOnClickListener {
            if (macroActions.isEmpty()) {
                appendOutput("[!] No macro recorded. Record first.\n")
            } else {
                playMacro()
            }
        }

        btnClearMacro.setOnClickListener {
            macroActions.clear()
            appendOutput("[*] Macro cleared\n")
        }

        btnSchedule.setOnClickListener {
            val name = etTaskName.text.toString().trim()
            val delayStr = etTaskDelay.text.toString().trim()
            if (name.isEmpty() || delayStr.isEmpty()) {
                appendOutput("[!] Enter task name and delay (minutes)\n")
            } else {
                val delay = delayStr.toLongOrNull() ?: 0
                if (delay < 1) {
                    appendOutput("[!] Delay must be at least 1 minute\n")
                } else {
                    scheduleTask(name, delay)
                }
            }
        }

        btnAutoRecon.setOnClickListener { runAutoRecon() }
        btnNotifLog.setOnClickListener { showNotificationLog() }
        btnListTasks.setOnClickListener { listScheduledTasks() }
    }

    private fun playMacro() {
        scope.launch {
            appendOutput("[*] Playing macro (${macroActions.size} actions)...\n")
            for ((idx, action) in macroActions.withIndex()) {
                appendOutput("  [${idx + 1}] Executing: $action\n")
                delay(500) // 500ms between actions
                withContext(Dispatchers.IO) {
                    executeAction(action)
                }
            }
            appendOutput("[+] Macro playback complete\n")
        }
    }

    private fun executeAction(action: String) {
        when {
            action.startsWith("CMD:") -> {
                val cmd = action.removePrefix("CMD:")
                com.hackerlauncher.utils.ShellExecutor.execute(cmd)
            }
            action.startsWith("SERVICE:START") -> {
                val intent = Intent(requireContext(), HackerForegroundService::class.java)
                intent.action = HackerForegroundService.ACTION_START
                requireContext().startService(intent)
            }
            action.startsWith("SERVICE:STOP") -> {
                val intent = Intent(requireContext(), HackerForegroundService::class.java)
                intent.action = HackerForegroundService.ACTION_STOP
                requireContext().startService(intent)
            }
            action.startsWith("LOG:") -> {
                Logger.log(action.removePrefix("LOG:"))
            }
        }
    }

    private fun scheduleTask(name: String, delayMinutes: Long) {
        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(requireContext(), HackerForegroundService::class.java).apply {
            action = "com.hackerlauncher.SCHEDULED_$name"
        }
        val pendingIntent = PendingIntent.getService(
            requireContext(), name.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + delayMinutes * 60 * 1000
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent
        )

        val timeStr = SimpleDateFormat.getDateTimeInstance().format(Date(triggerTime))
        scheduledTasks.add(ScheduledTask(name, timeStr, "scheduled_action", delayMinutes > 0))

        appendOutput("[+] Task '$name' scheduled for $timeStr\n")
    }

    private fun runAutoRecon() {
        scope.launch {
            appendOutput("═══ Auto Recon Started ═══\n")
            appendOutput("[*] Running automated reconnaissance...\n\n")

            // System info
            appendOutput("[1/5] Gathering system info...\n")
            val sysInfo = withContext(Dispatchers.IO) {
                com.hackerlauncher.utils.ShellExecutor.execute("uname -a && cat /proc/cpuinfo | head -5")
            }
            appendOutput(sysInfo.output.take(500) + "\n\n")

            // Network info
            appendOutput("[2/5] Gathering network info...\n")
            val netInfo = withContext(Dispatchers.IO) {
                com.hackerlauncher.utils.ShellExecutor.execute("ifconfig 2>/dev/null || ip addr show")
            }
            appendOutput(netInfo.output.take(500) + "\n\n")

            // Open ports
            appendOutput("[3/5] Checking open ports...\n")
            val ports = withContext(Dispatchers.IO) {
                com.hackerlauncher.utils.ShellExecutor.execute("netstat -tlnp 2>/dev/null || ss -tlnp")
            }
            appendOutput(ports.output.take(500) + "\n\n")

            // Running processes
            appendOutput("[4/5] Listing running processes...\n")
            val procs = withContext(Dispatchers.IO) {
                com.hackerlauncher.utils.ShellExecutor.execute("ps -A 2>/dev/null || ps")
            }
            appendOutput(procs.output.take(800) + "\n\n")

            // Disk usage
            appendOutput("[5/5] Checking disk usage...\n")
            val disk = withContext(Dispatchers.IO) {
                com.hackerlauncher.utils.ShellExecutor.execute("df -h 2>/dev/null || df")
            }
            appendOutput(disk.output.take(500) + "\n\n")

            appendOutput("═══ Auto Recon Complete ═══\n")
        }
    }

    private fun showNotificationLog() {
        val sb = StringBuilder("═══ Notification Log ═══\n")
        val prefs = PreferencesManager(requireContext())
        val logs = prefs.getNotificationLog()
        if (logs.isEmpty()) {
            sb.append("  No notifications logged yet.\n")
            sb.append("  Enable Accessibility Service for notification logging.\n")
        } else {
            for (log in logs.takeLast(20)) {
                sb.append("  $log\n")
            }
        }
        sb.append("════════════════════════\n")
        appendOutput(sb.toString())
    }

    private fun listScheduledTasks() {
        val sb = StringBuilder("═══ Scheduled Tasks ═══\n")
        if (scheduledTasks.isEmpty()) {
            sb.append("  No tasks scheduled.\n")
        } else {
            for ((idx, task) in scheduledTasks.withIndex()) {
                sb.append("  ${idx + 1}. ${task.name}\n")
                sb.append("     Time: ${task.time}\n")
                sb.append("     Recurring: ${task.isRecurring}\n\n")
            }
        }
        sb.append("════════════════════════\n")
        appendOutput(sb.toString())
    }

    fun logAction(action: String) {
        if (isRecording) {
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            macroActions.add("[$timestamp] $action")
        }
    }

    private fun appendOutput(text: String) {
        activity?.runOnUiThread {
            tvAutoOutput.append(text)
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scope.cancel()
    }
}
