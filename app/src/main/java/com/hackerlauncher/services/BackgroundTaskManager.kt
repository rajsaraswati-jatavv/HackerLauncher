package com.hackerlauncher.services

import android.content.Context
import androidx.work.*
import com.hackerlauncher.utils.Logger
import java.util.concurrent.TimeUnit

class BackgroundTaskManager {

    companion object {
        private const val TAG_PERIODIC_SCAN = "periodic_scan"
        private const val TAG_CLEANUP = "periodic_cleanup"
        private var initialized = false
        private val logger = Logger()

        fun init(context: Context) {
            if (initialized) return
            initialized = true

            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .setRequiresCharging(false)
                .build()

            // Periodic network scan every 15 minutes
            val scanRequest = PeriodicWorkRequestBuilder<NetworkScanWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .addTag(TAG_PERIODIC_SCAN)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TAG_PERIODIC_SCAN,
                ExistingPeriodicWorkPolicy.KEEP,
                scanRequest
            )

            // Periodic cleanup every 1 hour
            val cleanupRequest = PeriodicWorkRequestBuilder<CleanupWorker>(
                1, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .addTag(TAG_CLEANUP)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TAG_CLEANUP,
                ExistingPeriodicWorkPolicy.KEEP,
                cleanupRequest
            )

            logger.log("BackgroundTaskManager initialized with periodic tasks")
        }

        fun scheduleOneTimeScan(context: Context) {
            val request = OneTimeWorkRequestBuilder<NetworkScanWorker>()
                .addTag("one_time_scan")
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(TAG_PERIODIC_SCAN)
            WorkManager.getInstance(context).cancelAllWorkByTag(TAG_CLEANUP)
        }
    }
}

class NetworkScanWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val logger = Logger()
        logger.log("Periodic network scan triggered")
        // Perform lightweight network check
        return try {
            // Basic connectivity check
            val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(network)
            val hasInternet = caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            logger.log("Network scan: Internet available = $hasInternet")
            Result.success()
        } catch (e: Exception) {
            logger.log("Network scan failed: ${e.message}")
            Result.retry()
        }
    }
}

class CleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val logger = Logger()
        logger.log("Periodic cleanup triggered")
        return try {
            // Clean old logs and temp files
            val cacheDir = applicationContext.cacheDir
            cacheDir?.walkTopDown()?.forEach { file ->
                if (file.isFile && file.lastModified() < System.currentTimeMillis() - 24 * 60 * 60 * 1000) {
                    file.delete()
                }
            }
            logger.log("Cleanup completed")
            Result.success()
        } catch (e: Exception) {
            logger.log("Cleanup failed: ${e.message}")
            Result.retry()
        }
    }
}
