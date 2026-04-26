package com.hackerlauncher.utils

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

class Logger {

    private val tag = "HackerLauncher"
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    companion object {
        private val logBuffer = mutableListOf<String>()
        private const val MAX_LOG_SIZE = 1000

        fun getLogBuffer(): List<String> = logBuffer.toList()
        fun clearLogBuffer() { logBuffer.clear() }
    }

    fun log(message: String, level: LogLevel = LogLevel.INFO) {
        val timestamp = dateFormat.format(Date())
        val entry = "[$timestamp] [$level] $message"

        synchronized(logBuffer) {
            logBuffer.add(entry)
            if (logBuffer.size > MAX_LOG_SIZE) {
                logBuffer.removeAt(0)
            }
        }

        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message)
            LogLevel.INFO -> Log.i(tag, message)
            LogLevel.WARNING -> Log.w(tag, message)
            LogLevel.ERROR -> Log.e(tag, message)
        }
    }

    fun debug(message: String) = log(message, LogLevel.DEBUG)
    fun info(message: String) = log(message, LogLevel.INFO)
    fun warning(message: String) = log(message, LogLevel.WARNING)
    fun error(message: String) = log(message, LogLevel.ERROR)

    enum class LogLevel {
        DEBUG, INFO, WARNING, ERROR
    }
}
