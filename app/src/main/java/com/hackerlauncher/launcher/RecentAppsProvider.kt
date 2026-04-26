package com.hackerlauncher.launcher

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import java.util.Calendar
import java.util.concurrent.TimeUnit

// ─── App Usage Data ───────────────────────────────────────────────────────────

data class AppUsageInfo(
    val packageName: String,
    val lastUsed: Long,
    val totalTime: Long,       // Total time in foreground (ms)
    val launchCount: Int       // Number of times launched
) {
    fun getFormattedTime(): String {
        val hours = TimeUnit.MILLISECONDS.toHours(totalTime)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(totalTime) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(totalTime) % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    fun getFormattedLastUsed(): String {
        val now = System.currentTimeMillis()
        val diff = now - lastUsed
        return when {
            diff < 60_000 -> "just now"
            diff < 3_600_000 -> "${diff / 60_000}m ago"
            diff < 86_400_000 -> "${diff / 3_600_000}h ago"
            diff < 604_800_000 -> "${diff / 86_400_000}d ago"
            else -> "${diff / 604_800_000}w ago"
        }
    }
}

// ─── Cache Entry ──────────────────────────────────────────────────────────────

private data class CacheEntry<T>(
    val data: T,
    val timestamp: Long
) {
    fun isExpired(cacheDurationMs: Long): Boolean {
        return System.currentTimeMillis() - timestamp > cacheDurationMs
    }
}

// ─── Recent Apps Provider ─────────────────────────────────────────────────────

class RecentAppsProvider private constructor(private val context: Context) {

    companion object {
        private const val TAG = "RecentAppsProvider"
        private const val CACHE_DURATION_MS = 5 * 60 * 1000L // 5 minutes

        @Volatile
        private var instance: RecentAppsProvider? = null

        fun getInstance(context: Context): RecentAppsProvider {
            return instance ?: synchronized(this) {
                instance ?: RecentAppsProvider(context.applicationContext).also { instance = it }
            }
        }
    }

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    // Caches
    private var recentAppsCache: CacheEntry<List<AppUsageInfo>>? = null
    private var mostUsedAppsCache: CacheEntry<List<AppUsageInfo>>? = null
    private var dailyUsageCache: CacheEntry<Map<String, Long>>? = null
    private var weeklyUsageCache: CacheEntry<Map<String, Long>>? = null
    private var appUsageTimeCache: CacheEntry<Map<String, AppUsageInfo>>? = null

    // ─── Permission Check ──────────────────────────────────────────────────

    fun hasUsageAccess(): Boolean {
        val now = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 1000 * 60,
            now
        )
        return stats != null && stats.isNotEmpty()
    }

    fun requestUsageAccess() {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open usage access settings", e)
        }
    }

    // ─── Get Recent Apps ───────────────────────────────────────────────────

    fun getRecentApps(limit: Int = 10): List<AppUsageInfo> {
        recentAppsCache?.let { cache ->
            if (!cache.isExpired(CACHE_DURATION_MS)) return cache.data.take(limit)
        }

        val now = System.currentTimeMillis()
        val startTime = now - TimeUnit.DAYS.toMillis(1)

        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startTime, now
        )

        if (usageStats.isNullOrEmpty()) return emptyList()

        val recentApps = usageStats
            .filter { it.lastTimeUsed > 0 && it.packageName != context.packageName }
            .sortedByDescending { it.lastTimeUsed }
            .map { stats ->
                AppUsageInfo(
                    packageName = stats.packageName,
                    lastUsed = stats.lastTimeUsed,
                    totalTime = stats.totalTimeInForeground,
                    launchCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        stats.appLaunchCount
                    } else {
                        @Suppress("DEPRECATION")
                        0
                    }
                )
            }

        recentAppsCache = CacheEntry(recentApps, System.currentTimeMillis())
        return recentApps.take(limit)
    }

    // ─── Get Most Used Apps ────────────────────────────────────────────────

    fun getMostUsedApps(limit: Int = 10): List<AppUsageInfo> {
        mostUsedAppsCache?.let { cache ->
            if (!cache.isExpired(CACHE_DURATION_MS)) return cache.data.take(limit)
        }

        val now = System.currentTimeMillis()
        val startTime = now - TimeUnit.DAYS.toMillis(7)

        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_WEEKLY, startTime, now
        )

        if (usageStats.isNullOrEmpty()) return emptyList()

        val mostUsed = usageStats
            .filter { it.totalTimeInForeground > 0 && it.packageName != context.packageName }
            .sortedByDescending { it.totalTimeInForeground }
            .map { stats ->
                AppUsageInfo(
                    packageName = stats.packageName,
                    lastUsed = stats.lastTimeUsed,
                    totalTime = stats.totalTimeInForeground,
                    launchCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        stats.appLaunchCount
                    } else {
                        @Suppress("DEPRECATION")
                        0
                    }
                )
            }

        mostUsedAppsCache = CacheEntry(mostUsed, System.currentTimeMillis())
        return mostUsed.take(limit)
    }

    // ─── Get App Usage Time ────────────────────────────────────────────────

    fun getAppUsageTime(packageName: String): Long {
        return getAppUsageMap()[packageName]?.totalTime ?: 0L
    }

    fun getAppUsageInfo(packageName: String): AppUsageInfo? {
        return getAppUsageMap()[packageName]
    }

    private fun getAppUsageMap(): Map<String, AppUsageInfo> {
        appUsageTimeCache?.let { cache ->
            if (!cache.isExpired(CACHE_DURATION_MS)) return cache.data
        }

        val now = System.currentTimeMillis()
        val startTime = now - TimeUnit.DAYS.toMillis(1)

        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startTime, now
        )

        val usageMap = mutableMapOf<String, AppUsageInfo>()
        if (usageStats != null) {
            for (stats in usageStats) {
                if (stats.totalTimeInForeground > 0) {
                    usageMap[stats.packageName] = AppUsageInfo(
                        packageName = stats.packageName,
                        lastUsed = stats.lastTimeUsed,
                        totalTime = stats.totalTimeInForeground,
                        launchCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            stats.appLaunchCount
                        } else {
                            @Suppress("DEPRECATION")
                            0
                        }
                    )
                }
            }
        }

        appUsageTimeCache = CacheEntry(usageMap, System.currentTimeMillis())
        return usageMap
    }

    // ─── Get Daily Usage ───────────────────────────────────────────────────

    fun getDailyUsage(): Map<String, Long> {
        dailyUsageCache?.let { cache ->
            if (!cache.isExpired(CACHE_DURATION_MS)) return cache.data
        }

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = cal.timeInMillis
        val endTime = System.currentTimeMillis()

        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startTime, endTime
        )

        val dailyMap = mutableMapOf<String, Long>()
        if (usageStats != null) {
            for (stats in usageStats) {
                if (stats.totalTimeInForeground > 0 && stats.packageName != context.packageName) {
                    dailyMap[stats.packageName] = stats.totalTimeInForeground
                }
            }
        }

        dailyUsageCache = CacheEntry(dailyMap, System.currentTimeMillis())
        return dailyMap
    }

    // ─── Get Weekly Usage ──────────────────────────────────────────────────

    fun getWeeklyUsage(): Map<String, Long> {
        weeklyUsageCache?.let { cache ->
            if (!cache.isExpired(CACHE_DURATION_MS)) return cache.data
        }

        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -7)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = cal.timeInMillis
        val endTime = System.currentTimeMillis()

        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_WEEKLY, startTime, endTime
        )

        val weeklyMap = mutableMapOf<String, Long>()
        if (usageStats != null) {
            for (stats in usageStats) {
                if (stats.totalTimeInForeground > 0 && stats.packageName != context.packageName) {
                    weeklyMap[stats.packageName] = stats.totalTimeInForeground
                }
            }
        }

        weeklyUsageCache = CacheEntry(weeklyMap, System.currentTimeMillis())
        return weeklyMap
    }

    // ─── Get Usage Stats Formatted ─────────────────────────────────────────

    fun getDailyUsageFormatted(): Map<String, String> {
        return getDailyUsage().mapValues { (_, time) -> formatDuration(time) }
    }

    fun getWeeklyUsageFormatted(): Map<String, String> {
        return getWeeklyUsage().mapValues { (_, time) -> formatDuration(time) }
    }

    // ─── Aggregate Stats ───────────────────────────────────────────────────

    fun getTotalDailyScreenTime(): Long {
        return getDailyUsage().values.sum()
    }

    fun getTotalWeeklyScreenTime(): Long {
        return getWeeklyUsage().values.sum()
    }

    fun getDailyAppCount(): Int {
        return getDailyUsage().size
    }

    fun getWeeklyAppCount(): Int {
        return getWeeklyUsage().size
    }

    // ─── Invalidate Cache ──────────────────────────────────────────────────

    fun invalidateCache() {
        recentAppsCache = null
        mostUsedAppsCache = null
        dailyUsageCache = null
        weeklyUsageCache = null
        appUsageTimeCache = null
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private fun formatDuration(ms: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(ms)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return when {
            hours > 0 -> String.format("%dh %dm", hours, minutes)
            minutes > 0 -> String.format("%dm %ds", minutes, seconds)
            else -> String.format("%ds", seconds)
        }
    }

    // Suppress Build import - use android.os.Build
    private object Build {
        val SDK_INT = android.os.Build.VERSION.SDK_INT
        val VERSION = android.os.Build.VERSION
    }
}
