package com.hackerlauncher.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionHelper(private val activity: Activity) {

    companion object {
        const val REQUEST_CODE_PERMISSIONS = 1001
        const val REQUEST_CODE_OVERLAY = 1002
        const val REQUEST_CODE_BATTERY_OPT = 1003
        const val REQUEST_CODE_ACCESSIBILITY = 1004

        val REQUIRED_PERMISSIONS = mutableListOf<String>().apply {
            add(Manifest.permission.INTERNET)
            add(Manifest.permission.ACCESS_NETWORK_STATE)
            add(Manifest.permission.ACCESS_WIFI_STATE)
            add(Manifest.permission.CHANGE_WIFI_STATE)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.BLUETOOTH)
            add(Manifest.permission.BLUETOOTH_ADMIN)
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            add(Manifest.permission.RECEIVE_BOOT_COMPLETED)
            add(Manifest.permission.WAKE_LOCK)
            add(Manifest.permission.SET_WALLPAPER)
            add(Manifest.permission.FOREGROUND_SERVICE)
            add(Manifest.permission.USE_BIOMETRIC)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }

    fun requestAllPermissions() {
        val ungranted = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        if (ungranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, ungranted.toTypedArray(), REQUEST_CODE_PERMISSIONS)
        }
    }

    fun hasAllPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(activity)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivityForResult(intent, REQUEST_CODE_OVERLAY)
        }
    }

    fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(activity)
    }

    fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = activity.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(activity.packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${activity.packageName}")
                    }
                    activity.startActivityForResult(intent, REQUEST_CODE_BATTERY_OPT)
                } catch (e: Exception) {
                    // Fallback to battery settings
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    activity.startActivity(intent)
                }
            }
        }
    }

    fun isIgnoringBatteryOptimization(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = activity.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            return pm.isIgnoringBatteryOptimizations(activity.packageName)
        }
        return true
    }

    fun requestAccessibilityPermission() {
        Toast.makeText(activity, "Enable HackerLauncher Accessibility in Settings", Toast.LENGTH_LONG).show()
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        activity.startActivity(intent)
    }

    fun requestManageExternalStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivity(intent)
            }
        }
    }

    fun getPermissionStatus(): Map<String, Boolean> {
        return REQUIRED_PERMISSIONS.associateWith {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
