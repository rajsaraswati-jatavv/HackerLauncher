package com.hackerlauncher.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import com.hackerlauncher.utils.Logger

class AccessibilityHelper : AccessibilityService() {

    private val logger = Logger()

    companion object {
        var isRunning = false
            private set
        var lastEvent: String = ""
            private set
        var eventLog: MutableList<String> = mutableListOf()
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        logger.log("AccessibilityHelper connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val eventStr = "${event.eventType}:${event.packageName}:${event.className}:${event.text}"
        lastEvent = eventStr
        if (eventLog.size > 500) eventLog.removeAt(0)
        eventLog.add(eventStr)
    }

    override fun onInterrupt() {
        logger.log("AccessibilityHelper interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        logger.log("AccessibilityHelper destroyed")
    }
}
