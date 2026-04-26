package com.hackerlauncher.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.hackerlauncher.launcher.AppLockService
import com.hackerlauncher.utils.PreferencesManager

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "Received broadcast: $action")

        val prefs = PreferencesManager(context)

        // Always start core services regardless of action
        startAllCoreServices(context, prefs)

        // Log boot event
        Log.i(TAG, "HackerLauncher services started on: $action")
    }

    private fun startAllCoreServices(context: Context, prefs: PreferencesManager) {
        val servicesToStart = mutableListOf<Class<*>>()

        // ALWAYS start these services - they are always-running
        servicesToStart.add(DaemonService::class.java)
        servicesToStart.add(WatchdogService::class.java)
        servicesToStart.add(KeepAliveService::class.java)
        servicesToStart.add(HackerForegroundService::class.java)
        servicesToStart.add(NetworkMonitorService::class.java)
        servicesToStart.add(ProcessMonitorService::class.java)
        servicesToStart.add(SystemMonitorService::class.java)

        // Optional services based on settings
        if (prefs.isAppLockEnabled()) {
            servicesToStart.add(AppLockService::class.java)
        }
        if (prefs.isLocationTrackingEnabled()) {
            servicesToStart.add(LocationTrackerService::class.java)
        }
        if (prefs.isOverlayEnabled()) {
            servicesToStart.add(OverlayService::class.java)
        }

        for (serviceClass in servicesToStart) {
            try {
                val serviceIntent = Intent(context, serviceClass).apply {
                    action = "ACTION_START"
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                Log.d(TAG, "Started service: ${serviceClass.simpleName}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start ${serviceClass.simpleName}: ${e.message}")
            }
        }
    }
}
