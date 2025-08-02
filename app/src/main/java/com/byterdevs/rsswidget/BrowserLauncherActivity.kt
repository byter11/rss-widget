package com.byterdevs.rsswidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.util.Log

class BrowserLauncherActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("RssWidgetProvider", "BrowserLauncherActivity onCreate called.")

        val link = intent.getStringExtra("EXTRA_LINK")
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        Log.d("RssWidgetProvider", "Received link extra: $link")

        if (!link.isNullOrEmpty()) {
            if(appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                ReadItemsStore.markRead(this, appWidgetId, link)
            }

            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                Log.d("RssWidgetProvider", "Attempting to start browser with link: $link")
                startActivity(browserIntent)
            } catch (e: Exception) {
                Log.e("RssWidgetProvider", "Failed to start browser", e)
            }
        }

        finish()
    }
}