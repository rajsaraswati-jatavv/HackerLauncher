package com.hackerlauncher

import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.work.*
import com.hackerlauncher.auth.FirebaseAuthManager
import com.hackerlauncher.launcher.*
import com.hackerlauncher.modules.*
import com.hackerlauncher.services.*
import com.hackerlauncher.utils.Logger
import com.hackerlauncher.utils.PermissionHelper
import com.hackerlauncher.utils.PreferencesManager
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var tabLayout: TabLayout
    private lateinit var fragmentContainer: FrameLayout
    private lateinit var tvGreeting: TextView
    private lateinit var tvTerminalWidget: TextView
    private lateinit var tvSystemStatus: TextView
    private lateinit var prefs: PreferencesManager
    private val logger = Logger
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 78 fragments total - original 47 + 31 new
    private val fragmentTags = arrayOf(
        // Original 47
        "home", "drawer", "search", "quicksettings", "weather",
        "calculator", "notes", "todo", "deviceinfo",
        "clipboard", "screenrec", "audiorec", "flashlight",
        "battery", "ramcleaner", "processmgr", "speedtest",
        "qrscanner", "compass", "downloads", "contacts",
        "sms", "calllog", "calendar", "syscleaner",
        "dupcleaner", "storageanalyzer", "appcache", "bigfiles",
        "terminal", "network", "osint", "crypto", "web",
        "anonymity", "files", "automation", "chat",
        "root", "wifarsenal", "subnetscan", "password", "bluetooth",
        "cpumonitor", "netspeed", "appmgr", "datausage",
        // 31 NEW features
        "vpn", "portscan", "dns", "ipgeo", "whois",
        "ssl", "httphead", "mac", "packet", "connlog",
        "wifipass", "sip", "netboost",
        "antitheft", "intruder", "perman", "deeplink", "honeypot",
        "vault", "panic", "fakeloc", "pinapp",
        "apkext", "sensor", "dimmer", "notifhist", "callrec",
        "appclone", "devadmin", "revimg", "metadata"
    )

    private val tabLabels = listOf(
        // Original 47
        "Home", "Apps", "Search", "Quick", "Wthr",
        "Calc", "Notes", "Todo", "DevI",
        "Clip", "Rec", "Audio", "Flash",
        "Batt", "RAM", "Proc", "Speed",
        "QR", "Comp", "DL", "Cont",
        "SMS", "Call", "Cal", "Clean",
        "Dup", "Store", "Cache", "BigF",
        "Term", "Net", "OSINT", "Crypt", "Web",
        "Anon", "Files", "Auto", "Chat",
        "Root", "WiFi", "Sub", "Pass", "BT",
        "CPU", "NetS", "AppM", "Data",
        // 31 NEW tabs
        "VPN", "Port", "DNS", "IP", "Whois",
        "SSL", "HTTP", "MAC", "Pkt", "Conn",
        "WPass", "SIP", "Boost",
        "AntiT", "Intr", "Perm", "Deep", "Honey",
        "Vault", "Panic", "Fake", "Pin",
        "APK", "Sensor", "Dim", "Notif", "CallR",
        "Clone", "DevAd", "RevImg", "Meta"
    )

    private lateinit var gestureDetector: GestureDetector
    private var currentFragmentIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PreferencesManager(this)

        try {
            if (prefs.isBiometricLockEnabled()) showBiometricLock()
            if (!prefs.isDisclaimerAccepted()) showDisclaimer()
        } catch (e: Exception) {
            Logger.error("Init check failed: ${e.message}")
        }

        tabLayout = findViewById(R.id.tabLayout)
        fragmentContainer = findViewById(R.id.fragmentContainer)
        tvGreeting = findViewById(R.id.tvGreeting)
        tvTerminalWidget = findViewById(R.id.tvTerminalWidget)
        tvSystemStatus = findViewById(R.id.tvSystemStatus)

        updateGreeting()
        setupTabs()
        setupGestures()

        // Request permissions safely
        try {
            val ph = PermissionHelper(this)
            ph.requestAllPermissions()
            ph.requestOverlayPermission()
            ph.requestManageExternalStorage()
            ph.requestIgnoreBatteryOptimization()
        } catch (e: Exception) {
            Logger.error("Permission request failed: ${e.message}")
        }

        // Start auto-messaging cron job (every 1 hour)
        scheduleHourlyAutoMessage()

        // Start auto-upgrade check loop
        scheduleAutoUpgradeCheck()

        // Services are started by HackerApp - don't start them again here
        startOptionalServices()

        startLogUpdater()
        startStatusUpdater()

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { moveTaskToBack(true) }
        })
    }

    private fun showBiometricLock() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    Toast.makeText(this@MainActivity, "Access granted", Toast.LENGTH_SHORT).show()
                }
                override fun onAuthenticationFailed() {
                    Toast.makeText(this@MainActivity, "Authentication failed", Toast.LENGTH_SHORT).show()
                    finishAffinity()
                }
            })
        biometricPrompt.authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle("HackerLauncher Lock")
            .setSubtitle("Authenticate to access")
            .setNegativeButtonText("Use PIN")
            .build())
    }

    private fun showDisclaimer() {
        AlertDialog.Builder(this)
            .setTitle("DISCLAIMER")
            .setMessage("HackerLauncher v12.0 ULTIMATE is designed for EDUCATIONAL and AUTHORIZED TESTING purposes only.\n\n" +
                "By using this application, you agree that:\n\n" +
                "1. You will ONLY use these tools on systems you own or have explicit permission to test.\n" +
                "2. You are solely responsible for any actions performed using this application.\n" +
                "3. The developers are NOT liable for any misuse or damage caused.\n" +
                "4. Unauthorized access to computer systems is ILLEGAL in most jurisdictions.\n\n" +
                "Use responsibly and ethically.")
            .setPositiveButton("I Understand & Agree") { _, _ -> prefs.setDisclaimerAccepted(true) }
            .setNegativeButton("Exit") { _, _ -> finishAffinity() }
            .setCancelable(false)
            .show()
    }

    private fun updateGreeting() {
        try {
            val userName = FirebaseAuthManager.getUserName()
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val greeting = when (hour) { in 0..5 -> "Good night"; in 6..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening" }
            tvGreeting.text = "$greeting, $userName"
        } catch (e: Exception) {
            tvGreeting.text = "Welcome, Hacker"
        }
    }

    private fun setupTabs() {
        for (label in tabLabels) tabLayout.addTab(tabLayout.newTab().setText(label))
        if (supportFragmentManager.findFragmentById(R.id.fragmentContainer) == null) showFragment(0)
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { currentFragmentIndex = tab.position; showFragment(tab.position) }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun createFragment(index: Int): Fragment = when (index) {
        // Original 47 fragments
        0 -> HomeLauncherFragment()
        1 -> AppDrawerFragment()
        2 -> SearchFragment()
        3 -> QuickSettingsFragment()
        4 -> WeatherFragment()
        5 -> CalculatorFragment()
        6 -> NotesFragment()
        7 -> TodoFragment()
        8 -> DeviceInfoFragment()
        9 -> ClipboardManagerFragment()
        10 -> ScreenRecorderFragment()
        11 -> AudioRecorderFragment()
        12 -> FlashlightFragment()
        13 -> BatteryOptimizerFragment()
        14 -> RamCleanerFragment()
        15 -> ProcessManagerFragment()
        16 -> SpeedTestFragment()
        17 -> QrScannerFragment()
        18 -> CompassFragment()
        19 -> DownloadManagerFragment()
        20 -> ContactsManagerFragment()
        21 -> SmsManagerFragment()
        22 -> CallLogFragment()
        23 -> CalendarFragment()
        24 -> SystemCleanerFragment()
        25 -> DuplicateFileCleanerFragment()
        26 -> StorageAnalyzerFragment()
        27 -> AppCacheCleanerFragment()
        28 -> BigFileFinderFragment()
        29 -> TerminalFragment()
        30 -> NetworkModuleFragment()
        31 -> OsintFragment()
        32 -> CryptoFragment()
        33 -> WebTestFragment()
        34 -> AnonymityFragment()
        35 -> FileFragment()
        36 -> AutomationFragment()
        37 -> ChatFragment()
        38 -> RootToolsFragment()
        39 -> WifiArsenalFragment()
        40 -> SubnetScannerFragment()
        41 -> PasswordToolsFragment()
        42 -> BluetoothScannerFragment()
        43 -> CpuMonitorFragment()
        44 -> NetworkSpeedMonitorFragment()
        45 -> AppManagerFragment()
        46 -> DataUsageTrackerFragment()
        // 31 NEW features (index 47-77)
        47 -> VpnMonitorFragment()
        48 -> PortScannerFragment()
        49 -> DnsChangerFragment()
        50 -> IpGeolocationFragment()
        51 -> WhoisLookupFragment()
        52 -> SslCertificateCheckerFragment()
        53 -> HttpHeaderInspectorFragment()
        54 -> MacAddressChangerFragment()
        55 -> PacketCaptureFragment()
        56 -> ConnectionLoggerFragment()
        57 -> WifiPasswordViewerFragment()
        58 -> SipScannerFragment()
        59 -> NetworkBoosterFragment()
        60 -> AntiTheftFragment()
        61 -> IntruderSelfieFragment()
        62 -> PermissionAnalyzerFragment()
        63 -> DeepLinkScannerFragment()
        64 -> HoneypotDetectorFragment()
        65 -> SecureVaultFragment()
        66 -> PanicButtonFragment()
        67 -> FakeLocationFragment()
        68 -> ScreenPinningFragment()
        69 -> ApkExtractorFragment()
        70 -> SensorBoxFragment()
        71 -> ScreenDimmerFragment()
        72 -> NotificationHistoryFragment()
        73 -> CallRecorderFragment()
        74 -> AppClonerFragment()
        75 -> DeviceAdminManagerFragment()
        76 -> ReverseImageSearchFragment()
        77 -> MetadataExtractorFragment()
        else -> TerminalFragment()
    }

    private fun showFragment(index: Int) {
        try {
            val tag = if (index < fragmentTags.size) fragmentTags[index] else "terminal"
            val existingFragment = supportFragmentManager.findFragmentByTag(tag)
            val fragment = existingFragment ?: createFragment(index)
            supportFragmentManager.beginTransaction().replace(R.id.fragmentContainer, fragment, tag).commit()
        } catch (e: Exception) {
            Logger.error("Fragment show failed: ${e.message}")
        }
    }

    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val dx = e2.x - (e1?.x ?: 0f)
                val dy = e2.y - (e1?.y ?: 0f)
                if (Math.abs(dx) > Math.abs(dy)) {
                    if (dx > 100) { tabLayout.getTabAt((currentFragmentIndex - 1).coerceAtLeast(0))?.select(); return true }
                    else if (dx < -100) { tabLayout.getTabAt((currentFragmentIndex + 1).coerceAtMost(tabLabels.size - 1))?.select(); return true }
                } else {
                    if (dy > 100) { startActivity(Intent(this@MainActivity, AppDrawerActivity::class.java)); return true }
                    else if (dy < -100) { startActivity(Intent(this@MainActivity, QuickSettingsActivity::class.java)); return true }
                }
                return false
            }
            override fun onDoubleTap(e: MotionEvent): Boolean { tabLayout.getTabAt(0)?.select(); return true }
            override fun onLongPress(e: MotionEvent) { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)

    /**
     * HOURLY AUTO-MESSAGE CRON JOB
     * WorkManager PeriodicWorkRequest that runs every 1 hour
     */
    private fun scheduleHourlyAutoMessage() {
        try {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .setRequiresCharging(false)
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            val autoMessageWork = PeriodicWorkRequestBuilder<AutoMessageWorker>(
                1, java.util.concurrent.TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .addTag("auto_message_hourly")
                .build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "auto_message_hourly",
                ExistingPeriodicWorkPolicy.KEEP,
                autoMessageWork
            )

            Logger.info("Hourly auto-message cron job scheduled")
        } catch (e: Exception) {
            Logger.error("Failed to schedule auto-message: ${e.message}")
        }
    }

    /**
     * AUTO-UPGRADE CHECK LOOP
     * Checks for new version every 6 hours and notifies user
     */
    private fun scheduleAutoUpgradeCheck() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val upgradeWork = PeriodicWorkRequestBuilder<AutoUpgradeWorker>(
                6, java.util.concurrent.TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .addTag("auto_upgrade_check")
                .build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "auto_upgrade_check",
                ExistingPeriodicWorkPolicy.KEEP,
                upgradeWork
            )

            Logger.info("Auto-upgrade check loop scheduled (every 6 hours)")
        } catch (e: Exception) {
            Logger.error("Failed to schedule auto-upgrade: ${e.message}")
        }
    }

    private fun startOptionalServices() {
        try {
            if (prefs.isOverlayEnabled()) {
                startService(Intent(this, OverlayService::class.java))
            }
        } catch (e: Exception) {
            Logger.error("Failed to start overlay service: ${e.message}")
        }

        try {
            if (prefs.isAppLockEnabled()) {
                val intent = Intent(this, AppLockService::class.java).apply { action = AppLockService.ACTION_START }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            }
        } catch (e: Exception) {
            Logger.error("Failed to start app lock service: ${e.message}")
        }
    }

    private fun startLogUpdater() {
        scope.launch {
            while (isActive) {
                delay(2000)
                try {
                    val logs = Logger.getLogBuffer().takeLast(3).joinToString("\n")
                    if (logs.isNotEmpty()) tvTerminalWidget.text = logs
                } catch (e: Exception) { /* ignore */ }
            }
        }
    }

    private fun startStatusUpdater() {
        scope.launch {
            while (isActive) {
                delay(5000)
                try {
                    val cm = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                    val network = cm.activeNetwork
                    val caps = cm.getNetworkCapabilities(network)
                    val hasInternet = caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                    tvSystemStatus.setTextColor(if (hasInternet) 0xFF00FF00.toInt() else 0xFFFF0000.toInt())
                } catch (e: Exception) { tvSystemStatus.setTextColor(0xFFFF0000.toInt()) }
            }
        }
    }

    override fun onResume() { super.onResume(); updateGreeting() }
    override fun onDestroy() { super.onDestroy(); scope.cancel() }

    // Inner fragment wrappers for launcher features
    class HomeLauncherFragment : androidx.fragment.app.Fragment() {
        override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, savedInstanceState: android.os.Bundle?) =
            android.widget.TextView(inflater.context).apply {
                text = "HOME SCREEN\n\nClock/Date Widget\nPinned Apps Grid\nDock Bar (5 apps)\nFolders Support\n\nSwipe gestures active\nTap to open Home Screen"
                setTextColor(0xFF00FF00.toInt()); textSize = 14f; setTypeface(android.graphics.Typeface.MONOSPACE); setPadding(32, 32, 32, 32)
                setOnClickListener { startActivity(Intent(context, HomeScreenActivity::class.java)) }
            }
    }
    class AppDrawerFragment : androidx.fragment.app.Fragment() {
        override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, savedInstanceState: android.os.Bundle?) =
            android.widget.TextView(inflater.context).apply {
                text = "APP DRAWER\n\nAll Installed Apps\nSearch & Filter\nNotification Badges\nAlphabetical Sections\n\nTap to open"
                setTextColor(0xFF00FF00.toInt()); textSize = 14f; setTypeface(android.graphics.Typeface.MONOSPACE); setPadding(32, 32, 32, 32)
                setOnClickListener { startActivity(Intent(context, AppDrawerActivity::class.java)) }
            }
    }
    class SearchFragment : androidx.fragment.app.Fragment() {
        override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, savedInstanceState: android.os.Bundle?) =
            android.widget.TextView(inflater.context).apply {
                text = "UNIVERSAL SEARCH\n\nApps / Contacts / Files / Web\nVoice Search\nRecent Searches\n\nTap to open"
                setTextColor(0xFF00FF00.toInt()); textSize = 14f; setTypeface(android.graphics.Typeface.MONOSPACE); setPadding(32, 32, 32, 32)
                setOnClickListener { startActivity(Intent(context, AppSearchActivity::class.java)) }
            }
    }
    class QuickSettingsFragment : androidx.fragment.app.Fragment() {
        override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, savedInstanceState: android.os.Bundle?) =
            android.widget.TextView(inflater.context).apply {
                text = "QUICK SETTINGS\n\nWiFi / BT / Data\nFlashlight / DND / Location\nBrightness & Volume\n\nTap to open"
                setTextColor(0xFF00FF00.toInt()); textSize = 14f; setTypeface(android.graphics.Typeface.MONOSPACE); setPadding(32, 32, 32, 32)
                setOnClickListener { startActivity(Intent(context, QuickSettingsActivity::class.java)) }
            }
    }
    class WeatherFragment : androidx.fragment.app.Fragment() {
        override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, savedInstanceState: android.os.Bundle?) =
            android.widget.TextView(inflater.context).apply {
                text = "WEATHER\n\nLocation-based\nTemperature & Conditions\n3-Day Forecast\nAuto-refresh\n\nTap to refresh"
                setTextColor(0xFF00FF00.toInt()); textSize = 14f; setTypeface(android.graphics.Typeface.MONOSPACE); setPadding(32, 32, 32, 32)
                setOnClickListener { Toast.makeText(context, "Refreshing weather...", Toast.LENGTH_SHORT).show() }
            }
    }
}
