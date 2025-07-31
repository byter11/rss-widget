package com.byterdevs.rsswidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.util.Log

class RssWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_rss)
            val intent = Intent(context, RssWidgetService::class.java)
            views.setRemoteAdapter(R.id.widget_list, intent)
            views.setEmptyView(R.id.widget_list, R.id.empty_text)

            // This intent now points directly to your BrowserLauncherActivity
            val clickIntent = Intent(context, BrowserLauncherActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_list, pendingIntent)

            // Add refresh button PendingIntent
            val refreshIntent = Intent(context, RssWidgetProvider::class.java).apply {
                action = "com.byterdevs.rsswidget.ACTION_REFRESH"
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId, // unique per widget
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_refresh, refreshPendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }


    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d("RssWidgetProvider", "onReceive triggered with action: ${intent.action}")

        if (intent.action == "com.byterdevs.rsswidget.ACTION_REFRESH") {
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            Log.d("RssWidgetProvider", "appWidgetId: $appWidgetId")
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
            }
        }
    }

}
