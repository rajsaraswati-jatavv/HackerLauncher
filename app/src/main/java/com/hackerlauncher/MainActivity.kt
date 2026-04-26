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
import com.hackerlauncher.auth.FirebaseAuthManager
import com.hackerlauncher.auth.LoginActivity
import com.hackerlauncher.livewallpaper.HackerWallpaperService
import com.hackerlauncher.modules.*
import com.hackerlauncher.services.HackerForegroundService
import com.hackerlauncher.services.OverlayService
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
    private val logger = Logger()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val fragmentTags = arrayOf(
        "terminal", "network", "osint", "crypto", "web",
        "anonymity", "files", "automation", "chat",
        "root", "wifarsenal", "subnetscan", "password", "bluetooth"
    )

    private val tabLabels = listOf(
        "Term", "Net", "OSINT", "Crypto", "Web",
        "Anon", "Files", "Auto", "Chat",
        "Root", "WiFi", "Subnet", "Pass", "BT"
    )

    private lateinit var gestureDetector: GestureDetector
    private var currentFragmentIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PreferencesManager(this)

        // Biometric lock check
        if (prefs.isBiometricLockEnabled()) {
            showBiometricLock()
        }

        // Disclaimer check
        if (!prefs.isDisclaimerAccepted()) {
            showDisclaimer()
        }

        // Initialize views
        tabLayout = findViewById(R.id.tabLayout)
        fragmentContainer = findViewById(R.id.fragmentContainer)
        tvGreeting = findViewById(R.id.tvGreeting)
        tvTerminalWidget = findViewById(R.id.tvTerminalWidget)
        tvSystemStatus = findViewById(R.id.tvSystemStatus)

        // Setup greeting
        updateGreeting()

        // Setup tabs
        setupTabs()

        // Setup gestures
        setupGestures()

        // Request permissions
        val permissionHelper = PermissionHelper(this)
        permissionHelper.requestAllPermissions()
        permissionHelper.requestOverlayPermission()
        permissionHelper.requestManageExternalStorage()
        permissionHelper.requestIgnoreBatteryOptimization()

        // Start foreground service
        startCoreService()

        // Setup terminal widget log
        startLogUpdater()

        // System status updater
        startStatusUpdater()

        // Handle back press
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
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

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("HackerLauncher Lock")
            .setSubtitle("Authenticate to access")
            .setNegativeButtonText("Use PIN")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun showDisclaimer() {
        AlertDialog.Builder(this)
            .setTitle("DISCLAIMER")
            .setMessage(
                "HackerLauncher v3.0 is designed for EDUCATIONAL and AUTHORIZED TESTING purposes only.\n\n" +
                "By using this application, you agree that:\n\n" +
                "1. You will ONLY use these tools on systems you own or have explicit permission to test.\n" +
                "2. You are solely responsible for any actions performed using this application.\n" +
                "3. The developers are NOT liable for any misuse or damage caused.\n" +
                "4. Unauthorized access to computer systems is ILLEGAL in most jurisdictions.\n\n" +
                "Use responsibly and ethically."
            )
            .setPositiveButton("I Understand & Agree") { _, _ ->
                prefs.setDisclaimerAccepted(true)
            }
            .setNegativeButton("Exit") { _, _ ->
                finishAffinity()
            }
            .setCancelable(false)
            .show()
    }

    private fun updateGreeting() {
        val userName = if (FirebaseAuthManager.isLoggedIn()) {
            FirebaseAuthManager.getUserName()
        } else "Hacker"
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 0..5 -> "Good night"
            in 6..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
        tvGreeting.text = "$greeting, $userName"
    }

    private fun setupTabs() {
        for (label in tabLabels) {
            tabLayout.addTab(tabLayout.newTab().setText(label))
        }

        if (supportFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
            showFragment(0)
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentFragmentIndex = tab.position
                showFragment(tab.position)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun createFragment(index: Int): Fragment = when (index) {
        0 -> TerminalFragment()
        1 -> NetworkModuleFragment()
        2 -> OsintFragment()
        3 -> CryptoFragment()
        4 -> WebTestFragment()
        5 -> AnonymityFragment()
        6 -> FileFragment()
        7 -> AutomationFragment()
        8 -> ChatFragment()
        9 -> RootToolsFragment()
        10 -> WifiArsenalFragment()
        11 -> SubnetScannerFragment()
        12 -> PasswordToolsFragment()
        13 -> BluetoothScannerFragment()
        else -> TerminalFragment()
    }

    private fun showFragment(index: Int) {
        val tag = if (index < fragmentTags.size) fragmentTags[index] else "terminal"
        val existingFragment = supportFragmentManager.findFragmentByTag(tag)
        val fragment = existingFragment ?: createFragment(index)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment, tag)
            .commit()
    }

    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent,
                velocityX: Float, velocityY: Float
            ): Boolean {
                val dx = e2.x - (e1?.x ?: 0f)
                val dy = e2.y - (e1?.y ?: 0f)

                if (Math.abs(dx) > Math.abs(dy)) {
                    if (dx > 100) {
                        // Swipe right - previous tab
                        val newIndex = (currentFragmentIndex - 1).coerceAtLeast(0)
                        if (newIndex != currentFragmentIndex) {
                            tabLayout.getTabAt(newIndex)?.select()
                        }
                        return true
                    } else if (dx < -100) {
                        // Swipe left - next tab
                        val newIndex = (currentFragmentIndex + 1).coerceAtMost(tabLabels.size - 1)
                        if (newIndex != currentFragmentIndex) {
                            tabLayout.getTabAt(newIndex)?.select()
                        }
                        return true
                    }
                } else {
                    if (dy > 100) {
                        // Swipe down - notifications
                        val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
                        }
                        startActivity(intent)
                        return true
                    }
                }
                return false
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                // Double tap to switch to terminal
                tabLayout.getTabAt(0)?.select()
                return true
            }
        })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    private fun startCoreService() {
        if (prefs.isForegroundServiceEnabled()) {
            val intent = Intent(this, HackerForegroundService::class.java).apply {
                action = HackerForegroundService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }

        if (prefs.isOverlayEnabled()) {
            val overlayIntent = Intent(this, OverlayService::class.java)
            startService(overlayIntent)
        }
    }

    private fun startLogUpdater() {
        scope.launch {
            while (isActive) {
                delay(2000)
                val logs = Logger.getLogBuffer().takeLast(3).joinToString("\n")
                if (logs.isNotEmpty()) {
                    tvTerminalWidget.text = logs
                }
            }
        }
    }

    private fun startStatusUpdater() {
        scope.launch {
            while (isActive) {
                delay(5000)
                val cm = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val network = cm.activeNetwork
                val caps = cm.getNetworkCapabilities(network)
                val hasInternet = caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                tvSystemStatus.setTextColor(if (hasInternet) 0xFF00FF00.toInt() else 0xFFFF0000.toInt())
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateGreeting()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
