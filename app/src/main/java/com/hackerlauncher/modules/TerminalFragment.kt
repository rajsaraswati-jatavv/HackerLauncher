package com.hackerlauncher.modules

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.hackerlauncher.R
import com.hackerlauncher.utils.ShellExecutor
import com.hackerlauncher.utils.TermuxBridge
import com.hackerlauncher.utils.Logger
import kotlinx.coroutines.*

class TerminalFragment : Fragment() {

    private lateinit var tvOutput: TextView
    private lateinit var etCommand: EditText
    private lateinit var btnSend: Button
    private lateinit var btnClear: Button
    private lateinit var btnTermux: Button
    private lateinit var scrollView: ScrollView
    private val logger = Logger
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_terminal, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvOutput = view.findViewById(R.id.tvTerminalOutput)
        etCommand = view.findViewById(R.id.etCommand)
        btnSend = view.findViewById(R.id.btnSend)
        btnClear = view.findViewById(R.id.btnClear)
        btnTermux = view.findViewById(R.id.btnOpenTermux)
        scrollView = view.findViewById(R.id.scrollViewTerminal)

        appendOutput("HackerLauncher Terminal v1.0\n")
        appendOutput("Type 'help' for available commands.\n\n")

        if (!TermuxBridge.isTermuxInstalled(requireContext())) {
            appendOutput("[!] Termux not installed. Some commands may not work.\n")
            appendOutput("[*] Tap 'Open Termux' to install it.\n\n")
        } else {
            appendOutput("[+] Termux detected. Full terminal available.\n\n")
        }

        btnSend.setOnClickListener {
            val cmd = etCommand.text.toString().trim()
            if (cmd.isNotEmpty()) {
                executeCommand(cmd)
                etCommand.text.clear()
            }
        }

        btnClear.setOnClickListener {
            tvOutput.text = ""
            appendOutput("Terminal cleared.\n")
        }

        btnTermux.setOnClickListener {
            if (TermuxBridge.isTermuxInstalled(requireContext())) {
                TermuxBridge.openTermux(requireContext())
            } else {
                TermuxBridge.promptInstallTermux(requireContext())
            }
        }

        etCommand.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                btnSend.performClick()
                true
            } else false
        }
    }

    private fun executeCommand(command: String) {
        appendOutput("hacker@launcher:~$ $command\n")

        when {
            command == "help" -> {
                appendOutput("""
                    Available commands:
                    help          - Show this help
                    clear         - Clear terminal
                    whoami        - Current user
                    uname -a      - System info
                    date          - Current date/time
                    uptime        - System uptime
                    df            - Disk usage
                    free          - Memory info
                    ps            - Process list
                    ifconfig      - Network interfaces
                    ping <host>   - Ping host
                    nslookup <h>  - DNS lookup
                    netstat       - Network connections
                    ls <dir>      - List directory
                    cat <file>    - Read file
                    pwd           - Print working dir
                    id            - User ID info
                    env           - Environment vars
                    exit          - Close terminal
                    
                """.trimIndent() + "\n")
            }
            command == "clear" -> {
                tvOutput.text = ""
            }
            else -> {
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        ShellExecutor.execute(command)
                    }
                    if (result.output.isNotEmpty()) {
                        appendOutput(result.output + "\n")
                    }
                    if (result.error.isNotEmpty()) {
                        appendOutput("[E] ${result.error}\n")
                    }
                    if (result.exitCode != 0 && result.error.isEmpty() && result.output.isEmpty()) {
                        appendOutput("[E] Command exited with code ${result.exitCode}\n")
                    }
                }
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
