package com.hackerlauncher.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.hackerlauncher.utils.Logger
import com.hackerlauncher.utils.PreferencesManager

class BootReceiver : BroadcastReceiver() {

    private val logger = Logger()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == Intent.ACTION_REBOOT
        ) {
            logger.log("Boot received: ${intent.action}")

            val prefs = PreferencesManager(context)
            if (prefs.isAutoStartOnBoot()) {
                val serviceIntent = Intent(context, HackerForegroundService::class.java).apply {
                    action = HackerForegroundService.ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                logger.log("HackerForegroundService started on boot")
            }
        }
    }
}
