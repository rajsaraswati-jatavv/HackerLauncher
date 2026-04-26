package com.hackerlauncher.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.hackerlauncher.MainActivity
import com.hackerlauncher.R
import com.hackerlauncher.utils.Logger

class TerminalWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_UPDATE_LOG = "com.hackerlauncher.UPDATE_WIDGET_LOG"
        private var lastLogLines = ""

        fun updateWidget(context: Context, logText: String) {
            lastLogLines = logText.take(200) // Limit text size
            val intent = Intent(context, TerminalWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_LOG
            }
            context.sendBroadcast(intent)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE_LOG) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, TerminalWidgetProvider::class.java)
            )
            for (id in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, id)
            }
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_terminal)

        // Set log text
        val displayText = if (lastLogLines.isNotEmpty()) lastLogLines else "HackerLauncher\nWaiting for logs..."
        views.setTextViewText(R.id.tvWidgetLog, displayText)

        // Click to open app
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetLayout, openPending)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
