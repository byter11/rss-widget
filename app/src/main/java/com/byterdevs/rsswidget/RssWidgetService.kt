package com.byterdevs.rsswidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.widget.RemoteViewsService

class RssWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val rssUrl = intent.getStringExtra("rss_url")
        val maxItems = intent.getIntExtra("max_items", 20)
        val showDescription = intent.getBooleanExtra("show_description", false)
        val customTitle = intent.getStringExtra("custom_title")
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        val factory = RssRemoteViewsFactory(this.applicationContext, rssUrl, maxItems, showDescription)
        factory.setHeader(customTitle)
        factory.setAppWidgetId(appWidgetId)
        return factory
    }
}
