package com.hackerlauncher.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.hackerlauncher.utils.Logger

/**
 * BootReceiver - Starts all services on device boot.
 * CRITICAL FIX: Stagger service starts to prevent crash.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Logger.i(TAG, "BootReceiver triggered: ${intent?.action}")

        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_REBOOT,
            Intent.ACTION_USER_PRESENT,
            Intent.ACTION_SCREEN_ON,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                startServicesStaggered(context)
            }
        }
    }

    private fun startServicesStaggered(context: Context) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

        // Start core service immediately
        tryStartService(context, HackerForegroundService::class.java, HackerForegroundService.ACTION_START)

        // Stagger the rest
        mainHandler.postDelayed({
            tryStartService(context, DaemonService::class.java, DaemonService.ACTION_START)
        }, 3000L)

        mainHandler.postDelayed({
            tryStartService(context, WatchdogService::class.java, WatchdogService.ACTION_START)
        }, 6000L)

        mainHandler.postDelayed({
            tryStartService(context, KeepAliveService::class.java, KeepAliveService.ACTION_START)
        }, 9000L)

        mainHandler.postDelayed({
            tryStartService(context, NetworkMonitorService::class.java, NetworkMonitorService.ACTION_START)
        }, 12000L)

        mainHandler.postDelayed({
            tryStartService(context, ProcessMonitorService::class.java, ProcessMonitorService.ACTION_START)
        }, 15000L)

        mainHandler.postDelayed({
            tryStartService(context, SystemMonitorService::class.java, SystemMonitorService.ACTION_START)
        }, 18000L)
    }

    private fun tryStartService(context: Context, serviceClass: Class<*>, action: String) {
        try {
            val intent = Intent(context, serviceClass).apply { this.action = action }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Logger.i(TAG, "Boot-started: ${serviceClass.simpleName}")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to boot-start ${serviceClass.simpleName}: ${e.message}")
        }
    }
}
