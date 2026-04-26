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

class WeatherWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val ACTION_REFRESH = "com.hackerlauncher.ACTION_REFRESH_WEATHER"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId, "Loading...", "--", "N/A")
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, WeatherWidgetProvider::class.java)
            )
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        temperature: String,
        condition: String,
        location: String
    ) {
        val views = RemoteViews(context.packageName, R.layout.weather_widget)

        views.setTextViewText(R.id.tvWidgetTemp, temperature)
        views.setTextViewText(R.id.tvWidgetCondition, condition)
        views.setTextViewText(R.id.tvWidgetLocation, location)

        // Click to open app
        val openIntent = Intent(context, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.weatherWidgetRoot, openPendingIntent)

        // Refresh button
        val refreshIntent = Intent(context, WeatherWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context, 0, refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.tvWidgetTemp, refreshPendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
