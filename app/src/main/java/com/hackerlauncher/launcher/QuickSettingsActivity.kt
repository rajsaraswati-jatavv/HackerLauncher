package com.hackerlauncher.launcher

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Quick Settings Panel Activity.
 * Toggle buttons for WiFi, Bluetooth, Mobile Data, Airplane Mode,
 * Flashlight, Auto-rotate, DND, Location.
 * SeekBars for brightness and volume.
 */
class QuickSettingsActivity : AppCompatActivity() {

    private lateinit var gridLayout: GridLayout

    // Toggle states
    private var wifiEnabled = false
    private var bluetoothEnabled = false
    private var mobileDataEnabled = false
    private var airplaneModeEnabled = false
    private var flashlightEnabled = false
    private var autoRotateEnabled = false
    private var dndEnabled = false
    private var locationEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#CC000000"))
            setPadding(16, 48, 16, 32)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Title
        val titleView = TextView(this).apply {
            text = "> quick_settings"
            setTextColor(Color.parseColor("#00FF00"))
            typeface = Typeface.MONOSPACE
            textSize = 20f
            setShadowLayer(8f, 0f, 0f, Color.parseColor("#3300FF00"))
            setPadding(0, 0, 0, 16)
        }
        rootLayout.addView(titleView)

        // Toggle grid
        gridLayout = GridLayout(this).apply {
            columnCount = 4
            rowCount = 2
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(gridLayout)

        // Add toggle buttons
        addToggleButton("WIFI", ::toggleWifi, ::isWifiEnabled)
        addToggleButton("BT", ::toggleBluetooth, ::isBluetoothEnabled)
        addToggleButton("DATA", ::toggleMobileData, ::isMobileDataEnabled)
        addToggleButton("AIRPLANE", ::toggleAirplaneMode, ::isAirplaneModeEnabled)
        addToggleButton("FLASH", ::toggleFlashlight, ::isFlashlightEnabled)
        addToggleButton("ROTATE", ::toggleAutoRotate, ::isAutoRotateEnabled)
        addToggleButton("DND", ::toggleDnd, ::isDndEnabled)
        addToggleButton("LOCATION", ::toggleLocation, ::isLocationEnabled)

        // Separator
        View(this).apply {
            setBackgroundColor(Color.parseColor("#003300"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                2
            ).apply { topMargin = 16; bottomMargin = 16 }
        }.also { rootLayout.addView(it) }

        // Brightness
        val brightnessLabel = TextView(this).apply {
            text = "> brightness"
            setTextColor(Color.parseColor("#00FF00"))
            typeface = Typeface.MONOSPACE
            textSize = 13f
        }
        rootLayout.addView(brightnessLabel)

        val brightnessBar = SeekBar(this).apply {
            max = 255
            progress = getCurrentBrightness()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) setBrightness(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            // Green theming
            progressDrawable?.setTint(Color.parseColor("#00FF00"))
            thumb?.setTint(Color.parseColor("#00FF00"))
        }
        rootLayout.addView(brightnessBar)

        // Volume
        val volumeLabel = TextView(this).apply {
            text = "> volume"
            setTextColor(Color.parseColor("#00FF00"))
            typeface = Typeface.MONOSPACE
            textSize = 13f
            setPadding(0, 16, 0, 0)
        }
        rootLayout.addView(volumeLabel)

        val volumeBar = SeekBar(this).apply {
            max = 15
            progress = getCurrentVolume()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) setVolume(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            progressDrawable?.setTint(Color.parseColor("#00FF00"))
            thumb?.setTint(Color.parseColor("#00FF00"))
        }
        rootLayout.addView(volumeBar)

        // Close button
        val closeBtn = TextView(this).apply {
            text = "[ CLOSE ]"
            setTextColor(Color.parseColor("#00FF00"))
            typeface = Typeface.MONOSPACE
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 0)
            setOnClickListener { finish() }
        }
        rootLayout.addView(closeBtn)

        setContentView(rootLayout)
    }

    override fun onResume() {
        super.onResume()
        refreshAllToggles()
    }

    private fun addToggleButton(
        label: String,
        toggleFn: () -> Unit,
        stateFn: () -> Boolean
    ) {
        val isEnabled = try { stateFn() } catch (e: Exception) { false }

        val btnLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(8, 12, 8, 12)
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                height = GridLayout.LayoutParams.WRAP_CONTENT
            }
        }

        // Icon placeholder
        val iconView = TextView(this).apply {
            text = getToggleIcon(label, isEnabled)
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(if (isEnabled) Color.parseColor("#00FF00") else Color.parseColor("#004400"))
        }

        val labelView = TextView(this).apply {
            text = label
            setTextColor(if (isEnabled) Color.parseColor("#00FF00") else Color.parseColor("#004400"))
            typeface = Typeface.MONOSPACE
            textSize = 10f
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 0)
        }

        btnLayout.addView(iconView)
        btnLayout.addView(labelView)

        btnLayout.setOnClickListener {
            try {
                toggleFn()
                // Refresh state after toggle
                val newState = try { stateFn() } catch (e: Exception) { false }
                iconView.text = getToggleIcon(label, newState)
                iconView.setTextColor(if (newState) Color.parseColor("#00FF00") else Color.parseColor("#004400"))
                labelView.setTextColor(if (newState) Color.parseColor("#00FF00") else Color.parseColor("#004400"))
                Toast.makeText(this, "> $label: ${if (newState) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "> error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        gridLayout.addView(btnLayout)
    }

    private fun getToggleIcon(label: String, isEnabled: Boolean): String {
        return when (label) {
            "WIFI" -> if (isEnabled) "📶" else "📵"
            "BT" -> if (isEnabled) "🔵" else "⚫"
            "DATA" -> if (isEnabled) "📱" else "📵"
            "AIRPLANE" -> if (isEnabled) "✈️" else "🚫"
            "FLASH" -> if (isEnabled) "🔦" else "💡"
            "ROTATE" -> if (isEnabled) "🔄" else "🔒"
            "DND" -> if (isEnabled) "🔇" else "🔔"
            "LOCATION" -> if (isEnabled) "📍" else "❌"
            else -> "⚙️"
        }
    }

    private fun refreshAllToggles() {
        // Refresh is handled by onResume which recreates the view
    }

    //region Toggle Implementations

    private fun toggleWifi() {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiManager.isWifiEnabled = !wifiManager.isWifiEnabled
        } catch (e: Exception) {
            // Fallback: open WiFi settings
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
    }

    private fun isWifiEnabled(): Boolean {
        return try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiManager.isWifiEnabled
        } catch (e: Exception) { false }
    }

    private fun toggleBluetooth() {
        try {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = bluetoothManager.adapter
            if (adapter != null) {
                @Suppress("DEPRECATION")
                if (adapter.isEnabled) {
                    adapter.disable()
                } else {
                    adapter.enable()
                }
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, "> bluetooth_permission_required", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }
    }

    private fun isBluetoothEnabled(): Boolean {
        return try {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothManager.adapter?.isEnabled == true
        } catch (e: Exception) { false }
    }

    private fun toggleMobileData() {
        try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE)
                as android.net.ConnectivityManager
            // Use reflection to access setMobileDataEnabled
            val method = connectivityManager.javaClass.getMethod(
                "setMobileDataEnabled",
                Boolean::class.javaPrimitiveType
            )
            method.invoke(connectivityManager, !isMobileDataEnabled())
        } catch (e: Exception) {
            // Fallback: open mobile data settings
            try {
                val intent = Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
                startActivity(intent)
            } catch (ex: Exception) {
                startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            }
            Toast.makeText(this, "> open_mobile_data_settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isMobileDataEnabled(): Boolean {
        return try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE)
                as android.net.ConnectivityManager
            val method = connectivityManager.javaClass.getMethod("getMobileDataEnabled")
            method.invoke(connectivityManager) as? Boolean ?: false
        } catch (e: Exception) { false }
    }

    private fun toggleAirplaneMode() {
        try {
            // Toggle airplane mode
            val isEnabled = Settings.Global.getInt(
                contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                0
            ) == 1

            Settings.Global.putInt(
                contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                if (isEnabled) 0 else 1
            )

            // Broadcast the change
            val intent = Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED).apply {
                putExtra("state", !isEnabled)
            }
            sendBroadcast(intent)
        } catch (e: SecurityException) {
            Toast.makeText(this, "> permission_required_for_airplane_mode", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
        }
    }

    private fun isAirplaneModeEnabled(): Boolean {
        return try {
            Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        } catch (e: Exception) { false }
    }

    private fun toggleFlashlight() {
        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }

            if (cameraId != null) {
                flashlightEnabled = !flashlightEnabled
                cameraManager.setTorchMode(cameraId, flashlightEnabled)
            } else {
                Toast.makeText(this, "> no_flash_available", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "> error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isFlashlightEnabled(): Boolean = flashlightEnabled

    private fun toggleAutoRotate() {
        try {
            val isEnabled = Settings.System.getInt(
                contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                0
            ) == 1

            Settings.System.putInt(
                contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (isEnabled) 0 else 1
            )
        } catch (e: Exception) {
            Toast.makeText(this, "> error: ${e.message}", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
        }
    }

    private fun isAutoRotateEnabled(): Boolean {
        return try {
            Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
        } catch (e: Exception) { false }
    }

    private fun toggleDnd() {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager

            if (!notificationManager.isNotificationPolicyAccessGranted) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                startActivity(intent)
                Toast.makeText(this, "> grant_dnd_access_first", Toast.LENGTH_SHORT).show()
                return
            }

            val currentFilter = notificationManager.currentInterruptionFilter
            val newFilter = if (currentFilter == android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY ||
                currentFilter == android.app.NotificationManager.INTERRUPTION_FILTER_NONE
            ) {
                android.app.NotificationManager.INTERRUPTION_FILTER_ALL
            } else {
                android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY
            }
            notificationManager.setInterruptionFilter(newFilter)
        } catch (e: Exception) {
            Toast.makeText(this, "> error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isDndEnabled(): Boolean {
        return try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager
            val filter = notificationManager.currentInterruptionFilter
            filter == android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY ||
                filter == android.app.NotificationManager.INTERRUPTION_FILTER_NONE
        } catch (e: Exception) { false }
    }

    private fun toggleLocation() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // On Android 10+, we can't programmatically toggle location
                // Open location settings instead
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                Toast.makeText(this, "> open_location_settings", Toast.LENGTH_SHORT).show()
            } else {
                @Suppress("DEPRECATION")
                val isEnabled = Settings.Secure.getInt(
                    contentResolver,
                    Settings.Secure.LOCATION_MODE,
                    Settings.Secure.LOCATION_MODE_OFF
                ) != Settings.Secure.LOCATION_MODE_OFF

                Settings.Secure.putInt(
                    contentResolver,
                    Settings.Secure.LOCATION_MODE,
                    if (isEnabled) Settings.Secure.LOCATION_MODE_OFF
                    else Settings.Secure.LOCATION_MODE_HIGH_ACCURACY
                )
            }
        } catch (e: SecurityException) {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            Toast.makeText(this, "> permission_required", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
    }

    private fun isLocationEnabled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                locationManager.isLocationEnabled
            } else {
                @Suppress("DEPRECATION")
                Settings.Secure.getInt(
                    contentResolver,
                    Settings.Secure.LOCATION_MODE,
                    Settings.Secure.LOCATION_MODE_OFF
                ) != Settings.Secure.LOCATION_MODE_OFF
            }
        } catch (e: Exception) { false }
    }

    //endregion

    //region Brightness & Volume

    private fun getCurrentBrightness(): Int {
        return try {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        } catch (e: Exception) { 128 }
    }

    private fun setBrightness(value: Int) {
        try {
            // First ensure manual brightness mode
            Settings.System.putInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)

            // Apply to window
            val layoutParams = window.attributes
            layoutParams.screenBrightness = value / 255f
            window.attributes = layoutParams
        } catch (e: SecurityException) {
            Toast.makeText(this, "> permission_required_for_brightness", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            // Silently fail
        }
    }

    private fun getCurrentVolume(): Int {
        return try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        } catch (e: Exception) { 7 }
    }

    private fun setVolume(value: Int) {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            audioManager.setStreamVolume(
                android.media.AudioManager.STREAM_MUSIC,
                value,
                0
            )
        } catch (e: SecurityException) {
            Toast.makeText(this, "> permission_required_for_volume", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            // Silently fail
        }
    }

    //endregion

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(0, 0)
    }
}
