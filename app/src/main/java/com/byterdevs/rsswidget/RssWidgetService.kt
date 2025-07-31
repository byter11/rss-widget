package com.byterdevs.rsswidget

import android.content.Intent
import android.widget.RemoteViewsService

class RssWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return RssRemoteViewsFactory(this.applicationContext)
    }
}
