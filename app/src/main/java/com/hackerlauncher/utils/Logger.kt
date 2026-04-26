package com.hackerlauncher.utils

import android.util.Log

/**
 * Centralized logger for HackerLauncher.
 * All services use this for consistent logging.
 */
object Logger {

    private const val APP_TAG = "HackerLauncher"

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
