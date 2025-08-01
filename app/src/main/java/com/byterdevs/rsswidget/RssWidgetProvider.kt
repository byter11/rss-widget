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
            val url = RssWidgetConfigureActivity.loadRssUrlPref(context, appWidgetId) ?: "https://hnrss.org/frontpage?link=comments&description=0"
            val customTitle = RssWidgetConfigureActivity.loadTitlePref(context, appWidgetId)
            val maxItems = RssWidgetConfigureActivity.loadMaxItemsPref(context, appWidgetId)
            val showDescription = RssWidgetConfigureActivity.loadDescriptionPref(context, appWidgetId)
            updateAppWidget(context, appWidgetManager, appWidgetId, url, customTitle, maxItems, showDescription)
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

    companion object {
        const val PREFS_NAME = "com.byterdevs.rsswidget.RssWidgetProvider"
        const val PREF_PREFIX_KEY = "rss_url_"
        fun loadRssUrlPref(context: Context, appWidgetId: Int): String? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(PREF_PREFIX_KEY + appWidgetId, null)
        }

        // Add this function to update the widget with the selected URL
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, url: String, customTitle: String? = null, maxItems: Int = 20, showDescription: Boolean = false) {
            val views = RemoteViews(context.packageName, R.layout.widget_rss)
            val intent = Intent(context, RssWidgetService::class.java)
            intent.putExtra("rss_url", url)
            intent.putExtra("max_items", maxItems)
            intent.putExtra("show_description", showDescription)
            views.setRemoteAdapter(R.id.widget_list, intent)
            views.setEmptyView(R.id.widget_list, R.id.empty_text)

            // Set custom title if provided
            if (!customTitle.isNullOrEmpty()) {
                views.setTextViewText(R.id.widget_title, customTitle)
                views.setViewVisibility(R.id.widget_title, android.view.View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_title, android.view.View.GONE)
            }

            // Set up click and refresh intents
            val clickIntent = Intent(context, BrowserLauncherActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_list, pendingIntent)

            val refreshIntent = Intent(context, RssWidgetProvider::class.java).apply {
                action = "com.byterdevs.rsswidget.ACTION_REFRESH"
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_refresh, refreshPendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

}
