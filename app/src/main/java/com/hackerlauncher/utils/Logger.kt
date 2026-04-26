package com.hackerlauncher.utils

import android.util.Log

/**
 * Centralized logger for HackerLauncher.
 * All services use this for consistent logging.
 */
object Logger {

    private const val APP_TAG = "HackerLauncher"
    private val logBuffer = mutableListOf<String>()
    private const val MAX_LOG_BUFFER = 200

    /** Allow Logger to be called as a function: Logger("message") */
    operator fun invoke(message: String) {
        log(message)
    }

    fun log(message: String) {
        val entry = "[${System.currentTimeMillis()}] $message"
        synchronized(logBuffer) {
            logBuffer.add(entry)
            if (logBuffer.size > MAX_LOG_BUFFER) logBuffer.removeAt(0)
        }
        Log.d(APP_TAG, message)
    }

    fun info(message: String) {
        log(message)
        Log.i(APP_TAG, message)
    }

    fun error(message: String) {
        log(message)
        Log.e(APP_TAG, message)
    }

    fun getLogBuffer(): List<String> {
        synchronized(logBuffer) {
            return logBuffer.toList()
        }
    }

    fun d(tag: String, message: String) {
        Log.d("$APP_TAG:$tag", message)
    }

    fun i(tag: String, message: String) {
        Log.i("$APP_TAG:$tag", message)
    }

    fun w(tag: String, message: String) {
        Log.w("$APP_TAG:$tag", message)
    }

    fun w(tag: String, message: String, throwable: Throwable) {
        Log.w("$APP_TAG:$tag", message, throwable)
    }

    fun e(tag: String, message: String) {
        Log.e("$APP_TAG:$tag", message)
    }

    fun e(tag: String, message: String, throwable: Throwable) {
        Log.e("$APP_TAG:$tag", message, throwable)
    }

    fun v(tag: String, message: String) {
        Log.v("$APP_TAG:$tag", message)
    }
}
