package com.hackerlauncher.modules

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.location.Location
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Bundle
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AntiTheftFragment : Fragment() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var scrollView: ScrollView
    private lateinit var tvOutput: TextView
    private lateinit var etTrustedPhone: EditText
    private lateinit var etPin: EditText
    private lateinit var switchSimDetect: Switch
    private lateinit var switchRemoteLock: Switch
    private lateinit var switchRemoteAlarm: Switch
    private lateinit var switchRemoteLocate: Switch
    private lateinit var switchRemoteWipe: Switch
    private var simChangeReceiver: BroadcastReceiver? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
            setPadding(12, 12, 12, 12)
        }

        // Title
        root.addView(TextView(context).apply {
            text = ">> ANTI-THEFT SYSTEM v2.1"
            setTextColor(0xFF00FF00.toInt())
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setPadding(0, 8, 0, 8)
        })

        // Trusted phone number
        root.addView(TextView(context).apply {
            text = "Trusted Phone Number:"
            setTextColor(0xFF00FF00.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
        })
        etTrustedPhone = EditText(context).apply {
            hint = "+1234567890"
            setTextColor(0xFF00FF00.toInt())
            setHintTextColor(0xFF005500.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            inputType = InputType.TYPE_CLASS_PHONE
            setPadding(8, 8, 8, 8)
        }
        root.addView(etTrustedPhone)

        // PIN input
        root.addView(TextView(context).apply {
            text = "Emergency PIN:"
            setTextColor(0xFF00FF00.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(0, 8, 0, 0)
        })
        etPin = EditText(context).apply {
            hint = "4-6 digit PIN"
            setTextColor(0xFF00FF00.toInt())
            setHintTextColor(0xFF005500.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setPadding(8, 8, 8, 8)
        }
        root.addView(etPin)

        // Feature switches
        val switchLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }

        switchSimDetect = makeSwitch("SIM Change Detection", true)
        switchRemoteLock = makeSwitch("Remote Lock (SMS)", false)
        switchRemoteAlarm = makeSwitch("Remote Alarm (SMS)", true)
        switchRemoteLocate = makeSwitch("Remote Locate (SMS)", true)
        switchRemoteWipe = makeSwitch("Remote Wipe (SMS)", false)

        switchLayout.addView(switchSimDetect)
        switchLayout.addView(switchRemoteLock)
        switchLayout.addView(switchRemoteAlarm)
        switchLayout.addView(switchRemoteLocate)
        switchLayout.addView(switchRemoteWipe)
        root.addView(switchLayout)

        // Remote commands info
        root.addView(TextView(context).apply {
            text = "[i] SMS Commands: LOCK:pin ALARM:locate WIPE"
            setTextColor(0xFF005500.toInt())
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(0, 4, 0, 4)
        })

        // Button grid
        val btnRow1 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        btnRow1.addView(makeButton("Save Config") { saveConfig() })
        btnRow1.addView(makeButton("SIM Check") { checkSimChange() })
        btnRow1.addView(makeButton("Test Alarm") { triggerAlarm() })
        root.addView(btnRow1)

        val btnRow2 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        btnRow2.addView(makeButton("Locate") { locateDevice() })
        btnRow2.addView(makeButton("Remote Lock") { remoteLock() })
        btnRow2.addView(makeButton("WIPE", Color.RED) { factoryResetShortcut() })
        root.addView(btnRow2)

        val btnRow3 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        btnRow3.addView(makeButton("Last Location") { showLastKnownLocation() })
        btnRow3.addView(makeButton("SMS Help") { showSmsHelp() })
        root.addView(btnRow3)

        // Output area
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

        loadConfig()
        appendOutput("╔══════════════════════════════════╗\n")
        appendOutput("║    ANTI-THEFT SYSTEM v2.1       ║\n")
        appendOutput("║  SIM detect, SMS remote ctrl    ║\n")
        appendOutput("║  LOCK:pin ALARM:locate WIPE     ║\n")
        appendOutput("╚══════════════════════════════════╝\n\n")

        return root
    }

    private fun makeSwitch(label: String, default: Boolean): Switch {
        return Switch(context).apply {
            text = label
            setTextColor(0xFF00FF00.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            isChecked = default
            setOnCheckedChangeListener { _, isChecked ->
                appendOutput("[$label] ${if (isChecked) "ENABLED" else "DISABLED"}\n")
            }
        }
    }

    private fun makeButton(label: String, color: Int = 0xFF00FF00.toInt(), onClick: () -> Unit): Button {
        return Button(context).apply {
            text = label
            setTextColor(color)
            setBackgroundColor(0xFF1A1A1A.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(8, 4, 8, 4)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(2, 2, 2, 2)
            }
        }
    }

    private fun saveConfig() {
        try {
            val prefs = requireContext().getSharedPreferences("antitheft", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("trusted_phone", etTrustedPhone.text.toString().trim())
                .putBoolean("sim_detect", switchSimDetect.isChecked)
                .putBoolean("remote_lock", switchRemoteLock.isChecked)
                .putBoolean("remote_alarm", switchRemoteAlarm.isChecked)
                .putBoolean("remote_locate", switchRemoteLocate.isChecked)
                .putBoolean("remote_wipe", switchRemoteWipe.isChecked)
                .apply()
            appendOutput("[+] Configuration saved\n")
            registerSimReceiver()
        } catch (e: Exception) {
            appendOutput("[E] Save failed: ${e.message}\n")
        }
    }

    private fun loadConfig() {
        try {
            val prefs = requireContext().getSharedPreferences("antitheft", Context.MODE_PRIVATE)
            etTrustedPhone.setText(prefs.getString("trusted_phone", ""))
            switchSimDetect.isChecked = prefs.getBoolean("sim_detect", true)
            switchRemoteLock.isChecked = prefs.getBoolean("remote_lock", false)
            switchRemoteAlarm.isChecked = prefs.getBoolean("remote_alarm", true)
            switchRemoteLocate.isChecked = prefs.getBoolean("remote_locate", true)
            switchRemoteWipe.isChecked = prefs.getBoolean("remote_wipe", false)
        } catch (e: Exception) {
            appendOutput("[E] Load config: ${e.message}\n")
        }
    }

    private fun registerSimReceiver() {
        try {
            if (simChangeReceiver != null) {
                try { requireContext().unregisterReceiver(simChangeReceiver) } catch (_: Exception) {}
            }
            simChangeReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    if (intent.action == "android.intent.action.SIM_STATE_CHANGED") {
                        appendOutput("[!!!] SIM STATE CHANGED BROADCAST RECEIVED!\n")
                        checkSimChange()
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction("android.intent.action.SIM_STATE_CHANGED")
            }
            requireContext().registerReceiver(simChangeReceiver, filter)
            appendOutput("[+] SIM change receiver registered\n")
        } catch (e: Exception) {
            appendOutput("[E] SIM receiver: ${e.message}\n")
        }
    }

    private fun checkSimChange() {
        try {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║       SIM Card Check            ║\n")
            appendOutput("╠══════════════════════════════════╣\n")

            val tm = requireContext().getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            try {
                val simState = when (tm.simState) {
                    TelephonyManager.SIM_STATE_ABSENT -> "ABSENT"
                    TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN REQUIRED"
                    TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK REQUIRED"
                    TelephonyManager.SIM_STATE_READY -> "READY"
                    TelephonyManager.SIM_STATE_NOT_READY -> "NOT READY"
                    TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "NETWORK LOCKED"
                    else -> "UNKNOWN(${tm.simState})"
                }
                appendOutput("║ SIM State: $simState\n")
            } catch (e: SecurityException) {
                appendOutput("║ SIM State: [Permission denied]\n")
            }

            try {
                val operator = tm.simOperatorName ?: "Unknown"
                appendOutput("║ Operator: $operator\n")
            } catch (e: SecurityException) {
                appendOutput("║ Operator: [Permission denied]\n")
            }

            try {
                val country = tm.simCountryIso ?: "N/A"
                appendOutput("║ Country:  $country\n")
            } catch (e: SecurityException) {
                appendOutput("║ Country:  [Permission denied]\n")
            }

            // SIM change detection via ICCID comparison
            val prefs = requireContext().getSharedPreferences("antitheft", Context.MODE_PRIVATE)
            val lastIccId = prefs.getString("last_iccid", null)

            try {
                val currentIccId = tm.simSerialNumber
                if (lastIccId == null) {
                    if (currentIccId != null) {
                        prefs.edit().putString("last_iccid", currentIccId).apply()
                        appendOutput("║ [i] SIM ICCID registered\n")
                    }
                } else {
                    if (currentIccId != null && currentIccId != lastIccId) {
                        appendOutput("║ [!!!] SIM CARD CHANGED DETECTED!\n")
                        appendOutput("║ Previous: $lastIccId\n")
                        appendOutput("║ Current:  $currentIccId\n")

                        val trustedPhone = prefs.getString("trusted_phone", "")
                        if (!trustedPhone.isNullOrEmpty() && switchSimDetect.isChecked) {
                            try {
                                val sms = SmsManager.getDefault()
                                sms.sendTextMessage(trustedPhone, null,
                                    "[ANTI-THEFT] SIM card changed! New ICCID: $currentIccId", null, null)
                                appendOutput("║ [+] SMS alert sent to $trustedPhone\n")
                            } catch (e: Exception) {
                                appendOutput("║ [E] SMS alert: ${e.message}\n")
                            }
                        }
                    } else {
                        appendOutput("║ [+] SIM unchanged\n")
                    }
                }
            } catch (e: SecurityException) {
                appendOutput("║ [!] Phone permission needed for ICCID\n")
            }

            appendOutput("╚══════════════════════════════════╝\n\n")
        } catch (e: Exception) {
            appendOutput("[E] SIM check failed: ${e.message}\n")
        }
    }

    private fun triggerAlarm() {
        try {
            appendOutput("[*] Triggering alarm...\n")

            val audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val ringtone = RingtoneManager.getRingtone(requireContext(), uri)
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()
            ringtone.play()

            appendOutput("[+] Alarm triggered at maximum volume\n")
            appendOutput("[*] Alarm will play for 30 seconds...\n")

            scope.launch {
                delay(30000)
                try {
                    ringtone.stop()
                    appendOutput("[*] Alarm stopped\n")
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            appendOutput("[E] Alarm failed: ${e.message}\n")
        }
    }

    private fun locateDevice() {
        scope.launch {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║      Device Location            ║\n")
            appendOutput("╠══════════════════════════════════╣\n")

            try {
                val lm = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val providers = lm.allProviders
                appendOutput("║ Providers: ${providers.joinToString(", ")}\n")

                for (provider in providers) {
                    try {
                        val lastKnown = lm.getLastKnownLocation(provider)
                        if (lastKnown != null) {
                            appendOutput("║ [$provider]\n")
                            appendOutput("║   Lat: ${lastKnown.latitude}\n")
                            appendOutput("║   Lon: ${lastKnown.longitude}\n")
                            appendOutput("║   Acc: ${lastKnown.accuracy}m\n")
                            appendOutput("║   Time: ${java.util.Date(lastKnown.time)}\n")

                            val mapsUrl = "https://maps.google.com/?q=${lastKnown.latitude},${lastKnown.longitude}"
                            appendOutput("║   Map: $mapsUrl\n")

                            val prefs = requireContext().getSharedPreferences("antitheft", Context.MODE_PRIVATE)
                            prefs.edit()
                                .putString("last_lat", lastKnown.latitude.toString())
                                .putString("last_lon", lastKnown.longitude.toString())
                                .putLong("last_loc_time", lastKnown.time)
                                .apply()

                            if (switchRemoteLocate.isChecked) {
                                val trustedPhone = prefs.getString("trusted_phone", "")
                                if (!trustedPhone.isNullOrEmpty()) {
                                    try {
                                        val sms = SmsManager.getDefault()
                                        sms.sendTextMessage(trustedPhone, null,
                                            "[ANTI-THEFT] Location: $mapsUrl", null, null)
                                        appendOutput("║ [+] Location SMS sent to $trustedPhone\n")
                                    } catch (e: Exception) {
                                        appendOutput("║ [E] SMS failed: ${e.message}\n")
                                    }
                                }
                            }
                        }
                    } catch (e: SecurityException) {
                        appendOutput("║ [$provider] Permission denied\n")
                    } catch (e: Exception) {
                        appendOutput("║ [$provider] ${e.message}\n")
                    }
                }
            } catch (e: Exception) {
                appendOutput("║ [E] ${e.message}\n")
            }
            appendOutput("╚══════════════════════════════════╝\n\n")
        }
    }

    private fun showLastKnownLocation() {
        try {
            val prefs = requireContext().getSharedPreferences("antitheft", Context.MODE_PRIVATE)
            val lat = prefs.getString("last_lat", null)
            val lon = prefs.getString("last_lon", null)
            val time = prefs.getLong("last_loc_time", 0L)

            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║     Last Known Location         ║\n")
            appendOutput("╠══════════════════════════════════╣\n")

            if (lat != null && lon != null) {
                appendOutput("║ Lat: $lat\n")
                appendOutput("║ Lon: $lon\n")
                appendOutput("║ Time: ${if (time > 0) java.util.Date(time) else "Unknown"}\n")
                appendOutput("║ Map: https://maps.google.com/?q=$lat,$lon\n")
            } else {
                appendOutput("║ No last known location stored\n")
                appendOutput("║ Press 'Locate' to get current position\n")
            }
            appendOutput("╚══════════════════════════════════╝\n\n")
        } catch (e: Exception) {
            appendOutput("[E] ${e.message}\n")
        }
    }

    private fun remoteLock() {
        try {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║      Remote Lock                ║\n")
            appendOutput("╠══════════════════════════════════╣\n")

            val pin = etPin.text.toString().trim()
            if (pin.isNotEmpty()) {
                appendOutput("║ [*] Setting lock PIN: ****\n")
                val dpm = requireContext().getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val componentName = ComponentName(requireContext(), javaClass)
                if (dpm.isAdminActive(componentName)) {
                    try {
                        dpm.resetPassword(pin, 0)
                        dpm.lockNow()
                        appendOutput("║ [+] Device locked via Device Admin\n")
                    } catch (e: Exception) {
                        appendOutput("║ [E] Lock failed: ${e.message}\n")
                    }
                } else {
                    appendOutput("║ [!] Device Admin not active\n")
                    appendOutput("║ [*] Activate via DeviceAdminManager module\n")
                }
                appendOutput("║\n")
                appendOutput("║ SMS: LOCK <pin> to trusted phone\n")
            } else {
                appendOutput("║ [!] Enter PIN first\n")
            }
            appendOutput("╚══════════════════════════════════╝\n\n")
        } catch (e: Exception) {
            appendOutput("[E] Lock failed: ${e.message}\n")
        }
    }

    private fun factoryResetShortcut() {
        try {
            appendOutput("╔══════════════════════════════════╗\n")
            appendOutput("║    !! FACTORY RESET WARNING !!   ║\n")
            appendOutput("╠══════════════════════════════════╣\n")
            appendOutput("║ This will ERASE ALL DATA!\n")
            appendOutput("║ This action is IRREVERSIBLE!\n")
            appendOutput("║\n")
            appendOutput("║ SMS Command: WIPE (needs admin)\n")
            appendOutput("╚══════════════════════════════════╝\n\n")

            try {
                val intent = Intent(android.provider.Settings.ACTION_PRIVACY_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                appendOutput("[*] Opened privacy/reset settings\n")
            } catch (e: Exception) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
                    startActivity(intent)
                } catch (e2: Exception) {
                    appendOutput("[E] Could not open settings: ${e2.message}\n")
                }
            }
        } catch (e: Exception) {
            appendOutput("[E] ${e.message}\n")
        }
    }

    private fun showSmsHelp() {
        appendOutput("╔══════════════════════════════════╗\n")
        appendOutput("║      SMS Remote Commands        ║\n")
        appendOutput("╠══════════════════════════════════╣\n")
        appendOutput("║ Send from trusted phone:\n")
        appendOutput("║\n")
        appendOutput("║ LOCK <pin>  - Lock with PIN\n")
        appendOutput("║ ALARM       - Sound alarm max\n")
        appendOutput("║ LOCATE      - Reply with GPS\n")
        appendOutput("║ WIPE        - Factory reset\n")
        appendOutput("║ RING        - Ring for 60s\n")
        appendOutput("║\n")
        appendOutput("║ Setup:\n")
        appendOutput("║ 1. Set trusted phone above\n")
        appendOutput("║ 2. Enable features via switches\n")
        appendOutput("║ 3. Send SMS: <command>\n")
        appendOutput("║\n")
        appendOutput("║ [!] Requires SMS permissions\n")
        appendOutput("║ [!] Device Admin for lock/wipe\n")
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
        try {
            simChangeReceiver?.let { requireContext().unregisterReceiver(it) }
        } catch (_: Exception) {}
        scope.cancel()
    }
}
