package com.byterdevs.rsswidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.WindowManager
import android.graphics.Color
import com.byterdevs.rsswidget.webview.BottomSheetWebView
import androidx.core.net.toUri
import androidx.core.graphics.drawable.toDrawable

class BrowserLauncherActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        window.decorView.setBackgroundColor(Color.TRANSPARENT)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        supportActionBar?.hide()
        Log.d("RssWidgetProvider", "BrowserLauncherActivity onCreate called.")
        val link = intent.getStringExtra("EXTRA_LINK")
        if(link.isNullOrEmpty()) return

        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        if(appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        ReadItemsStore.markRead(this, appWidgetId, link)
        val prefs = applicationContext.getWidgetPrefs(appWidgetId)
        if (prefs.readFilterMode != ReadFilterMode.SHOW) {
            AppWidgetManager.getInstance(this).notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
        }
        val readerType = prefs.readerType

        when (readerType) {
            ReaderType.INTERNAL -> openInternal(appWidgetId, link, false)
            ReaderType.READER -> openInternal(appWidgetId, link, true)
            ReaderType.EXTERNAL -> openExternal(appWidgetId, link)
        }
    }

    fun openInternal(appWidgetId: Int, link: String, readerable: Boolean) {
        val bottomSheetWebView = BottomSheetWebView(this, this, readerable)
        bottomSheetWebView.showWithUrl(link)
    }

    fun openExternal(appWidgetId: Int, link: String) {
        Log.d("RssWidgetProvider", "Received link extra: $link")

        if (link.isNotEmpty()) {

            val browserIntent = Intent(Intent.ACTION_VIEW, link.toUri())
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