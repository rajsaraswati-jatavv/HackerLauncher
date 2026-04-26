package com.hackerlauncher.utils

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "hacker_launcher_prefs", Context.MODE_PRIVATE
    )

    // Auto start on boot
    fun isAutoStartOnBoot(): Boolean = prefs.getBoolean("auto_start_boot", true)
    fun setAutoStartOnBoot(enabled: Boolean) = prefs.edit().putBoolean("auto_start_boot", enabled).apply()

    // Foreground service
    fun isForegroundServiceEnabled(): Boolean = prefs.getBoolean("foreground_service", true)
    fun setForegroundServiceEnabled(enabled: Boolean) = prefs.edit().putBoolean("foreground_service", enabled).apply()

    // Overlay
    fun isOverlayEnabled(): Boolean = prefs.getBoolean("overlay_enabled", false)
    fun setOverlayEnabled(enabled: Boolean) = prefs.edit().putBoolean("overlay_enabled", enabled).apply()

    // Biometric lock
    fun isBiometricLockEnabled(): Boolean = prefs.getBoolean("biometric_lock", false)
    fun setBiometricLockEnabled(enabled: Boolean) = prefs.edit().putBoolean("biometric_lock", enabled).apply()

    // First launch disclaimer
    fun isDisclaimerAccepted(): Boolean = prefs.getBoolean("disclaimer_accepted", false)
    fun setDisclaimerAccepted(accepted: Boolean) = prefs.edit().putBoolean("disclaimer_accepted", accepted).apply()

    // Chat API settings
    fun getChatApiKey(): String = prefs.getString("chat_api_key", "") ?: ""
    fun setChatApiKey(key: String) = prefs.edit().putString("chat_api_key", key).apply()

    fun getChatApiProvider(): String = prefs.getString("chat_api_provider", "openai") ?: "openai"
    fun setChatApiProvider(provider: String) = prefs.edit().putString("chat_api_provider", provider).apply()

    // HIBP API key
    fun getHibpApiKey(): String = prefs.getString("hibp_api_key", "") ?: ""
    fun setHibpApiKey(key: String) = prefs.edit().putString("hibp_api_key", key).apply()

    // Theme
    fun isDarkTheme(): Boolean = prefs.getBoolean("dark_theme", true)
    fun setDarkTheme(dark: Boolean) = prefs.edit().putBoolean("dark_theme", dark).apply()

    // Wallpaper as background
    fun isWallpaperAsBackground(): Boolean = prefs.getBoolean("wallpaper_background", true)
    fun setWallpaperAsBackground(enabled: Boolean) = prefs.edit().putBoolean("wallpaper_background", enabled).apply()

    // Notification log
    fun getNotificationLog(): List<String> {
        val log = prefs.getStringSet("notification_log", emptySet()) ?: emptySet()
        return log.sorted()
    }

    fun addNotificationLog(entry: String) {
        val current = prefs.getStringSet("notification_log", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add("${System.currentTimeMillis()}: $entry")
        if (current.size > 100) {
            val sorted = current.sorted().takeLast(100).toMutableSet()
            prefs.edit().putStringSet("notification_log", sorted).apply()
        } else {
            prefs.edit().putStringSet("notification_log", current).apply()
        }
    }

    // Gesture settings
    fun getSwipeUpAction(): String = prefs.getString("swipe_up", "terminal") ?: "terminal"
    fun setSwipeUpAction(action: String) = prefs.edit().putString("swipe_up", action).apply()

    fun getSwipeDownAction(): String = prefs.getString("swipe_down", "notifications") ?: "notifications"
    fun setSwipeDownAction(action: String) = prefs.edit().putString("swipe_down", action).apply()

    fun getSwipeLeftAction(): String = prefs.getString("swipe_left", "network") ?: "network"
    fun setSwipeLeftAction(action: String) = prefs.edit().putString("swipe_left", action).apply()

    fun getSwipeRightAction(): String = prefs.getString("swipe_right", "chat") ?: "chat"
    fun setSwipeRightAction(action: String) = prefs.edit().putString("swipe_right", action).apply()

    // Scan settings
    fun getScanTimeout(): Int = prefs.getInt("scan_timeout", 1000)
    fun setScanTimeout(timeout: Int) = prefs.edit().putInt("scan_timeout", timeout).apply()

    fun getMaxPortScanRange(): Int = prefs.getInt("max_port_scan", 1024)
    fun setMaxPortScanRange(max: Int) = prefs.edit().putInt("max_port_scan", max).apply()

    // XOR key for crypto
    fun getXorKey(): String = prefs.getString("xor_key", "HACKER") ?: "HACKER"
    fun setXorKey(key: String) = prefs.edit().putString("xor_key", key).apply()

    // Caesar shift
    fun getCaesarShift(): Int = prefs.getInt("caesar_shift", 3)
    fun setCaesarShift(shift: Int) = prefs.edit().putInt("caesar_shift", shift).apply()

    // Password length
    fun getPasswordLength(): Int = prefs.getInt("password_length", 16)
    fun setPasswordLength(length: Int) = prefs.edit().putInt("password_length", length).apply()
}
