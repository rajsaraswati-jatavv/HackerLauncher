package com.hackerlauncher.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "hacker_launcher_prefs", Context.MODE_PRIVATE
    )

    private val gson = Gson()

    // ========================================
    // CORE SETTINGS
    // ========================================

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

    // ========================================
    // LAUNCHER SETTINGS
    // ========================================

    // Home screen grid size
    fun getGridSize(): String = prefs.getString("grid_size", "4x5") ?: "4x5"
    fun setGridSize(size: String) = prefs.edit().putString("grid_size", size).apply()

    // Icon size
    fun getIconSize(): String = prefs.getString("icon_size", "medium") ?: "medium"
    fun setIconSize(size: String) = prefs.edit().putString("icon_size", size).apply()

    // Show labels
    fun isShowLabels(): Boolean = prefs.getBoolean("show_labels", true)
    fun setShowLabels(show: Boolean) = prefs.edit().putBoolean("show_labels", show).apply()

    // Dock apps count
    fun getDockAppsCount(): Int = prefs.getInt("dock_apps_count", 5)
    fun setDockAppsCount(count: Int) = prefs.edit().putInt("dock_apps_count", count).apply()

    // Dock apps (package names as JSON array)
    fun getDockApps(): List<String> {
        val json = prefs.getString("dock_apps", null) ?: return emptyList()
        return try { gson.fromJson(json, object : TypeToken<List<String>>() {}.type) } catch (e: Exception) { emptyList() }
    }
    fun setDockApps(apps: List<String>) = prefs.edit().putString("dock_apps", gson.toJson(apps)).apply()

    // Pinned apps (package names as JSON array)
    fun getPinnedApps(): List<String> {
        val json = prefs.getString("pinned_apps", null) ?: return emptyList()
        return try { gson.fromJson(json, object : TypeToken<List<String>>() {}.type) } catch (e: Exception) { emptyList() }
    }
    fun setPinnedApps(apps: List<String>) = prefs.edit().putString("pinned_apps", gson.toJson(apps)).apply()

    // Hidden apps (package names as JSON array)
    fun getHiddenApps(): List<String> {
        val json = prefs.getString("hidden_apps", null) ?: return emptyList()
        return try { gson.fromJson(json, object : TypeToken<List<String>>() {}.type) } catch (e: Exception) { emptyList() }
    }
    fun setHiddenApps(apps: List<String>) = prefs.edit().putString("hidden_apps", gson.toJson(apps)).apply()
    fun addHiddenApp(packageName: String) {
        val apps = getHiddenApps().toMutableList()
        if (packageName !in apps) {
            apps.add(packageName)
            setHiddenApps(apps)
        }
    }
    fun removeHiddenApp(packageName: String) {
        val apps = getHiddenApps().toMutableList()
        apps.remove(packageName)
        setHiddenApps(apps)
    }

    // Folders (JSON map of folderName -> List<packageName>)
    fun getFolders(): Map<String, List<String>> {
        val json = prefs.getString("folders", null) ?: return emptyMap()
        return try { gson.fromJson(json, object : TypeToken<Map<String, List<String>>>() {}.type) } catch (e: Exception) { emptyMap() }
    }
    fun setFolders(folders: Map<String, List<String>>) = prefs.edit().putString("folders", gson.toJson(folders)).apply()

    // App drawer sort order
    fun getSortOrder(): String = prefs.getString("sort_order", "alpha") ?: "alpha"
    fun setSortOrder(order: String) = prefs.edit().putString("sort_order", order).apply()

    // ========================================
    // APP LOCK SETTINGS
    // ========================================

    fun isAppLockEnabled(): Boolean = prefs.getBoolean("app_lock_enabled", false)
    fun setAppLockEnabled(enabled: Boolean) = prefs.edit().putBoolean("app_lock_enabled", enabled).apply()

    // Locked apps list
    fun getLockedApps(): Set<String> = prefs.getStringSet("locked_apps", emptySet()) ?: emptySet()
    fun setLockedApps(apps: Set<String>) = prefs.edit().putStringSet("locked_apps", apps).apply()
    fun addLockedApp(packageName: String) {
        val apps = getLockedApps().toMutableSet()
        apps.add(packageName)
        setLockedApps(apps)
    }
    fun removeLockedApp(packageName: String) {
        val apps = getLockedApps().toMutableSet()
        apps.remove(packageName)
        setLockedApps(apps)
    }

    // Lock method: "pin", "pattern", "biometric"
    fun getLockMethod(): String = prefs.getString("lock_method", "pin") ?: "pin"
    fun setLockMethod(method: String) = prefs.edit().putString("lock_method", method).apply()

    // Lock PIN (SHA-256 hash)
    fun getLockPinHash(): String = prefs.getString("lock_pin_hash", "") ?: ""
    fun setLockPinHash(hash: String) = prefs.edit().putString("lock_pin_hash", hash).apply()

    // Intruder photo
    fun isIntruderPhotoEnabled(): Boolean = prefs.getBoolean("intruder_photo", false)
    fun setIntruderPhotoEnabled(enabled: Boolean) = prefs.edit().putBoolean("intruder_photo", enabled).apply()

    // Fake crash
    fun isFakeCrashEnabled(): Boolean = prefs.getBoolean("fake_crash", false)
    fun setFakeCrashEnabled(enabled: Boolean) = prefs.edit().putBoolean("fake_crash", enabled).apply()

    // Auto lock timeout (minutes)
    fun getAutoLockTimeout(): Int = prefs.getInt("auto_lock_timeout", 0)
    fun setAutoLockTimeout(timeout: Int) = prefs.edit().putInt("auto_lock_timeout", timeout).apply()

    // Wrong attempt count
    fun getWrongAttempts(): Int = prefs.getInt("wrong_attempts", 0)
    fun setWrongAttempts(count: Int) = prefs.edit().putInt("wrong_attempts", count).apply()
    fun incrementWrongAttempts() = setWrongAttempts(getWrongAttempts() + 1)

    // ========================================
    // THEME SETTINGS
    // ========================================

    fun getThemeName(): String = prefs.getString("theme_name", "matrix_green") ?: "matrix_green"
    fun setThemeName(name: String) = prefs.edit().putString("theme_name", name).apply()

    fun getFontName(): String = prefs.getString("font_name", "monospace") ?: "monospace"
    fun setFontName(name: String) = prefs.edit().putString("font_name", name).apply()

    fun getAccentColor(): Int = prefs.getInt("accent_color", 0xFF00FF00.toInt())
    fun setAccentColor(color: Int) = prefs.edit().putInt("accent_color", color).apply()

    // Icon pack
    fun getIconPackPackage(): String = prefs.getString("icon_pack", "") ?: ""
    fun setIconPackPackage(pkg: String) = prefs.edit().putString("icon_pack", pkg).apply()

    // Animations
    fun isAnimationsEnabled(): Boolean = prefs.getBoolean("animations", true)
    fun setAnimationsEnabled(enabled: Boolean) = prefs.edit().putBoolean("animations", enabled).apply()

    // ========================================
    // NOTIFICATION SETTINGS
    // ========================================

    fun isShowBadges(): Boolean = prefs.getBoolean("show_badges", true)
    fun setShowBadges(show: Boolean) = prefs.edit().putBoolean("show_badges", show).apply()

    // ========================================
    // WEATHER SETTINGS
    // ========================================

    fun getWeatherApiKey(): String = prefs.getString("weather_api_key", "") ?: ""
    fun setWeatherApiKey(key: String) = prefs.edit().putString("weather_api_key", key).apply()

    fun getWeatherCache(): String = prefs.getString("weather_cache", "") ?: ""
    fun setWeatherCache(json: String) = prefs.edit().putString("weather_cache", json).apply()

    fun getWeatherCacheTime(): Long = prefs.getLong("weather_cache_time", 0)
    fun setWeatherCacheTime(time: Long) = prefs.edit().putLong("weather_cache_time", time).apply()

    fun isTemperatureCelsius(): Boolean = prefs.getBoolean("temp_celsius", true)
    fun setTemperatureCelsius(celsius: Boolean) = prefs.edit().putBoolean("temp_celsius", celsius).apply()

    // ========================================
    // CHAT API SETTINGS
    // ========================================

    fun getChatApiKey(): String = prefs.getString("chat_api_key", "") ?: ""
    fun setChatApiKey(key: String) = prefs.edit().putString("chat_api_key", key).apply()

    fun getChatApiProvider(): String = prefs.getString("chat_api_provider", "openai") ?: "openai"
    fun setChatApiProvider(provider: String) = prefs.edit().putString("chat_api_provider", provider).apply()

    // HIBP API key
    fun getHibpApiKey(): String = prefs.getString("hibp_api_key", "") ?: ""
    fun setHibpApiKey(key: String) = prefs.edit().putString("hibp_api_key", key).apply()

    // ========================================
    // GESTURE SETTINGS
    // ========================================

    fun getSwipeUpAction(): String = prefs.getString("swipe_up", "drawer") ?: "drawer"
    fun setSwipeUpAction(action: String) = prefs.edit().putString("swipe_up", action).apply()

    fun getSwipeDownAction(): String = prefs.getString("swipe_down", "notifications") ?: "notifications"
    fun setSwipeDownAction(action: String) = prefs.edit().putString("swipe_down", action).apply()

    fun getSwipeLeftAction(): String = prefs.getString("swipe_left", "tools") ?: "tools"
    fun setSwipeLeftAction(action: String) = prefs.edit().putString("swipe_left", action).apply()

    fun getSwipeRightAction(): String = prefs.getString("swipe_right", "settings") ?: "settings"
    fun setSwipeRightAction(action: String) = prefs.edit().putString("swipe_right", action).apply()

    fun getDoubleTapAction(): String = prefs.getString("double_tap", "lock") ?: "lock"
    fun setDoubleTapAction(action: String) = prefs.edit().putString("double_tap", action).apply()

    fun getLongPressAction(): String = prefs.getString("long_press", "edit") ?: "edit"
    fun setLongPressAction(action: String) = prefs.edit().putString("long_press", action).apply()

    fun getGestureSensitivity(): Int = prefs.getInt("gesture_sensitivity", 100)
    fun setGestureSensitivity(sensitivity: Int) = prefs.edit().putInt("gesture_sensitivity", sensitivity).apply()

    // ========================================
    // SCAN SETTINGS
    // ========================================

    fun getScanTimeout(): Int = prefs.getInt("scan_timeout", 1000)
    fun setScanTimeout(timeout: Int) = prefs.edit().putInt("scan_timeout", timeout).apply()

    fun getMaxPortScanRange(): Int = prefs.getInt("max_port_scan", 1024)
    fun setMaxPortScanRange(max: Int) = prefs.edit().putInt("max_port_scan", max).apply()

    // ========================================
    // CRYPTO SETTINGS
    // ========================================

    fun getXorKey(): String = prefs.getString("xor_key", "HACKER") ?: "HACKER"
    fun setXorKey(key: String) = prefs.edit().putString("xor_key", key).apply()

    fun getCaesarShift(): Int = prefs.getInt("caesar_shift", 3)
    fun setCaesarShift(shift: Int) = prefs.edit().putInt("caesar_shift", shift).apply()

    fun getPasswordLength(): Int = prefs.getInt("password_length", 16)
    fun setPasswordLength(length: Int) = prefs.edit().putInt("password_length", length).apply()

    // ========================================
    // NOTIFICATION LOG
    // ========================================

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

    // ========================================
    // SEARCH SETTINGS
    // ========================================

    fun getRecentSearches(): List<String> {
        val json = prefs.getString("recent_searches", null) ?: return emptyList()
        return try { gson.fromJson(json, object : TypeToken<List<String>>() {}.type) } catch (e: Exception) { emptyList() }
    }
    fun addRecentSearch(query: String) {
        val searches = getRecentSearches().toMutableList()
        searches.remove(query)
        searches.add(0, query)
        if (searches.size > 20) searches.takeLast(20)
        prefs.edit().putString("recent_searches", gson.toJson(searches)).apply()
    }
    fun clearRecentSearches() = prefs.edit().remove("recent_searches").apply()

    // ========================================
    // TODO SETTINGS
    // ========================================

    fun getTodos(): String = prefs.getString("todos", "[]") ?: "[]"
    fun setTodos(json: String) = prefs.edit().putString("todos", json).apply()

    // ========================================
    // NOTES SETTINGS
    // ========================================

    fun getNotes(): String = prefs.getString("notes", "[]") ?: "[]"
    fun setNotes(json: String) = prefs.edit().putString("notes", json).apply()

    // ========================================
    // LOCATION TRACKING
    // ========================================

    fun isLocationTrackingEnabled(): Boolean = prefs.getBoolean("location_tracking", false)
    fun setLocationTrackingEnabled(enabled: Boolean) = prefs.edit().putBoolean("location_tracking", enabled).apply()

    fun getLocationUpdateInterval(): Int = prefs.getInt("location_interval", 30)
    fun setLocationUpdateInterval(interval: Int) = prefs.edit().putInt("location_interval", interval).apply()

    // ========================================
    // ALWAYS-RUNNING SERVICE CONTROL
    // ========================================

    fun isDaemonServiceEnabled(): Boolean = prefs.getBoolean("daemon_service", true)
    fun setDaemonServiceEnabled(enabled: Boolean) = prefs.edit().putBoolean("daemon_service", enabled).apply()

    fun isWatchdogEnabled(): Boolean = prefs.getBoolean("watchdog_service", true)
    fun setWatchdogEnabled(enabled: Boolean) = prefs.edit().putBoolean("watchdog_service", enabled).apply()

    fun isKeepAliveEnabled(): Boolean = prefs.getBoolean("keep_alive", true)
    fun setKeepAliveEnabled(enabled: Boolean) = prefs.edit().putBoolean("keep_alive", enabled).apply()

    fun isNetworkMonitorEnabled(): Boolean = prefs.getBoolean("network_monitor", true)
    fun setNetworkMonitorEnabled(enabled: Boolean) = prefs.edit().putBoolean("network_monitor", enabled).apply()

    fun isProcessMonitorEnabled(): Boolean = prefs.getBoolean("process_monitor", true)
    fun setProcessMonitorEnabled(enabled: Boolean) = prefs.edit().putBoolean("process_monitor", enabled).apply()

    fun isSystemMonitorEnabled(): Boolean = prefs.getBoolean("system_monitor", true)
    fun setSystemMonitorEnabled(enabled: Boolean) = prefs.edit().putBoolean("system_monitor", enabled).apply()

    // ========================================
    // DEBUG / ADVANCED
    // ========================================

    fun isDebugMode(): Boolean = prefs.getBoolean("debug_mode", false)
    fun setDebugMode(enabled: Boolean) = prefs.edit().putBoolean("debug_mode", enabled).apply()

    // Theme legacy
    fun isDarkTheme(): Boolean = prefs.getBoolean("dark_theme", true)
    fun setDarkTheme(dark: Boolean) = prefs.edit().putBoolean("dark_theme", dark).apply()

    // Wallpaper as background
    fun isWallpaperAsBackground(): Boolean = prefs.getBoolean("wallpaper_background", true)
    fun setWallpaperAsBackground(enabled: Boolean) = prefs.edit().putBoolean("wallpaper_background", enabled).apply()

    // ========================================
    // EXPORT / IMPORT / RESET
    // ========================================

    fun exportAll(): Map<String, *> = prefs.all

    fun importAll(data: Map<String, *>) {
        val editor = prefs.edit()
        for ((key, value) in data) {
            when (value) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Boolean -> editor.putBoolean(key, value)
            }
        }
        editor.apply()
    }

    fun resetAll() = prefs.edit().clear().apply()
}
