package com.hackerlauncher.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

class TermuxBridge {

    companion object {
        private const val TERMUX_PACKAGE = "com.termux"
        private const val TERMUX_MAIN_ACTIVITY = "com.termux.app.TermuxActivity"
        private const val TERMUX_SERVICE = "com.termux.app.TermuxService"

        fun isTermuxInstalled(context: Context): Boolean {
            return try {
                context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
                true
            } catch (e: Exception) {
                false
            }
        }

        fun openTermux(context: Context) {
            try {
                val intent = Intent().apply {
                    setClassName(TERMUX_PACKAGE, TERMUX_MAIN_ACTIVITY)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Logger.log("Termux activity not found: ${e.message}")
            }
        }

        fun runCommand(context: Context, command: String) {
            if (!isTermuxInstalled(context)) return
            try {
                val intent = Intent().apply {
                    setClassName(TERMUX_PACKAGE, TERMUX_SERVICE)
                    action = "com.termux.RUN_COMMAND"
                    putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/$TERMUX_PACKAGE/files/usr/bin/bash")
                    putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
                    putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startService(intent)
            } catch (e: Exception) {
                Logger.log("Termux command failed: ${e.message}")
            }
        }

        fun promptInstallTermux(context: Context) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://f-droid.org/packages/$TERMUX_PACKAGE/")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                // Try Play Store
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("market://details?id=$TERMUX_PACKAGE")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (e2: Exception) {
                    Logger.log("Cannot open store: ${e2.message}")
                }
            }
        }

        fun getTermuxPrefix(context: Context): String {
            return if (isTermuxInstalled(context)) {
                "/data/data/$TERMUX_PACKAGE/files/usr"
            } else {
                "/usr"
            }
        }
    }
}
