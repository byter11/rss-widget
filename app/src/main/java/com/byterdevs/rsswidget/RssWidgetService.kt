package com.byterdevs.rsswidget

import android.content.Intent
import android.widget.RemoteViewsService

class RssWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val rssUrl = intent.getStringExtra("rss_url")
        val maxItems = intent.getIntExtra("max_items", 20)
        val showDescription = intent.getBooleanExtra("show_description", false)
        return RssRemoteViewsFactory(this.applicationContext, rssUrl, maxItems, showDescription)
    }
}
