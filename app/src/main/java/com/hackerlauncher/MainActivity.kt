package com.hackerlauncher

import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
    private lateinit var fragmentContainer: LinearLayout
    private lateinit var tvGreeting: TextView
    private lateinit var tvTerminalWidget: TextView
    private lateinit var prefs: PreferencesManager
    private val logger = Logger()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Fragment tags for reuse (avoid recreation on config change)
    private val fragmentTags = arrayOf(
        "terminal", "network", "osint", "crypto", "web",
        "anonymity", "files", "automation", "chat"
    )

    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PreferencesManager(this)

        // Disclaimer check
        if (!prefs.isDisclaimerAccepted()) {
            showDisclaimer()
        }

        // Initialize views
        tabLayout = findViewById(R.id.tabLayout)
        fragmentContainer = findViewById(R.id.fragmentContainer)
        tvGreeting = findViewById(R.id.tvGreeting)
        tvTerminalWidget = findViewById(R.id.tvTerminalWidget)

        // Setup greeting
        val userName = if (FirebaseAuthManager.isLoggedIn()) {
            FirebaseAuthManager.getUserName()
        } else "Hacker"
        tvGreeting.text = "Welcome, $userName"

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

        // FIX: Use OnBackPressedDispatcher instead of deprecated onBackPressed()
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Stay as launcher - move task to background instead of exiting
                moveTaskToBack(true)
            }
        })
    }

    private fun showDisclaimer() {
        AlertDialog.Builder(this)
            .setTitle("DISCLAIMER")
            .setMessage(
                "HackerLauncher is designed for EDUCATIONAL and AUTHORIZED TESTING purposes only.\n\n" +
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

    private fun setupTabs() {
        val tabs = listOf("Terminal", "Network", "OSINT", "Crypto", "Web", "Anon", "Files", "Auto", "Chat")
        for (tab in tabs) {
            tabLayout.addTab(tabLayout.newTab().setText(tab))
        }

        // Show terminal by default (only if not restoring from saved state)
        if (supportFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
            showFragment(TerminalFragment(), fragmentTags[0])
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val fragment = when (tab.position) {
                    0 -> TerminalFragment()
                    1 -> NetworkModuleFragment()
                    2 -> OsintFragment()
                    3 -> CryptoFragment()
                    4 -> WebTestFragment()
                    5 -> AnonymityFragment()
                    6 -> FileFragment()
                    7 -> AutomationFragment()
                    8 -> ChatFragment()
                    else -> TerminalFragment()
                }
                val tag = if (tab.position < fragmentTags.size) fragmentTags[tab.position] else "terminal"
                showFragment(fragment, tag)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun showFragment(fragment: Fragment, tag: String) {
        // FIX: Check if fragment already exists in FragmentManager to avoid recreation
        val existingFragment = supportFragmentManager.findFragmentByTag(tag)
        val fragmentToShow = existingFragment ?: fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragmentToShow, tag)
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
                    // Horizontal swipe
                    if (dx > 100) {
                        // Swipe right
                        handleSwipe(prefs.getSwipeRightAction())
                        return true
                    } else if (dx < -100) {
                        // Swipe left
                        handleSwipe(prefs.getSwipeLeftAction())
                        return true
                    }
                } else {
                    // Vertical swipe
                    if (dy > 100) {
                        // Swipe down
                        handleSwipe(prefs.getSwipeDownAction())
                        return true
                    } else if (dy < -100) {
                        // Swipe up
                        handleSwipe(prefs.getSwipeUpAction())
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun handleSwipe(action: String) {
        val fragment = when (action) {
            "terminal" -> TerminalFragment()
            "network" -> NetworkModuleFragment()
            "osint" -> OsintFragment()
            "crypto" -> CryptoFragment()
            "web" -> WebTestFragment()
            "anonymity" -> AnonymityFragment()
            "files" -> FileFragment()
            "automation" -> AutomationFragment()
            "chat" -> ChatFragment()
            "notifications" -> {
                // Open notification settings
                val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
                }
                startActivity(intent)
                return
            }
            else -> return
        }
        val index = listOf("terminal", "network", "osint", "crypto", "web", "anonymity", "files", "automation", "chat").indexOf(action)
        val tag = if (index >= 0 && index < fragmentTags.size) fragmentTags[index] else "terminal"
        showFragment(fragment, tag)
        if (index >= 0) {
            tabLayout.getTabAt(index)?.select()
        }
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

        // Start overlay service if enabled
        if (prefs.isOverlayEnabled()) {
            val overlayIntent = Intent(this, OverlayService::class.java)
            startService(overlayIntent)
        }
    }

    private fun startLogUpdater() {
        scope.launch {
            while (isActive) {
                delay(2000)
                val logs = Logger.getLogBuffer().takeLast(5).joinToString("\n")
                if (logs.isNotEmpty()) {
                    tvTerminalWidget.text = logs
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Update greeting if login state changed
        val userName = if (FirebaseAuthManager.isLoggedIn()) {
            FirebaseAuthManager.getUserName()
        } else "Hacker"
        tvGreeting.text = "Welcome, $userName"
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
