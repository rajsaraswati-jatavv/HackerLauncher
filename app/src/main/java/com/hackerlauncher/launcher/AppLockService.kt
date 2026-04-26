package com.hackerlauncher.launcher

import com.hackerlauncher.R

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

// ─── Constants ────────────────────────────────────────────────────────────────

private const val TAG = "AppLockService"
private const val CHANNEL_ID = "app_lock_channel"
private const val NOTIFICATION_ID = 1001
private const val POLL_INTERVAL = 500L
private const val PREFS_NAME = "app_lock_prefs"
private const val KEY_LOCKED_APPS = "locked_apps"
private const val KEY_PIN_HASH = "pin_hash"
private const val KEY_TIMEOUT_MINUTES = "timeout_minutes"
private const val KEY_FAKE_CRASH = "fake_crash_enabled"
private const val KEY_INTRUDER_PHOTO = "intruder_photo_enabled"
private const val DEFAULT_TIMEOUT = 5

// ─── Service ──────────────────────────────────────────────────────────────────

class AppLockService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var lastUnlockedPackage: String? = null
    private var lastUnlockedTime: Long = 0
    private lateinit var prefs: SharedPreferences

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                checkForegroundApp()
                handler.postDelayed(this, POLL_INTERVAL)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isRunning) return START_STICKY

        startForeground(NOTIFICATION_ID, buildNotification())
        isRunning = true
        handler.post(pollRunnable)
        Log.d(TAG, "AppLockService started")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(pollRunnable)
        Log.d(TAG, "AppLockService stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─── Notification ──────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "App Lock Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Monitoring locked applications"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("> APP_LOCK_ACTIVE")
            .setContentText("Monitoring locked apps...")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setColor(0xFF00FF00.toInt())
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // ─── Core Logic ────────────────────────────────────────────────────────

    private fun checkForegroundApp() {
        val currentPackage = getForegroundApp() ?: return
        val lockedApps = getLockedApps()

        if (currentPackage !in lockedApps) return
        if (currentPackage == packageName) return

        // Check timeout
        val timeoutMinutes = prefs.getInt(KEY_TIMEOUT_MINUTES, DEFAULT_TIMEOUT)
        if (currentPackage == lastUnlockedPackage) {
            val elapsed = System.currentTimeMillis() - lastUnlockedTime
            if (elapsed < timeoutMinutes * 60_000L) return
        }

        // Locked app detected - show lock screen
        Log.d(TAG, "Locked app detected: $currentPackage")

        if (prefs.getBoolean(KEY_FAKE_CRASH, false)) {
            showFakeCrash(currentPackage)
        } else {
            showLockScreen(currentPackage)
        }
    }

    @Suppress("DEPRECATION")
    private fun getForegroundApp(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 10_000

        val usageStats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        if (usageStats.isNullOrEmpty()) return null

        return usageStats.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    private fun getLockedApps(): Set<String> {
        val json = prefs.getString(KEY_LOCKED_APPS, null) ?: return emptySet()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapTo(mutableSetOf()) { arr.getString(it) }
        } catch (_: Exception) { emptySet() }
    }

    // ─── Lock Screen Activity ──────────────────────────────────────────────

    private fun showLockScreen(packageName: String) {
        val intent = Intent(this, LockScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra("locked_package", packageName)
        }
        startActivity(intent)
    }

    // ─── Fake Crash ────────────────────────────────────────────────────────

    private fun showFakeCrash(packageName: String) {
        val intent = Intent(this, FakeCrashActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra("locked_package", packageName)
        }
        startActivity(intent)
    }

    // ─── Intruder Photo ────────────────────────────────────────────────────

    fun takeIntruderPhoto() {
        if (!prefs.getBoolean(KEY_INTRUDER_PHOTO, false)) return

        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = cameraManager.cameraIdList.find { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            } ?: return

            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val size = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(ImageFormat.JPEG)?.firstOrNull() ?: return

            val imageReader = ImageReader.newInstance(
                size.width, size.height, ImageFormat.JPEG, 1
            )

            val thread = HandlerThread("CameraThread").apply { start() }
            val handler = Handler(thread.looper)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                cameraManager.openCamera(cameraId, ContextCompat.getMainExecutor(this), object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        val surface = imageReader.surface
                        val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                            addTarget(surface)
                        }.build()

                        camera.createCaptureSession(
                            listOf(surface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    session.capture(captureRequest, object : CameraCaptureSession.CaptureCallback() {}, handler)
                                }
                                override fun onConfigureFailed(session: CameraCaptureSession) {}
                            },
                            handler
                        )
                    }
                    override fun onDisconnected(camera: CameraDevice) { camera.close() }
                    override fun onError(camera: CameraDevice, error: Int) { camera.close() }
                })
            }

            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    saveIntruderPhoto(image)
                    image.close()
                }
                imageReader.close()
                thread.quitSafely()
            }, handler)
        } catch (e: SecurityException) {
            Log.e(TAG, "Camera permission not granted", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to take intruder photo", e)
        }
    }

    private fun saveIntruderPhoto(image: Image) {
        val buffer: ByteBuffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val dir = File(getExternalFilesDir(null), "intruder_photos")
        if (!dir.exists()) dir.mkdirs()
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val file = File(dir, "intruder_${sdf.format(Date())}.jpg")
        FileOutputStream(file).use { it.write(bytes) }
        Log.d(TAG, "Intruder photo saved: ${file.absolutePath}")
    }

    // ─── PIN Hashing ───────────────────────────────────────────────────────

    fun verifyPin(input: String): Boolean {
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val inputHash = hashPin(input)
        return inputHash == storedHash
    }

    companion object {
        fun hashPin(pin: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(pin.toByteArray(Charsets.UTF_8))
            return hash.joinToString("") { "%02x".format(it) }
        }

        fun setPin(prefs: SharedPreferences, pin: String) {
            prefs.edit().putString(KEY_PIN_HASH, hashPin(pin)).apply()
        }

        fun addLockedApp(prefs: SharedPreferences, packageName: String) {
            val json = prefs.getString(KEY_LOCKED_APPS, null)
            val arr = if (json != null) JSONArray(json) else JSONArray()
            if ((0 until arr.length()).none { arr.getString(it) == packageName }) {
                arr.put(packageName)
            }
            prefs.edit().putString(KEY_LOCKED_APPS, arr.toString()).apply()
        }

        fun removeLockedApp(prefs: SharedPreferences, packageName: String) {
            val json = prefs.getString(KEY_LOCKED_APPS, null) ?: return
            val arr = JSONArray(json)
            val newArr = JSONArray()
            for (i in 0 until arr.length()) {
                if (arr.getString(i) != packageName) newArr.put(arr.getString(i))
            }
            prefs.edit().putString(KEY_LOCKED_APPS, newArr.toString()).apply()
        }
    }
}

// ─── Lock Screen Activity ────────────────────────────────────────────────────

class LockScreenActivity : AppCompatActivity() {

    private var wrongAttempts = 0
    private var lockedPackage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lockedPackage = intent.getStringExtra("locked_package")

        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.BLACK)
            setPadding(48, 96, 48, 48)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }

        // Icon
        val icon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_lock_lock)
            setColorFilter(android.graphics.Color.parseColor("#00FF00"))
            layoutParams = LinearLayout.LayoutParams(120, 120).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
        }
        root.addView(icon)

        // Title
        val title = TextView(this).apply {
            text = "> ACCESS_DENIED"
            setTextColor(android.graphics.Color.parseColor("#00FF00"))
            textSize = 20f
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = android.view.Gravity.CENTER
            setPadding(0, 24, 0, 8)
        }
        root.addView(title)

        // Subtitle
        val subtitle = TextView(this).apply {
            text = "Enter PIN to unlock"
            setTextColor(android.graphics.Color.GRAY)
            textSize = 14f
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        root.addView(subtitle)

        // PIN input
        val pinInput = EditText(this).apply {
            hint = "PIN"
            setTextColor(android.graphics.Color.parseColor("#00FF00"))
            setHintTextColor(android.graphics.Color.GRAY)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setBackgroundColor(android.graphics.Color.parseColor("#1A1A1A"))
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 24f
            gravity = android.view.Gravity.CENTER
            setPadding(32, 16, 32, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(pinInput)

        // Biometric button
        val biometricBtn = Button(this).apply {
            text = "[ BIOMETRIC ]"
            setTextColor(android.graphics.Color.parseColor("#00FF00"))
            setBackgroundColor(android.graphics.Color.parseColor("#1A1A1A"))
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(16, 8, 16, 8)
            setOnClickListener { showBiometricPrompt() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
        }
        root.addView(biometricBtn)

        // Unlock button
        val unlockBtn = Button(this).apply {
            text = "[ UNLOCK ]"
            setTextColor(android.graphics.Color.BLACK)
            setBackgroundColor(android.graphics.Color.parseColor("#00FF00"))
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(16, 16, 16, 16)
            textSize = 16f
            setOnClickListener { attemptUnlock(pinInput.text.toString()) }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
        }
        root.addView(unlockBtn)

        setContentView(root)
    }

    private fun attemptUnlock(pin: String) {
        val service = getSystemService(Context.ACCESSIBILITY_SERVICE)
        // Get the service instance via preferences
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedHash = prefs.getString(KEY_PIN_HASH, null)

        if (storedHash != null && AppLockService.hashPin(pin) == storedHash) {
            // Correct PIN
            wrongAttempts = 0
            val appLockService = AppLockService()
            // Mark as unlocked
            prefs.edit().putLong("last_unlock_${lockedPackage}", System.currentTimeMillis()).apply()
            finish()
        } else {
            // Wrong PIN
            wrongAttempts++
            Toast.makeText(this, "> WRONG_PIN [Attempt $wrongAttempts]", Toast.LENGTH_SHORT).show()

            if (wrongAttempts >= 3) {
                // Take intruder photo
                val svc = AppLockService()
                // Trigger intruder photo through the running service
                val photoIntent = Intent(this, AppLockService::class.java).apply {
                    action = "TAKE_INTRUDER_PHOTO"
                }
                startService(photoIntent)
            }
        }
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().putLong("last_unlock_${lockedPackage}", System.currentTimeMillis()).apply()
                    finish()
                }

                override fun onAuthenticationFailed() {
                    Toast.makeText(
                        this@LockScreenActivity,
                        "> BIOMETRIC_FAILED",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("> BIOMETRIC_VERIFY")
            .setSubtitle("Confirm your identity")
            .setNegativeButtonText("Use PIN")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    override fun onBackPressed() {
        // Go to home instead of back
        val home = Intent(Intent.ACTION_MAIN)
        home.addCategory(Intent.CATEGORY_HOME)
        home.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(home)
    }
}

// ─── Fake Crash Activity ─────────────────────────────────────────────────────

class FakeCrashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val lockedPackage = intent.getStringExtra("locked_package") ?: ""
        val appName = try {
            packageManager.getApplicationInfo(lockedPackage, 0).loadLabel(packageManager).toString()
        } catch (_: Exception) { lockedPackage }

        AlertDialog.Builder(this)
            .setTitle("Unfortunately, $appName has stopped")
            .setMessage("Would you like to report this issue?")
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ ->
                val home = Intent(Intent.ACTION_MAIN)
                home.addCategory(Intent.CATEGORY_HOME)
                home.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(home)
                finish()
            }
            .show()
    }
}
