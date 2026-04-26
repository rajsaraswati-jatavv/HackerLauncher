package com.hackerlauncher.launcher

import android.content.ComponentName
import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: Drawable,
    val isSystemApp: Boolean = false,
    var notificationCount: Int = 0,
    var isSelected: Boolean = false
) {
    fun getComponentName(): ComponentName = ComponentName(packageName, activityName)
}

enum class ViewMode {
    GRID,    // Icon + label, 4 columns
    LIST     // Icon + name + package + size
}
